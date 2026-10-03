package com.nook.msgapp.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.push.Push
import kotlinx.coroutines.tasks.await

object Auth {
    /** Shows the Google account picker and returns an ID token, or null if the user backed out. */
    private suspend fun googleIdToken(activity: Activity): String? {
        if (AppConfig.googleWebClientId.isEmpty()) {
            throw UserFacingError("Google sign-in is not set up yet: nook.googleWebClientId is missing from gradle.properties.")
        }
        val option = GetSignInWithGoogleOption.Builder(AppConfig.googleWebClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(cred.data).idToken
            } else {
                throw UserFacingError("Google didn't return an account. Try again.")
            }
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (_: NoCredentialException) {
            throw UserFacingError("No Google account found on this phone. Add one in Settings > Accounts.")
        } catch (e: GetCredentialException) {
            val msg = e.message.orEmpty()
            if (msg.contains("10") || msg.contains("DEVELOPER_ERROR", ignoreCase = true)) {
                throw UserFacingError("Google sign-in isn't configured for this build. Add the app's SHA-1 in Firebase.")
            }
            throw UserFacingError("Google sign-in failed: ${e.type.substringAfterLast('.')}")
        }
    }

    /** Returns true when signed in, false when the user backed out of the picker. */
    suspend fun signInWithGoogle(activity: Activity): Boolean {
        val token = googleIdToken(activity) ?: return false
        Fb.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        return true
    }

    /** Stops push, presence and listeners for this device. */
    private suspend fun teardown(uid: String?) {
        if (uid != null) {
            Push.unregister(uid)
            Presence.stop()
        }
        Session.resetData()
    }

    private suspend fun clearGoogle(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Signs out of Firebase and Google and clears this device's app lock (also the "forgot PIN" path). */
    suspend fun signOut(context: Context) {
        teardown(Fb.uid)
        LockStore.clear()
        Prefs.resetForSignOut()
        clearGoogle(context)
        Fb.auth.signOut()
    }

    /**
     * Deletes this account: my messages (and their media), push tokens, profile, username
     * reservation and the Firebase Auth user. Re-authenticates with Google first.
     */
    suspend fun deleteAccount(activity: Activity, username: String?): Boolean {
        val user = Fb.auth.currentUser ?: return false
        val token = googleIdToken(activity) ?: return false
        user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()

        deleteMyMessages(user.uid)
        teardown(user.uid)
        runCatching { Private.delete(user.uid) }

        val batch = Fb.db.batch()
        batch.delete(Fb.users().document(user.uid))
        if (!username.isNullOrEmpty()) batch.delete(Fb.usernames().document(username))
        batch.commit().await()

        user.delete().await()
        LockStore.clear()
        Prefs.resetForSignOut()
        clearGoogle(activity)
        return true
    }

    /** Deletes every message I sent, in pages. Media goes through the Worker so files are removed too. */
    private suspend fun deleteMyMessages(uid: String) {
        repeat(50) {
            val snap = Fb.db.collectionGroup("messages").whereEqualTo("senderId", uid).limit(200).get().await()
            if (snap.isEmpty) return
            val plain = Fb.db.batch()
            var plainCount = 0
            val media = mutableMapOf<String, MutableList<String>>()
            snap.documents.forEach { d ->
                val chatId = d.reference.parent.parent?.id
                @Suppress("UNCHECKED_CAST")
                val publicId = (d.get("media") as? Map<String, Any?>)?.get("publicId") as? String
                if (chatId != null && !publicId.isNullOrEmpty() && AppConfig.workerConfigured) {
                    media.getOrPut(chatId) { mutableListOf() }.add(d.id)
                } else {
                    plain.delete(d.reference)
                    plainCount++
                }
            }
            media.forEach { (chatId, ids) -> Worker.call("/messages/delete", mapOf("chatId" to chatId, "messageIds" to ids)) }
            if (plainCount > 0) plain.commit().await()
        }
    }

}

package com.nook.msgapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * userPrivate/{uid}: only its owner (and the Worker, via its service account) can read it.
 * Holds this account's FCM device tokens and the people it has blocked.
 */
object Private {
    suspend fun setBlocked(me: String, other: String, blocked: Boolean) {
        Fb.userPrivate(me).set(
            mapOf(
                "blocked" to if (blocked) FieldValue.arrayUnion(other) else FieldValue.arrayRemove(other),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun addPushToken(me: String, token: String) {
        Fb.userPrivate(me).set(
            mapOf("tokens" to FieldValue.arrayUnion(token), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun removePushToken(me: String, token: String) {
        Fb.userPrivate(me).set(
            mapOf("tokens" to FieldValue.arrayRemove(token), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun delete(me: String) {
        Fb.userPrivate(me).delete().await()
    }
}

package com.nook.msgapp.data

import android.content.Context
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.nook.core.ChatLogic
import com.nook.core.Profile
import com.nook.core.Username
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Public profiles: claims, lookups and an in-memory cache of the people I talk to. */
object Profiles {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _cache = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val cache: StateFlow<Map<String, Profile>> = _cache.asStateFlow()
    private val inFlight = mutableSetOf<String>()

    fun get(uid: String?): Profile? = uid?.let { _cache.value[it] }

    fun put(profile: Profile) = _cache.update { it + (profile.uid to profile) }

    fun reset() {
        _cache.value = emptyMap()
        synchronized(inFlight) { inFlight.clear() }
    }

    /** Fetches missing profiles in batches of 10 (one read each). */
    fun ensure(uids: Collection<String>) {
        val missing = synchronized(inFlight) {
            uids.filter { it.isNotEmpty() && it !in _cache.value && inFlight.add(it) }
        }
        if (missing.isEmpty()) return
        scope.launch {
            missing.chunked(10).forEach { chunk ->
                runCatching {
                    val snap = Fb.users().whereIn(FieldPath.documentId(), chunk).get().await()
                    val found = snap.documents.mapNotNull { it.toProfile() }
                    _cache.update { map -> map + found.associateBy { it.uid } }
                }
                synchronized(inFlight) { inFlight.removeAll(chunk.toSet()) }
            }
        }
    }

    /** Re-fetches one profile (e.g. after opening their info screen). */
    suspend fun refresh(uid: String): Profile? {
        val p = Fb.users().document(uid).get().await().toProfile()
        if (p != null) put(p)
        return p
    }

    /** One document read. Callers debounce so typing doesn't burn the read quota. */
    suspend fun isUsernameAvailable(raw: String): Boolean =
        !Fb.usernames().document(Username.normalize(raw)).get().await().exists()

    /** Atomically reserves usernames/{name} and creates users/{uid}. Rules make the username permanent. */
    suspend fun claimUsername(uid: String, raw: String, displayName: String, photoURL: String?) {
        val username = Username.normalize(raw)
        Username.validate(username)?.let { throw UserFacingError(it) }
        val nameRef = Fb.usernames().document(username)
        val userRef = Fb.users().document(uid)
        Fb.db.runTransaction<Void?> { tx ->
            val nameSnap = tx.get(nameRef)
            val userSnap = tx.get(userRef)
            if (userSnap.exists()) throw UserFacingError("You already picked a username.")
            if (nameSnap.exists()) throw UserFacingError("That username was just taken. Try another.")
            tx.set(nameRef, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            tx.set(
                userRef,
                mapOf(
                    "uid" to uid,
                    "username" to username,
                    "displayName" to (displayName.trim().take(40).ifEmpty { username }),
                    "photoURL" to photoURL,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            null
        }.await()
    }

    /** Exact-username lookup: two document reads, no listing (usernames can't be enumerated). */
    suspend fun findByUsername(raw: String): Profile? {
        val name = Username.normalize(raw)
        if (Username.validate(name) != null) return null
        val nameSnap = Fb.usernames().document(name).get().await()
        val uid = nameSnap.getString("uid") ?: return null
        return Fb.users().document(uid).get().await().toProfile()?.also { put(it) }
    }

    /** Uploads a photo to Cloudinary and sets it as the profile picture (null removes it). */
    suspend fun setPhoto(context: Context, uid: String, file: PickedFile?, onProgress: (Float) -> Unit = {}) {
        val url = file?.let { ChatLogic.thumbnailUrl(Cloudinary.upload(context, it, "image", onProgress).url, 512) }
        Fb.users().document(uid).update("photoURL", url).await()
    }

    suspend fun setDisplayName(uid: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) throw UserFacingError("Enter a name.")
        Fb.users().document(uid).update("displayName", clean).await()
    }
}

package com.nook.msgapp.push

import com.google.firebase.messaging.FirebaseMessaging
import com.nook.msgapp.data.Private
import kotlinx.coroutines.tasks.await

/**
 * Push payloads never contain message text. Data keys:
 *   type: 'message' | 'call' | 'call_cancel', chatId, callId, video ('1' | '0'), callerName
 */
object Push {
    @Volatile private var token: String? = null

    suspend fun register(uid: String) {
        try {
            val t = FirebaseMessaging.getInstance().token.await()
            token = t
            Private.addPushToken(uid, t)
        } catch (_: Exception) {
            // No Play services / offline: try again next launch.
        }
    }

    suspend fun onNewToken(uid: String, t: String) {
        token = t
        runCatching { Private.addPushToken(uid, t) }
    }

    /** Called before sign-out / account deletion so this phone stops receiving pushes. */
    suspend fun unregister(uid: String) {
        val t = token ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        token = null
        if (t != null) runCatching { Private.removePushToken(uid, t) }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }
}

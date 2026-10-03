package com.nook.msgapp.nav

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a notification tap (or the call screen) wants the app to go. */
sealed interface DeepLink {
    data class Chat(val chatId: String) : DeepLink
    data class IncomingCall(val callId: String, val accept: Boolean) : DeepLink
}

object DeepLinks {
    const val EXTRA_TYPE = "type"
    const val EXTRA_CHAT_ID = "chatId"
    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_ACCEPT = "accept"

    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun post(link: DeepLink) {
        _pending.value = link
    }

    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }

    /** FCM puts data keys into the launch intent's extras when a notification is tapped. */
    fun fromIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        when (extras.getString(EXTRA_TYPE)) {
            "message" -> extras.getString(EXTRA_CHAT_ID)?.let { post(DeepLink.Chat(it)) }
            "call" -> extras.getString(EXTRA_CALL_ID)?.let {
                post(DeepLink.IncomingCall(it, extras.getBoolean(EXTRA_ACCEPT, false) || extras.getString(EXTRA_ACCEPT) == "1"))
            }
        }
        // Don't replay the same link after a configuration change.
        intent.removeExtra(EXTRA_TYPE)
    }
}

package com.nook.msgapp.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Fb
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.nav.DeepLink
import com.nook.msgapp.nav.DeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

class NookMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = Fb.uid ?: return
        scope.launch { Push.onNewToken(uid, token) }
    }

    private fun inForeground() =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "call" -> {
                val callId = data["callId"] ?: return
                // Already in this call (or another one): don't ring over it.
                if (CallController.isBusy()) return
                if (inForeground()) {
                    DeepLinks.post(DeepLink.IncomingCall(callId, accept = false))
                } else {
                    Notifications.showIncomingCall(applicationContext, callId, data["callerName"] ?: "Someone", data["video"] == "1")
                }
            }
            "call_cancel" -> {
                val callId = data["callId"] ?: return
                Notifications.dismissCall(applicationContext, callId)
                CallController.remoteCancelled(callId)
            }
            "message" -> {
                // Background messages are shown by the system from the notification payload.
                val body = message.notification?.body ?: return
                if (inForeground() && Prefs.messageNotifications.value && data["chatId"] != Session.activeChatId) Toasts.show(body)
            }
        }
    }
}

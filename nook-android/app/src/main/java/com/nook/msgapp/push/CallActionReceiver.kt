package com.nook.msgapp.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nook.core.CallStatus
import com.nook.msgapp.data.Calls
import com.nook.msgapp.nav.DeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Decline" on the incoming-call notification: marks the call declined without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val callId = intent.getStringExtra(DeepLinks.EXTRA_CALL_ID) ?: return
        Notifications.dismissCall(context, callId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { Calls.setStatus(callId, CallStatus.Declined) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.nook.msgapp.DECLINE_CALL"
    }
}

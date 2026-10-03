package expo.modules.nookcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * "Decline" on the notification: stops ringing without opening the app. The app reports the
 * decline to Firestore the next time it runs (consumeDeclinedCalls); until then the caller sees
 * "No answer" when their ring times out.
 */
class DeclineReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION) return
    val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
    CallNotifications.dismiss(context, callId)
    Ringer.stop()
    CallNotifications.rememberDeclined(context, callId)
  }

  companion object {
    const val ACTION = "expo.modules.nookcall.DECLINE"
    const val EXTRA_CALL_ID = "callId"
  }
}

package expo.modules.nookcall

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class NookCallModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("NookCall")

    Function("showIncomingCall") { callId: String, callerName: String, video: Boolean ->
      CallNotifications.showIncoming(context, callId, callerName, video)
    }

    Function("dismissIncomingCall") { callId: String ->
      CallNotifications.dismiss(context, callId)
      Ringer.stop()
    }

    Function("startRingtone") {
      Ringer.start(context)
    }

    Function("stopRingtone") {
      Ringer.stop()
    }

    Function("setShowWhenLocked") { show: Boolean ->
      appContext.currentActivity?.let { activity -> applyShowWhenLocked(activity, show) }
    }

    Function("canUseFullScreenIntent") {
      CallNotifications.canUseFullScreenIntent(context)
    }

    Function("openFullScreenIntentSettings") {
      val intent = if (Build.VERSION.SDK_INT >= 34) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
      } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
      }
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }

    Function("consumeDeclinedCalls") {
      CallNotifications.consumeDeclined(context)
    }

    // Opened from the call notification: allow the call screen above the lock screen.
    OnActivityEntersForeground {
      appContext.currentActivity?.let { activity ->
        if (isCallIntent(activity.intent)) applyShowWhenLocked(activity, true)
      }
    }

    OnNewIntent { intent ->
      appContext.currentActivity?.let { activity ->
        if (isCallIntent(intent)) applyShowWhenLocked(activity, true)
      }
    }
  }

  private fun isCallIntent(intent: Intent?): Boolean {
    val data = intent?.data ?: return false
    return data.scheme == "nook" && data.host == "call"
  }

  private fun applyShowWhenLocked(activity: Activity, show: Boolean) {
    activity.runOnUiThread {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        activity.setShowWhenLocked(show)
        activity.setTurnScreenOn(show)
      } else {
        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (show) activity.window.addFlags(flags) else activity.window.clearFlags(flags)
      }
    }
  }
}

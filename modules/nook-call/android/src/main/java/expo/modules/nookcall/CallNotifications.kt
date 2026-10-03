package expo.modules.nookcall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Builds the incoming-call notification. It carries a full-screen intent (shows the NOOK call
 * screen over the lock screen when the phone is idle) plus Answer / Decline actions. The sound is
 * the phone's ringtone and repeats (FLAG_INSISTENT) until the person acts or it times out.
 */
object CallNotifications {
  const val CHANNEL_ID = "nook_calls_v1"
  private const val TAG = "nook_call"
  private const val TIMEOUT_MS = 45_000L
  private const val PREFS = "nook_call"
  private const val DECLINED = "declined"

  private fun notificationId(callId: String) = callId.hashCode()

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
      description = "Rings when a friend calls you on NOOK"
      setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      enableVibration(true)
      vibrationPattern = longArrayOf(0, 800, 600, 800)
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
  }

  private fun deepLink(context: Context, callId: String, accept: Boolean): Intent {
    val uri = Uri.parse("nook://call/incoming?callId=${Uri.encode(callId)}" + if (accept) "&accept=1" else "")
    return Intent(Intent.ACTION_VIEW, uri).apply {
      setPackage(context.packageName)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
  }

  private fun smallIcon(context: Context): Int {
    val id = context.resources.getIdentifier("notification_icon", "drawable", context.packageName)
    return if (id != 0) id else context.applicationInfo.icon
  }

  fun showIncoming(context: Context, callId: String, callerName: String, video: Boolean) {
    ensureChannel(context)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val base = notificationId(callId)
    val fullScreen = PendingIntent.getActivity(context, base, deepLink(context, callId, false), flags)
    val accept = PendingIntent.getActivity(context, base + 1, deepLink(context, callId, true), flags)
    val declineIntent = Intent(context, DeclineReceiver::class.java).apply {
      action = DeclineReceiver.ACTION
      putExtra(DeclineReceiver.EXTRA_CALL_ID, callId)
    }
    val decline = PendingIntent.getBroadcast(context, base + 2, declineIntent, flags)

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(smallIcon(context))
      .setColor(0xFFFF6A33.toInt())
      .setContentTitle(callerName)
      .setContentText(if (video) "Incoming video call" else "Incoming voice call")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setTimeoutAfter(TIMEOUT_MS)
      .setContentIntent(fullScreen)
      .setFullScreenIntent(fullScreen, true)
      .addAction(0, "Decline", decline)
      .addAction(0, "Answer", accept)
      .build()
    notification.flags = notification.flags or Notification.FLAG_INSISTENT

    try {
      NotificationManagerCompat.from(context).notify(TAG, notificationId(callId), notification)
    } catch (_: SecurityException) {
      // Notification permission denied: nothing we can show.
    }
  }

  fun dismiss(context: Context, callId: String) {
    NotificationManagerCompat.from(context).cancel(TAG, notificationId(callId))
  }

  fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return manager.canUseFullScreenIntent()
  }

  fun rememberDeclined(context: Context, callId: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(DECLINED, emptySet())?.toMutableSet() ?: mutableSetOf()
    set.add(callId)
    prefs.edit().putStringSet(DECLINED, set).apply()
  }

  fun consumeDeclined(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(DECLINED, emptySet())?.toList() ?: emptyList()
    prefs.edit().remove(DECLINED).apply()
    return set
  }
}

/** Plays the default ringtone in a loop while the in-app incoming screen is up. */
object Ringer {
  private var ringtone: Ringtone? = null

  fun start(context: Context) {
    stop()
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
    ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
      audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
      play()
    }
  }

  fun stop() {
    ringtone?.stop()
    ringtone = null
  }
}

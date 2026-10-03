package com.nook.msgapp.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nook.msgapp.MainActivity
import com.nook.msgapp.R
import com.nook.msgapp.nav.DeepLinks

/**
 * Notification channels plus the incoming-call notification. The call notification carries a
 * full-screen intent (shows the NOOK call screen over the lock screen) and Answer / Decline actions.
 * The sound is the phone's ringtone and repeats (FLAG_INSISTENT) until the person acts or it times out.
 */
object Notifications {
    const val MESSAGES = "messages"
    const val CALLS = "nook_calls_v1"
    const val ONGOING = "nook_ongoing_call"
    private const val CALL_TAG = "nook_call"
    private const val TIMEOUT_MS = 45_000L

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(MESSAGES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New messages from your friends"
                    enableVibration(true)
                },
            )
        }
        if (manager.getNotificationChannel(CALLS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Rings when a friend calls you on NOOK"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 600, 800)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        if (manager.getNotificationChannel(ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while you're in a call"
                    setSound(null, null)
                },
            )
        }
    }

    private fun callIntent(context: Context, callId: String, accept: Boolean) =
        Intent(context, MainActivity::class.java).apply {
            putExtra(DeepLinks.EXTRA_TYPE, "call")
            putExtra(DeepLinks.EXTRA_CALL_ID, callId)
            putExtra(DeepLinks.EXTRA_ACCEPT, accept)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    private fun callNotificationId(callId: String) = callId.hashCode()

    fun showIncomingCall(context: Context, callId: String, callerName: String, video: Boolean) {
        ensureChannels(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val base = callNotificationId(callId)
        val fullScreen = PendingIntent.getActivity(context, base, callIntent(context, callId, false), flags)
        val accept = PendingIntent.getActivity(context, base + 1, callIntent(context, callId, true), flags)
        val decline = PendingIntent.getBroadcast(
            context,
            base + 2,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE
                putExtra(DeepLinks.EXTRA_CALL_ID, callId)
            },
            flags,
        )
        val notification = NotificationCompat.Builder(context, CALLS)
            .setSmallIcon(R.drawable.notification_icon)
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
            NotificationManagerCompat.from(context).notify(CALL_TAG, callNotificationId(callId), notification)
        } catch (_: SecurityException) {
            // Notification permission denied: nothing we can show.
        }
    }

    fun dismissCall(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(CALL_TAG, callNotificationId(callId))
    }

    /** Clears a chat's message notifications when it is opened (the Worker tags them with the chat id). */
    fun clearChat(context: Context, chatId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == chatId }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
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

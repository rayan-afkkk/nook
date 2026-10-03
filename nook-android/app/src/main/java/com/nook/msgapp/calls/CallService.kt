package com.nook.msgapp.calls

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nook.msgapp.MainActivity
import com.nook.msgapp.R
import com.nook.msgapp.push.Notifications

/** Keeps the microphone (and camera) alive while a call runs in the background. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "NOOK call"
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, Notifications.ONGOING)
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(0xFFFF6A33.toInt())
            .setContentTitle(name)
            .setContentText(if (video) "Video call in progress" else "Call in progress")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            val camera = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (video && camera) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            t
        } else 0
        try {
            ServiceCompat.startForeground(this, 42, notification, type)
        } catch (_: Exception) {
            // Missing permission (e.g. camera denied): the call still works while the app is open.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_VIDEO = "video"
    }
}

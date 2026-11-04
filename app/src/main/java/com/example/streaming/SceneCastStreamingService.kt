package com.example.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Android Foreground Service that keeps MediaProjection (Screen Capture), Camera,
 * Microphone, and YouTube RTMPS streaming alive when switching apps or streaming in background.
 */
class SceneCastStreamingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NOTIFICATION_STOP -> {
                stopBroadcastCallbacks?.invoke()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val statusTitle = intent?.getStringExtra(EXTRA_STATUS_TITLE) ?: "SceneCast Live Active"
                val statusBody = intent?.getStringExtra(EXTRA_STATUS_BODY)
                    ?: "Screen capture & live studio engine running"
                val includeProjection = intent?.getBooleanExtra(EXTRA_INCLUDE_PROJECTION, false) ?: false

                val notification = buildNotification(statusTitle, statusBody)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        var serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        if (includeProjection) {
                            serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        }
                        startForeground(NOTIFICATION_ID, notification, serviceTypes)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    acquireWakeLock()
                } catch (_: Exception) {
                    // Fallback if started before specific runtime permission is granted
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SceneCastLive::BroadcastWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // Up to 4 hours per session
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SceneCastStreamingService::class.java).apply {
            action = ACTION_NOTIFICATION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_live),
                pendingStop
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scenecast_live_channel"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START_OR_UPDATE = "com.example.streaming.START_OR_UPDATE"
        const val ACTION_NOTIFICATION_STOP = "com.example.streaming.NOTIFICATION_STOP"
        const val ACTION_STOP_SERVICE = "com.example.streaming.STOP_SERVICE"
        const val EXTRA_STATUS_TITLE = "extra_status_title"
        const val EXTRA_STATUS_BODY = "extra_status_body"
        const val EXTRA_INCLUDE_PROJECTION = "extra_include_projection"

        var stopBroadcastCallbacks: (() -> Unit)? = null

        fun startOrUpdate(
            context: Context,
            title: String,
            body: String,
            includeProjection: Boolean
        ) {
            val intent = Intent(context, SceneCastStreamingService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_STATUS_TITLE, title)
                putExtra(EXTRA_STATUS_BODY, body)
                putExtra(EXTRA_INCLUDE_PROJECTION, includeProjection)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SceneCastStreamingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}

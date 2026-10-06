package com.ncorti.kotlin.template.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ScreenshotDetectorService : Service() {
    private lateinit var mediaObserver: MediaObserver
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        DiagnosticLog.add(this, "Service.onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        mediaObserver = MediaObserver(this, handler) { uri ->
            val judgment =
                if (BubsModeManager.isEnabled(this)) {
                    BubsModeManager.nextJudgment(this)
                } else {
                    null
                }

            try {
                PopupOverlayUI.show(
                    this,
                    uri,
                    judgment
                )
            } catch (t: Throwable) {
                DiagnosticLog.add(
                    this,
                    "Popup invocation FAILED: " +
                        t.javaClass.simpleName + ": " + t.message
                )
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
            DiagnosticLog.add(
                this,
                "Observer registered: " +
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI +
                    " descendants=true"
            )
        } catch (t: Throwable) {
            DiagnosticLog.add(
                this,
                "Observer registration FAILED: " +
                    t.javaClass.simpleName + ": " + t.message
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        DiagnosticLog.add(
            this,
            "Service.onStartCommand startId=" + startId + " flags=" + flags
        )

        if (intent?.action == ACTION_SCAN_NOW) {
            DiagnosticLog.add(this, "Manual scan command received")
            mediaObserver.scanNow()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DiagnosticLog.add(this, "Service.onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        DiagnosticLog.add(this, "Service.onDestroy")

        if (::mediaObserver.isInitialized) {
            runCatching {
                contentResolver.unregisterContentObserver(mediaObserver)
            }.onFailure {
                DiagnosticLog.add(
                    this,
                    "Observer unregister FAILED: " + it.message
                )
            }
            mediaObserver.destroy()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scorg watcher",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps screenshot sorting ready in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scorg is active")
            .setContentText("New screenshots are ready to sort")
            .setSmallIcon(R.drawable.ic_scorg_notification)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_SCAN_NOW = "com.mrbubs.scorg.ACTION_SCAN_NOW"
        private const val CHANNEL_ID = "scorg_watcher_channel"
        private const val NOTIFICATION_ID = 7001

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

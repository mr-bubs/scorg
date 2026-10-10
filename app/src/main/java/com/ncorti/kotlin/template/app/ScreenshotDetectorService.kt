package com.ncorti.kotlin.template.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ScreenshotDetectorService : Service() {
    private lateinit var mediaObserver: MediaObserver
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        startedAt = SystemClock.elapsedRealtime()
        isRunning = true

        DiagnosticLog.add(this, "Service.onCreate")
        createNotificationChannel()

        // A foreground service must post its notification immediately.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            DiagnosticLog.add(this, "Foreground watcher notification posted")
        } catch (t: Throwable) {
            DiagnosticLog.add(
                this,
                "Foreground start FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            stopSelf()
            return
        }

        mediaObserver = MediaObserver(this, handler) { uri ->
            val judgment = if (BubsModeManager.isEnabled(this)) {
                BubsModeManager.nextJudgment(this)
            } else {
                null
            }

            try {
                PopupOverlayUI.show(this, uri, judgment)
            } catch (t: Throwable) {
                DiagnosticLog.add(
                    this,
                    "Popup invocation FAILED: ${t.javaClass.simpleName}: ${t.message}"
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
                "Observer registered: ${MediaStore.Images.Media.EXTERNAL_CONTENT_URI} descendants=true"
            )
        } catch (t: Throwable) {
            DiagnosticLog.add(
                this,
                "Observer registration FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        DiagnosticLog.add(
            this,
            "Service.onStartCommand action=${action ?: "STICKY_RESTART"} " +
                "startId=$startId flags=$flags"
        )

        if (action == ACTION_STOP) {
            WatcherPreferences.setWatchingRequested(this, false)
            DiagnosticLog.add(this, "Watcher stopped intentionally in Scorg")
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            WatcherPreferences.setWatchingRequested(this, true)
            DiagnosticLog.add(this, "Watcher enabled by user")
        }

        val requested = WatcherPreferences.isWatchingRequested(this)
        if (!requested) {
            DiagnosticLog.add(this, "Watcher not enabled; skip command/restore")
            stopSelf()
            return START_NOT_STICKY
        }

        if (requested && action != ACTION_START) {
            val missing = WatcherRecovery.missingRequirement(this)
            if (missing != null) {
                DiagnosticLog.add(this, "Watcher restore blocked; missing $missing")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        isRunning = true

        if (action == ACTION_SCAN_NOW) {
            DiagnosticLog.add(this, "Manual scan command received")
            if (::mediaObserver.isInitialized) mediaObserver.scanNow()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DiagnosticLog.add(
            this,
            "Service.onTaskRemoved (watchingRequested=${WatcherPreferences.isWatchingRequested(this)})"
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000L
        DiagnosticLog.add(
            this,
            "Service.onDestroy after ${seconds}s " +
                "watchingRequested=${WatcherPreferences.isWatchingRequested(this)}"
        )

        if (::mediaObserver.isInitialized) {
            runCatching {
                contentResolver.unregisterContentObserver(mediaObserver)
            }.onFailure {
                DiagnosticLog.add(this, "Observer unregister FAILED: ${it.message}")
            }
            mediaObserver.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // New ID: an existing channel's importance cannot be lowered in code.
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scorg · Silent watcher",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet background screenshot sorting"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scorg_notification)
            .setContentTitle("Scorg · Watching")
            .setContentText("Screenshots ready to sort")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val ACTION_START = "com.mrbubs.scorg.ACTION_START_WATCHING"
        const val ACTION_STOP = "com.mrbubs.scorg.ACTION_STOP_WATCHING"
        const val ACTION_RESTORE = "com.mrbubs.scorg.ACTION_RESTORE_WATCHING"
        const val ACTION_SCAN_NOW = "com.mrbubs.scorg.ACTION_SCAN_NOW"

        private const val CHANNEL_ID = "scorg_watcher_silent_v2"
        private const val NOTIFICATION_ID = 7001

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

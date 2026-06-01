package com.ncorti.kotlin.template.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScreenshotDetectorService : Service() {

    private lateinit var mediaObserver: MediaObserver
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        mediaObserver = MediaObserver(this, handler) { uri ->
            PopupOverlayUI.show(this, uri)
        }

        contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
        mediaObserver.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scorg Watcher",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Watching for screenshots"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scorg")
            .setContentText("📸 Watching for screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "scorg_watcher_channel"
        private const val NOTIFICATION_ID = 7001
    }
}

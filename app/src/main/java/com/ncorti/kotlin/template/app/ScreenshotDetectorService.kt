package com.ncorti.kotlin.template.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat

class ScreenshotDetectorService : Service() {
    private lateinit var mediaObserver: MediaObserver

    // We aren't binding this service to an app screen, so return null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // 1. Create the persistent notification to stay alive
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "SCORG_CHANNEL")
            .setContentTitle("Scorg is Active")
            .setContentText("Listening for screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Default android icon for now
            .build()
        
        startForeground(1, notification)

        // 2. Start the MediaObserver to watch the image folders
        mediaObserver = MediaObserver(Handler(Looper.getMainLooper()), this)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up when the service stops
        contentResolver.unregisterContentObserver(mediaObserver)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SCORG_CHANNEL",
                "Scorg Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

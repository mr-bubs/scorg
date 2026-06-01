package com.ncorti.kotlin.template.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple programmatic layout
        val button = Button(this).apply {
            text = "Enable Scorg"
        }
        setContentView(button)

        button.setOnClickListener {
            checkAndStartService()
        }
    }

    private fun checkAndStartService() {
        // 1. Check for the Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please enable 'Draw over other apps' permission", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Start our Background Service
        val serviceIntent = Intent(this, ScreenshotDetectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Scorg is running!", Toast.LENGTH_SHORT).show()
    }
}

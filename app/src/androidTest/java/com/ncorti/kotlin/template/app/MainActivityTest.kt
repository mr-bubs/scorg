package com.ncorti.kotlin.template.app


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val button = Button(this).apply {
            text = "Enable Scorg"
        }
        setContentView(button)

        button.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        
        // 1. Check Storage Permissions (Android 13+ vs Older)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 2. Pop up the system box asking for Storage access
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
            Toast.makeText(this, "Please allow storage access, then tap Enable again.", Toast.LENGTH_LONG).show()
            return
        }

        // 3. Check for Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Draw over other apps', then tap Enable again.", Toast.LENGTH_LONG).show()
            return
        }

        // 4. Start the Service!
        val serviceIntent = Intent(this, ScreenshotDetectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Scorg is fully armed! Take a screenshot.", Toast.LENGTH_SHORT).show()
    }
}

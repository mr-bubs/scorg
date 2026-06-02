package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestStoragePermission()
        } else {
            toast("Draw over apps is required — please grant it")
        }
    }

    private val storageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (!granted) toast("Storage denied — file moving won't work")
        requestManageMedia()
    }

    private val manageMediaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestNotificationPermission()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startScorgService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 56, 56)
        }

        val emoji = TextView(this).apply {
            text = "📸"
            textSize = 48f
            setPadding(0, 0, 0, 8)
        }

        val title = TextView(this).apply {
            text = "Scorg"
            textSize = 32f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Screenshot organiser"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 48)
        }

        val statusText = TextView(this).apply {
            id = android.R.id.message
            text = "Tap below to start"
            textSize = 15f
            setPadding(0, 0, 0, 24)
        }

        val startBtn = Button(this).apply {
            text = "Start Scorg"
            textSize = 16f
            setOnClickListener { checkAndRequestAll() }
        }

        val manageBtn = Button(this).apply {
            text = "Manage Folders"
            setOnClickListener {
                toast("Folder management coming soon")
            }
        }

        layout.addView(emoji)
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(statusText)
        layout.addView(startBtn)
        layout.addView(manageBtn)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun checkAndRequestAll() {
        when {
            !Settings.canDrawOverlays(this) -> requestOverlay()
            !hasStoragePermission() -> requestStoragePermission()
            needsManageMedia() -> requestManageMedia()
            !hasNotificationPermission() -> requestNotificationPermission()
            else -> startScorgService()
        }
    }

    private fun requestOverlay() {
        toast("Grant 'Draw over other apps' — tap Scorg in the list")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayLauncher.launch(intent)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        storageLauncher.launch(perms)
    }

    private fun needsManageMedia(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return !android.provider.MediaStore.canManageMedia(this)
    }

    private fun requestManageMedia() {
        if (!needsManageMedia()) {
            requestNotificationPermission()
            return
        }
        toast("Grant 'Modify media' to move screenshots without popups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manageMediaLauncher.launch(
                Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScorgService()
        }
    }

    private fun startScorgService() {
        val intent = Intent(this, ScreenshotDetectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatusText()
        toast("✅ Scorg is watching — take a screenshot to test!")
    }

    private fun updateStatusText() {
        val tv = findViewById<TextView>(android.R.id.message) ?: return
        val overlayOk = Settings.canDrawOverlays(this)
        val storageOk = hasStoragePermission()
        tv.text = "Overlay: ${if (overlayOk) "✅" else "❌"}   " +
                  "Storage: ${if (storageOk) "✅" else "❌"}"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

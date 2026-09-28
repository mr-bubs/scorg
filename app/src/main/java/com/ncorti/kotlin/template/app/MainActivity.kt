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
    private lateinit var diagnosticText: TextView

    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) requestStoragePermission()
        else toast("Draw over apps is required — please grant it")
    }

    private val storageLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (!results.values.any { it }) toast("Storage denied — file moving won't work")
        requestManageMedia()
    }

    private val manageMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requestNotificationPermission()
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startScorgService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLog.add(this, "MainActivity.onCreate")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 56, 56)
        }
        val emoji = TextView(this).apply { text = "📸"; textSize = 48f; setPadding(0, 0, 0, 8) }
        val title = TextView(this).apply { text = "Scorg"; textSize = 32f; setTypeface(null, android.graphics.Typeface.BOLD) }
        val subtitle = TextView(this).apply { text = "Screenshot organiser — diagnostic build"; textSize = 14f; setTextColor(android.graphics.Color.GRAY); setPadding(0, 0, 0, 32) }
        val statusText = TextView(this).apply { id = android.R.id.message; text = "Tap below to start"; textSize = 15f; setPadding(0, 0, 0, 16) }
        val startBtn = Button(this).apply { text = "Start Scorg"; setOnClickListener { checkAndRequestAll() } }
        val testBtn = Button(this).apply {
            text = "🧪 Test Popup (Debug)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    toast("❌ Overlay permission not granted - tap Start Scorg first")
                    return@setOnClickListener
                }
                DiagnosticLog.add(this@MainActivity, "Manual popup test requested")
                PopupOverlayUI.show(applicationContext, Uri.parse("content://test/0"))
                refreshDiagnostics()
            }
        }
        val scanBtn = Button(this).apply {
            text = "🔎 Scan MediaStore Now"
            setOnClickListener {
                DiagnosticLog.add(this@MainActivity, "Manual MediaStore scan requested from UI")
                val intent = Intent(this@MainActivity, ScreenshotDetectorService::class.java).apply {
                    action = ScreenshotDetectorService.ACTION_SCAN_NOW
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                toast("Scan requested. Tap Refresh Diagnostics in 2 seconds.")
            }
        }
        val refreshBtn = Button(this).apply { text = "🔄 Refresh Diagnostics"; setOnClickListener { refreshDiagnostics() } }
        val clearBtn = Button(this).apply {
            text = "🧹 Clear Diagnostics"
            setOnClickListener { DiagnosticLog.clear(this@MainActivity); refreshDiagnostics() }
        }
        diagnosticText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 24)
        }

        content.addView(emoji); content.addView(title); content.addView(subtitle); content.addView(statusText)
        content.addView(startBtn); content.addView(testBtn); content.addView(scanBtn); content.addView(refreshBtn); content.addView(clearBtn)
        content.addView(diagnosticText)

        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLog.add(this, "MainActivity.onResume")
        updateStatusText()
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        if (::diagnosticText.isInitialized) diagnosticText.text = DiagnosticLog.read(this)
    }

    private fun checkAndRequestAll() {
        DiagnosticLog.add(this, "Start Scorg tapped")
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
        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestStoragePermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storageLauncher.launch(perms)
    }

    private fun needsManageMedia(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !android.provider.MediaStore.canManageMedia(this)

    private fun requestManageMedia() {
        if (!needsManageMedia()) { requestNotificationPermission(); return }
        toast("Grant 'Modify media' to move screenshots without popups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            manageMediaLauncher.launch(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:$packageName")))
    }

    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startScorgService()
    }

    private fun startScorgService() {
        DiagnosticLog.add(this, "Starting ScreenshotDetectorService")
        val intent = Intent(this, ScreenshotDetectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        updateStatusText()
        refreshDiagnostics()
        toast("✅ Scorg is watching — take a screenshot to test!")
    }

    private fun updateStatusText() {
        val tv = findViewById<TextView>(android.R.id.message) ?: return
        val overlayOk = Settings.canDrawOverlays(this)
        val storageOk = hasStoragePermission()
        val notificationOk = hasNotificationPermission()
        tv.text = "Overlay: ${if (overlayOk) "✅" else "❌"}   Storage: ${if (storageOk) "✅" else "❌"}   Notifications: ${if (notificationOk) "✅" else "❌"}"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

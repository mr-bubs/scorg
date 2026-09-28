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

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatusText()
        refreshDiagnostics()
    }

    private val screenshotFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            DiagnosticLog.add(this, "SAF folder picker cancelled")
            toast("Screenshots folder was not selected")
            updateStatusText()
            refreshDiagnostics()
            return@registerForActivityResult
        }

        val saved = ScreenshotTreeAccess.saveTreeUri(this, uri)
        if (saved) {
            toast("✅ Screenshots folder connected")
            DiagnosticLog.add(
                this,
                "SAF folder connected label=" + ScreenshotTreeAccess.treeLabel(this)
            )
        } else {
            toast("❌ Could not keep access to that folder")
        }

        updateStatusText()
        refreshDiagnostics()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startScorgService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLog.add(
            this,
            "MainActivity.onCreate SDK=" + Build.VERSION.SDK_INT +
                " SAF=" + ScreenshotTreeAccess.hasTreeAccess(this)
        )

        val content = LinearLayout(this).apply {
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
            text = "Screenshot organiser — SAF prototype"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 32)
        }

        val statusText = TextView(this).apply {
            id = android.R.id.message
            text = "Choose your Screenshots folder, then start Scorg"
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }

        val chooseFolderBtn = Button(this).apply {
            text = "📁 Choose Screenshots Folder"
            setOnClickListener {
                DiagnosticLog.add(this@MainActivity, "Opening SAF Screenshots folder picker")
                screenshotFolderLauncher.launch(null)
            }
        }

        val startBtn = Button(this).apply {
            text = "Start Scorg"
            textSize = 16f
            setOnClickListener { checkAndStart() }
        }

        val testBtn = Button(this).apply {
            text = "🧪 Test Popup With Latest Screenshot"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    toast("❌ Overlay permission not granted — tap Start Scorg first")
                    return@setOnClickListener
                }

                val entry = ScreenshotTreeAccess.findNewestScreenshot(
                    context = this@MainActivity,
                    recentOnly = false
                )

                if (entry == null) {
                    toast("No screenshot is visible in the selected folder")
                    DiagnosticLog.add(this@MainActivity, "Manual popup test found no SAF screenshot")
                    refreshDiagnostics()
                    return@setOnClickListener
                }

                DiagnosticLog.add(
                    this@MainActivity,
                    "Manual popup test using SAF uri=" + entry.uri
                )
                PopupOverlayUI.show(applicationContext, entry.uri)
                refreshDiagnostics()
            }
        }

        val scanBtn = Button(this).apply {
            text = "🔎 Scan Screenshots Folder Now"
            setOnClickListener {
                DiagnosticLog.add(
                    this@MainActivity,
                    "Manual SAF scan requested from UI"
                )

                val intent = Intent(
                    this@MainActivity,
                    ScreenshotDetectorService::class.java
                ).apply {
                    action = ScreenshotDetectorService.ACTION_SCAN_NOW
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                toast("SAF scan requested. Tap Refresh Diagnostics in 2 seconds.")
            }
        }

        val refreshBtn = Button(this).apply {
            text = "🔄 Refresh Diagnostics"
            setOnClickListener {
                updateStatusText()
                refreshDiagnostics()
            }
        }

        val clearBtn = Button(this).apply {
            text = "🧹 Clear Diagnostics"
            setOnClickListener {
                DiagnosticLog.clear(this@MainActivity)
                DiagnosticLog.add(
                    this@MainActivity,
                    "SAF access now=" + ScreenshotTreeAccess.hasTreeAccess(this@MainActivity) +
                        " folder=" + ScreenshotTreeAccess.treeLabel(this@MainActivity)
                )
                refreshDiagnostics()
            }
        }

        diagnosticText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 24)
        }

        content.addView(emoji)
        content.addView(title)
        content.addView(subtitle)
        content.addView(statusText)
        content.addView(chooseFolderBtn)
        content.addView(startBtn)
        content.addView(testBtn)
        content.addView(scanBtn)
        content.addView(refreshBtn)
        content.addView(clearBtn)
        content.addView(diagnosticText)

        val scroll = ScrollView(this).apply {
            addView(content)
        }

        setContentView(scroll)
        updateStatusText()
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLog.add(
            this,
            "MainActivity.onResume SAF=" + ScreenshotTreeAccess.hasTreeAccess(this) +
                " mediaAccess=" + mediaAccessStatus()
        )
        updateStatusText()
        refreshDiagnostics()
    }

    private fun checkAndStart() {
        DiagnosticLog.add(
            this,
            "Start Scorg tapped SAF=" + ScreenshotTreeAccess.hasTreeAccess(this)
        )

        when {
            !Settings.canDrawOverlays(this) -> requestOverlay()
            !ScreenshotTreeAccess.hasTreeAccess(this) -> {
                toast("Choose the DCIM/Screenshots folder first")
                screenshotFolderLauncher.launch(null)
            }
            !hasNotificationPermission() -> requestNotificationPermission()
            else -> startScorgService()
        }
    }

    private fun requestOverlay() {
        toast("Grant 'Draw over other apps' — tap Scorg in the list")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + packageName)
        )
        overlayLauncher.launch(intent)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScorgService()
        }
    }

    private fun startScorgService() {
        if (!ScreenshotTreeAccess.hasTreeAccess(this)) {
            toast("Screenshots folder access is missing")
            return
        }

        DiagnosticLog.add(
            this,
            "Starting ScreenshotDetectorService SAF folder=" +
                ScreenshotTreeAccess.treeLabel(this)
        )

        val intent = Intent(this, ScreenshotDetectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateStatusText()
        refreshDiagnostics()
        toast("✅ Scorg is watching — take a screenshot to test!")
    }

    private fun updateStatusText() {
        val tv = findViewById<TextView>(android.R.id.message) ?: return
        val overlayOk = Settings.canDrawOverlays(this)
        val safOk = ScreenshotTreeAccess.hasTreeAccess(this)
        val notificationOk = hasNotificationPermission()

        val folder = if (safOk) {
            "✅ " + ScreenshotTreeAccess.treeLabel(this)
        } else {
            "❌ Not selected"
        }

        val overlayLabel = if (overlayOk) "✅" else "❌"
        val notificationLabel = if (notificationOk) "✅" else "❌"

        tv.text =
            "Overlay: " + overlayLabel +
                "   Screenshots: " + folder +
                "   Notifications: " + notificationLabel
    }

    private fun mediaAccessStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                "FULL"
            } else {
                "DENIED"
            }
        }

        val full = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        if (full) return "FULL"

        if (Build.VERSION.SDK_INT >= 34) {
            val partial = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED

            if (partial) return "PARTIAL"
        }

        return "DENIED"
    }

    private fun refreshDiagnostics() {
        if (::diagnosticText.isInitialized) {
            diagnosticText.text = DiagnosticLog.read(this)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

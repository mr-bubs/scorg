package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var folderButton: Button
    private lateinit var debugContainer: LinearLayout
    private lateinit var diagnosticText: TextView

    private var continueStartAfterFolderPicker = false

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUi()
        if (Settings.canDrawOverlays(this)) {
            checkAndStart()
        } else {
            toast("Scorg needs permission to show the sorting popup")
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        DiagnosticLog.add(
            this,
            "Detection permission result=" + detectionPermissionStatus()
        )
        refreshUi()

        if (hasDetectionPermission()) {
            checkAndStart()
        } else {
            toast("Photo permission is needed to detect new screenshots")
        }
    }

    private val screenshotFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val shouldContinue = continueStartAfterFolderPicker
        continueStartAfterFolderPicker = false

        if (uri == null) {
            DiagnosticLog.add(this, "Screenshots folder picker cancelled")
            toast("Screenshots folder was not selected")
            refreshUi()
            return@registerForActivityResult
        }

        if (ScreenshotTreeAccess.saveTreeUri(this, uri)) {
            DiagnosticLog.add(
                this,
                "Screenshots folder connected label=" +
                    ScreenshotTreeAccess.treeLabel(this)
            )
            toast("✅ Screenshots folder connected")
            refreshUi()

            if (shouldContinue) {
                checkAndStart()
            }
        } else {
            toast("❌ Could not keep access to that folder")
            refreshUi()
        }
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshUi()

        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startScorgService()
        } else {
            toast("Notifications are required for Scorg's background watcher")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DiagnosticLog.add(
            this,
            "MainActivity.onCreate SDK=" + Build.VERSION.SDK_INT +
                " folderAccess=" + ScreenshotTreeAccess.hasTreeAccess(this)
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(32))
        }

        val emoji = TextView(this).apply {
            text = "📸"
            textSize = 42f
        }

        val title = TextView(this).apply {
            text = "Scorg"
            textSize = 30f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Sort screenshots the moment you take them."
            textSize = 15f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(4), 0, dp(24))
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        }

        folderButton = Button(this).apply {
            setOnClickListener {
                continueStartAfterFolderPicker = false
                openScreenshotFolderPicker()
            }
        }

        val startButton = Button(this).apply {
            text = "Start Scorg"
            textSize = 16f
            setOnClickListener {
                checkAndStart()
            }
        }

        val explanation = TextView(this).apply {
            text =
                "Scorg only reads and moves files inside the Screenshots folder " +
                    "you choose. Your category folders live inside it."
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(14), 0, dp(18))
        }

        val diagnosticsToggle = Button(this).apply {
            text = "Diagnostics"
            setOnClickListener {
                val showing = debugContainer.visibility == View.VISIBLE
                debugContainer.visibility = if (showing) View.GONE else View.VISIBLE
                text = if (showing) "Diagnostics" else "Hide diagnostics"
                if (!showing) {
                    refreshDiagnostics()
                }
            }
        }

        debugContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val testPopupButton = Button(this).apply {
            text = "Test popup with latest screenshot"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    toast("Overlay permission is not granted")
                    return@setOnClickListener
                }

                val entry = ScreenshotTreeAccess.findNewestScreenshot(
                    this@MainActivity,
                    recentOnly = false
                )

                if (entry == null) {
                    toast("No screenshot found in the selected folder")
                } else {
                    DiagnosticLog.add(
                        this@MainActivity,
                        "Manual popup test uri=" + entry.uri
                    )
                    PopupOverlayUI.show(applicationContext, entry.uri)
                }

                refreshDiagnostics()
            }
        }

        val scanButton = Button(this).apply {
            text = "Scan screenshots folder now"
            setOnClickListener {
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

                toast("Scan requested")
            }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh diagnostics"
            setOnClickListener {
                refreshUi()
                refreshDiagnostics()
            }
        }

        val clearButton = Button(this).apply {
            text = "Clear diagnostics"
            setOnClickListener {
                DiagnosticLog.clear(this@MainActivity)
                refreshDiagnostics()
            }
        }

        diagnosticText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }

        debugContainer.addView(testPopupButton)
        debugContainer.addView(scanButton)
        debugContainer.addView(refreshButton)
        debugContainer.addView(clearButton)
        debugContainer.addView(diagnosticText)

        content.addView(emoji)
        content.addView(title)
        content.addView(subtitle)
        content.addView(statusText)
        content.addView(folderButton)
        content.addView(startButton)
        content.addView(explanation)
        content.addView(diagnosticsToggle)
        content.addView(debugContainer)

        setContentView(
            ScrollView(this).apply {
                addView(content)
            }
        )

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()

        if (::debugContainer.isInitialized &&
            debugContainer.visibility == View.VISIBLE
        ) {
            refreshDiagnostics()
        }
    }

    private fun checkAndStart() {
        DiagnosticLog.add(
            this,
            "Start requested overlay=" + Settings.canDrawOverlays(this) +
                " detection=" + detectionPermissionStatus() +
                " folder=" + ScreenshotTreeAccess.hasTreeAccess(this) +
                " notifications=" + hasNotificationPermission()
        )

        when {
            !Settings.canDrawOverlays(this) -> requestOverlayPermission()

            !hasDetectionPermission() -> requestDetectionPermission()

            !ScreenshotTreeAccess.hasTreeAccess(this) -> {
                continueStartAfterFolderPicker = true
                openScreenshotFolderPicker()
            }

            !hasNotificationPermission() -> requestNotificationPermission()

            else -> startScorgService()
        }
    }

    private fun requestOverlayPermission() {
        toast("Allow Scorg to display the sorting popup")
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
        )
    }

    private fun openScreenshotFolderPicker() {
        DiagnosticLog.add(this, "Opening Screenshots folder picker")
        screenshotFolderLauncher.launch(null)
    }

    private fun hasDetectionPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) == PackageManager.PERMISSION_GRANTED
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }

            else -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun detectionPermissionStatus(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "FULL"
        } else {
            "DENIED"
        }
    }

    private fun requestDetectionPermission() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )

            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        mediaPermissionLauncher.launch(permissions)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
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
            toast("Reconnect your Screenshots folder")
            return
        }

        val intent = Intent(this, ScreenshotDetectorService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        DiagnosticLog.add(
            this,
            "Scorg watcher started folder=" +
                ScreenshotTreeAccess.treeLabel(this)
        )

        toast("✅ Scorg is watching for screenshots")
        refreshUi()
    }

    private fun refreshUi() {
        if (!::statusText.isInitialized) return

        val overlay = if (Settings.canDrawOverlays(this)) "✅" else "○"
        val detection = if (hasDetectionPermission()) "✅" else "○"
        val folder = if (ScreenshotTreeAccess.hasTreeAccess(this)) {
            "✅ " + ScreenshotTreeAccess.treeLabel(this)
        } else {
            "○ Not selected"
        }
        val notifications = if (hasNotificationPermission()) "✅" else "○"

        statusText.text =
            "Popup " + overlay +
                "   Detection " + detection +
                "   Notifications " + notifications +
                "\nScreenshots folder: " + folder

        folderButton.text = if (ScreenshotTreeAccess.hasTreeAccess(this)) {
            "Change Screenshots Folder"
        } else {
            "Choose Screenshots Folder"
        }
    }

    private fun refreshDiagnostics() {
        if (::diagnosticText.isInitialized) {
            diagnosticText.text = DiagnosticLog.read(this)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var readinessPill: TextView
    private lateinit var readinessTitle: TextView
    private lateinit var readinessBody: TextView
    private lateinit var primaryButton: TextView

    private lateinit var popupRow: LinearLayout
    private lateinit var detectionRow: LinearLayout
    private lateinit var folderRow: LinearLayout
    private lateinit var notificationRow: LinearLayout

    private lateinit var popupStatus: TextView
    private lateinit var detectionStatus: TextView
    private lateinit var folderStatus: TextView
    private lateinit var notificationStatus: TextView

    private lateinit var categoriesContainer: LinearLayout
    private lateinit var diagnosticsContainer: LinearLayout
    private lateinit var diagnosticText: TextView

    private var continueStartAfterFolderPicker = false

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUi()
        if (Settings.canDrawOverlays(this)) {
            checkAndStart()
        } else {
            showRestrictedOverlayHelp()
        }
    }

    private val appInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUi()
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            checkAndStart()
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
            toast("Photo access is needed so Scorg can notice new screenshots.")
        }
    }

    private val screenshotFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val shouldContinue = continueStartAfterFolderPicker
        continueStartAfterFolderPicker = false

        if (uri == null) {
            DiagnosticLog.add(this, "Screenshots folder picker cancelled")
            refreshUi()
            return@registerForActivityResult
        }

        if (ScreenshotTreeAccess.saveTreeUri(this, uri)) {
            DiagnosticLog.add(
                this,
                "Screenshots folder connected label=" +
                    ScreenshotTreeAccess.treeLabel(this)
            )
            toast("Screenshots folder connected")
            refreshUi()

            if (shouldContinue) {
                checkAndStart()
            }
        } else {
            toast("Scorg could not keep access to that folder.")
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
            toast("Notifications keep Scorg's background watcher visible to Android.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG

        DiagnosticLog.add(
            this,
            "MainActivity.onCreate SDK=" + Build.VERSION.SDK_INT +
                " folderAccess=" + ScreenshotTreeAccess.hasTreeAccess(this)
        )

        setContentView(buildScreen())
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (::readinessTitle.isInitialized) {
            refreshUi()
            if (diagnosticsContainer.visibility == View.VISIBLE) {
                refreshDiagnostics()
            }
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(34))
            setBackgroundColor(BG)
        }

        root.addView(buildHeader())
        root.addView(space(22))
        root.addView(buildReadinessCard())
        root.addView(space(26))
        root.addView(sectionLabel("SETUP"))
        root.addView(space(10))
        root.addView(buildSetupCard())
        root.addView(space(26))
        root.addView(buildCategoriesHeader())
        root.addView(space(10))
        root.addView(buildCategoriesCard())
        root.addView(space(26))
        root.addView(sectionLabel("HOW IT WORKS"))
        root.addView(space(10))
        root.addView(buildHowItWorksCard())
        root.addView(space(26))
        root.addView(buildDiagnostics())
        root.addView(space(18))
        root.addView(
            TextView(this).apply {
                text = "Scorg only reads and moves files inside the Screenshots folder you choose."
                setTextColor(MUTED)
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
            }
        )

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
            addView(root)
        }
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(18, SURFACE_ALT)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        }

        val logo = ImageView(this).apply {
            setImageResource(com.ncorti.kotlin.template.app.R.drawable.ic_scorg)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        logoWrap.addView(logo)

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        copy.addView(
            TextView(this).apply {
                text = "Scorg"
                setTextColor(TEXT)
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        copy.addView(
            TextView(this).apply {
                text = "Screenshots, sorted instantly."
                setTextColor(MUTED)
                textSize = 14f
                setPadding(0, dp(2), 0, 0)
            }
        )

        row.addView(logoWrap)
        row.addView(copy)
        return row
    }

    private fun buildReadinessCard(): View {
        val card = verticalCard()

        readinessPill = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        card.addView(readinessPill)

        readinessTitle = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(16), 0, 0)
        }
        card.addView(readinessTitle)

        readinessBody = TextView(this).apply {
            setTextColor(MUTED)
            textSize = 13.5f
            setPadding(0, dp(6), 0, dp(18))
        }
        card.addView(readinessBody)

        primaryButton = actionButton("Finish setup", true) {
            if (ScreenshotDetectorService.isRunning) {
                toast("Scorg is already watching for screenshots.")
            } else {
                checkAndStart()
            }
        }
        card.addView(primaryButton)

        return card
    }

    private fun buildSetupCard(): View {
        val card = verticalCard(dp(10))

        popupRow = setupRow(
            icon = "↗",
            title = "Sorting popup",
            subtitle = "Show folder choices over other apps",
            onClick = { requestOverlayPermission() }
        ).also {
            popupStatus = it.getChildAt(2) as TextView
            card.addView(it)
        }

        card.addView(divider())

        detectionRow = setupRow(
            icon = "◎",
            title = "Screenshot detection",
            subtitle = "Notice when a new screenshot is created",
            onClick = { requestDetectionPermission() }
        ).also {
            detectionStatus = it.getChildAt(2) as TextView
            card.addView(it)
        }

        card.addView(divider())

        folderRow = setupRow(
            icon = "▣",
            title = "Screenshots folder",
            subtitle = "Choose the folder Scorg is allowed to organize",
            onClick = {
                continueStartAfterFolderPicker = false
                openScreenshotFolderPicker()
            }
        ).also {
            folderStatus = it.getChildAt(2) as TextView
            card.addView(it)
        }

        card.addView(divider())

        notificationRow = setupRow(
            icon = "●",
            title = "Background watcher",
            subtitle = "Keep Scorg active while you use other apps",
            onClick = { requestNotificationPermission() }
        ).also {
            notificationStatus = it.getChildAt(2) as TextView
            card.addView(it)
        }

        return card
    }

    private fun buildCategoriesHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(
            sectionLabel("SORTING FOLDERS").apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )

        row.addView(
            TextView(this).apply {
                text = "+ Add"
                setTextColor(ACCENT)
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = rounded(10, ACCENT_SOFT)
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, NewFolderActivity::class.java))
                }
            }
        )

        return row
    }

    private fun buildCategoriesCard(): View {
        val card = verticalCard(dp(12))

        card.addView(
            TextView(this).apply {
                text = "These appear in the popup after every screenshot. Long-press one to remove it from the menu."
                setTextColor(MUTED)
                textSize = 12.5f
                setPadding(dp(4), dp(3), dp(4), dp(12))
            }
        )

        categoriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(categoriesContainer)

        return card
    }

    private fun buildHowItWorksCard(): View {
        val card = verticalCard(dp(14))
        card.addView(howRow("1", "Take a screenshot", "Scorg notices it in the background."))
        card.addView(space(10))
        card.addView(howRow("2", "Choose a folder", "A compact sorting popup appears immediately."))
        card.addView(space(10))
        card.addView(howRow("3", "Done", "The screenshot moves into that folder automatically."))
        return card
    }

    private fun buildDiagnostics(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toggle = TextView(this).apply {
            text = "Developer diagnostics  ›"
            setTextColor(MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setOnClickListener {
                val showing = diagnosticsContainer.visibility == View.VISIBLE
                diagnosticsContainer.visibility = if (showing) View.GONE else View.VISIBLE
                text = if (showing) "Developer diagnostics  ›" else "Hide diagnostics  ⌃"
                if (!showing) refreshDiagnostics()
            }
        }

        diagnosticsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        diagnosticsContainer.addView(
            compactButton("Test popup with latest screenshot") {
                if (!Settings.canDrawOverlays(this)) {
                    showRestrictedOverlayHelp()
                    return@compactButton
                }

                val entry = ScreenshotTreeAccess.findNewestScreenshot(
                    this,
                    recentOnly = false
                )

                if (entry == null) {
                    toast("No screenshot found in the selected folder.")
                } else {
                    PopupOverlayUI.show(applicationContext, entry.uri)
                }
                refreshDiagnostics()
            }
        )

        diagnosticsContainer.addView(space(7))

        diagnosticsContainer.addView(
            compactButton("Scan screenshots folder") {
                val intent = Intent(
                    this,
                    ScreenshotDetectorService::class.java
                ).apply {
                    action = ScreenshotDetectorService.ACTION_SCAN_NOW
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                toast("Scan requested.")
            }
        )

        diagnosticsContainer.addView(space(7))

        diagnosticsContainer.addView(
            compactButton("Refresh log") { refreshDiagnostics() }
        )

        diagnosticText = TextView(this).apply {
            setTextColor(Color.parseColor("#7F8C99"))
            textSize = 10.5f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(12), dp(4), 0)
        }

        diagnosticsContainer.addView(diagnosticText)
        outer.addView(toggle)
        outer.addView(diagnosticsContainer)
        return outer
    }

    private fun setupRow(
        icon: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
        }

        val iconView = TextView(this).apply {
            text = icon
            setTextColor(ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            background = rounded(12, ACCENT_SOFT)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        copy.addView(
            TextView(this).apply {
                text = title
                setTextColor(TEXT)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        copy.addView(
            TextView(this).apply {
                text = subtitle
                setTextColor(MUTED)
                textSize = 11.5f
                setPadding(0, dp(2), 0, 0)
            }
        )

        val status = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }

        row.addView(iconView)
        row.addView(copy)
        row.addView(status)
        return row
    }

    private fun howRow(number: String, title: String, body: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        row.addView(
            TextView(this).apply {
                text = number
                setTextColor(BG)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = rounded(10, ACCENT)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            }
        )

        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                addView(
                    TextView(this@MainActivity).apply {
                        text = title
                        setTextColor(TEXT)
                        textSize = 13.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                )

                addView(
                    TextView(this@MainActivity).apply {
                        text = body
                        setTextColor(MUTED)
                        textSize = 11.5f
                        setPadding(0, dp(2), 0, 0)
                    }
                )
            }
        )
        return row
    }

    private fun refreshUi() {
        if (!::readinessTitle.isInitialized) return

        val popupOk = Settings.canDrawOverlays(this)
        val detectionOk = hasDetectionPermission()
        val folderOk = ScreenshotTreeAccess.hasTreeAccess(this)
        val notificationOk = hasNotificationPermission()
        val ready = popupOk && detectionOk && folderOk && notificationOk
        val running = ScreenshotDetectorService.isRunning

        bindStatus(popupStatus, popupOk, if (popupOk) "Ready" else "Set up")
        bindStatus(
            detectionStatus,
            detectionOk,
            if (detectionOk) detectionPermissionStatus().lowercase().replaceFirstChar { it.uppercase() }
            else "Set up"
        )
        bindStatus(
            folderStatus,
            folderOk,
            if (folderOk) ScreenshotTreeAccess.treeLabel(this) else "Choose"
        )
        bindStatus(notificationStatus, notificationOk, if (notificationOk) "Ready" else "Set up")

        when {
            running -> {
                readinessPill.text = "●  ACTIVE"
                readinessPill.setTextColor(ACCENT)
                readinessPill.background = rounded(20, ACCENT_SOFT)
                readinessTitle.text = "Scorg is watching"
                readinessBody.text = "Take a screenshot anywhere. Your sorting popup will appear automatically."
                primaryButton.text = "Watcher active"
                primaryButton.background = rounded(14, Color.parseColor("#243229"))
                primaryButton.setTextColor(Color.parseColor("#9BC7AA"))
            }

            ready -> {
                readinessPill.text = "●  READY"
                readinessPill.setTextColor(ACCENT)
                readinessPill.background = rounded(20, ACCENT_SOFT)
                readinessTitle.text = "Ready to sort"
                readinessBody.text = "Everything is connected. Start Scorg and forget about screenshot clutter."
                primaryButton.text = "Start watching"
                primaryButton.background = rounded(14, ACCENT)
                primaryButton.setTextColor(BG)
            }

            else -> {
                val missing = listOf(
                    !popupOk,
                    !detectionOk,
                    !folderOk,
                    !notificationOk
                ).count { it }

                readinessPill.text = "SETUP  ·  " + missing + " LEFT"
                readinessPill.setTextColor(WARNING)
                readinessPill.background = rounded(20, WARNING_SOFT)
                readinessTitle.text = "A few things first"
                readinessBody.text = "Scorg needs " + missing +
                    if (missing == 1) " more permission before it can work everywhere."
                    else " more setup steps before it can work everywhere."
                primaryButton.text = "Finish setup"
                primaryButton.background = rounded(14, ACCENT)
                primaryButton.setTextColor(BG)
            }
        }

        refreshCategories()
    }

    private fun refreshCategories() {
        if (!::categoriesContainer.isInitialized) return
        categoriesContainer.removeAllViews()

        val folders = FolderManager.getFolders(this)
        if (folders.isEmpty()) {
            categoriesContainer.addView(
                TextView(this).apply {
                    text = "No sorting folders yet. Tap + Add to create one."
                    setTextColor(MUTED)
                    textSize = 12.5f
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                }
            )
            return
        }

        folders.forEachIndexed { index, folder ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = rounded(13, SURFACE_ALT)
                setOnLongClickListener {
                    confirmRemoveFolder(folder)
                    true
                }
            }

            row.addView(
                TextView(this).apply {
                    text = "▰"
                    setTextColor(ACCENT)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                }
            )

            row.addView(
                TextView(this).apply {
                    text = folder
                    setTextColor(TEXT)
                    textSize = 13.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(8), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )

            row.addView(
                TextView(this).apply {
                    text = "hold to remove"
                    setTextColor(Color.parseColor("#677482"))
                    textSize = 10.5f
                }
            )

            categoriesContainer.addView(row)
            if (index != folders.lastIndex) {
                categoriesContainer.addView(space(7))
            }
        }
    }

    private fun confirmRemoveFolder(folder: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove “" + folder + "”?")
            .setMessage(
                "This only removes the folder from Scorg's sorting menu. " +
                    "The actual folder and its screenshots stay on your device."
            )
            .setPositiveButton("Remove") { _, _ ->
                FolderManager.removeFolder(this, folder)
                refreshCategories()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        DiagnosticLog.add(this, "Opening overlay permission screen")
        try {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
        } catch (t: Throwable) {
            DiagnosticLog.add(
                this,
                "Overlay settings launch FAILED: " + t.javaClass.simpleName + ": " + t.message
            )
            showRestrictedOverlayHelp()
        }
    }

    private fun showRestrictedOverlayHelp() {
        if (isFinishing) return

        AlertDialog.Builder(this)
            .setTitle("Android blocked the popup permission")
            .setMessage(
                "If you saw “App was denied access”, Android has restricted this setting " +
                    "because Scorg was installed manually.\n\n" +
                    "1. Open App info\n" +
                    "2. Tap ⋮ in the top-right\n" +
                    "3. Choose “Allow restricted settings”\n" +
                    "4. Return to Scorg and enable “Display over other apps”\n\n" +
                    "You only need to do this once."
            )
            .setPositiveButton("Open App info") { _, _ ->
                appInfoLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
            .setNeutralButton("Try again") { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton("Later", null)
            .show()
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

    private fun requestDetectionPermission() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )

            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            toast("Reconnect your Screenshots folder.")
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
            "Scorg watcher started folder=" + ScreenshotTreeAccess.treeLabel(this)
        )

        toast("Scorg is watching for screenshots.")
        primaryButton.postDelayed({ refreshUi() }, 250L)
    }

    private fun bindStatus(view: TextView, ok: Boolean, label: String) {
        view.text = if (ok) "✓ " + label else label
        view.setTextColor(if (ok) ACCENT else WARNING)
        view.background = rounded(
            16,
            if (ok) ACCENT_SOFT else WARNING_SOFT
        )
    }

    private fun refreshDiagnostics() {
        if (::diagnosticText.isInitialized) {
            diagnosticText.text = DiagnosticLog.read(this)
        }
    }

    private fun verticalCard(padding: Int = dp(18)): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = roundedWithStroke(20, SURFACE, BORDER)
        }

    private fun sectionLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#71808F"))
            textSize = 11f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun actionButton(
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            setTextColor(if (primary) BG else TEXT)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(14, if (primary) ACCENT else SURFACE_ALT)
            setOnClickListener { onClick() }
        }

    private fun compactButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(TEXT)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedWithStroke(12, SURFACE_ALT, BORDER)
            setOnClickListener { onClick() }
        }

    private fun divider(): View =
        View(this).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                marginStart = dp(58)
                marginEnd = dp(8)
            }
        }

    private fun space(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
        }

    private fun rounded(radiusDp: Int, color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun roundedWithStroke(
        radiusDp: Int,
        fill: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#0B0F14")
        private val SURFACE = Color.parseColor("#121821")
        private val SURFACE_ALT = Color.parseColor("#18212C")
        private val BORDER = Color.parseColor("#25303D")
        private val TEXT = Color.parseColor("#F4F7FA")
        private val MUTED = Color.parseColor("#98A5B3")
        private val ACCENT = Color.parseColor("#5BE39B")
        private val ACCENT_SOFT = Color.parseColor("#173D2A")
        private val WARNING = Color.parseColor("#F4C95D")
        private val WARNING_SOFT = Color.parseColor("#3B321B")
    }
}

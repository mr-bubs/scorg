package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private lateinit var bubsPeek: ImageView

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
        if (Settings.canDrawOverlays(this)) checkAndStart()
        else showRestrictedOverlayHelp()
    }

    private val appInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUi()
        if (!Settings.canDrawOverlays(this)) requestOverlayPermission()
        else checkAndStart()
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        DiagnosticLog.add(this, "Detection permission result=" + detectionPermissionStatus())
        refreshUi()
        if (hasDetectionPermission()) checkAndStart()
        else toast("Photo access is needed so Scorg can notice new screenshots.")
    }

    private val screenshotFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val shouldContinue = continueStartAfterFolderPicker
        continueStartAfterFolderPicker = false

        if (uri == null) {
            refreshUi()
            return@registerForActivityResult
        }

        if (ScreenshotTreeAccess.saveTreeUri(this, uri)) {
            toast("Screenshots folder connected")
            refreshUi()
            if (shouldContinue) checkAndStart()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        setContentView(buildScreen())
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (::readinessTitle.isInitialized) {
            refreshUi()
            if (diagnosticsContainer.visibility == View.VISIBLE) refreshDiagnostics()
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
            setBackgroundColor(BG)
        }

        root.addView(buildHeader())
        root.addView(space(24))
        root.addView(buildReadinessCard())
        root.addView(space(24))
        root.addView(sectionLabel("SETUP"))
        root.addView(space(8))
        root.addView(buildSetupCard())
        root.addView(space(24))
        root.addView(buildCategoriesHeader())
        root.addView(space(8))
        root.addView(buildCategoriesCard())
        root.addView(space(24))
        root.addView(sectionLabel("HOW IT WORKS"))
        root.addView(space(8))
        root.addView(buildHowItWorksCard())
        root.addView(space(24))
        root.addView(buildDiagnostics())
        root.addView(space(16))
        root.addView(
            TextView(this).apply {
                text = "Everything stays inside the Screenshots folder you choose."
                setTextColor(MUTED)
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
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
            background = rounded(18, LILAC)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }

        logoWrap.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_scorg)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        copy.addView(
            TextView(this).apply {
                text = "Scorg"
                setTextColor(TEXT)
                textSize = 29f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        copy.addView(
            TextView(this).apply {
                text = "Screenshots, sorted instantly."
                setTextColor(MUTED)
                textSize = 13.5f
                setPadding(0, dp(2), 0, 0)
            }
        )

        row.addView(logoWrap)
        row.addView(copy)
        return row
    }

    private fun buildReadinessCard(): View {
        val card = folderCard()

        readinessPill = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        card.addView(readinessPill)

        readinessTitle = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), dp(76), 0)
        }
        card.addView(readinessTitle)

        readinessBody = TextView(this).apply {
            setTextColor(FOLDER_BODY)
            textSize = 13.5f
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(4), dp(70), dp(12))
        }
        card.addView(readinessBody)

        primaryButton = button("Finish setup", TEXT, Color.WHITE) { checkAndStart() }
        card.addView(primaryButton)

        val folder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(folderTab())
            addView(card)
        }

        bubsPeek = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            alpha = 0.96f

            BubsMascotFrames.setReaction(
                this@MainActivity,
                this,
                BubsMascotFrames.Reaction.NEUTRAL
            )
        }

        return FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false

            addView(
                folder,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                bubsPeek,
                FrameLayout.LayoutParams(
                    dp(82),
                    dp(82),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = -dp(24)
                    marginEnd = dp(14)
                }
            )
        }
    }

    private fun buildSetupCard(): View {
        val card = indexCard(dp(8))

        card.addView(
            setupRow(
                symbol = "01",
                title = "Sorting popup",
                subtitle = "Show folder choices over other apps",
                accent = LAVENDER
            ) { requestOverlayPermission() }.also {
                popupStatus = it.getChildAt(2) as TextView
            }
        )

        card.addView(divider())

        card.addView(
            setupRow(
                symbol = "02",
                title = "Screenshot detection",
                subtitle = "Notice when a new screenshot is created",
                accent = TURQUOISE
            ) { requestDetectionPermission() }.also {
                detectionStatus = it.getChildAt(2) as TextView
            }
        )

        card.addView(divider())

        card.addView(
            setupRow(
                symbol = "03",
                title = "Screenshots folder",
                subtitle = "Choose the folder Scorg can organize",
                accent = BLUE
            ) {
                continueStartAfterFolderPicker = false
                openScreenshotFolderPicker()
            }.also {
                folderStatus = it.getChildAt(2) as TextView
            }
        )

        card.addView(divider())

        card.addView(
            setupRow(
                symbol = "04",
                title = "Background watcher",
                subtitle = "Keep sorting ready while you use other apps",
                accent = LILAC_DEEP
            ) { requestNotificationPermission() }.also {
                notificationStatus = it.getChildAt(2) as TextView
            }
        )

        return card
    }

    private fun buildCategoriesHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                sectionLabel("SORTING FOLDERS").apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = "+ Add"
                    setTextColor(TEXT)
                    textSize = 12.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = rounded(14, LILAC)
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, NewFolderActivity::class.java))
                    }
                }
            )
        }
    }

    private fun buildCategoriesCard(): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        wrap.addView(
            TextView(this).apply {
                text = "Same order as your sorting popup. Hold a tab to remove it."
                setTextColor(MUTED)
                textSize = 12f
                setPadding(dp(2), 0, dp(2), dp(10))
            }
        )

        categoriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(6), 0, dp(6), 0)
        }

        wrap.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(categoriesContainer)
            }
        )

        wrap.addView(
            View(this).apply {
                setBackgroundColor(LAVENDER)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(3)
                )
            }
        )
        return wrap
    }

    private fun buildHowItWorksCard(): View {
        val card = card(dp(16))
        card.addView(howRow("1", "Take a screenshot", "Scorg notices it quietly."))
        card.addView(space(12))
        card.addView(howRow("2", "Choose a folder", "One clean popup. One tap."))
        card.addView(space(12))
        card.addView(howRow("3", "Done", "The file is moved and everything stays tidy."))
        return card
    }

    private fun buildDiagnostics(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toggle = TextView(this).apply {
            text = "Developer diagnostics  ›"
            setTextColor(MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
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

                val entry = ScreenshotTreeAccess.findNewestScreenshot(this, false)
                if (entry == null) toast("No screenshot found in the selected folder.")
                else PopupOverlayUI.show(applicationContext, entry.uri)

                refreshDiagnostics()
            }
        )

        diagnosticsContainer.addView(space(8))

        diagnosticsContainer.addView(
            compactButton("Scan screenshots folder") {
                val intent = Intent(this, ScreenshotDetectorService::class.java).apply {
                    action = ScreenshotDetectorService.ACTION_SCAN_NOW
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            }
        )

        diagnosticsContainer.addView(space(8))

        diagnosticsContainer.addView(compactButton("Refresh log") { refreshDiagnostics() })

        diagnosticText = TextView(this).apply {
            setTextColor(MUTED)
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
        symbol: String,
        title: String,
        subtitle: String,
        accent: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(12))
            minimumHeight = dp(72)
            setOnClickListener { onClick() }

            addView(
                TextView(this@MainActivity).apply {
                    text = symbol
                    setTextColor(TEXT)
                    textSize = 11.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = rounded(12, accent)
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                }
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
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
                            text = subtitle
                            setTextColor(MUTED)
                            textSize = 11.5f
                            setPadding(0, dp(2), 0, 0)
                        }
                    )
                }
            )

            addView(
                TextView(this@MainActivity).apply {
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    minWidth = dp(72)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                }
            )
        }
    }

    private fun howRow(number: String, title: String, body: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                TextView(this@MainActivity).apply {
                    text = number
                    setTextColor(TEXT)
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = rounded(11, BLUE)
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                }
            )

            addView(
                LinearLayout(this@MainActivity).apply {
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
        }
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
                bubsPeek.visibility = View.VISIBLE
                BubsMascotFrames.setReaction(
                    this,
                    bubsPeek,
                    BubsMascotFrames.Reaction.HAPPY
                )
                readinessPill.text = "●  ACTIVE"
                readinessPill.setTextColor(TEXT)
                readinessPill.background = rounded(18, SUCCESS)
                readinessTitle.text = "Everything is in its place"
                readinessBody.text = "Scorg is watching quietly. Take a screenshot anywhere and sort it in one tap."
                primaryButton.text = "Watcher active"
                primaryButton.background = rounded(14, SUCCESS)
                primaryButton.setTextColor(TEXT)
            }

            ready -> {
                bubsPeek.visibility = View.VISIBLE
                BubsMascotFrames.setReaction(
                    this,
                    bubsPeek,
                    BubsMascotFrames.Reaction.NEUTRAL
                )
                readinessPill.text = "●  READY"
                readinessPill.setTextColor(TEXT)
                readinessPill.background = rounded(18, TEAL)
                readinessTitle.text = "Ready to sort"
                readinessBody.text = "Everything is aligned and connected. Start Scorg and keep screenshots exactly where they belong."
                primaryButton.text = "Start watching"
                primaryButton.background = rounded(14, TEXT)
                primaryButton.setTextColor(Color.WHITE)
            }

            else -> {
                bubsPeek.visibility = View.GONE
                val missing = listOf(!popupOk, !detectionOk, !folderOk, !notificationOk).count { it }
                readinessPill.text = "SETUP  ·  " + missing + " LEFT"
                readinessPill.setTextColor(TEXT)
                readinessPill.background = rounded(18, WARNING)
                readinessTitle.text = "A few things first"
                readinessBody.text = "Complete the remaining setup steps below. Each one has a single clear purpose."
                primaryButton.text = "Finish setup"
                primaryButton.background = rounded(14, TEXT)
                primaryButton.setTextColor(Color.WHITE)
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
                    text = "No sorting folders yet."
                    setTextColor(MUTED)
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(16), dp(8), dp(16))
                }
            )
            return
        }

        val accents = listOf(BLUE, TEAL, LILAC_DEEP, TURQUOISE)

        folders.forEachIndexed { index, folder ->
            categoriesContainer.addView(
                TextView(this).apply {
                    text = folder
                    setTextColor(TEXT)
                    textSize = 13.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(148)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minimumHeight = dp(44)
                    setPadding(dp(16), dp(10), dp(16), dp(10 + if (index % 2 == 1) 4 else 0))
                    background = tabShape(accents[index % accents.size])
                    setOnLongClickListener {
                        confirmRemoveFolder(folder)
                        true
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                }
            )
        }
    }

    private fun confirmRemoveFolder(folder: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove “" + folder + "”?")
            .setMessage("This only removes it from Scorg's sorting menu. The actual folder and screenshots stay on your device.")
            .setPositiveButton("Remove") { _, _ ->
                FolderManager.removeFolder(this, folder)
                refreshCategories()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndStart() {
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
        try {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
            )
        } catch (_: Throwable) {
            showRestrictedOverlayHelp()
        }
    }

    private fun showRestrictedOverlayHelp() {
        if (isFinishing) return

        AlertDialog.Builder(this)
            .setTitle("Android blocked the popup permission")
            .setMessage(
                "If you saw “App was denied access”, Android restricted this setting because Scorg was installed manually.\n\n" +
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
            .setNeutralButton("Try again") { _, _ -> requestOverlayPermission() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openScreenshotFolderPicker() {
        screenshotFolderLauncher.launch(null)
    }

    private fun hasDetectionPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun detectionPermissionStatus(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                return "FULL"
            }

            if (
                Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            ) {
                return "PARTIAL"
            }

            return "DENIED"
        }

        return if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) "FULL" else "DENIED"
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
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)

        toast("Scorg is watching for screenshots.")
        primaryButton.postDelayed({ refreshUi() }, 250L)
    }

    private fun bindStatus(view: TextView, ok: Boolean, label: String) {
        view.text = if (ok) "✓ " + label else label
        view.setTextColor(TEXT)
        view.background = rounded(16, if (ok) SUCCESS else WARNING)
    }

    private fun refreshDiagnostics() {
        if (::diagnosticText.isInitialized) diagnosticText.text = DiagnosticLog.read(this)
    }

    private fun card(padding: Int = dp(16)): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = roundedWithStroke(20, SURFACE, BORDER)
            elevation = dp(1).toFloat()
        }

    private fun folderCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            val big = dp(20).toFloat()
            val small = dp(4).toFloat()
            background = GradientDrawable().apply {
                setColor(LAVENDER)
                cornerRadii = floatArrayOf(small, small, big, big, big, big, big, big)
            }
        }

    private fun folderTab(): View =
        View(this).apply {
            background = tabShape(LAVENDER)
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(14))
        }

    private fun indexCard(padding: Int = dp(8)): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding + dp(6), padding, padding, padding)
            val r = dp(14).toFloat()
            val edge = GradientDrawable().apply {
                setColor(TURQUOISE)
                cornerRadius = r
            }
            val paper = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                setStroke(dp(1), BORDER)
            }
            background = LayerDrawable(arrayOf(edge, paper)).apply {
                setLayerInset(1, dp(6), 0, 0, 0)
            }
            elevation = dp(1).toFloat()
        }

    private fun tabShape(color: Int): GradientDrawable {
        val r = dp(12).toFloat()
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    private fun sectionLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(MUTED)
            textSize = 10.5f
            letterSpacing = 0.10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun button(label: String, fill: Int, textColor: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(textColor)
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(14, fill)
            setOnClickListener { onClick() }
        }

    private fun compactButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(TEXT)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedWithStroke(12, SURFACE, BORDER)
            setOnClickListener { onClick() }
        }

    private fun divider(): View =
        View(this).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                marginStart = dp(60)
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

    private fun roundedWithStroke(radiusDp: Int, fill: Int, stroke: Int): GradientDrawable =
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
        private val BG = Color.parseColor("#F1EEF9")
        private val SURFACE = Color.parseColor("#FFFFFF")
        private val SURFACE_ALT = Color.parseColor("#F2F4FA")
        private val BORDER = Color.parseColor("#E7E5EF")
        private val TEXT = Color.parseColor("#35384D")
        private val MUTED = Color.parseColor("#85899C")

        private val LAVENDER = Color.parseColor("#CBB8FF")
        private val LILAC = Color.parseColor("#E9E2FA")
        private val LILAC_DEEP = Color.parseColor("#D8CCFA")
        private val TEAL = Color.parseColor("#CDEFE7")
        private val TURQUOISE = Color.parseColor("#AEE7E3")
        private val BLUE = Color.parseColor("#B9D1FA")
        private val SUCCESS = Color.parseColor("#DDF3ED")
        private val WARNING = Color.parseColor("#F8EFD1")
        private val FOLDER_BODY = Color.parseColor("#4A4D66")
    }
}
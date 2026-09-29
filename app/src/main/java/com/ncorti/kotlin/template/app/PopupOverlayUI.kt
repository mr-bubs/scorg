package com.ncorti.kotlin.template.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object PopupOverlayUI {

    private var overlayView: View? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismiss() }

    @Suppress("DEPRECATION")
    fun show(context: Context, screenshotUri: Uri) {
        dismiss()
        currentScreenshotUri = screenshotUri

        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val folders = FolderManager.getFolders(context)

        // Blur-behind needs Android 12+ and a device that allows it (some skins turn it off).
        val blurOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            windowManager.isCrossWindowBlurEnabled

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, 16),
                dp(context, 16),
                dp(context, 16),
                dp(context, 14)
            )
            background = roundedWithStroke(
                context,
                22,
                if (blurOn) GLASS_BLURRED else GLASS_SOLID,
                GLASS_EDGE
            )
            elevation = dp(context, 10).toFloat()
        }

        container.addView(
            TextView(context).apply {
                text = "SCREENSHOT READY"
                setTextColor(TEXT)
                textSize = 10f
                letterSpacing = 0.10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(
                    dp(context, 10),
                    dp(context, 6),
                    dp(context, 10),
                    dp(context, 6)
                )
                background = rounded(context, 18, LILAC)
            }
        )

        container.addView(
            TextView(context).apply {
                text = "Where should this go?"
                setTextColor(TEXT)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 14), 0, 0)
            }
        )

        container.addView(
            TextView(context).apply {
                text = "One tap and it’s sorted."
                setTextColor(MUTED)
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 4), 0, dp(context, 14))
            }
        )

        if (folders.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = "No sorting folders yet."
                    setTextColor(MUTED)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    minimumHeight = dp(context, 48)
                    background = rounded(context, 14, SURFACE_ALT)
                }
            )
        } else {
            folders.forEachIndexed { index, folder ->
                container.addView(folderRow(context, folder, ACCENTS[index % ACCENTS.size]))
                if (index != folders.lastIndex) {
                    container.addView(spacer(context, 8))
                }
            }
        }

        container.addView(spacer(context, 12))

        container.addView(
            TextView(context).apply {
                text = "＋  New folder"
                setTextColor(DARK)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                minimumHeight = dp(context, 48)
                background = roundedWithStroke(
                    context,
                    14,
                    LAVENDER,
                    LAVENDER
                )
                setOnClickListener {
                    val intent = Intent(
                        context,
                        NewFolderActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("screenshot_uri", screenshotUri.toString())
                    }
                    context.startActivity(intent)
                    dismiss()
                }
            }
        )

        container.addView(
            TextView(context).apply {
                text = "Dismiss"
                setTextColor(MUTED)
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(
                    dp(context, 12),
                    dp(context, 12),
                    dp(context, 12),
                    dp(context, 2)
                )
                setOnClickListener { dismiss() }
            }
        )

        val bar = View(context).apply {
            background = rounded(context, 2, LAVENDER)
            pivotX = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 3)
            ).apply { topMargin = dp(context, 10) }
        }
        container.addView(bar)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val desiredWidth = dp(context, 380)
        val availableWidth =
            (screenWidth - dp(context, 24)).coerceAtLeast(dp(context, 280))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(context, 56)
            width = minOf(desiredWidth, availableWidth)
            if (blurOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(context, 28)
            }
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
            bar.animate()
                .scaleX(0f)
                .setDuration(15_000L)
                .setInterpolator(LinearInterpolator())
                .start()
            dismissHandler.postDelayed(autoDismiss, 15_000L)
        } catch (e: SecurityException) {
            DiagnosticLog.add(context, "Popup permission error: " + e.message)
            Toast.makeText(
                context,
                "Scorg needs permission to display the sorting popup.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            DiagnosticLog.add(
                context,
                "Popup error: " + e.javaClass.simpleName + ": " + e.message
            )
            Toast.makeText(
                context,
                "Scorg couldn't show the sorting popup.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun folderRow(context: Context, folder: String, accent: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 52)
            setPadding(
                dp(context, 12),
                dp(context, 8),
                dp(context, 12),
                dp(context, 8)
            )
            background = roundedWithStroke(context, 14, SURFACE_ALT, GLASS_EDGE)
            setOnClickListener {
                FileMover.moveToFolder(
                    context,
                    currentScreenshotUri ?: return@setOnClickListener,
                    folder
                )
                dismiss()
            }

            addView(
                TextView(context).apply {
                    text = folder.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
                    setTextColor(DARK)
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = rounded(context, 11, accent)
                    layoutParams = LinearLayout.LayoutParams(
                        dp(context, 36),
                        dp(context, 36)
                    )
                }
            )

            addView(
                TextView(context).apply {
                    text = folder
                    setTextColor(TEXT)
                    textSize = 13.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(context, 12), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )

            addView(
                TextView(context).apply {
                    text = "›"
                    setTextColor(MUTED)
                    textSize = 20f
                    gravity = Gravity.CENTER
                }
            )
        }
    }

    private var currentScreenshotUri: Uri? = null

    private fun spacer(context: Context, heightDp: Int): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, heightDp)
            )
        }

    fun dismiss() {
        dismissHandler.removeCallbacks(autoDismiss)
        currentScreenshotUri = null

        overlayView?.let { view ->
            try {
                val windowManager =
                    view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    private fun rounded(
        context: Context,
        radiusDp: Int,
        color: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    private fun roundedWithStroke(
        context: Context,
        radiusDp: Int,
        fill: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            setStroke(dp(context, 1), stroke)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private val TEXT = Color.parseColor("#EEEDFA")
    private val MUTED = Color.parseColor("#A9A8C4")
    private val DARK = Color.parseColor("#2A2540")
    private val SURFACE_ALT = Color.parseColor("#1FFFFFFF")
    private val LILAC = Color.parseColor("#33CBB8FF")
    private val LAVENDER = Color.parseColor("#CBB8FF")
    private val BLUE = Color.parseColor("#B9D1FA")
    private val TEAL = Color.parseColor("#9EDFD8")
    private val PINK = Color.parseColor("#F6D6DE")
    private val ACCENTS = listOf(BLUE, TEAL, PINK, LAVENDER)

    // Night glass: translucent when the blur is on, more opaque when it is not.
    private val GLASS_BLURRED = Color.parseColor("#A6221F45")
    private val GLASS_SOLID = Color.parseColor("#EB1E1B3C")
    private val GLASS_EDGE = Color.parseColor("#38FFFFFF")

    init {
        // no-op
    }
}
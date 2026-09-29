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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object PopupOverlayUI {

    private var overlayView: View? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismiss() }

    fun show(context: Context, screenshotUri: Uri) {
        dismiss()

        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val folders = FolderManager.getFolders(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, 18),
                dp(context, 17),
                dp(context, 18),
                dp(context, 14)
            )
            background = roundedWithStroke(
                context,
                22,
                Color.parseColor("#F5141B23"),
                Color.parseColor("#374452")
            )
            elevation = dp(context, 16).toFloat()
        }

        val readyPill = TextView(context).apply {
            text = "SCREENSHOT READY"
            setTextColor(ACCENT)
            textSize = 10.5f
            letterSpacing = 0.11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(
                dp(context, 10),
                dp(context, 6),
                dp(context, 10),
                dp(context, 6)
            )
            background = rounded(
                context,
                20,
                Color.parseColor("#173D2A")
            )
        }
        container.addView(readyPill)

        container.addView(
            TextView(context).apply {
                text = "Where should this go?"
                setTextColor(TEXT)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(context, 13), 0, 0)
            }
        )

        container.addView(
            TextView(context).apply {
                text = "Tap a folder and Scorg will move it instantly."
                setTextColor(MUTED)
                textSize = 11.5f
                setPadding(0, dp(context, 3), 0, dp(context, 13))
            }
        )

        if (folders.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = "No sorting folders yet."
                    setTextColor(MUTED)
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(context, 12),
                        dp(context, 14),
                        dp(context, 12),
                        dp(context, 14)
                    )
                    background = rounded(
                        context,
                        14,
                        Color.parseColor("#18212C")
                    )
                }
            )
        } else {
            folders.forEachIndexed { index, folderName ->
                container.addView(
                    folderRow(context, folderName) {
                        FileMover.moveToFolder(
                            context,
                            screenshotUri,
                            folderName
                        )
                        dismiss()
                    }
                )

                if (index != folders.lastIndex) {
                    container.addView(
                        spacer(context, 7)
                    )
                }
            }
        }

        container.addView(spacer(context, 10))

        container.addView(
            TextView(context).apply {
                text = "＋  New folder"
                setTextColor(ACCENT)
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(
                    dp(context, 14),
                    dp(context, 13),
                    dp(context, 14),
                    dp(context, 13)
                )
                background = roundedWithStroke(
                    context,
                    14,
                    Color.TRANSPARENT,
                    Color.parseColor("#355D48")
                )
                setOnClickListener {
                    val intent = Intent(
                        context,
                        NewFolderActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(
                            "screenshot_uri",
                            screenshotUri.toString()
                        )
                    }
                    context.startActivity(intent)
                    dismiss()
                }
            }
        )

        container.addView(
            TextView(context).apply {
                text = "Dismiss"
                setTextColor(Color.parseColor("#748290"))
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

        val screenWidth = context.resources.displayMetrics.widthPixels
        val desiredWidth = dp(context, 390)
        val availableWidth =
            (screenWidth - dp(context, 24))
                .coerceAtLeast(dp(context, 280))

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
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(context, 12)
            y = dp(context, 64)
            width = minOf(desiredWidth, availableWidth)
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
            dismissHandler.postDelayed(autoDismiss, 15_000L)
        } catch (e: SecurityException) {
            DiagnosticLog.add(
                context,
                "Popup permission error: " + e.message
            )
            Toast.makeText(
                context,
                "Scorg needs permission to display the sorting popup.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            DiagnosticLog.add(
                context,
                "Popup error: " +
                    e.javaClass.simpleName + ": " + e.message
            )
            Toast.makeText(
                context,
                "Scorg couldn't show the sorting popup.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun dismiss() {
        dismissHandler.removeCallbacks(autoDismiss)

        overlayView?.let { view ->
            try {
                val windowManager =
                    view.context.getSystemService(
                        Context.WINDOW_SERVICE
                    ) as WindowManager
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    private fun folderRow(
        context: Context,
        folderName: String,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(context, 11),
                dp(context, 10),
                dp(context, 11),
                dp(context, 10)
            )
            background = rounded(
                context,
                14,
                Color.parseColor("#1B242E")
            )
            setOnClickListener { onClick() }
        }

        val firstLetter =
            folderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"

        row.addView(
            TextView(context).apply {
                text = firstLetter
                setTextColor(Color.parseColor("#0B0F14"))
                textSize = 13f
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
                gravity = Gravity.CENTER
                background = rounded(
                    context,
                    10,
                    ACCENT
                )
                layoutParams = LinearLayout.LayoutParams(
                    dp(context, 34),
                    dp(context, 34)
                )
            }
        )

        row.addView(
            TextView(context).apply {
                text = folderName
                setTextColor(TEXT)
                textSize = 13.5f
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
                setPadding(
                    dp(context, 11),
                    0,
                    dp(context, 8),
                    0
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )

        row.addView(
            TextView(context).apply {
                text = "›"
                setTextColor(Color.parseColor("#687684"))
                textSize = 22f
                gravity = Gravity.CENTER
            }
        )

        return row
    }

    private fun spacer(
        context: Context,
        heightDp: Int
    ): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, heightDp)
            )
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

    private val TEXT = Color.parseColor("#F4F7FA")
    private val MUTED = Color.parseColor("#98A5B3")
    private val ACCENT = Color.parseColor("#5BE39B")
}

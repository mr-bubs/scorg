package com.ncorti.kotlin.template.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
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
            setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE202124"))
                cornerRadius = dp(context, 18).toFloat()
            }
            elevation = dp(context, 12).toFloat()
        }

        val header = TextView(context).apply {
            text = "📁 Sort this screenshot"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 12))
        }
        container.addView(header)

        folders.forEach { folderName ->
            container.addView(
                makeFolderButton(context, "📂 " + folderName) {
                    FileMover.moveToFolder(context, screenshotUri, folderName)
                    dismiss()
                }
            )
        }

        container.addView(
            makeFolderButton(
                context,
                "＋ New folder",
                Color.parseColor("#1565C0")
            ) {
                val intent = Intent(context, NewFolderActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("screenshot_uri", screenshotUri.toString())
                }
                context.startActivity(intent)
                dismiss()
            }
        )

        val dismissBtn = TextView(context).apply {
            text = "✕ dismiss"
            setTextColor(Color.parseColor("#AFAFAF"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 12), 0, 0)
            setOnClickListener { dismiss() }
        }
        container.addView(dismissBtn)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val desiredWidth = dp(context, 420)
        val availableWidth = (screenWidth - dp(context, 24)).coerceAtLeast(dp(context, 260))

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
            y = dp(context, 72)
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
                "Enable Scorg's background pop-up permission in app settings",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            DiagnosticLog.add(
                context,
                "Popup error: " + e.javaClass.simpleName + ": " + e.message
            )
            Toast.makeText(
                context,
                "Popup error: " + e.message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun dismiss() {
        dismissHandler.removeCallbacks(autoDismiss)

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

    private fun makeFolderButton(
        context: Context,
        label: String,
        color: Int = Color.parseColor("#2E7D32"),
        onClick: () -> Unit
    ): Button =
        Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(context, 10).toFloat()
            }
            textSize = 13f
            setOnClickListener { onClick() }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(context, 3), 0, dp(context, 3))
            }
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

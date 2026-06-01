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
import android.widget.*

object PopupOverlayUI {

    private var overlayView: View? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismiss() }

    fun show(context: Context, screenshotUri: Uri) {
        dismiss() 

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val folders = FolderManager.getFolders(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E8212121"))
                cornerRadius = 24f
            }
            elevation = 16f
        }

        val header = TextView(context).apply {
            text = "📁 Sort this screenshot"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 20)
        }
        container.addView(header)

        folders.forEach { folderName ->
            val btn = makeFolderButton(context, "📂 $folderName") {
                FileMover.moveToFolder(context, screenshotUri, folderName)
                dismiss()
            }
            container.addView(btn)
        }

        val newBtn = makeFolderButton(context, "＋ New folder", Color.parseColor("#1565C0")) {
            val intent = Intent(context, NewFolderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("screenshot_uri", screenshotUri.toString())
            }
            context.startActivity(intent)
            dismiss()
        }
        container.addView(newBtn)

        val dismissBtn = TextView(context).apply {
            text = "✕ dismiss"
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            setOnClickListener { dismiss() }
        }
        container.addView(dismissBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 180 
            width = 360
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
            dismissHandler.postDelayed(autoDismiss, 12_000L)
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                "MIUI: Enable 'Display pop-up windows while running in background' for Scorg in Settings → Apps",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Popup error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        dismissHandler.removeCallbacks(autoDismiss)
        overlayView?.let { view ->
            try {
                val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun makeFolderButton(
        context: Context,
        label: String,
        color: Int = Color.parseColor("#2E7D32"),
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12f
            }
            textSize = 13f
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 4, 0, 4)
            layoutParams = lp
        }
    }
}

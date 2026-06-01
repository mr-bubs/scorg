package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PopupOverlayUI(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: LinearLayout? = null

    fun showPopup(imagePath: String) {
        // If a popup is already showing, remove it first
        removePopup()

        // 1. Create the main box (Dark grey, vertical list)
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE222222")) 
            setPadding(48, 48, 48, 48)
        }

        // 2. Add a Title
        val title = TextView(context).apply {
            text = "Move Screenshot To:"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        
        // 3. Add the 'Recipes' Button
        val btnRecipes = Button(context).apply {
            text = "Recipes"
            setOnClickListener {
                FileMover(context).moveToFolder(imagePath, "Recipes")
                removePopup()
            }
        }

        // 4. Add a 'Dismiss' Button
        val btnDismiss = Button(context).apply {
            text = "Dismiss"
            setOnClickListener {
                removePopup()
            }
        }

        // Combine them all into the main box
        overlayView?.addView(title)
        overlayView?.addView(btnRecipes)
        overlayView?.addView(btnDismiss)

        // 5. Setup WindowManager rules (Draw over apps, translucent)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 50 // Offset slightly from the left edge
            y = 300 // Offset from the bottom edge so it clears the native preview
        }

        windowManager.addView(overlayView, params)
    }

    private fun removePopup() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            // View might already be gone, safe to ignore
        }
    }
}

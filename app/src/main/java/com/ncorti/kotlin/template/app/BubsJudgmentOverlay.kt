package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object BubsJudgmentOverlay {

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var removeRunnable: Runnable? = null

    fun show(context: Context, judgment: BubsModeManager.Judgment) {
        if (!BubsModeManager.isEnabled(context)) return

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        ) {
            return
        }

        handler.post {
            removeNow()

            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val shell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                alpha = 0f
                translationY = -dp(context, 8).toFloat()
                setPadding(
                    dp(context, 8),
                    dp(context, 7),
                    dp(context, 10),
                    dp(context, 7)
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F7FFFFFF"))
                    cornerRadius = dp(context, 18).toFloat()
                    setStroke(
                        dp(context, 1),
                        Color.parseColor("#55CBB8FF")
                    )
                }
                elevation = dp(context, 8).toFloat()
            }

            val bubs = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            BubsMascotFrames.setReaction(
                context,
                bubs,
                judgment.reaction
            )

            shell.addView(
                bubs,
                LinearLayout.LayoutParams(
                    dp(context, 58),
                    dp(context, 64)
                )
            )

            shell.addView(
                TextView(context).apply {
                    text = judgment.message
                    setTextColor(Color.parseColor("#35384D"))
                    textSize = 12f
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                    )
                    maxLines = 2
                    setPadding(dp(context, 7), 0, 0, 0)
                },
                LinearLayout.LayoutParams(
                    dp(context, 150),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val params = WindowManager.LayoutParams(
                dp(context, 232),
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(context, 14)
                y = dp(context, 86)
            }

            try {
                windowManager.addView(shell, params)
                currentView = shell

                shell.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(160L)
                    .start()

                val remove = Runnable {
                    shell.animate()
                        .alpha(0f)
                        .translationY(-dp(context, 6).toFloat())
                        .setDuration(220L)
                        .withEndAction { removeNow() }
                        .start()
                }

                removeRunnable = remove
                handler.postDelayed(remove, 1_650L)
            } catch (_: Throwable) {
                removeNow()
            }
        }
    }

    private fun removeNow() {
        removeRunnable?.let { handler.removeCallbacks(it) }
        removeRunnable = null

        currentView?.let { view ->
            runCatching {
                val windowManager =
                    view.context.getSystemService(
                        Context.WINDOW_SERVICE
                    ) as WindowManager
                windowManager.removeView(view)
            }
        }

        currentView = null
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

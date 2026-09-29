package com.ncorti.kotlin.template.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

object BubsCelebrationOverlay {

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var removeRunnable: Runnable? = null

    fun show(context: Context) {
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

            val shell = FrameLayout(context).apply {
                alpha = 0f
                translationY = dp(context, 10).toFloat()
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2FFFFFF"))
                    cornerRadius = dp(context, 22).toFloat()
                    setStroke(
                        dp(context, 1),
                        Color.parseColor("#42CBB8FF")
                    )
                }
                elevation = dp(context, 8).toFloat()
                setPadding(
                    dp(context, 6),
                    dp(context, 6),
                    dp(context, 6),
                    dp(context, 6)
                )
            }

            val bubs = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            BubsMascotFrames.setReaction(
                context,
                bubs,
                BubsMascotFrames.Reaction.HAPPY
            )

            shell.addView(
                bubs,
                FrameLayout.LayoutParams(
                    dp(context, 68),
                    dp(context, 68),
                    Gravity.CENTER
                )
            )

            val params = WindowManager.LayoutParams(
                dp(context, 80),
                dp(context, 80),
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
                gravity = Gravity.BOTTOM or Gravity.END
                x = dp(context, 18)
                y = dp(context, 92)
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
                        .translationY(dp(context, 8).toFloat())
                        .setDuration(220L)
                        .withEndAction { removeNow() }
                        .start()
                }

                removeRunnable = remove
                handler.postDelayed(remove, 800L)
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

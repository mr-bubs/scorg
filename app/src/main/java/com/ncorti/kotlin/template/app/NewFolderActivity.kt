package com.ncorti.kotlin.template.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class NewFolderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val screenshotUriStr = intent.getStringExtra("screenshot_uri")

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = roundedWithStroke(
                22,
                Color.WHITE,
                Color.parseColor("#E7E5EF")
            )
        }

        card.addView(
            TextView(this).apply {
                text = "NEW SORTING FOLDER"
                setTextColor(Color.parseColor("#7D70A8"))
                textSize = 10f
                letterSpacing = 0.10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            }
        )

        card.addView(
            TextView(this).apply {
                text = "Name your folder"
                setTextColor(Color.parseColor("#35384D"))
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        )

        card.addView(
            TextView(this).apply {
                text = if (screenshotUriStr != null) {
                    "It will be created and this screenshot will move there."
                } else {
                    "It will appear in the same place in your sorting popup."
                }
                setTextColor(Color.parseColor("#85899C"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(16))
            }
        )

        val editText = EditText(this).apply {
            hint = "e.g. Memes"
            setHintTextColor(Color.parseColor("#AAAEC0"))
            setTextColor(Color.parseColor("#35384D"))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedWithStroke(
                14,
                Color.parseColor("#F8F7FC"),
                Color.parseColor("#E7E5EF")
            )
        }
        card.addView(editText)

        card.addView(space(16))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        actions.addView(
            TextView(this).apply {
                text = "Cancel"
                setTextColor(Color.parseColor("#85899C"))
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                minimumHeight = dp(46)
                background = roundedWithStroke(
                    14,
                    Color.parseColor("#F8F7FC"),
                    Color.parseColor("#E7E5EF")
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setOnClickListener { finish() }
            }
        )

        actions.addView(widthSpace(8))

        actions.addView(
            TextView(this).apply {
                text = if (screenshotUriStr != null) "Create & move" else "Create"
                setTextColor(Color.parseColor("#35384D"))
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                minimumHeight = dp(46)
                background = rounded(
                    14,
                    Color.parseColor("#CBB8FF")
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                setOnClickListener {
                    val name = editText.text.toString().trim()

                    when {
                        name.isEmpty() -> {
                            toast("Enter a folder name.")
                            return@setOnClickListener
                        }

                        name.length > 40 -> {
                            toast("Keep folder names under 40 characters.")
                            return@setOnClickListener
                        }

                        name.contains("/") || name.contains("\\") -> {
                            toast("Folder names can't contain / or \\.")
                            return@setOnClickListener
                        }
                    }

                    val added = FolderManager.addFolder(
                        this@NewFolderActivity,
                        name
                    )

                    if (!added && screenshotUriStr == null) {
                        toast("That folder is already in Scorg.")
                        return@setOnClickListener
                    }

                    screenshotUriStr?.let { uriString ->
                        FileMover.moveToFolder(
                            this@NewFolderActivity,
                            Uri.parse(uriString),
                            name
                        )
                    }

                    finish()
                }
            }
        )

        card.addView(actions)
        setContentView(card)

        val width = minOf(
            resources.displayMetrics.widthPixels - dp(32),
            dp(400)
        )

        window.setLayout(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        editText.requestFocus()
    }

    private fun space(heightDp: Int): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        }

    private fun widthSpace(widthDp: Int): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(widthDp),
                1
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

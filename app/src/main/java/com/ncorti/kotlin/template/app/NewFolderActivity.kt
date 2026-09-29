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
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = roundedWithStroke(
                22,
                Color.parseColor("#141B23"),
                Color.parseColor("#31404E")
            )
        }

        card.addView(
            TextView(this).apply {
                text = "NEW SORTING FOLDER"
                setTextColor(Color.parseColor("#5BE39B"))
                textSize = 10.5f
                letterSpacing = 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        card.addView(
            TextView(this).apply {
                text = "Name your folder"
                setTextColor(Color.parseColor("#F4F7FA"))
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(9), 0, 0)
            }
        )

        card.addView(
            TextView(this).apply {
                text = if (screenshotUriStr != null) {
                    "Scorg will create it and move this screenshot there."
                } else {
                    "It will appear in your sorting popup from now on."
                }
                setTextColor(Color.parseColor("#98A5B3"))
                textSize = 12.5f
                setPadding(0, dp(4), 0, dp(16))
            }
        )

        val editText = EditText(this).apply {
            hint = "e.g. Memes"
            setHintTextColor(Color.parseColor("#647280"))
            setTextColor(Color.parseColor("#F4F7FA"))
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedWithStroke(
                13,
                Color.parseColor("#1B242E"),
                Color.parseColor("#33404D")
            )
        }
        card.addView(editText)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val cancel = TextView(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#98A5B3"))
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener { finish() }
        }

        val create = TextView(this).apply {
            text = if (screenshotUriStr != null) "Create & move" else "Create"
            setTextColor(Color.parseColor("#0B0F14"))
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(
                13,
                Color.parseColor("#5BE39B")
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.15f
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

                if (!added) {
                    DiagnosticLog.add(
                        this@NewFolderActivity,
                        "New-folder request reused existing category=" + name
                    )
                }

                screenshotUriStr?.let { uriStr ->
                    FileMover.moveToFolder(
                        this@NewFolderActivity,
                        Uri.parse(uriStr),
                        name
                    )
                }

                finish()
            }
        }

        actions.addView(cancel)
        actions.addView(space(8))
        actions.addView(create)
        card.addView(actions)

        setContentView(card)

        val displayWidth = resources.displayMetrics.widthPixels
        val width = minOf(
            displayWidth - dp(32),
            dp(420)
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

    private fun space(widthDp: Int): TextView =
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(widthDp),
                1
            )
        }

    private fun rounded(
        radiusDp: Int,
        color: Int
    ): GradientDrawable =
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

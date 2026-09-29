package com.ncorti.kotlin.template.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
            setPadding(dp(20), dp(20), dp(24), dp(24))
            background = shadowed(22, Color.WHITE)
        }

        card.addView(
            TextView(this).apply {
                text = "NEW SORTING FOLDER"
                setTextColor(INK)
                textSize = 10f
                letterSpacing = 0.08f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = boxed(14, TEAL)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(10)
                }
            }
        )

        card.addView(
            TextView(this).apply {
                text = "Name your folder"
                setTextColor(INK)
                textSize = 22f
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
                setTextColor(Color.parseColor("#555A75"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(16))
            }
        )

        val editText = EditText(this).apply {
            hint = "e.g. Memes"
            setHintTextColor(Color.parseColor("#8A8FA8"))
            setTextColor(INK)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = boxed(14, Color.WHITE)
        }
        card.addView(editText)

        val existing = FolderManager.getFolders(this)
        val picks = listOf("Work", "Receipts", "Travel", "Memes", "Recipes")
            .filter { pick -> existing.none { it.equals(pick, ignoreCase = true) } }
            .take(3)

        if (picks.isNotEmpty()) {
            card.addView(
                TextView(this).apply {
                    text = "Quick picks"
                    setTextColor(INK)
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, dp(14), 0, dp(8))
                }
            )

            val pickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val pickColors = listOf(BLUE, TEAL, PINK)
            picks.forEachIndexed { index, pick ->
                pickRow.addView(
                    TextView(this).apply {
                        text = pick
                        setTextColor(INK)
                        textSize = 12.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        background = boxed(16, pickColors[index % pickColors.size])
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(8) }
                        setOnClickListener {
                            editText.setText(pick)
                            editText.setSelection(pick.length)
                        }
                    }
                )
            }
            card.addView(pickRow)
        }

        card.addView(space(18))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        actions.addView(
            TextView(this).apply {
                text = "Cancel"
                setTextColor(INK)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                background = boxed(14, Color.WHITE)
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
                setTextColor(INK)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                background = boxed(14, LAVENDER)
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

    private fun boxed(radiusDp: Int, fill: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(2), INK)
        }

    // Outlined card with a hard offset shadow (4dp down and right).
    private fun shadowed(radiusDp: Int, fill: Int): LayerDrawable {
        val shadow = GradientDrawable().apply {
            setColor(INK)
            cornerRadius = dp(radiusDp).toFloat()
        }
        return LayerDrawable(arrayOf(shadow, boxed(radiusDp, fill))).apply {
            setLayerInset(0, dp(4), dp(4), 0, 0)
            setLayerInset(1, 0, 0, dp(4), dp(4))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val INK = Color.parseColor("#2A2D40")
        val LAVENDER = Color.parseColor("#CBB8FF")
        val TEAL = Color.parseColor("#9EDFD8")
        val BLUE = Color.parseColor("#B9D1FA")
        val PINK = Color.parseColor("#F6D6DE")
    }
}
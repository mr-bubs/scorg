package com.ncorti.kotlin.template.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class NewFolderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenshotUriStr = intent.getStringExtra("screenshot_uri")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val title = TextView(this).apply {
            text = "New folder"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        val editText = EditText(this).apply {
            hint = "e.g. Recipes"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(24, 16, 24, 16)
            textSize = 16f
            setSingleLine(true)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 24, 0, 0)
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }

        val createBtn = Button(this).apply {
            text = "Create"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setOnClickListener {
                val name = editText.text.toString().trim()

                when {
                    name.isEmpty() -> {
                        toast("Enter a folder name")
                        return@setOnClickListener
                    }

                    name.length > 40 -> {
                        toast("Keep folder names under 40 characters")
                        return@setOnClickListener
                    }

                    name.contains("/") || name.contains("\\") -> {
                        toast("Folder names can't contain / or \\")
                        return@setOnClickListener
                    }
                }

                val added = FolderManager.addFolder(this@NewFolderActivity, name)
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

        row.addView(cancelBtn)
        row.addView(createBtn)
        layout.addView(title)
        layout.addView(editText)
        layout.addView(row)
        setContentView(layout)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

package com.ncorti.kotlin.template.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*

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
                if (name.isEmpty()) {
                    Toast.makeText(this@NewFolderActivity, "Enter a folder name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                FolderManager.addFolder(this@NewFolderActivity, name)
                
                screenshotUriStr?.let { uriStr ->
                    FileMover.moveToFolder(this@NewFolderActivity, Uri.parse(uriStr), name)
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
}

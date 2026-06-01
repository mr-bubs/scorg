package com.ncorti.kotlin.template.app 


import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File

class FileMover(private val context: Context) {

    fun moveToFolder(imagePath: String, folderName: String) {
        val file = File(imagePath)
        if (!file.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // 1. Find the internal Android Database ID for this specific screenshot
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.DATA} = ?"
                val selectionArgs = arrayOf(imagePath)
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                
                var imageUri: Uri? = null
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                }

                // 2. Update the path to route it to the new subfolder
                if (imageUri != null) {
                    val values = ContentValues().apply {
                        // Keeps it in DCIM/Screenshots, but nests it inside your custom folder
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Screenshots/$folderName")
                    }
                    context.contentResolver.update(imageUri!!, values, null, null)
                    Toast.makeText(context, "Moved to $folderName!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Scorg", "Failed to move file", e)
                Toast.makeText(context, "Error moving screenshot.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Scorg requires Android 10+ to organize files.", Toast.LENGTH_SHORT).show()
        }
    }
}

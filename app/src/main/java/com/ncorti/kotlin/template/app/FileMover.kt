package com.ncorti.kotlin.template.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.*

object FileMover {

    fun moveToFolder(context: Context, imageUri: Uri, folderName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = tryMove(context, imageUri, folderName)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "✅ Moved to $folderName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ Move failed — check MANAGE_MEDIA permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun tryMove(context: Context, imageUri: Uri, folderName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Screenshots/$folderName/")
                    put(MediaStore.Images.Media.DISPLAY_NAME, getFileName(context, imageUri))
                }
                val rows = context.contentResolver.update(imageUri, values, null, null)
                rows > 0
            } else {
                legacyMove(context, imageUri, folderName)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun legacyMove(context: Context, imageUri: Uri, folderName: String): Boolean {
        return try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            context.contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val sourcePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val sourceFile = java.io.File(sourcePath)
                val destDir = java.io.File(sourceFile.parentFile, folderName).also { it.mkdirs() }
                val destFile = java.io.File(destDir, sourceFile.name)
                val moved = sourceFile.renameTo(destFile)
                if (moved) {
                    context.contentResolver.delete(imageUri, null, null)
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }
                moved
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) { null }
    }
}

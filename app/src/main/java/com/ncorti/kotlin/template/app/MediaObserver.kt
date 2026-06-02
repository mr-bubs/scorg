package com.ncorti.kotlin.template.app

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import kotlinx.coroutines.*

class MediaObserver(
    private val context: Context,
    handler: Handler,
    private val onScreenshotDetected: (Uri) -> Unit
) : ContentObserver(handler) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null
    private var lastProcessedId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(1500L) 
            checkForNewScreenshot()
        }
    }

    private fun checkForNewScreenshot() {
        val tenSecondsAgo = (System.currentTimeMillis() / 1000L) - 10L

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(tenSecondsAgo.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return

            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            if (id == lastProcessedId) return 

            val data = runCatching {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            }.getOrDefault("")

            val displayName = runCatching {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
            }.getOrDefault("")

            val relativePath = runCatching {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
            }.getOrDefault("")

            val isScreenshot = listOf(data, displayName, relativePath).any { field ->
                field.contains("screenshot", ignoreCase = true) ||
                field.contains("screen_shot", ignoreCase = true) ||
                field.contains("screencap", ignoreCase = true)
            }

            if (isScreenshot) {
                lastProcessedId = id
                val imageUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                scope.launch(Dispatchers.Main) {
                    onScreenshotDetected(imageUri)
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}

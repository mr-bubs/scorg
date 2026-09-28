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
        DiagnosticLog.add(context, "ContentObserver.onChange selfChange=$selfChange uri=$uri")
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(1500L)
            checkForNewScreenshot("observer callback")
        }
    }

    fun scanNow() {
        scope.launch { checkForNewScreenshot("manual scan") }
    }

    private fun checkForNewScreenshot(reason: String) {
        DiagnosticLog.add(context, "MediaStore scan started: $reason")
        val tenSecondsAgo = (System.currentTimeMillis() / 1000L) - 10L
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val manual = reason == "manual scan"
        val selection = if (manual) null else "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = if (manual) null else arrayOf(tenSecondsAgo.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                DiagnosticLog.add(context, "MediaStore query returned ${cursor.count} row(s)")
                if (!cursor.moveToFirst()) {
                    DiagnosticLog.add(context, "No image rows matched the scan")
                    return
                }
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val data = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) }.getOrDefault("")
                val displayName = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) }.getOrDefault("")
                val relativePath = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)) }.getOrDefault("")
                val dateAdded = runCatching { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) }.getOrDefault(0L)
                val dateTaken = runCatching { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)) }.getOrDefault(0L)
                val imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                DiagnosticLog.add(context, "Newest image id=$id name=$displayName path=$relativePath dateAdded=$dateAdded dateTaken=$dateTaken uri=$imageUri data=$data")

                val isScreenshot = listOf(data, displayName, relativePath).any { field ->
                    field.contains("screenshot", ignoreCase = true) ||
                        field.contains("screen_shot", ignoreCase = true) ||
                        field.contains("screencap", ignoreCase = true)
                }
                DiagnosticLog.add(context, "Screenshot classification: $isScreenshot")
                if (!isScreenshot) return

                if (id == lastProcessedId && !manual) {
                    DiagnosticLog.add(context, "Skipped duplicate screenshot id=$id")
                    return
                }
                lastProcessedId = id
                scope.launch(Dispatchers.Main) {
                    DiagnosticLog.add(context, "Invoking overlay callback for $imageUri")
                    onScreenshotDetected(imageUri)
                }
            } ?: DiagnosticLog.add(context, "MediaStore query returned null cursor")
        } catch (t: Throwable) {
            DiagnosticLog.add(context, "MediaStore scan FAILED: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun destroy() {
        DiagnosticLog.add(context, "MediaObserver.destroy")
        scope.cancel()
    }
}

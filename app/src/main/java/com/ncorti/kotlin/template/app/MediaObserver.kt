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

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_TAKEN
    )

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        DiagnosticLog.add(context, "ContentObserver.onChange selfChange=$selfChange uri=$uri")

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500L)

            if (uri != null) {
                DiagnosticLog.add(context, "Inspecting exact callback URI: $uri")
                val handled = inspectExactUri(uri)
                if (handled) return@launch

                DiagnosticLog.add(
                    context,
                    "Exact callback URI did not produce a usable screenshot; falling back to broad MediaStore scan"
                )
            } else {
                DiagnosticLog.add(context, "Observer callback URI was null; using broad MediaStore scan")
            }

            scanLatestImage("observer fallback")
        }
    }

    fun scanNow() {
        scope.launch {
            DiagnosticLog.add(context, "Manual MediaStore scan started")
            scanLatestImage("manual scan")
        }
    }

    private fun inspectExactUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                DiagnosticLog.add(context, "Exact URI query returned ${cursor.count} row(s)")

                if (!cursor.moveToFirst()) {
                    DiagnosticLog.add(context, "Exact URI exists as callback but query returned no accessible row")
                    return false
                }

                inspectCurrentRow(cursor, uri, "exact callback URI")
            } ?: run {
                DiagnosticLog.add(context, "Exact URI query returned null cursor")
                false
            }
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "Exact URI query FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            false
        }
    }

    private fun scanLatestImage(reason: String): Boolean {
        DiagnosticLog.add(context, "Broad MediaStore scan started: $reason")

        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                DiagnosticLog.add(context, "Broad MediaStore query returned ${cursor.count} row(s)")

                if (!cursor.moveToFirst()) {
                    DiagnosticLog.add(context, "No accessible image rows found")
                    return false
                }

                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                )
                val imageUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                inspectCurrentRow(cursor, imageUri, reason)
            } ?: run {
                DiagnosticLog.add(context, "Broad MediaStore query returned null cursor")
                false
            }
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "Broad MediaStore scan FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            false
        }
    }

    private fun inspectCurrentRow(
        cursor: android.database.Cursor,
        imageUri: Uri,
        source: String
    ): Boolean {
        val id = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        )

        val data = runCatching {
            cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            )
        }.getOrDefault("")

        val displayName = runCatching {
            cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            )
        }.getOrDefault("")

        val relativePath = runCatching {
            cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            )
        }.getOrDefault("")

        val dateAdded = runCatching {
            cursor.getLong(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            )
        }.getOrDefault(0L)

        val dateTaken = runCatching {
            cursor.getLong(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            )
        }.getOrDefault(0L)

        DiagnosticLog.add(
            context,
            "Image metadata source=$source id=$id name=$displayName path=$relativePath " +
                "dateAdded=$dateAdded dateTaken=$dateTaken uri=$imageUri data=$data"
        )

        val isScreenshot = listOf(data, displayName, relativePath).any { field ->
            field.contains("screenshot", ignoreCase = true) ||
                field.contains("screen_shot", ignoreCase = true) ||
                field.contains("screencap", ignoreCase = true)
        }

        DiagnosticLog.add(
            context,
            "Screenshot classification from $source: $isScreenshot"
        )

        if (!isScreenshot) return false

        if (id == lastProcessedId) {
            DiagnosticLog.add(context, "Skipped duplicate screenshot id=$id")
            return true
        }

        lastProcessedId = id

        scope.launch(Dispatchers.Main) {
            DiagnosticLog.add(
                context,
                "Invoking overlay callback for $imageUri from $source"
            )
            onScreenshotDetected(imageUri)
        }

        return true
    }

    fun destroy() {
        DiagnosticLog.add(context, "MediaObserver.destroy")
        scope.cancel()
    }
}

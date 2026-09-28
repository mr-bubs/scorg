package com.ncorti.kotlin.template.app

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
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
            delay(300L)

            if (uri == null) {
                DiagnosticLog.add(context, "Observer URI is null; using broad MediaStore scan")
                scanLatestAcrossVolumes("null observer URI")
                return@launch
            }

            probeExactUriWithRetries(uri)
        }
    }

    fun scanNow() {
        scope.launch {
            DiagnosticLog.add(context, "Manual MediaStore scan started")
            scanLatestAcrossVolumes("manual scan")
        }
    }

    private suspend fun probeExactUriWithRetries(uri: Uri) {
        val waits = longArrayOf(0L, 1000L, 1500L, 2500L, 5000L)

        for (attempt in waits.indices) {
            val waitMs = waits[attempt]
            if (waitMs > 0) delay(waitMs)

            DiagnosticLog.add(
                context,
                "Exact URI probe attempt=${attempt + 1}/${waits.size} uri=$uri"
            )

            if (inspectExactUri(uri)) {
                return
            }
        }

        DiagnosticLog.add(
            context,
            "Exact URI still inaccessible/not classified after retries; running broad fallback"
        )
        scanLatestAcrossVolumes("observer fallback after retries")
    }

    private fun inspectExactUri(uri: Uri): Boolean {
        val resolver = context.contentResolver

        val mime = runCatching { resolver.getType(uri) }
            .onFailure {
                DiagnosticLog.add(
                    context,
                    "getType FAILED: ${it.javaClass.simpleName}: ${it.message}"
                )
            }
            .getOrNull()

        DiagnosticLog.add(context, "Exact URI MIME type: $mime")

        val minimalCount = try {
            resolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )?.use { cursor ->
                DiagnosticLog.add(context, "Minimal exact query returned ${cursor.count} row(s)")
                cursor.count
            } ?: run {
                DiagnosticLog.add(context, "Minimal exact query returned null cursor")
                0
            }
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "Minimal exact query FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            0
        }

        val streamReadable = try {
            resolver.openInputStream(uri)?.use { stream ->
                stream.read()
                true
            } ?: false
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "openInputStream FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            false
        }

        DiagnosticLog.add(context, "Exact URI streamReadable=$streamReadable")

        if (minimalCount <= 0) {
            return false
        }

        return queryAndClassify(uri, "exact callback URI")
    }

    private fun metadataProjection(): Array<String> {
        val columns = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns.add(MediaStore.MediaColumns.IS_PENDING)
        }

        return columns.toTypedArray()
    }

    private fun queryAndClassify(uri: Uri, source: String): Boolean {
        return try {
            context.contentResolver.query(
                uri,
                metadataProjection(),
                null,
                null,
                null
            )?.use { cursor ->
                DiagnosticLog.add(
                    context,
                    "Metadata query source=$source returned ${cursor.count} row(s)"
                )

                if (!cursor.moveToFirst()) return false
                inspectCurrentRow(cursor, uri, source)
            } ?: run {
                DiagnosticLog.add(context, "Metadata query source=$source returned null cursor")
                false
            }
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "Metadata query source=$source FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            false
        }
    }

    private fun scanLatestAcrossVolumes(reason: String): Boolean {
        DiagnosticLog.add(context, "Broad MediaStore scan started: $reason")

        val uris = linkedSetOf<Uri>()
        uris.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val names = runCatching { MediaStore.getExternalVolumeNames(context) }
                .getOrElse { emptySet() }

            DiagnosticLog.add(context, "External volume names: $names")

            for (name in names) {
                uris.add(MediaStore.Images.Media.getContentUri(name))
            }
        }

        for (uri in uris) {
            try {
                context.contentResolver.query(
                    uri,
                    metadataProjection(),
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    DiagnosticLog.add(
                        context,
                        "Broad query uri=$uri returned ${cursor.count} row(s)"
                    )

                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        )
                        val itemUri = Uri.withAppendedPath(uri, id.toString())
                        if (inspectCurrentRow(cursor, itemUri, "broad scan $uri")) {
                            return true
                        }
                    }
                } ?: DiagnosticLog.add(context, "Broad query uri=$uri returned null cursor")
            } catch (t: Throwable) {
                DiagnosticLog.add(
                    context,
                    "Broad query uri=$uri FAILED: ${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }

        DiagnosticLog.add(context, "No accessible/classified screenshot row found in broad scan")
        return false
    }

    private fun inspectCurrentRow(
        cursor: Cursor,
        imageUri: Uri,
        source: String
    ): Boolean {
        val id = cursor.getLong(
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        )

        val displayName = cursor.stringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME)
        val relativePath = cursor.stringOrEmpty(MediaStore.Images.Media.RELATIVE_PATH)
        val dateAdded = cursor.longOrZero(MediaStore.Images.Media.DATE_ADDED)
        val dateTaken = cursor.longOrZero(MediaStore.Images.Media.DATE_TAKEN)
        val isPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.longOrZero(MediaStore.MediaColumns.IS_PENDING)
        } else {
            -1L
        }

        DiagnosticLog.add(
            context,
            "Image metadata source=$source id=$id name=$displayName path=$relativePath " +
                "dateAdded=$dateAdded dateTaken=$dateTaken isPending=$isPending uri=$imageUri"
        )

        val isScreenshot = listOf(displayName, relativePath).any { field ->
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

    private fun Cursor.stringOrEmpty(column: String): String {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun Cursor.longOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }

    fun destroy() {
        DiagnosticLog.add(context, "MediaObserver.destroy")
        scope.cancel()
    }
}

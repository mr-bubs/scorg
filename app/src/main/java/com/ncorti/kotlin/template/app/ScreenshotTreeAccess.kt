package com.ncorti.kotlin.template.app

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.atomic.AtomicLong

object ScreenshotTreeAccess {

    private const val PREFS = "scorg_prefs"
    private const val KEY_TREE_URI = "screenshots_tree_uri"
    private const val KEY_LAST_PROCESSED_URI = "last_processed_screenshot_uri"
    private const val RECENT_WINDOW_MS = 20_000L
    private val suppressObserverUntil = AtomicLong(0L)

    data class ScreenshotEntry(
        val uri: Uri,
        val name: String,
        val lastModified: Long
    )

    fun saveTreeUri(context: Context, uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        return try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TREE_URI, uri.toString())
                .remove(KEY_LAST_PROCESSED_URI)
                .apply()

            val root = DocumentFile.fromTreeUri(context, uri)
            DiagnosticLog.add(
                context,
                "SAF tree saved uri=" + uri +
                    " name=" + root?.name +
                    " canRead=" + root?.canRead() +
                    " canWrite=" + root?.canWrite()
            )
            true
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF tree save FAILED: " + t.javaClass.simpleName + ": " + t.message
            )
            false
        }
    }

    fun getTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
            ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun hasTreeAccess(context: Context): Boolean {
        val uri = getTreeUri(context) ?: return false

        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!persisted) return false

        val root = DocumentFile.fromTreeUri(context, uri) ?: return false
        return root.exists() && root.canRead() && root.canWrite()
    }

    fun treeLabel(context: Context): String {
        val uri = getTreeUri(context) ?: return "Not selected"
        return DocumentFile.fromTreeUri(context, uri)?.name ?: "Selected folder"
    }

    fun getLastProcessedUri(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PROCESSED_URI, null)

    fun markProcessed(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PROCESSED_URI, uri.toString())
            .apply()
    }

    fun suppressObserverEventsFor(durationMs: Long) {
        val until = SystemClock.elapsedRealtime() + durationMs
        while (true) {
            val current = suppressObserverUntil.get()
            if (current >= until) return
            if (suppressObserverUntil.compareAndSet(current, until)) return
        }
    }

    fun observerSuppressionRemainingMs(): Long =
        (suppressObserverUntil.get() - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun findNewestScreenshot(
        context: Context,
        recentOnly: Boolean
    ): ScreenshotEntry? {
        val treeUri = getTreeUri(context) ?: run {
            DiagnosticLog.add(context, "SAF scan skipped: no Screenshots folder selected")
            return null
        }

        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            DiagnosticLog.add(context, "SAF scan failed: invalid tree URI")
            return null
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            treeDocumentId
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        var newest: ScreenshotEntry? = null
        var directFileCount = 0

        try {
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val modifiedIndex = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                val mimeIndex = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                while (cursor.moveToNext()) {
                    val mime = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) {
                        cursor.getString(mimeIndex)
                    } else {
                        ""
                    }

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    directFileCount++

                    val name = cursor.getString(nameIndex).orEmpty()

                    val modified = if (
                        modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)
                    ) {
                        cursor.getLong(modifiedIndex)
                    } else {
                        0L
                    }

                    val documentId = cursor.getString(idIndex)
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                    )

                    val candidate = ScreenshotEntry(
                        uri = documentUri,
                        name = name,
                        lastModified = modified
                    )

                    if (
                        newest == null ||
                        candidate.lastModified > newest!!.lastModified
                    ) {
                        newest = candidate
                    }
                }
            } ?: DiagnosticLog.add(context, "SAF children query returned null cursor")
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF children query FAILED: " + t.javaClass.simpleName + ": " + t.message
            )
            return null
        }

        DiagnosticLog.add(
            context,
            "SAF scan directFiles=" + directFileCount +
                " recentOnly=" + recentOnly
        )

        val result = newest ?: run {
            DiagnosticLog.add(context, "SAF scan: no files found in selected folder")
            return null
        }

        val now = System.currentTimeMillis()
        val age = if (result.lastModified > 0L) {
            now - result.lastModified
        } else {
            Long.MAX_VALUE
        }

        DiagnosticLog.add(
            context,
            "SAF newest name=" + result.name +
                " modified=" + result.lastModified +
                " ageMs=" + age +
                " uri=" + result.uri
        )

        if (recentOnly && result.lastModified > 0L && age > RECENT_WINDOW_MS) {
            DiagnosticLog.add(context, "SAF newest file is older than recent window; ignoring")
            return null
        }

        return result
    }

    fun moveToFolder(
        context: Context,
        sourceUri: Uri,
        folderName: String
    ): Boolean {
        val treeUri = getTreeUri(context) ?: return false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return false

        val targetDir = root.findFile(folderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(folderName)
            ?: return false

        DiagnosticLog.add(
            context,
            "SAF move requested source=" + source.name +
                " targetFolder=" + folderName +
                " sourceUri=" + sourceUri
        )

        suppressObserverEventsFor(1_500L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val movedUri = DocumentsContract.moveDocument(
                    context.contentResolver,
                    sourceUri,
                    root.uri,
                    targetDir.uri
                )
                if (movedUri != null) {
                    suppressObserverEventsFor(1_500L)
                    DiagnosticLog.add(
                        context,
                        "SAF moveDocument success movedUri=" + movedUri
                    )
                    return true
                }
            } catch (t: Throwable) {
                DiagnosticLog.add(
                    context,
                    "SAF moveDocument FAILED: " +
                        t.javaClass.simpleName + ": " + t.message +
                        "; trying copy/delete"
                )
            }
        }

        val copied = copyThenDelete(context, source, targetDir)
        if (copied) suppressObserverEventsFor(1_500L)
        return copied
    }

    private fun copyThenDelete(
        context: Context,
        source: DocumentFile,
        targetDir: DocumentFile
    ): Boolean {
        val name = source.name ?: return false
        val mime = source.type ?: "application/octet-stream"
        val destination = targetDir.createFile(mime, name) ?: return false

        return try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(destination.uri)?.use { output ->
                    input.copyTo(output)
                } ?: return false
            } ?: return false

            val deleted = source.delete()
            DiagnosticLog.add(
                context,
                "SAF copy/delete copied=true sourceDeleted=" + deleted +
                    " destination=" + destination.uri
            )
            deleted
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF copy/delete FAILED: " +
                    t.javaClass.simpleName + ": " + t.message
            )
            runCatching { destination.delete() }
            false
        }
    }
}

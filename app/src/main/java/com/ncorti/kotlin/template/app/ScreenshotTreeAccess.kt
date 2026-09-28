package com.ncorti.kotlin.template.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

object ScreenshotTreeAccess {

    private const val PREFS = "scorg_prefs"
    private const val KEY_TREE_URI = "screenshots_tree_uri"
    private const val RECENT_WINDOW_MS = 20_000L

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
                .apply()

            val root = DocumentFile.fromTreeUri(context, uri)
            DiagnosticLog.add(
                context,
                "SAF tree saved uri=$uri name=${root?.name} canRead=${root?.canRead()} canWrite=${root?.canWrite()}"
            )
            true
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF tree save FAILED: ${t.javaClass.simpleName}: ${t.message}"
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

    fun findNewestScreenshot(
        context: Context,
        recentOnly: Boolean
    ): ScreenshotEntry? {
        val treeUri = getTreeUri(context) ?: run {
            DiagnosticLog.add(context, "SAF scan skipped: no Screenshots folder selected")
            return null
        }

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            DiagnosticLog.add(context, "SAF scan failed: cannot open persisted tree uri=$treeUri")
            return null
        }

        val files = try {
            root.listFiles().filter { it.isFile }
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF listFiles FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            return null
        }

        DiagnosticLog.add(
            context,
            "SAF scan root=${root.name} directFiles=${files.size} recentOnly=$recentOnly"
        )

        val screenshots = files.filter { file ->
            val name = file.name.orEmpty()
            name.contains("screenshot", ignoreCase = true) ||
                name.contains("screen_shot", ignoreCase = true) ||
                name.contains("screencap", ignoreCase = true)
        }

        val newest = screenshots.maxByOrNull { it.lastModified() } ?: run {
            DiagnosticLog.add(context, "SAF scan: no screenshot-like files found in selected folder")
            return null
        }

        val now = System.currentTimeMillis()
        val age = if (newest.lastModified() > 0L) now - newest.lastModified() else Long.MAX_VALUE

        DiagnosticLog.add(
            context,
            "SAF newest name=${newest.name} modified=${newest.lastModified()} ageMs=$age uri=${newest.uri}"
        )

        if (recentOnly && age > RECENT_WINDOW_MS) {
            DiagnosticLog.add(context, "SAF newest file is older than recent window; ignoring")
            return null
        }

        return ScreenshotEntry(
            uri = newest.uri,
            name = newest.name.orEmpty(),
            lastModified = newest.lastModified()
        )
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
            "SAF move requested source=${source.name} targetFolder=$folderName sourceUri=$sourceUri"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val movedUri = DocumentsContract.moveDocument(
                    context.contentResolver,
                    sourceUri,
                    root.uri,
                    targetDir.uri
                )
                if (movedUri != null) {
                    DiagnosticLog.add(context, "SAF moveDocument success movedUri=$movedUri")
                    return true
                }
            } catch (t: Throwable) {
                DiagnosticLog.add(
                    context,
                    "SAF moveDocument FAILED: ${t.javaClass.simpleName}: ${t.message}; trying copy/delete"
                )
            }
        }

        return copyThenDelete(context, source, targetDir)
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
                "SAF copy/delete result copied=true sourceDeleted=$deleted destination=${destination.uri}"
            )
            deleted
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "SAF copy/delete FAILED: ${t.javaClass.simpleName}: ${t.message}"
            )
            runCatching { destination.delete() }
            false
        }
    }
}

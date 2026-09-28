package com.ncorti.kotlin.template.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import kotlinx.coroutines.*

object FileMover {

    fun moveToFolder(context: Context, imageUri: Uri, folderName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val success =
                DocumentsContract.isDocumentUri(context, imageUri) &&
                    ScreenshotTreeAccess.hasTreeAccess(context) &&
                    ScreenshotTreeAccess.moveToFolder(context, imageUri, folderName)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(
                        context,
                        "✅ Moved to " + folderName,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    DiagnosticLog.add(
                        context,
                        "Move FAILED uri=" + imageUri + " target=" + folderName
                    )
                    Toast.makeText(
                        context,
                        "❌ Move failed — reconnect the Screenshots folder in Scorg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

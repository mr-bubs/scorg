package com.ncorti.kotlin.template.app

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import kotlinx.coroutines.*

class MediaObserver(
    private val context: Context,
    handler: Handler,
    private val onScreenshotDetected: (Uri) -> Unit
) : ContentObserver(handler) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null
    private var lastProcessedSafUri: String? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        DiagnosticLog.add(
            context,
            "ContentObserver.onChange selfChange=" + selfChange + " mediaUri=" + uri
        )

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(250L)
            findRecentSafScreenshot()
        }
    }

    fun scanNow() {
        scope.launch {
            DiagnosticLog.add(context, "Manual SAF screenshot-folder scan started")

            if (!ScreenshotTreeAccess.hasTreeAccess(context)) {
                DiagnosticLog.add(context, "Manual SAF scan FAILED: Screenshots folder is not granted")
                return@launch
            }

            val entry = ScreenshotTreeAccess.findNewestScreenshot(
                context = context,
                recentOnly = false
            )

            if (entry == null) {
                DiagnosticLog.add(context, "Manual SAF scan found no screenshot")
                return@launch
            }

            lastProcessedSafUri = entry.uri.toString()
            dispatchScreenshot(entry.uri, "manual SAF scan")
        }
    }

    private suspend fun findRecentSafScreenshot() {
        if (!ScreenshotTreeAccess.hasTreeAccess(context)) {
            DiagnosticLog.add(
                context,
                "Screenshot event detected but SAF Screenshots folder access is missing"
            )
            return
        }

        val waits = longArrayOf(0L, 300L, 500L, 800L, 1200L, 2000L)

        for (index in waits.indices) {
            val waitMs = waits[index]
            if (waitMs > 0L) delay(waitMs)

            DiagnosticLog.add(
                context,
                "SAF screenshot lookup attempt=" + (index + 1) + "/" + waits.size
            )

            val entry = ScreenshotTreeAccess.findNewestScreenshot(
                context = context,
                recentOnly = true
            ) ?: continue

            val key = entry.uri.toString()
            if (key == lastProcessedSafUri) {
                DiagnosticLog.add(
                    context,
                    "SAF screenshot already processed uri=" + entry.uri
                )
                return
            }

            lastProcessedSafUri = key
            DiagnosticLog.add(
                context,
                "SAF screenshot detected name=" + entry.name + " uri=" + entry.uri
            )
            dispatchScreenshot(entry.uri, "automatic SAF lookup")
            return
        }

        DiagnosticLog.add(
            context,
            "MediaStore event fired but no new screenshot became visible in granted SAF folder"
        )
    }

    private fun dispatchScreenshot(uri: Uri, source: String) {
        scope.launch(Dispatchers.Main) {
            DiagnosticLog.add(
                context,
                "Invoking overlay callback for SAF uri=" + uri + " source=" + source
            )
            onScreenshotDetected(uri)
        }
    }

    fun destroy() {
        DiagnosticLog.add(context, "MediaObserver.destroy")
        scope.cancel()
    }
}

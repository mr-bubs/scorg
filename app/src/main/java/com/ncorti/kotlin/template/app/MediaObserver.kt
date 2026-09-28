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
    private var lastProcessedSafUri: String? =
        ScreenshotTreeAccess.getLastProcessedUri(context)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        val suppressedFor = ScreenshotTreeAccess.observerSuppressionRemainingMs()

        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (suppressedFor > 0L) {
                DiagnosticLog.add(
                    context,
                    "Observer event during SCORG move; deferring scan by " +
                        suppressedFor + "ms"
                )
                delay(suppressedFor + 100L)
            } else {
                delay(150L)
            }

            findRecentSafScreenshot()
        }
    }

    fun scanNow() {
        scope.launch {
            DiagnosticLog.add(context, "Manual screenshot-folder scan started")

            if (!ScreenshotTreeAccess.hasTreeAccess(context)) {
                DiagnosticLog.add(
                    context,
                    "Manual scan FAILED: Screenshots folder is not granted"
                )
                return@launch
            }

            val entry = ScreenshotTreeAccess.findNewestScreenshot(
                context = context,
                recentOnly = false
            )

            if (entry == null) {
                DiagnosticLog.add(context, "Manual scan found no screenshot")
                return@launch
            }

            processEntry(entry, "manual scan")
        }
    }

    private suspend fun findRecentSafScreenshot() {
        if (!ScreenshotTreeAccess.hasTreeAccess(context)) {
            DiagnosticLog.add(
                context,
                "Screenshot event detected but Screenshots folder access is missing"
            )
            return
        }

        val waits = longArrayOf(0L, 250L, 450L, 700L, 1_000L)

        for (index in waits.indices) {
            val waitMs = waits[index]
            if (waitMs > 0L) delay(waitMs)

            val entry = ScreenshotTreeAccess.findNewestScreenshot(
                context = context,
                recentOnly = true
            ) ?: continue

            if (processEntry(entry, "automatic lookup")) {
                return
            }
        }

        DiagnosticLog.add(
            context,
            "Screenshot signal received but no new root screenshot was found"
        )
    }

    private fun processEntry(
        entry: ScreenshotTreeAccess.ScreenshotEntry,
        source: String
    ): Boolean {
        val key = entry.uri.toString()

        if (key == lastProcessedSafUri) {
            DiagnosticLog.add(
                context,
                "Ignoring already-processed screenshot uri=" + entry.uri
            )
            return true
        }

        lastProcessedSafUri = key
        ScreenshotTreeAccess.markProcessed(context, entry.uri)

        DiagnosticLog.add(
            context,
            "Screenshot ready name=" + entry.name +
                " source=" + source
        )

        dispatchScreenshot(entry.uri)
        return true
    }

    private fun dispatchScreenshot(uri: Uri) {
        scope.launch(Dispatchers.Main) {
            DiagnosticLog.add(
                context,
                "Showing sort popup for uri=" + uri
            )
            onScreenshotDetected(uri)
        }
    }

    fun destroy() {
        DiagnosticLog.add(context, "MediaObserver.destroy")
        scope.cancel()
    }
}

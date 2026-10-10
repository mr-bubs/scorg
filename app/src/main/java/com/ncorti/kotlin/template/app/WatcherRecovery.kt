package com.ncorti.kotlin.template.app

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/**
 * Best-effort recovery only where Android allows it.
 * Never schedules repeated forced restarts or overrides a system/user Stop action.
 */
object WatcherRecovery {
    fun missingRequirement(context: Context): String? {
        if (!Settings.canDrawOverlays(context)) return "overlay permission"
        if (!ScreenshotTreeAccess.hasTreeAccess(context)) return "Screenshots folder access"

        val mediaOk = when {
            Build.VERSION.SDK_INT >= 34 ->
                granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (!mediaOk) return "media detection permission"

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) return "notification permission"

        return null
    }

    fun logPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = context.getSystemService(ActivityManager::class.java)
            val latest = manager
                .getHistoricalProcessExitReasons(null, 0, 5)
                .firstOrNull { it.timestamp > 0L } ?: return

            if (latest.timestamp <= WatcherPreferences.lastRecordedExit(context)) return
            WatcherPreferences.setLastRecordedExit(context, latest.timestamp)

            val type = when (latest.reason) {
                ApplicationExitInfo.REASON_CRASH -> "CRASH"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCES"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
                ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                ApplicationExitInfo.REASON_OTHER -> "OTHER"
                else -> "reason=${latest.reason}"
            }

            val whenText = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.MEDIUM
            ).format(Date(latest.timestamp))

            DiagnosticLog.add(
                context,
                "Previous process exit: type=$type " +
                    "at=$whenText status=${latest.status} " +
                    "importance=${latest.importance} " +
                    "detail=${latest.description?.take(120).orEmpty()}"
            )
        } catch (t: Throwable) {
            DiagnosticLog.add(
                context,
                "Process exit history unavailable: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun tryRestoreFromSystemEvent(context: Context, event: String) {
        if (!WatcherPreferences.isWatchingRequested(context)) {
            DiagnosticLog.add(context, "System event $event: watcher not requested; skip")
            return
        }

        val missing = missingRequirement(context)
        if (missing != null) {
            DiagnosticLog.add(context, "System event $event: cannot restore; missing $missing")
            return
        }

        val intent = Intent(context, ScreenshotDetectorService::class.java).apply {
            action = ScreenshotDetectorService.ACTION_RESTORE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            DiagnosticLog.add(context, "System event $event: restore requested")
        } catch (t: Throwable) {
            // Background FGS starts are restricted on modern Android and on some OEMs.
            DiagnosticLog.add(
                context,
                "System event $event: restore disallowed: " +
                    "${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }
}

package com.ncorti.kotlin.template.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * User-opted-in restore opportunity after device reboot or an app update.
 * This does not bypass Android's background-launch and user-stop restrictions.
 */
class WatcherRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.action ?: return
        if (
            event == Intent.ACTION_BOOT_COMPLETED ||
            event == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WatcherRecovery.tryRestoreFromSystemEvent(context, event)
        }
    }
}

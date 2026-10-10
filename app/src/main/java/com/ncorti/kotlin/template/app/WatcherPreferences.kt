package com.ncorti.kotlin.template.app

import android.content.Context

/** Tracks explicit user intent, not whether Android has kept a process alive. */
object WatcherPreferences {
    private const val NAME = "scorg_watcher_preferences"
    private const val KEY_WATCHING_REQUESTED = "watching_requested"
    private const val KEY_RECORDED_EXIT = "recorded_exit_timestamp"

    fun isWatchingRequested(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WATCHING_REQUESTED, false)

    fun setWatchingRequested(context: Context, requested: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WATCHING_REQUESTED, requested)
            .apply()
    }

    fun lastRecordedExit(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong(KEY_RECORDED_EXIT, 0L)

    fun setLastRecordedExit(context: Context, timestamp: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_RECORDED_EXIT, timestamp)
            .apply()
    }
}

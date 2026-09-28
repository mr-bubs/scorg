package com.ncorti.kotlin.template.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private const val PREFS = "scorg_diagnostics"
    private const val KEY_LOG = "log"
    private const val MAX_CHARS = 24000

    @Synchronized
    fun add(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val old = prefs.getString(KEY_LOG, "").orEmpty()
        var updated = "$time  $message\n$old"
        if (updated.length > MAX_CHARS) updated = updated.take(MAX_CHARS)
        prefs.edit().putString(KEY_LOG, updated).apply()
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "No diagnostic events yet.").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOG).apply()
        add(context, "Diagnostic log cleared")
    }
}

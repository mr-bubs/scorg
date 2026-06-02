package com.ncorti.kotlin.template.app

import android.content.Context

object FolderManager {
    private const val PREFS = "scorg_prefs"
    private const val KEY_FOLDERS = "folder_list"
    private val DEFAULT_FOLDERS = listOf("Work", "Recipes", "Shopping")

    fun getFolders(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDERS, null)
            ?: return DEFAULT_FOLDERS
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun addFolder(context: Context, name: String): Boolean {
        val current = getFolders(context).toMutableList()
        if (current.contains(name)) return false
        current.add(name)
        save(context, current)
        return true
    }

    fun removeFolder(context: Context, name: String) {
        val current = getFolders(context).toMutableList()
        current.remove(name)
        save(context, current)
    }

    private fun save(context: Context, folders: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDERS, folders.joinToString("|"))
            .apply()
    }
}

package com.example.archive

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class ArchivePreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("soypics_vault_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ARCHIVE_TREE_URI = "key_archive_tree_uri"
        private const val KEY_ARCHIVE_DIR = "key_archive_dir"
        private const val KEY_ARCHIVE_DISPLAY_PATH = "key_archive_display_path"
        private const val KEY_VAULT_PASSWORD = "key_vault_password"
        private const val KEY_FIRST_SETUP = "key_first_setup_completed"
        const val DEFAULT_PASSWORD = "soypics2026"
    }

    private val defaultDir: String = context.filesDir.absolutePath

    fun getArchiveTreeUri(): String? {
        return prefs.getString(KEY_ARCHIVE_TREE_URI, null)
    }

    fun setArchiveTreeUri(uriString: String, displayPath: String) {
        prefs.edit()
            .putString(KEY_ARCHIVE_TREE_URI, uriString)
            .putString(KEY_ARCHIVE_DISPLAY_PATH, displayPath)
            .remove(KEY_ARCHIVE_DIR)
            .apply()
    }

    fun getArchiveDirectoryPath(): String {
        return prefs.getString(KEY_ARCHIVE_DIR, defaultDir) ?: defaultDir
    }

    fun setArchiveDirectoryPath(path: String, displayPath: String = path) {
        prefs.edit()
            .putString(KEY_ARCHIVE_DIR, path)
            .putString(KEY_ARCHIVE_DISPLAY_PATH, displayPath)
            .remove(KEY_ARCHIVE_TREE_URI)
            .apply()
    }

    fun getArchiveDisplayPath(): String {
        val display = prefs.getString(KEY_ARCHIVE_DISPLAY_PATH, null)
        if (!display.isNullOrBlank()) {
            val clean = display.trim().trimEnd('/')
            return if (clean.endsWith("gamelog.logz")) clean else "$clean/gamelog.logz"
        }

        val tree = getArchiveTreeUri()
        if (tree != null) return "SD Card: /gamelog.logz"

        val dir = getArchiveDirectoryPath().trim().trimEnd('/')
        return "$dir/gamelog.logz"
    }

    fun getVaultPassword(): String {
        return prefs.getString(KEY_VAULT_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    fun setVaultPassword(password: String) {
        prefs.edit().putString(KEY_VAULT_PASSWORD, password).apply()
    }

    fun isFirstSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_FIRST_SETUP, false)
    }

    fun setFirstSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_SETUP, completed).apply()
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}

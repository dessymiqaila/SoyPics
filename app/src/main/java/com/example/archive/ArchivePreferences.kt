package com.example.archive

import android.content.Context
import android.content.SharedPreferences

class ArchivePreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("soypics_vault_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ARCHIVE_TREE_URI = "key_archive_tree_uri"
        private const val KEY_ARCHIVE_DIR = "key_archive_dir"
        private const val KEY_ARCHIVE_DISPLAY_PATH = "key_archive_display_path"
        private const val KEY_VAULT_PASSWORD = "key_vault_password"
        private const val KEY_FIRST_SETUP = "key_first_setup_completed"
        private const val KEY_THEME_MODE = "key_theme_mode" // "DARK", "LIGHT", "SYSTEM"
        private const val KEY_SLIDE_ORIENTATION = "key_slide_orientation" // "VERTICAL", "HORIZONTAL"
        private const val KEY_SMOOTHNESS_MS = "key_smoothness_ms" // 200..800
        private const val KEY_SHUFFLE_ORDER = "key_shuffle_order" // boolean (default true)
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

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getSlideOrientation(): String {
        return prefs.getString(KEY_SLIDE_ORIENTATION, "VERTICAL") ?: "VERTICAL"
    }

    fun setSlideOrientation(orientation: String) {
        prefs.edit().putString(KEY_SLIDE_ORIENTATION, orientation).apply()
    }

    fun getSmoothnessMs(): Int {
        return prefs.getInt(KEY_SMOOTHNESS_MS, 400)
    }

    fun setSmoothnessMs(ms: Int) {
        prefs.edit().putInt(KEY_SMOOTHNESS_MS, ms).apply()
    }

    fun isShuffleEnabled(): Boolean {
        // Default to TRUE per user request for fully randomized viewer
        return prefs.getBoolean(KEY_SHUFFLE_ORDER, true)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_ORDER, enabled).apply()
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}

package com.mkpdf.app

import android.content.Context

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Small store for user settings that must survive closing the app. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode {
        val saved = prefs.getString("theme", null) ?: return ThemeMode.SYSTEM
        return try { ThemeMode.valueOf(saved) } catch (_: IllegalArgumentException) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
    }
}
package dev.empyreal.client.core.state

import android.content.Context

class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("empyreal_theme", Context.MODE_PRIVATE)

    var darkMode: String
        get() = prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()

    var hudEnabled: Boolean
        get() = prefs.getBoolean(KEY_HUD, true)
        set(value) = prefs.edit().putBoolean(KEY_HUD, value).apply()

    companion object {
        const val MODE_SYSTEM = "system"
        const val MODE_LIGHT = "light"
        const val MODE_DARK = "dark"
        private const val KEY_MODE = "mode"
        private const val KEY_DYNAMIC = "dynamic"
        private const val KEY_HUD = "hud"
    }
}

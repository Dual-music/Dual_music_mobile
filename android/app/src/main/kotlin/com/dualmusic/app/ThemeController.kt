package com.dualmusic.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Préférence de thème choisie par l'utilisateur. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Gère le thème (clair / sombre / système) et le **persiste** entre les lancements
 * (SharedPreferences). Le mode est exposé en [StateFlow] pour piloter [DualMusicTheme]
 * depuis `setContent`.
 *
 * @param context contexte application.
 */
class ThemeController(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("dm_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(load())
    /** Mode courant, observable. */
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Change le mode et le persiste. */
    fun set(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)

    private companion object {
        const val KEY = "theme_mode"
    }
}

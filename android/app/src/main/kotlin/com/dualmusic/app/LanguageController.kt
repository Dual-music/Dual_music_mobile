package com.dualmusic.app

import android.content.Context
import com.dualmusic.core.ui.i18n.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gère la langue de l'interface (FR / EN) et la **persiste** entre les lancements
 * (SharedPreferences). Exposée en [StateFlow] pour fournir les chaînes localisées
 * (`LocalStrings`) depuis `setContent`.
 *
 * @param context contexte application.
 */
class LanguageController(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("dm_prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(load())
    /** Langue courante, observable. */
    val language: StateFlow<Language> = _language.asStateFlow()

    /** Change la langue et la persiste. */
    fun set(language: Language) {
        prefs.edit().putString(KEY, language.name).apply()
        _language.value = language
    }

    private fun load(): Language =
        runCatching { Language.valueOf(prefs.getString(KEY, Language.FR.name)!!) }
            .getOrDefault(Language.FR)

    private companion object {
        const val KEY = "app_language"
    }
}

package com.dualmusic.app

import android.content.Context
import com.dualmusic.core.ui.currency.DisplayCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gère la **devise d'affichage** choisie et la persiste (SharedPreferences).
 * On stocke le facteur euro→devise déjà calculé (à partir des taux de change) pour que
 * l'affichage n'ait pas à refaire d'appel réseau. Exposé en [StateFlow] pour alimenter
 * [com.dualmusic.core.ui.currency.LocalCurrency] depuis `setContent`.
 *
 * @param context contexte application.
 */
class CurrencyController(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("dm_prefs", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(load())
    /** Devise courante, observable. */
    val currency: StateFlow<DisplayCurrency> = _currency.asStateFlow()

    /** Change la devise et la persiste. */
    fun set(currency: DisplayCurrency) {
        prefs.edit()
            .putString(KEY_CODE, currency.code)
            .putString(KEY_SYMBOL, currency.symbol)
            .putString(KEY_FACTOR, currency.eurToTarget.toString())
            .apply()
        _currency.value = currency
    }

    private fun load(): DisplayCurrency = DisplayCurrency(
        code = prefs.getString(KEY_CODE, "EUR") ?: "EUR",
        symbol = prefs.getString(KEY_SYMBOL, "€") ?: "€",
        eurToTarget = prefs.getString(KEY_FACTOR, "1.0")?.toDoubleOrNull() ?: 1.0,
    )

    private companion object {
        const val KEY_CODE = "currency_code"
        const val KEY_SYMBOL = "currency_symbol"
        const val KEY_FACTOR = "currency_factor"
    }
}

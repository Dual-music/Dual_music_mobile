package com.dualmusic.core.ui.currency

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Devise d'affichage choisie par l'utilisateur : symbole + facteur de conversion
 * appliqué à une valeur exprimée **en euros** (les montants du backend font foi en euros).
 *
 * @property code code ISO (ex. `EUR`, `USD`, `XOF`).
 * @property symbol symbole affiché (ex. `€`, `$`).
 * @property eurToTarget multiplicateur euro → devise cible (1.0 pour l'euro).
 */
data class DisplayCurrency(
    val code: String = "EUR",
    val symbol: String = "€",
    val eurToTarget: Double = 1.0,
) {
    /** Formate une valeur (exprimée en euros) dans la devise choisie. */
    fun format(eurValue: Double): String = "%.2f %s".format(eurValue * eurToTarget, symbol)
}

/**
 * Devise d'affichage courante, fournie au sommet de l'app via `CompositionLocalProvider`
 * (comme [com.dualmusic.core.ui.i18n.LocalStrings]). Défaut : euro.
 */
val LocalCurrency = staticCompositionLocalOf { DisplayCurrency() }

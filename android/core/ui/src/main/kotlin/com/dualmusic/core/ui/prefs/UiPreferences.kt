package com.dualmusic.core.ui.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Préférences visuelles de l'utilisateur — parité web (`useUiPreferences.ts`).
 *
 * @property topDonorMode carte du top donateur : `full` | `reduced` | `off`.
 * @property topDonorAnimation animation : `default` (carte discrète) | `traversing` (bandeau).
 * @property reduceAnimations limite les effets visuels.
 * @property timezone fuseau horaire IANA ; `GMT` = UTC (défaut). Appliqué à l'affichage des dates.
 */
data class UiPrefs(
    val topDonorMode: String = "full",
    val topDonorAnimation: String = "traversing",
    val reduceAnimations: Boolean = false,
    val timezone: String = "GMT",
)

/**
 * Store global des préférences visuelles (source unique). Chargé depuis le backend au démarrage,
 * mis à jour à chaque changement. Lu par les écrans (Compose) et par [com.dualmusic.core.ui.datetime]
 * pour formater les dates dans le fuseau choisi partout dans l'app.
 */
object UiPreferencesStore {
    private val _state = MutableStateFlow(UiPrefs())
    val state: StateFlow<UiPrefs> = _state.asStateFlow()

    /** Remplace l'état complet (après chargement backend ou mise à jour). */
    fun set(prefs: UiPrefs) {
        _state.value = prefs
    }

    /** Snapshot non-Compose (ex. helpers de formatage de date). */
    fun current(): UiPrefs = _state.value
}

package com.dualmusic.domain.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Taux de change (table pivot USD) — `GET /settings/exchange-rates`.
 *
 * @property currencyCode code ISO de la devise.
 * @property name libellé lisible.
 * @property symbol symbole d'affichage.
 * @property ratePerUsd valeur d'1 USD dans cette devise.
 */
@Serializable
data class ExchangeRate(
    @SerialName("currency_code") val currencyCode: String,
    val name: String? = null,
    val symbol: String? = null,
    @SerialName("rate_per_usd") val ratePerUsd: Double = 1.0,
)

/**
 * Préférences visuelles de l'utilisateur — `GET/PUT /users/me/ui-preferences`.
 * Réponse en snake_case ; le PUT attend du camelCase (voir [UiPreferencesEndpoints]).
 */
@Serializable
data class UiPreferencesDto(
    @SerialName("top_donor_mode") val topDonorMode: String? = null,
    @SerialName("top_donor_animation") val topDonorAnimation: String? = null,
    @SerialName("reduce_animations") val reduceAnimations: Boolean? = null,
    val timezone: String? = null,
)

/** Chemins REST des réglages publics (source unique, partagée). */
object SettingsEndpoints {
    /** Table publique des taux de change (pivot USD). */
    const val EXCHANGE_RATES = "/settings/exchange-rates"

    /** Préférences visuelles de l'utilisateur (auth). */
    const val UI_PREFERENCES = "/users/me/ui-preferences"
}

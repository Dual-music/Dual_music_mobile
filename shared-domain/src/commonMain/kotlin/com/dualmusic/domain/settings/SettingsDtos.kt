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

/** Chemins REST des réglages publics (source unique, partagée). */
object SettingsEndpoints {
    /** Table publique des taux de change (pivot USD). */
    const val EXCHANGE_RATES = "/settings/exchange-rates"
}

package com.dualmusic.feature.auth

/**
 * Pays sélectionnable à l'inscription.
 *
 * @property code code ISO-2 (envoyé au backend en `countryCode`, ex. `CI`).
 * @property name nom affiché.
 * @property dial indicatif téléphonique (envoyé en `phoneCountryCode`, ex. `+225`).
 */
data class Country(val code: String, val name: String, val dial: String)

/**
 * Liste (curatée) des pays pour l'inscription — marchés cibles de Dual Music (Afrique
 * francophone + Europe + quelques grands marchés). Défaut : France.
 */
object Countries {
    val ALL: List<Country> = listOf(
        Country("CI", "Côte d'Ivoire", "+225"),
        Country("SN", "Sénégal", "+221"),
        Country("CM", "Cameroun", "+237"),
        Country("ML", "Mali", "+223"),
        Country("BF", "Burkina Faso", "+226"),
        Country("BJ", "Bénin", "+229"),
        Country("TG", "Togo", "+228"),
        Country("NE", "Niger", "+227"),
        Country("GN", "Guinée", "+224"),
        Country("CG", "Congo", "+242"),
        Country("CD", "RD Congo", "+243"),
        Country("GA", "Gabon", "+241"),
        Country("TD", "Tchad", "+235"),
        Country("MG", "Madagascar", "+261"),
        Country("CF", "Centrafrique", "+236"),
        Country("MR", "Mauritanie", "+222"),
        Country("MA", "Maroc", "+212"),
        Country("DZ", "Algérie", "+213"),
        Country("TN", "Tunisie", "+216"),
        Country("NG", "Nigeria", "+234"),
        Country("GH", "Ghana", "+233"),
        Country("FR", "France", "+33"),
        Country("BE", "Belgique", "+32"),
        Country("CH", "Suisse", "+41"),
        Country("LU", "Luxembourg", "+352"),
        Country("CA", "Canada", "+1"),
        Country("US", "États-Unis", "+1"),
        Country("GB", "Royaume-Uni", "+44"),
        Country("DE", "Allemagne", "+49"),
        Country("ES", "Espagne", "+34"),
        Country("IT", "Italie", "+39"),
        Country("PT", "Portugal", "+351"),
    )

    /** Pays par défaut (France). */
    val DEFAULT: Country = ALL.first { it.code == "FR" }
}

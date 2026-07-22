package com.dualmusic.domain.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Recharge de crédits par **Mobile Money** (CinetPay) — flux « hosted payment URL ».
 *
 * 1. [CinetpayInitRequest] → `POST /payments/cinetpay/init` (Bearer + `Idempotency-Key`)
 *    → [CinetpayInitResponse] (URL de paiement hébergée).
 * 2. L'app ouvre [CinetpayInitResponse.paymentUrl] (navigateur/Custom Tab).
 * 3. Le webhook serveur crédite le compte automatiquement (idempotent) — aucune action app.
 *
 * ⚠️ Requête en camelCase ; réponses `cinetpay/init` en camelCase, catalogue pays en snake_case.
 */

/** Corps de `POST /payments/cinetpay/init`. */
@Serializable
data class CinetpayInitRequest(
    /** Montant en crédits (entier ≥ 1). */
    val amount: Int,
    /** Code pays ISO-2 (ex. `CI`, `CM`). */
    val countryCode: String,
    val phone: String? = null,
)

/** Réponse de `POST /payments/cinetpay/init`. */
@Serializable
data class CinetpayInitResponse(
    val paymentUrl: String,
    val merchantTransactionId: String? = null,
    val credits: Int = 0,
)

/** Opérateur Mobile Money d'un pays. */
@Serializable
data class CinetpayOperator(val code: String, val label: String? = null)

/** Pays actif pour la recharge (`GET /payments/cinetpay/countries`). */
@Serializable
data class CinetpayCountry(
    @SerialName("country_code") val countryCode: String,
    @SerialName("country_name") val countryName: String? = null,
    val currency: String? = null,
    @SerialName("phone_prefix") val phonePrefix: String? = null,
    val operators: List<CinetpayOperator> = emptyList(),
)

/** Corps de `POST /payments/stripe/subscription` — achat d'abonnement (carte). */
@Serializable
data class StripeSubscriptionRequest(val plan: String)

/** Réponse d'un checkout Stripe (`{ url }`). */
@Serializable
data class StripeCheckoutResponse(val url: String)

/** Chemins REST des paiements. */
object PaymentEndpoints {
    const val CINETPAY_INIT = "/payments/cinetpay/init"
    const val CINETPAY_COUNTRIES = "/payments/cinetpay/countries"
    const val STRIPE_SUBSCRIPTION = "/payments/stripe/subscription"
    const val HISTORY = "/payments/history"
}

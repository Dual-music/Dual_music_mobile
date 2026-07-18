package com.dualmusic.domain.withdrawal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Méthode de retrait enregistrée (`user_payout_methods`).
 *
 * ⚠️ Valeur `method` canonique côté backend : `mobile_money | bank | paypal`
 * (ne PAS utiliser `bank_transfer`).
 */
@Serializable
data class PayoutMethodData(
    val id: String,
    val method: String,
    val label: String? = null,
    @SerialName("account_holder") val accountHolder: String? = null,
    @SerialName("bank_name") val bankName: String? = null,
    val iban: String? = null,
    @SerialName("paypal_email") val paypalEmail: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("mobile_operator") val mobileOperator: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
) {
    /** Sous-titre lisible selon le type de méthode. */
    val subtitle: String
        get() = when (method) {
            "mobile_money" -> "${mobileOperator ?: ""} • ${phoneNumber ?: ""}".trim(' ', '•', ' ')
            "bank" -> "${bankName ?: ""} • ${iban ?: ""}".trim(' ', '•', ' ')
            "paypal" -> paypalEmail ?: ""
            else -> ""
        }
}

/** Réponse de `GET /withdrawals/pin` — l'utilisateur a-t-il déjà un PIN de retrait. */
@Serializable
data class PinStatus(val hasPin: Boolean = false)

/** Réponse de `POST /withdrawals/pin/verify`. */
@Serializable
data class PinVerifyResult(val valid: Boolean = false)

/** Corps de `POST /withdrawals/pin` — création/remplacement du PIN (6 chiffres). */
@Serializable
data class SetPinRequest(val newPin: String, val currentPin: String? = null)

/** Corps de `POST /withdrawals/net` — aperçu du net après frais. */
@Serializable
data class NetRequest(val amount: Double)

/**
 * Corps de `POST /withdrawals` — demande de retrait.
 * Le [pin] (6 chiffres) est re-vérifié côté serveur avec verrouillage après échecs.
 */
@Serializable
data class CreateWithdrawalRequest(
    val amount: Double,
    val pin: String,
    val payoutMethodId: String? = null,
    val provider: String? = null,
)

/** Chemins REST des retraits (source unique, partagée). */
object WithdrawalEndpoints {
    const val PIN = "/withdrawals/pin"
    const val PIN_VERIFY = "/withdrawals/pin/verify"
    const val NET = "/withdrawals/net"
    const val METHODS = "/withdrawals/methods"
    const val MINE = "/withdrawals/me"
    const val CREATE = "/withdrawals"
}

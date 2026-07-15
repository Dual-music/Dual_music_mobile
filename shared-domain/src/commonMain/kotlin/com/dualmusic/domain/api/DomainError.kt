package com.dualmusic.domain.api

/**
 * Erreur métier plateforme-agnostique.
 *
 * `core-network` (iOS/Android) traduit tout échec — HTTP non-2xx, enveloppe `error`,
 * réseau, décodage — en une `DomainError`. Les use-cases et l'UI branchent dessus sans
 * connaître les détails de transport.
 *
 * @property httpStatus code HTTP (0 pour une erreur locale : réseau, décodage, annulation).
 * @property code code métier stable (voir [ErrorCode] pour les valeurs connues).
 * @property message message lisible.
 */
data class DomainError(
    val httpStatus: Int,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : Throwable(message) {

    /** Vrai si l'erreur peut se résoudre en réessayant (réseau, 5xx, timeout). */
    val isRetriable: Boolean
        get() = httpStatus == 0 || httpStatus in 500..599

    /** Vrai si le solde de crédits est insuffisant (à afficher comme invitation à recharger). */
    val isInsufficientBalance: Boolean
        get() = code == ErrorCode.WALLET_INSUFFICIENT

    /** Vrai si la session doit être renouvelée / l'utilisateur reconnecté. */
    val isAuthExpired: Boolean
        get() = httpStatus == 401
}

/**
 * Catalogue des codes d'erreur connus du backend (non exhaustif — voir OpenAPI).
 * Centralisé ici pour un branchement UI cohérent iOS/Android.
 */
object ErrorCode {
    const val UNKNOWN = "UNKNOWN"
    const val NETWORK = "NETWORK_ERROR"
    const val VALIDATION = "VALIDATION_ERROR"
    const val NOT_FOUND = "NOT_FOUND"
    const val FORBIDDEN = "FORBIDDEN"
    const val CONFLICT = "CONFLICT"

    // Portefeuille / paiements
    const val WALLET_INSUFFICIENT = "WALLET_INSUFFICIENT"
    const val AMOUNT_INVALID = "AMOUNT_INVALID"
    const val ALREADY_TICKETED = "ALREADY_TICKETED"
    const val TICKETS_SOLD_OUT = "TICKETS_SOLD_OUT"
    const val PAYMENT_PROVIDER_ERROR = "PAYMENT_PROVIDER_ERROR"

    // PIN de retrait
    const val PIN_WRONG = "PIN_WRONG"
    const val PIN_NOT_SET = "PIN_NOT_SET"
    const val PIN_LOCKED = "PIN_LOCKED"
}

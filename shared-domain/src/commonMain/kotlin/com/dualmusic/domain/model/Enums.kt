package com.dualmusic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rôles utilisateur (source de vérité : table `user_roles` du backend).
 * Un utilisateur peut cumuler plusieurs rôles.
 */
@Serializable
enum class UserRole {
    @SerialName("fan") FAN,
    @SerialName("artist") ARTIST,
    @SerialName("manager") MANAGER,
    @SerialName("moderator") MODERATOR,
    @SerialName("admin") ADMIN,
}

/** Cycle de vie d'un duel/live/concert/compétition (valeurs backend). */
@Serializable
enum class EventStatus {
    @SerialName("upcoming") UPCOMING,
    /** Concert admin planifié (équivalent d'`upcoming` pour la billetterie). */
    @SerialName("scheduled") SCHEDULED,
    @SerialName("live") LIVE,
    @SerialName("ended") ENDED,
    @SerialName("cancelled") CANCELLED,
    /** Statut spécifique aux concerts en attente de validation admin. */
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
}

/** Contexte d'un cadeau / d'un flux d'engagement (aligne les rooms temps réel). */
@Serializable
enum class EventContext {
    @SerialName("duel") DUEL,
    @SerialName("live") LIVE,
    @SerialName("concert") CONCERT,
    @SerialName("competition") COMPETITION,
}

/**
 * Méthode de retrait (payout). Valeurs backend : `mobile_money | bank | paypal`.
 * ⚠️ Attention : côté web historique on trouvait `bank_transfer` — la valeur canonique
 * backend est `bank`. Ne pas réintroduire `bank_transfer`.
 */
@Serializable
enum class PayoutMethod {
    @SerialName("mobile_money") MOBILE_MONEY,
    @SerialName("bank") BANK,
    @SerialName("paypal") PAYPAL,
}

/**
 * Fournisseur de paiement pour la RECHARGE de crédits.
 * - Sur mobile, l'achat de crédits passe OBLIGATOIREMENT par [APPLE_IAP] (iOS) /
 *   [GOOGLE_PLAY] (Android) — conformité stores.
 * - Les fournisseurs [CINETPAY]/[MONEROO]/[STRIPE] restent pour le web.
 */
@Serializable
enum class RechargeProvider {
    @SerialName("apple_iap") APPLE_IAP,
    @SerialName("google_play") GOOGLE_PLAY,
    @SerialName("cinetpay") CINETPAY,
    @SerialName("moneroo") MONEROO,
    @SerialName("stripe") STRIPE,
}

/**
 * Statut d'une demande de retrait.
 *
 * Cycle de vie : [PENDING] → [APPROVED] → [PROCESSING] → [COMPLETED] | [FAILED],
 * ou [REJECTED] si un administrateur refuse la demande (crédits recrédités).
 *
 * - [PROCESSING] : l'ordre de transfert est parti chez l'opérateur Mobile Money.
 *   Les crédits sont déjà débités mais l'argent n'est pas encore versé — cet état
 *   empêche qu'un même retrait soit soumis deux fois.
 * - [FAILED] : le transfert a définitivement échoué ; les crédits ont été rendus.
 */
@Serializable
enum class WithdrawalStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("rejected") REJECTED,
    @SerialName("failed") FAILED,
}

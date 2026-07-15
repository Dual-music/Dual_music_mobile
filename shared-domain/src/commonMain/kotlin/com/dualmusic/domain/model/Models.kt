package com.dualmusic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Modèles de domaine partagés.
 *
 * Représentent les entités que l'app mobile consomme le plus. Les champs suivent le
 * nommage `snake_case` du backend (mappé via @SerialName) pour un décodage direct de
 * l'enveloppe `data`.
 *
 * Extension : le backend compte ~81 tables. On modélise ici le sous-ensemble cœur ; on
 * ajoute les autres au fil des features en suivant EXACTEMENT ce pattern (data class
 * @Serializable + @SerialName sur chaque champ wire). La spec `openapi/openapi.json`
 * fait foi sur les champs exacts.
 */

/** Profil d'affichage minimal d'un utilisateur (repris partout : listes, chat, cadeaux). */
@Serializable
data class DisplayProfile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /** Nom de scène (artistes). Peut être absent → retomber sur [fullName]. */
    @SerialName("stage_name") val stageName: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
) {
    /** Nom à afficher : nom de scène si présent, sinon nom complet, sinon un repli court. */
    val displayName: String
        get() = stageName?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: "Utilisateur"
}

/** Solde du portefeuille de crédits. */
@Serializable
data class Wallet(
    @SerialName("user_id") val userId: String,
    /** Solde en crédits (unité interne ; 1 crédit = 0,50 € — invariant métier). */
    val balance: Double,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Cadeau virtuel du catalogue. */
@Serializable
data class VirtualGift(
    val id: String,
    val name: String,
    val emoji: String? = null,
    /** Prix en crédits. */
    val price: Double,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

/** Duel 1v1 entre deux artistes, avec votes payants et cadeaux. */
@Serializable
data class Duel(
    val id: String,
    @SerialName("artist1_id") val artist1Id: String,
    @SerialName("artist2_id") val artist2Id: String,
    @SerialName("manager_id") val managerId: String? = null,
    val status: EventStatus = EventStatus.UPCOMING,
    @SerialName("winner_id") val winnerId: String? = null,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("ticket_price") val ticketPrice: Double = 0.0,
    /** État du minuteur en cours (persisté pour les arrivants tardifs). */
    @SerialName("current_timer_ends_at") val currentTimerEndsAt: String? = null,
    @SerialName("current_timer_target_id") val currentTimerTargetId: String? = null,
    @SerialName("room_id") val roomId: String? = null,
    /** Profils hydratés côté serveur (peuvent être absents selon l'endpoint). */
    val artist1: DisplayProfile? = null,
    val artist2: DisplayProfile? = null,
)

/** Total de votes (crédits) par artiste pour un duel. */
@Serializable
data class DuelVoteTotal(
    @SerialName("duel_id") val duelId: String? = null,
    @SerialName("artist_id") val artistId: String,
    val total: Double,
)

/** Concert d'artiste (billetterie + dédicaces). */
@Serializable
data class Concert(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val title: String,
    val status: EventStatus = EventStatus.UPCOMING,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("ticket_price") val ticketPrice: Double = 0.0,
    @SerialName("allows_dedications") val allowsDedications: Boolean = false,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
)

/** Compétition (candidats, votes, cadeaux, classement). */
@Serializable
data class Competition(
    val id: String,
    val title: String,
    val status: String,
    @SerialName("manager_id") val managerId: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("reward_amount") val rewardAmount: Double = 0.0,
    @SerialName("cover_url") val coverUrl: String? = null,
)

/** Demande de retrait de crédits par un artiste/manager. */
@Serializable
data class WithdrawalRequest(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val status: WithdrawalStatus = WithdrawalStatus.PENDING,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Aperçu net d'un retrait (renvoyé par `POST /withdrawals/net`). Montants faisant foi. */
@Serializable
data class WithdrawalNet(
    @SerialName("fee_pct") val feePct: Double,
    val fee: Double,
    val net: Double,
)

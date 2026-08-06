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
    val phone: String? = null,
    @SerialName("phone_country_code") val phoneCountryCode: String? = null,
    /** Biographie du compte (présente sur `/auth/me`, absente des profils d'affichage courts). */
    val bio: String? = null,
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
    /** L'organisateur autorise-t-il la diffusion de pubs sponsor sur ce duel (parité web). */
    @SerialName("allows_sponsor_ads") val allowsSponsorAds: Boolean = true,
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

/**
 * Concert (billetterie + dédicaces). Modèle unifié pour les deux catalogues du backend —
 * `/artist-concerts` (concerts d'artistes : `cover_image_url`, `artist` hydraté) ET
 * `/concerts` (concerts admin : `image_url`, `artist_name`, `location`, `scheduled_time`,
 * statut `scheduled`). Tous les champs additionnels sont optionnels → un seul DTO désérialise
 * les deux.
 */
@Serializable
data class Concert(
    val id: String,
    @SerialName("artist_id") val artistId: String = "",
    val title: String,
    val description: String? = null,
    val status: EventStatus = EventStatus.UPCOMING,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("ticket_price") val ticketPrice: Double = 0.0,
    @SerialName("allows_dedications") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val allowsDedications: Boolean = false,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val location: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    val artist: DisplayProfile? = null,
    @SerialName("is_artist_concert") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val isArtistConcert: Boolean = false,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("tickets_sold") val ticketsSold: Int = 0,
    val revenue: Double = 0.0,
    @SerialName("max_tickets") val maxTickets: Int? = null,
    @SerialName("approval_status") val approvalStatus: String? = null,
) {
    /** Image de couverture effective (`cover_image_url` artiste OU `image_url` admin). */
    val cover: String? get() = coverImageUrl ?: imageUrl
}

/** Compétition (candidats, votes, cadeaux, classement). */
@Serializable
data class Competition(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String,
    @SerialName("manager_id") val managerId: String? = null,
    val mode: String? = null,
    @SerialName("max_candidates") val maxCandidates: Int? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("reward_amount") val rewardAmount: Double = 0.0,
    @SerialName("reward_description") val rewardDescription: String? = null,
    @SerialName("viewer_ticket_price") val viewerTicketPrice: Double = 0.0,
    @SerialName("is_public_paid") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val isPublicPaid: Boolean = false,
    @SerialName("entry_fee_required") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val entryFeeRequired: Boolean = false,
    @SerialName("entry_fee_amount") val entryFeeAmount: Double = 0.0,
    @SerialName("application_opens_at") val applicationOpensAt: String? = null,
    @SerialName("application_deadline") val applicationDeadline: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val country: String? = null,
    val city: String? = null,
    val commune: String? = null,
    val district: String? = null,
    @SerialName("venue_name") val venueName: String? = null,
    @SerialName("venue_address") val venueAddress: String? = null,
    @SerialName("venue_contact") val venueContact: String? = null,
    @SerialName("accepts_sponsors") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val acceptsSponsors: Boolean = true,
    @SerialName("sponsor_submission_deadline") val sponsorSubmissionDeadline: String? = null,
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

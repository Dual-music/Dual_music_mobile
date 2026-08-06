package com.dualmusic.domain.creator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Demande/défi de duel (`duel_requests`).
 *
 * Le caller peut en être l'émetteur ([requesterId]) ou le destinataire ([opponentId]).
 * Seul le destinataire peut accepter/refuser (le backend l'impose).
 */
@Serializable
data class DuelRequestItem(
    val id: String,
    @SerialName("requester_id") val requesterId: String? = null,
    @SerialName("opponent_id") val opponentId: String? = null,
    /** `pending` | `accepted` | `declined`. */
    val status: String = "pending",
    @SerialName("proposed_date") val proposedDate: String? = null,
    val message: String? = null,
)

/** Corps de `POST /duels/requests/:id/respond` — accepter/refuser un défi. */
@Serializable
data class RespondDuelRequest(val accept: Boolean)

/**
 * Corps de `PATCH /duels/requests/:id` — changer la date proposée d'un défi encore en attente.
 * Réservé à l'émetteur (ou admin). Le backend renotifie l'autre partie (notif + email).
 */
@Serializable
data class ChangeDuelDateRequest(val proposedDate: String?)

/** Corps de `POST /duels/requests` — envoyer un défi à un autre artiste. */
@Serializable
data class CreateDuelRequest(
    val opponentId: String,
    val proposedDate: String? = null,
    val message: String? = null,
)

/**
 * Corps de `POST /artist-concerts` — création d'un concert d'artiste.
 *
 * ⚠️ camelCase STRICT (Joi `stripUnknown`). Le concert est créé en `approval_status=pending`
 * (invisible jusqu'à validation admin). [coverImageUrl] = URL publique issue de l'upload
 * (catégorie `image`, ≤ 5 Mo). [scheduledDate] au format ISO 8601.
 */
@Serializable
data class CreateArtistConcert(
    val title: String,
    val description: String? = null,
    val scheduledDate: String,
    val ticketPrice: Double = 0.0,
    val maxTickets: Int? = null,
    val coverImageUrl: String? = null,
    val allowsDedications: Boolean = true,
    val allowsSponsorAds: Boolean = true,
)

/** Chemins REST des outils créateur (source unique, partagée). */
object CreatorEndpoints {
    /** Défis de duel du caller (émis + reçus). */
    const val DUEL_REQUESTS_MINE = "/duels/requests/mine"
    /** Envoi d'un défi. */
    const val DUEL_REQUEST_CREATE = "/duels/requests"
    /** Répondre à un défi. */
    fun duelRespond(id: String) = "/duels/requests/$id/respond"
    /** Changer la date proposée d'un défi (`PATCH`, émetteur/admin). */
    fun duelChangeDate(id: String) = "/duels/requests/$id"
    /** Concerts de l'artiste caller. */
    const val MY_CONCERTS = "/artist-concerts/me"

    /** Mise à jour du profil public artiste du caller (`PATCH`). Voir [ArtistProfile]. */
    const val ARTIST_ME = "/artists/me"
    /** Profil public manager du caller (`GET` lecture, `PATCH` mise à jour). Voir [ManagerProfile]. */
    const val MANAGER_ME = "/managers/me"
    /** Lecture d'un profil public (compte + profil artiste) — sert à relire le sien. */
    fun publicProfile(id: String) = "/users/$id"
}

package com.dualmusic.domain.competition

import com.dualmusic.domain.model.DisplayProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Candidat d'une compétition (`competition_candidates`).
 *
 * Les tallies ([totalVotes], [totalGiftsCredits]) sont maintenus par les procédures
 * atomiques du backend et alimentent le classement final.
 */
@Serializable
data class CompetitionCandidate(
    val id: String,
    @SerialName("competition_id") val competitionId: String,
    @SerialName("artist_id") val artistId: String,
    /** `pending` | `approved` | `rejected`. */
    val status: String = "pending",
    @SerialName("total_votes") val totalVotes: Double = 0.0,
    @SerialName("total_gifts_credits") val totalGiftsCredits: Double = 0.0,
    /** Profil d'affichage de l'artiste (hydraté serveur quand disponible). */
    val artist: DisplayProfile? = null,
) {
    /** Score d'engagement affiché (votes + cadeaux). */
    val score: Double get() = totalVotes + totalGiftsCredits
}

/** Corps de `POST /competitions/:id/vote` — vote payant pour un candidat. */
@Serializable
data class CompetitionVoteRequest(
    val candidateId: String,
    val credits: Int,
)

/**
 * Corps de `POST /competitions/:id/gifts` — cadeau à un candidat OU au manager.
 * Exactement un des deux destinataires doit être fourni (contrainte backend `xor`).
 */
@Serializable
data class CompetitionGiftRequest(
    val candidateId: String? = null,
    val recipientUserId: String? = null,
    val giftId: String,
    val credits: Int,
)

/** Chemins REST des compétitions (source unique, partagée). */
object CompetitionEndpoints {
    /** Catalogue public. */
    const val LIST = "/competitions"

    /** Candidatures du caller (artiste). */
    const val CANDIDACIES_MINE = "/competitions/candidacies/mine"

    /** Compétitions dont le caller a déjà acheté le billet. */
    const val TICKETS_MINE = "/competitions/tickets/mine"

    /** Détail d'une compétition. */
    fun detail(id: String) = "/competitions/$id"

    /** Candidats (avec tallies) d'une compétition. */
    fun candidates(id: String) = "/competitions/$id/candidates"

    /** Vote payant pour un candidat (débit atomique). */
    fun vote(id: String) = "/competitions/$id/vote"

    /** Cadeau (candidat ou manager). */
    fun gifts(id: String) = "/competitions/$id/gifts"

    /** Achat du billet spectateur. */
    fun tickets(id: String) = "/competitions/$id/tickets"

    /** Billet du caller pour cette compétition. */
    fun myTicket(id: String) = "/competitions/$id/my-ticket"
}

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
    /** Voix cumulées d'un jury hors ligne, saisies par l'organisateur (voir setJuryVotes). */
    @SerialName("jury_votes") val juryVotes: Int = 0,
    /** Profil d'affichage de l'artiste (hydraté serveur quand disponible). */
    val artist: DisplayProfile? = null,
    /** Présentation libre fournie par l'artiste à la candidature (`POST .../apply`). */
    val pitch: String? = null,
    /** Lien vidéo de démonstration fourni à la candidature. */
    @SerialName("video_demo_url") val videoDemoUrl: String? = null,
    /** Motif du rejet saisi par le manager (présent seulement si `status == "rejected"`). */
    @SerialName("rejection_reason") val rejectionReason: String? = null,
) {
    /** Score d'engagement affiché (votes + cadeaux + voix de jury). */
    val score: Double get() = totalVotes + totalGiftsCredits + juryVotes
}

/** Réponse de `DELETE /competitions/candidates/:id` — retrait d'un candidat (remboursement éventuel). */
@Serializable
data class RemoveCandidateResult(
    val removed: Boolean = false,
    /** Vrai si des frais d'inscription réellement débités ont été remboursés (souvent faux : voir doc endpoint). */
    val refunded: Boolean = false,
)

/** Réponse de `GET /competitions/candidates/pending-count/mine` — badge menu manager. */
@Serializable
data class PendingCandidatesCount(val count: Int = 0)

/** Compétition allégée jointe à une candidature (`GET /competitions/candidacies/mine`). */
@Serializable
data class CompetitionLite(
    val id: String,
    val title: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    val status: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
)

/**
 * Candidature du caller (artiste) enrichie de sa compétition
 * (`GET /competitions/candidacies/mine`) — alimente l'onglet « Mes compétitions ».
 */
@Serializable
data class MyCandidacy(
    val id: String,
    @SerialName("competition_id") val competitionId: String,
    /** `pending` | `approved` | `rejected`. */
    val status: String = "pending",
    @SerialName("total_votes") val totalVotes: Double = 0.0,
    @SerialName("total_gifts_credits") val totalGiftsCredits: Double = 0.0,
    val competition: CompetitionLite? = null,
) {
    /** Score d'engagement (votes + cadeaux). */
    val score: Double get() = totalVotes + totalGiftsCredits
}

/** Corps de `POST /competitions/:id/vote` — vote payant pour un candidat. */
@Serializable
data class CompetitionVoteRequest(
    val candidateId: String,
    val credits: Int,
)

/**
 * Corps de `POST /competitions/:id/apply` — auto-candidature de l'artiste (distinct de l'ajout
 * manuel par le manager, `/candidates/manual`). Champs camelCase envoyés tels quels : le backend
 * (`applyToCompetition`) les remappe lui-même vers `pitch`/`video_demo_url`. Parité web
 * `CompetitionApplyDialog` (`apply(id, { pitch, videoDemoUrl })`).
 */
@Serializable
data class CompetitionApplyRequest(
    val pitch: String? = null,
    val videoDemoUrl: String? = null,
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

    /** Chat de la compétition (sous-ressource `chat.helper` backend). */
    fun messages(id: String) = "/competitions/$id/messages"

    /** Vote payant pour un candidat (débit atomique). */
    fun vote(id: String) = "/competitions/$id/vote"

    /** Auto-candidature de l'artiste (distinct de l'ajout manuel `/candidates/manual`). */
    fun apply(id: String) = "/competitions/$id/apply"

    /** Cadeau (candidat ou manager). */
    fun gifts(id: String) = "/competitions/$id/gifts"

    /** Achat du billet spectateur. */
    fun tickets(id: String) = "/competitions/$id/tickets"

    /** Billet du caller pour cette compétition. */
    fun myTicket(id: String) = "/competitions/$id/my-ticket"

    /** Valide/rejette une candidature (manager). */
    fun reviewCandidate(candidateId: String) = "/competitions/candidates/$candidateId/review"

    /** Retire un candidat (remboursement automatique si des frais avaient réellement été débités). */
    fun removeCandidate(candidateId: String) = "/competitions/candidates/$candidateId"

    /** Compte cumulé des candidats en attente, toutes compétitions du manager (badge menu). */
    const val PENDING_CANDIDATES_MINE = "/competitions/candidates/pending-count/mine"
}

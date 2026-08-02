package com.dualmusic.feature.competition

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.competition.CompetitionCandidate
import com.dualmusic.domain.competition.CompetitionEndpoints
import com.dualmusic.domain.competition.CompetitionGiftRequest
import com.dualmusic.domain.competition.CompetitionVoteRequest
import com.dualmusic.domain.gift.GiftEndpoints
import com.dualmusic.domain.gift.InventoryItem
import com.dualmusic.domain.model.Competition
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès REST aux compétitions.
 *
 * ⚠️ Contrairement au duel (dont le vote passe par `/wallet/vote`), le vote de compétition
 * a son propre endpoint (`POST /competitions/:id/vote`) — mais reste un **débit atomique**
 * exécuté par une procédure stockée côté serveur.
 */
class CompetitionRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Catalogue public des compétitions. */
    suspend fun competitions(limit: Int = 50): List<Competition> =
        api.request(
            Endpoint.get(CompetitionEndpoints.LIST, query = mapOf("limit" to limit.toString())),
            ListSerializer(Competition.serializer()),
        )

    /** Candidatures du caller (artiste), enrichies de leur compétition. */
    suspend fun myCandidacies(): List<com.dualmusic.domain.competition.MyCandidacy> =
        api.request(
            Endpoint.get(CompetitionEndpoints.CANDIDACIES_MINE),
            ListSerializer(com.dualmusic.domain.competition.MyCandidacy.serializer()),
        )

    /** Détail d'une compétition. */
    suspend fun competition(id: String): Competition =
        api.request(Endpoint.get(CompetitionEndpoints.detail(id)), Competition.serializer())

    /** Candidats avec leurs tallies (base du classement). */
    suspend fun candidates(id: String): List<CompetitionCandidate> =
        api.request(
            Endpoint.get(CompetitionEndpoints.candidates(id)),
            ListSerializer(CompetitionCandidate.serializer()),
        )

    /**
     * Vote payant pour un candidat (débit atomique + idempotent).
     * @param credits nombre entier de crédits (contrainte backend).
     */
    suspend fun vote(
        competitionId: String,
        candidateId: String,
        credits: Int,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            CompetitionVoteRequest.serializer(),
            CompetitionVoteRequest(candidateId = candidateId, credits = credits),
        )
        api.request<Unit>(
            Endpoint.post(CompetitionEndpoints.vote(competitionId), body, idempotencyKey = idempotencyKey),
        )
    }

    /**
     * Envoie un cadeau à un candidat OU au manager de la compétition.
     * Exactement un destinataire doit être fourni (contrainte `xor` côté backend).
     */
    suspend fun sendGift(
        competitionId: String,
        request: CompetitionGiftRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(CompetitionGiftRequest.serializer(), request)
        api.request<Unit>(
            Endpoint.post(CompetitionEndpoints.gifts(competitionId), body, idempotencyKey = idempotencyKey),
        )
    }

    /** Inventaire de cadeaux du caller (partagé avec le live/boutique). */
    suspend fun inventory(): List<InventoryItem> =
        api.request(Endpoint.get(GiftEndpoints.INVENTORY), ListSerializer(InventoryItem.serializer()))

    /** Achète le billet spectateur de la compétition. */
    suspend fun buyTicket(
        competitionId: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        api.request<Unit>(
            Endpoint.post(CompetitionEndpoints.tickets(competitionId), idempotencyKey = idempotencyKey),
        )
    }

    // --- Espace MANAGER (organisateur de compétitions) ---

    /** Id du caller (manager) — `manager_id` à la création. */
    suspend fun myUserId(): String? =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.user.UserEndpoints.ME),
                com.dualmusic.domain.auth.MeResponse.serializer(),
            ).user?.id
        }.getOrNull()

    /** Compétitions gérées par ce manager (`GET /competitions/mine`). */
    suspend fun myCompetitions(): List<Competition> =
        api.request(Endpoint.get("/competitions/mine"), ListSerializer(Competition.serializer()))

    /**
     * Crée une compétition (le manager s'assigne organisateur). `POST /competitions`.
     * Champs essentiels ; le backend applique ses valeurs par défaut pour le reste.
     */
    suspend fun createCompetition(
        managerId: String,
        title: String,
        description: String,
        rewardAmount: Double,
        maxCandidates: Int,
        startAt: String?,
        endAt: String?,
    ) {
        val start = startAt?.takeIf { it.isNotBlank() }?.let { """"$it"""" } ?: "null"
        val end = endAt?.takeIf { it.isNotBlank() }?.let { """"$it"""" } ?: "null"
        val body = json.encodeToString(
            CreateCompetitionBody.serializer(),
            CreateCompetitionBody(
                managerId = managerId,
                title = title,
                description = description,
                rewardAmount = rewardAmount,
                maxCandidates = maxCandidates,
                startAt = startAt?.takeIf { it.isNotBlank() },
                endAt = endAt?.takeIf { it.isNotBlank() },
            ),
        )
        api.request<Unit>(Endpoint.post("/competitions", body))
    }

    // --- Contrôles MANAGER en direct ---

    /** Valide/rejette une candidature (`POST /competitions/candidates/:id/review`). */
    suspend fun reviewCandidate(candidateId: String, approve: Boolean) {
        api.request<Unit>(Endpoint.post("/competitions/candidates/$candidateId/review", """{"approve":$approve}"""))
    }

    /** Publie la compétition (ouvre les votes). `POST /competitions/:id/publish`. */
    suspend fun publish(id: String) {
        api.request<Unit>(Endpoint.post("/competitions/$id/publish", "{}"))
    }

    /** Désigne le performeur courant (ou `null` pour arrêter). `POST /competitions/:id/performer`. */
    suspend fun setPerformer(id: String, candidateId: String?, durationSec: Int) {
        val cid = candidateId?.let { """"$it"""" } ?: "null"
        api.request<Unit>(Endpoint.post("/competitions/$id/performer", """{"candidateId":$cid,"durationSec":$durationSec}"""))
    }

    /** Finalise le classement (clôture). `POST /competitions/:id/finalize`. */
    suspend fun finalize(id: String) {
        api.request<Unit>(Endpoint.post("/competitions/$id/finalize", "{}"))
    }
}

/** Corps JSON de création d'une compétition (`POST /competitions`). */
@kotlinx.serialization.Serializable
private data class CreateCompetitionBody(
    val managerId: String,
    val title: String,
    val description: String,
    val rewardAmount: Double,
    val maxCandidates: Int,
    val mode: String = "online",
    val eligibilityScope: String = "world",
    val startAt: String? = null,
    val endAt: String? = null,
    val status: String = "open",
)

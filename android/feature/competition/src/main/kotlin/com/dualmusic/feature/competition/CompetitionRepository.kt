package com.dualmusic.feature.competition

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.competition.CompetitionCandidate
import com.dualmusic.domain.competition.CompetitionEndpoints
import com.dualmusic.domain.competition.CompetitionGiftRequest
import com.dualmusic.domain.competition.CompetitionVoteRequest
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

    /** Achète le billet spectateur de la compétition. */
    suspend fun buyTicket(
        competitionId: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        api.request<Unit>(
            Endpoint.post(CompetitionEndpoints.tickets(competitionId), idempotencyKey = idempotencyKey),
        )
    }
}

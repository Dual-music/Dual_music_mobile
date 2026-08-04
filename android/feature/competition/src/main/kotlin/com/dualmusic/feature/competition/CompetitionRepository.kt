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

    /** Catalogue public des compétitions (filtre `status` optionnel : `published` / `live`). */
    suspend fun competitions(limit: Int = 50, status: String? = null): List<Competition> {
        val query = if (status != null) mapOf("limit" to limit.toString(), "status" to status)
        else mapOf("limit" to limit.toString())
        return api.request(Endpoint.get(CompetitionEndpoints.LIST, query = query), ListSerializer(Competition.serializer()))
    }

    /** Replays publics de compétitions (`GET /replays?sourceType=competition&isPublic=true`). */
    suspend fun competitionReplays(): List<com.dualmusic.domain.replay.ReplayVideo> =
        runCatching {
            api.request(
                Endpoint.get(
                    com.dualmusic.domain.replay.ReplayEndpoints.LIST,
                    query = mapOf("sourceType" to "competition", "isPublic" to "true", "limit" to "100"),
                ),
                ListSerializer(com.dualmusic.domain.replay.ReplayVideo.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Taux €/crédit dérivé du solde (aperçu fiat « ≈ »). */
    suspend fun perCreditEur(): Double = runCatching {
        val b = api.request(
            Endpoint.get(com.dualmusic.domain.wallet.WalletEndpoints.BALANCE),
            com.dualmusic.domain.wallet.WalletBalance.serializer(),
        )
        if (b.balance > 0) b.eurValue / b.balance else 0.0
    }.getOrDefault(0.0)

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
     * Parité stricte avec le formulaire web : tous les champs sont transmis ; les `null`
     * sont omis (explicitNulls=false) → le backend applique ses défauts. Le backend force
     * `status = 'draft'` à la création.
     */
    suspend fun createCompetition(body: CreateCompetitionBody) {
        val payload = json.encodeToString(CreateCompetitionBody.serializer(), body)
        api.request<Unit>(Endpoint.post("/competitions", payload))
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

/**
 * Corps JSON de création d'une compétition (`POST /competitions`) — parité stricte avec le
 * formulaire web (`CompetitionForm`). Les champs `null` sont omis à la sérialisation.
 */
@kotlinx.serialization.Serializable
data class CreateCompetitionBody(
    val managerId: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val mode: String = "online",
    val maxCandidates: Int = 10,
    val rewardDescription: String? = null,
    val rewardAmount: Double = 0.0,
    val entryFeeRequired: Boolean = false,
    val entryFeeAmount: Double = 0.0,
    /** Le manager accepte-t-il les sponsors ? (défaut oui). */
    val acceptsSponsors: Boolean = true,
    val eligibilityScope: String = "country",
    val eligibleCountries: List<String> = emptyList(),
    // Présentiel (onsite) — omis en ligne.
    val country: String? = null,
    val city: String? = null,
    val commune: String? = null,
    val district: String? = null,
    val venueName: String? = null,
    val venueAddress: String? = null,
    val venueContact: String? = null,
    // Dates (ISO). applicationOpensAt optionnel.
    val applicationOpensAt: String? = null,
    val applicationDeadline: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val status: String = "open",
)

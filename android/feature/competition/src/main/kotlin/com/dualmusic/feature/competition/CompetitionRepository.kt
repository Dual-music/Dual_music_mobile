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
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.moderation.AppointModeratorBody
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.moderation.ModerationEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Entrée du classement des donateurs (`GET /leaderboards/gifts?contextType=competition`). */
@Serializable
data class CompetitionDonorEntry(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val total: Double = 0.0,
    val score: Double = 0.0,
    val user: DisplayProfile? = null,
) {
    val displayName: String
        get() = user?.displayName ?: stageName?.takeIf { it.isNotBlank() } ?: fullName?.takeIf { it.isNotBlank() } ?: "Donateur"
    val value: Int get() = (if (total > 0.0) total else score).toInt()
}

/** Réponse de `GET /competitions/:id/my-ticket` — le caller a-t-il un billet ? */
@Serializable
data class CompetitionTicketInfo(val hasTicket: Boolean = false, val count: Int = 0)

/** Message de chat d'une compétition (sous-ressource `chat.helper`, auteur hydraté). */
@Serializable
data class CompetitionChatMessage(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    // Le backend utilise la clé `message` (colonne DB) et hydrate l'auteur sous `author`.
    @SerialName("message") val content: String,
    @SerialName("author") val user: DisplayProfile? = null,
    // Réponse à un message parent (chat en fil) — présent quand c'est une réponse.
    @SerialName("parent_id") val parentId: String? = null,
) {
    val authorName: String get() = user?.displayName ?: com.dualmusic.core.ui.i18n.appStrings.fan
}

/** Ligne de bannissement (`GET /moderation/stream-bans`) — on n'extrait que l'utilisateur banni. */
@Serializable
data class CompetitionStreamBanRow(
    @SerialName("banned_user_id") val bannedUserId: String? = null,
)

/** Charge utile `competition:banned` — un spectateur ou candidat vient d'être banni. */
@Serializable
data class CompetitionStreamBannedPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("competition_id") val competitionId: String? = null,
)

/** Réponse `GET /lives/:id/likes`. La compétition réutilise l'endpoint générique des likes. */
@Serializable
private data class CompetitionLikesResponse(val likes: Int = 0)

/** Réglage public du prix du vote (`GET /settings/public/vote_config`). */
@Serializable
private data class CompetitionVoteConfigSetting(val value: CompetitionVoteConfigValue? = null)

@Serializable
private data class CompetitionVoteConfigValue(@SerialName("price_per_vote") val pricePerVote: Double = 1.0)

/** Réglage public de l'ajout manuel de candidat (`GET /settings/public/manual_candidates_config`). */
@Serializable
private data class ManualCandidatesConfigSetting(val value: ManualCandidatesConfigValue? = null)

@Serializable
private data class ManualCandidatesConfigValue(val enabled: Boolean? = null)

/** Corps de `POST /competitions/:id/candidates/manual`. */
@Serializable
private data class AddCandidateManuallyBody(val artistId: String, val pitch: String? = null)

/**
 * Accès REST aux compétitions.
 *
 * ⚠️ Contrairement au duel (dont le vote passe par `/wallet/vote`), le vote de compétition
 * a son propre endpoint (`POST /competitions/:id/vote`) — mais reste un **débit atomique**
 * exécuté par une procédure stockée côté serveur.
 */
class CompetitionRepository(private val api: ApiClient) {

    /**
     * Signale cette compétition à la modération (parité web `LiveReportButton`).
     * `POST /moderation/reports/competition` — table dédiée (`competition_reports`), distincte de
     * `live_reports` (live/concert/duel) : envoyer ça vers `/moderation/reports/live` enregistrait
     * le signalement comme un "live" générique, invisible/mal lié pour l'admin.
     */
    suspend fun reportLive(liveId: String, reason: String) {
        api.request<Unit>(Endpoint.post("moderation/reports/competition", """{"competitionId":"$liveId","reason":"$reason"}"""))
    }

    /** Historique du chat (50 derniers messages). */
    suspend fun chatHistory(id: String): List<CompetitionChatMessage> =
        api.request(
            Endpoint.get(CompetitionEndpoints.messages(id), query = mapOf("limit" to "50")),
            ListSerializer(CompetitionChatMessage.serializer()),
        )

    /** Poste un message de chat (optionnellement une réponse à `parentId` — chat en fil). */
    suspend fun postMessage(id: String, content: String, parentId: String? = null) {
        val parent = parentId?.let { ""","parentId":"$it"""" } ?: ""
        api.request<Unit>(Endpoint.post(CompetitionEndpoints.messages(id), """{"message":${content.jsonQuoted()}$parent}"""))
    }

    /**
     * Bannit un spectateur OU un candidat de cette compétition (modération). `POST
     * /moderation/competition-bans` — table/canal DÉDIÉS (`competition_bans` / événement
     * `competition:banned`), PAS le mécanisme générique `/moderation/stream-bans` (corrigé :
     * l'ancien code postait sur `stream_bans`, invisible du web qui lit `competition_bans` —
     * un bannissement posé depuis mobile n'était donc jamais vu côté web, et réciproquement).
     * Le banni ne peut plus écrire, rejoindre, ni (s'il était candidat) continuer de diffuser.
     */
    suspend fun createStreamBan(competitionId: String, bannedUserId: String, reason: String?) {
        val r = reason?.let { ""","reason":${it.jsonQuoted()}""" } ?: ""
        api.request<Unit>(
            Endpoint.post(
                "/moderation/competition-bans",
                """{"competitionId":"$competitionId","bannedUserId":"$bannedUserId"$r}""",
            ),
        )
    }

    /** Liste des utilisateurs bannis de cette compétition (ids). Best-effort. */
    suspend fun listStreamBans(competitionId: String): List<String> =
        runCatching {
            api.request(
                Endpoint.get("/moderation/competition-bans", query = mapOf("competitionId" to competitionId)),
                ListSerializer(CompetitionStreamBanRow.serializer()),
            ).mapNotNull { it.bannedUserId }
        }.getOrDefault(emptyList())

    /** Compteur de J'aime persistant (`GET /lives/:id/likes`, générique par id). */
    suspend fun likesCount(competitionId: String): Int =
        runCatching {
            api.request(Endpoint.get("/lives/$competitionId/likes"), CompetitionLikesResponse.serializer()).likes
        }.getOrDefault(0)

    /** Incrémente le compteur de J'aime persistant (`POST /lives/:id/likes`). */
    suspend fun like(competitionId: String) {
        runCatching { api.request<Unit>(Endpoint.post("/lives/$competitionId/likes")) }
    }

    /** Prix d'un vote (crédits) piloté par l'admin (`GET /settings/public/vote_config`). */
    suspend fun votePricePerVote(): Double =
        runCatching {
            api.request(Endpoint.get("/settings/public/vote_config"), CompetitionVoteConfigSetting.serializer())
                .value?.pricePerVote ?: 1.0
        }.getOrDefault(1.0)

    /** Classement des donateurs (`GET /leaderboards/gifts?contextType=competition`). */
    suspend fun giftLeaderboard(id: String): List<CompetitionDonorEntry> =
        api.request(
            Endpoint.get("/leaderboards/gifts", query = mapOf("contextType" to "competition", "contextId" to id)),
            ListSerializer(CompetitionDonorEntry.serializer()),
        )

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

    /** Le caller possède-t-il un billet pour cette compétition ? (`GET /competitions/:id/my-ticket`). */
    suspend fun ticketInfo(id: String): CompetitionTicketInfo =
        api.request(Endpoint.get(CompetitionEndpoints.myTicket(id)), CompetitionTicketInfo.serializer())

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

    /** Met à jour une compétition existante (édition). `PATCH /competitions/:id`. */
    suspend fun updateCompetition(id: String, body: CreateCompetitionBody) {
        val payload = json.encodeToString(CreateCompetitionBody.serializer(), body)
        api.request<Unit>(Endpoint.patch("/competitions/$id", payload))
    }

    // --- Contrôles MANAGER en direct ---

    /** Valide/rejette une candidature (`POST /competitions/candidates/:id/review`). */
    suspend fun reviewCandidate(candidateId: String, approve: Boolean) {
        api.request<Unit>(Endpoint.post("/competitions/candidates/$candidateId/review", """{"approve":$approve}"""))
    }

    /**
     * L'ajout manuel de candidat est-il activé (réglage admin, `manual_candidates_config`) ?
     * Désactivé par défaut (demande explicite, parité web) — absent en base = `false`.
     */
    suspend fun manualCandidatesEnabled(): Boolean =
        runCatching {
            api.request(
                Endpoint.get("/settings/public/manual_candidates_config").copy(anonymous = true),
                ManualCandidatesConfigSetting.serializer(),
            ).value?.enabled == true
        }.getOrDefault(false)

    /** Annuaire public des artistes (`GET /artists`) — pour le picker d'ajout manuel. */
    suspend fun artistDirectory(): List<com.dualmusic.domain.creator.ArtistDirectoryEntry> =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.creator.CreatorEndpoints.ARTISTS),
                ListSerializer(com.dualmusic.domain.creator.ArtistDirectoryEntry.serializer()),
            )
        }.getOrDefault(emptyList())

    /**
     * Ajoute directement un candidat (walk-in, présentiel) sans passer par la candidature en
     * ligne. `POST /competitions/:id/candidates/manual` — verrouillé côté serveur par le
     * réglage [manualCandidatesEnabled].
     */
    suspend fun addCandidateManually(competitionId: String, artistId: String, pitch: String?) {
        val body = json.encodeToString(AddCandidateManuallyBody.serializer(), AddCandidateManuallyBody(artistId, pitch))
        api.request<Unit>(Endpoint.post("/competitions/$competitionId/candidates/manual", body))
    }

    /**
     * Fixe (valeur absolue, pas un incrément) les voix cumulées d'un jury hors ligne pour un
     * candidat — additionnées aux votes payants + cadeaux dans le classement (parité web).
     * `POST /competitions/candidates/:id/jury-votes`.
     */
    suspend fun setJuryVotes(candidateId: String, juryVotes: Int) {
        api.request<Unit>(Endpoint.post("/competitions/candidates/$candidateId/jury-votes", """{"juryVotes":$juryVotes}"""))
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

    /** Impose (ou libère avec `null`) la caméra épinglée pour tous. `POST /competitions/:id/focus`. */
    suspend fun setFocus(id: String, participantId: String?) {
        val pid = participantId?.let { """"$it"""" } ?: "null"
        api.request<Unit>(Endpoint.post("/competitions/$id/focus", """{"participantId":$pid}"""))
    }

    // --- Modération par évènement (chat on/off + modérateurs désignés) ---

    /**
     * Active/désactive le chat de cette compétition (manager organisateur ou admin uniquement,
     * 403 sinon). `PATCH /competitions/:id` — dédiée plutôt que [updateCompetition] (qui exige
     * le corps COMPLET de [CreateCompetitionBody], sans champ `chatEnabled`).
     */
    suspend fun setChatEnabled(id: String, enabled: Boolean) {
        api.request<Unit>(Endpoint.patch("/competitions/$id", """{"chatEnabled":$enabled}"""))
    }

    /** Spectateurs actuellement connectés à la room (manager uniquement) — vivier du picker de modérateurs. */
    suspend fun listCurrentViewers(competitionId: String): List<DisplayProfile> =
        runCatching {
            api.request(
                Endpoint.get(ModerationEndpoints.viewers("competition", competitionId)),
                ListSerializer(DisplayProfile.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Modérateurs désignés de cette compétition (max [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS]). */
    suspend fun listEventModerators(competitionId: String): List<EventModerator> =
        runCatching {
            api.request(
                Endpoint.get(ModerationEndpoints.moderators("competition", competitionId)),
                ListSerializer(EventModerator.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Désigne un spectateur connecté comme modérateur (manager uniquement). */
    suspend fun appointModerator(competitionId: String, userId: String) {
        api.request<Unit>(
            Endpoint.post(
                ModerationEndpoints.moderators("competition", competitionId),
                json.encodeToString(AppointModeratorBody.serializer(), AppointModeratorBody(userId)),
            ),
        )
    }

    /** Révoque un modérateur désigné (manager uniquement). */
    suspend fun revokeModerator(competitionId: String, userId: String) {
        api.request<Unit>(Endpoint.delete(ModerationEndpoints.revokeModerator("competition", competitionId, userId)))
    }
}

/** Échappe une chaîne pour l'insérer dans un corps JSON construit à la main. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
    /** Date limite des candidatures sponsor (ISO, si sponsors acceptés). */
    val sponsorSubmissionDeadline: String? = null,
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

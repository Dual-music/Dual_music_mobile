package com.dualmusic.feature.duel

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.duel.DuelEndpoints
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.DuelVoteTotal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Message de chat d'un duel (auteur hydraté par le backend). */
@Serializable
data class DuelChatMessage(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    // Le backend utilise la clé `message` (colonne DB), pas `content`.
    @SerialName("message") val content: String,
    // Le backend renvoie l'auteur sous la clé `author` (REST + temps réel).
    @SerialName("author") val user: DisplayProfile? = null,
) {
    val authorName: String get() = user?.displayName ?: com.dualmusic.core.ui.i18n.appStrings.fan
}

/**
 * Accès REST au catalogue et à l'état d'un duel.
 *
 * Le temps réel (votes, minuteur, statut, cadeaux, chat) arrive par Socket.IO ; ce
 * repository couvre le chargement initial + les actions non-financières.
 * ⚠️ Le **vote payant** n'est pas ici : c'est un débit, géré par `feature:wallet`.
 */
class DuelRepository(private val api: ApiClient) {

    /**
     * Signale ce direct à la modération (parité web `LiveReportButton`).
     * `POST /moderation/reports/live` — le duel est identifié par son id (`liveId`).
     */
    suspend fun reportLive(liveId: String, reason: String) {
        api.request<Unit>(Endpoint.post("moderation/reports/live", """{"liveId":"$liveId","reason":"$reason"}"""))
    }

    /**
     * Liste des duels.
     * @param status filtre optionnel (`upcoming` | `live` | `ended`).
     */
    suspend fun duels(status: String? = null, limit: Int = 50): List<Duel> =
        api.request(
            Endpoint.get(
                DuelEndpoints.LIST,
                query = buildMap {
                    status?.let { put("status", it) }
                    put("limit", limit.toString())
                },
            ),
            ListSerializer(Duel.serializer()),
        )

    /** Détail d'un duel (artistes hydratés). */
    suspend fun duel(id: String): Duel =
        api.request(Endpoint.get(DuelEndpoints.detail(id)), Duel.serializer())

    /** Tallies de votes du duel : total de crédits par artiste. */
    suspend fun voteTotals(id: String): List<DuelVoteTotal> =
        api.request(Endpoint.get(DuelEndpoints.votes(id)), ListSerializer(DuelVoteTotal.serializer()))

    /** Tallies de votes en masse pour plusieurs duels (`GET /duels/votes/batch?ids=a,b,c`). */
    suspend fun votesBatch(ids: List<String>): List<DuelVoteTotal> {
        if (ids.isEmpty()) return emptyList()
        return api.request(
            Endpoint.get(DuelEndpoints.VOTES_BATCH, query = mapOf("ids" to ids.joinToString(","))),
            ListSerializer(DuelVoteTotal.serializer()),
        )
    }

    /** Replays publics de duels (onglet Replays) — `GET /replays?sourceType=duel&isPublic=true`. */
    suspend fun duelReplays(): List<com.dualmusic.domain.replay.ReplayVideo> =
        api.request(
            Endpoint.get(
                com.dualmusic.domain.replay.ReplayEndpoints.LIST,
                query = mapOf("sourceType" to "duel", "isPublic" to "true", "limit" to "100"),
            ),
            ListSerializer(com.dualmusic.domain.replay.ReplayVideo.serializer()),
        )

    /** Historique de chat (dernière page) pour amorcer l'overlay. */
    suspend fun chatHistory(duelId: String): List<DuelChatMessage> =
        api.request(
            Endpoint.get(DuelEndpoints.messages(duelId), query = mapOf("limit" to "50")),
            ListSerializer(DuelChatMessage.serializer()),
        )

    /** Poste un message (le backend diffuse ensuite via Socket.IO). */
    suspend fun postMessage(duelId: String, content: String) {
        api.request<Unit>(Endpoint.post(DuelEndpoints.messages(duelId), """{"message":${content.jsonQuoted()}}"""))
    }

    /** Classement des donateurs du duel (`GET /leaderboards/gifts?contextType=duel`). */
    suspend fun giftLeaderboard(duelId: String): List<DuelDonorEntry> =
        api.request(
            Endpoint.get("/leaderboards/gifts", query = mapOf("contextType" to "duel", "contextId" to duelId)),
            ListSerializer(DuelDonorEntry.serializer()),
        )

    /** Catalogue des cadeaux virtuels (partagé). */
    suspend fun giftCatalog(): List<com.dualmusic.domain.model.VirtualGift> =
        api.request(
            Endpoint.get(com.dualmusic.domain.gift.GiftEndpoints.CATALOG),
            ListSerializer(com.dualmusic.domain.model.VirtualGift.serializer()),
        )

    /** Inventaire (cadeaux possédés) du caller. */
    suspend fun inventory(): List<com.dualmusic.domain.gift.InventoryItem> =
        api.request(
            Endpoint.get(com.dualmusic.domain.gift.GiftEndpoints.INVENTORY),
            ListSerializer(com.dualmusic.domain.gift.InventoryItem.serializer()),
        )

    /** Achète un cadeau (débit portefeuille atomique). */
    suspend fun purchaseGift(giftId: String, quantity: Int = 1) {
        api.request<Unit>(
            Endpoint.post(
                com.dualmusic.domain.gift.GiftEndpoints.PURCHASE,
                """{"giftId":"$giftId","quantity":$quantity}""",
                idempotencyKey = "buy-$giftId-$quantity-${System.nanoTime()}",
            ),
        )
    }

    /** Envoie un cadeau possédé à un artiste/manager dans le contexte du duel. */
    suspend fun sendGift(duelId: String, giftId: String, toUserId: String) {
        api.request<Unit>(
            Endpoint.post(
                "/wallet/gifts/send",
                """{"giftId":"$giftId","toUserId":"$toUserId","duelId":"$duelId"}""",
                idempotencyKey = "gift-$duelId-$giftId-$toUserId-${System.nanoTime()}",
            ),
        )
    }

    // --- Espace MANAGER (organisateur de duels) ---

    /**
     * Les managers ont-ils le droit de CRÉER des duels ? Réglage admin `manager_duel_creation`.
     * Défaut **false** (les duels sont assignés par l'admin) si le réglage est absent/illisible.
     */
    suspend fun managerDuelCreationEnabled(): Boolean =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.role.RoleEndpoints.publicSetting(com.dualmusic.domain.role.RoleEndpoints.MANAGER_DUEL_CREATION)),
                com.dualmusic.domain.role.PublicSetting.serializer(),
            ).value?.enabled ?: false
        }.getOrDefault(false)

    /** Id du caller (manager) — sert de `manager_id` à la création + à filtrer ses duels. */
    suspend fun myUserId(): String? =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.user.UserEndpoints.ME),
                com.dualmusic.domain.auth.MeResponse.serializer(),
            ).user?.id
        }.getOrNull()

    /** Duels gérés par ce manager (`GET /duels?managerId=&limit=`). */
    suspend fun managedDuels(managerId: String, status: String? = null): List<Duel> =
        api.request(
            Endpoint.get(
                DuelEndpoints.LIST,
                query = buildMap {
                    put("managerId", managerId)
                    status?.let { put("status", it) }
                    put("limit", "200")
                },
            ),
            ListSerializer(Duel.serializer()),
        )

    /** Annuaire des artistes (pour choisir les 2 adversaires). */
    suspend fun artists(): List<com.dualmusic.domain.artist.ArtistSummary> =
        api.request(
            Endpoint.get(com.dualmusic.domain.artist.ArtistEndpoints.LIST),
            ListSerializer(com.dualmusic.domain.artist.ArtistSummary.serializer()),
        )

    /**
     * Crée un duel entre 2 artistes (le manager s'assigne arbitre). `POST /duels`.
     * @param artist1Id / artist2Id ids UTILISATEUR des artistes.
     */
    suspend fun createDuel(artist1Id: String, artist2Id: String, scheduledTime: String?, managerId: String) {
        val sched = scheduledTime?.let { """"$it"""" } ?: "null"
        val body = """{"artist1_id":"$artist1Id","artist2_id":"$artist2Id","scheduled_time":$sched,"manager_id":"$managerId","status":"upcoming"}"""
        api.request<Unit>(Endpoint.post(DuelEndpoints.LIST, body))
    }

    /**
     * Met à jour un duel (contrôles manager en direct). `PATCH /duels/:id` — champs camelCase :
     * `status`, `winnerId`, `currentTimerEndsAt`, `currentTimerTargetId`. Le backend rediffuse
     * les events `timer`/`status` → tous les spectateurs se mettent à jour.
     */
    suspend fun updateDuel(id: String, bodyJson: String) {
        api.request<Unit>(Endpoint.patch(DuelEndpoints.detail(id), bodyJson))
    }
}

/** Entrée du classement des donateurs (`GET /leaderboards/gifts`). */
@Serializable
data class DuelDonorEntry(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    val total: Double = 0.0,
    val score: Double = 0.0,
    val user: DisplayProfile? = null,
) {
    val displayName: String
        get() = user?.displayName ?: stageName?.takeIf { it.isNotBlank() } ?: fullName?.takeIf { it.isNotBlank() } ?: "Donateur"
    val value: Int get() = (if (total > 0.0) total else score).toInt()
}

/** Échappe une chaîne pour un littéral JSON minimal. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

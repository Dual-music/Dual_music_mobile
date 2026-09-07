package com.dualmusic.feature.concert

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.concert.ConcertEndpoints
import com.dualmusic.domain.concert.ConcertTicketInfo
import com.dualmusic.domain.concert.DedicationRequest
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.model.VirtualGift
import com.dualmusic.domain.moderation.AppointModeratorBody
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.moderation.ModerationEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Message de chat d'un concert (auteur hydraté sous `author`). Le backend (chat.helper partagé)
 * utilise la colonne `message` et supporte les réponses en fil (`parent_id`).
 */
@Serializable
data class ConcertChatMessage(
    val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("message") val content: String = "",
    @SerialName("author") val user: DisplayProfile? = null,
    @SerialName("parent_id") val parentId: String? = null,
) {
    val authorName: String get() = user?.displayName ?: com.dualmusic.core.ui.i18n.appStrings.fan
}

/** Ligne de bannissement (`GET /moderation/stream-bans`) — on n'extrait que l'utilisateur banni. */
@Serializable
data class ConcertStreamBanRow(
    @SerialName("banned_user_id") val bannedUserId: String? = null,
)

/** Charge utile `stream:banned` — un spectateur vient d'être banni de ce direct. */
@Serializable
data class ConcertStreamBannedPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("stream_id") val streamId: String? = null,
)

/** Cadeau possédé dans l'inventaire (`GET /gifts/inventory`). */
@Serializable
data class OwnedConcertGift(
    @SerialName("gift_id") val id: String,
    val name: String = "",
    val price: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    val quantity: Int = 0,
)

/** Entrée du classement des donateurs (`GET /leaderboards/gifts`). */
@Serializable
data class ConcertDonorEntry(
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

/** Réponse de `GET /lives/:id/likes` (réutilisé pour les concerts). */
@Serializable
private data class LikesResponse(val likes: Int = 0)

/**
 * Accès REST aux concerts d'artistes.
 *
 * Lectures (catalogue, détail, billetterie) et achat de dédicace. L'achat du **billet**
 * est un débit du portefeuille (`POST /wallet/tickets/concert`) — voir `feature:wallet`.
 */
class ConcertRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /**
     * Signale ce direct à la modération (parité web `LiveReportButton`).
     * `POST /moderation/reports/live` — le concert est identifié par son id (`liveId`).
     */
    suspend fun reportLive(liveId: String, reason: String) {
        // `streamType` distingue les 3 entités partageant `live_reports` — sans lui, ce signalement
        // de CONCERT était enregistré par défaut comme "live", invisible/mal lié pour l'admin.
        api.request<Unit>(Endpoint.post("moderation/reports/live", """{"liveId":"$liveId","streamType":"concert","reason":"$reason"}"""))
    }

    /** Catalogue public des concerts d'artistes (approuvés). */
    suspend fun concerts(limit: Int = 50): List<Concert> =
        api.request(
            Endpoint.get(ConcertEndpoints.ARTIST_LIST, query = mapOf("limit" to limit.toString())),
            ListSerializer(Concert.serializer()),
        )

    /** Concerts admin (`GET /concerts`) — fusionnés avec le catalogue artiste (comme le web). */
    suspend fun adminConcerts(limit: Int = 100): List<Concert> =
        runCatching {
            api.request(
                Endpoint.get("/concerts", query = mapOf("limit" to limit.toString())),
                ListSerializer(Concert.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Replays publics de concerts (`GET /replays?sourceType=concert&isPublic=true`). */
    suspend fun concertReplays(): List<com.dualmusic.domain.replay.ReplayVideo> =
        runCatching {
            api.request(
                Endpoint.get(
                    com.dualmusic.domain.replay.ReplayEndpoints.LIST,
                    query = mapOf("sourceType" to "concert", "isPublic" to "true", "limit" to "100"),
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

    /** Détail d'un concert. */
    suspend fun concert(id: String): Concert =
        api.request(Endpoint.get(ConcertEndpoints.artistDetail(id)), Concert.serializer())

    /** Concerts en attente d'approbation (admin) : `GET /artist-concerts?approvalStatus=pending`. */
    suspend fun pendingConcerts(): List<Concert> =
        api.request(
            Endpoint.get(ConcertEndpoints.ARTIST_LIST, query = mapOf("approvalStatus" to "pending", "limit" to "100")),
            ListSerializer(Concert.serializer()),
        )

    /** Approuve/rejette un concert artiste (admin) : `POST /artist-concerts/:id/review`. */
    suspend fun reviewConcert(id: String, approve: Boolean) {
        api.request<Unit>(Endpoint.post(ConcertEndpoints.artistDetail(id) + "/review", """{"approve":$approve}"""))
    }

    /** Vrai si le caller est admin (pour afficher la file d'approbation des concerts). */
    suspend fun amIAdmin(): Boolean =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.user.UserEndpoints.ME),
                com.dualmusic.domain.auth.MeResponse.serializer(),
            ).roles.any { it == com.dualmusic.domain.model.UserRole.ADMIN }
        }.getOrDefault(false)

    /** Billetterie : prix, places restantes, et si le caller a déjà son billet. */
    suspend fun ticketInfo(id: String): ConcertTicketInfo =
        api.request(Endpoint.get(ConcertEndpoints.ticketInfo(id)), ConcertTicketInfo.serializer())

    /**
     * Achète une dédicace pour un concert (débit atomique + idempotent). Uniquement possible
     * AVANT le direct (statut `upcoming`) — contrairement au live, le serveur rejette toute
     * demande une fois le concert passé en `live`/`ended` (voir `resolveDedicationArtist`).
     * @param message texte lu par l'artiste pendant le concert.
     * @param priceCredits prix libre (minimum imposé par la config économique, voir [dedicationMinPrice]).
     */
    suspend fun purchaseDedication(
        concertId: String,
        message: String,
        priceCredits: Double,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            DedicationRequest.serializer(),
            DedicationRequest(concertId = concertId, message = message, priceCredits = priceCredits),
        )
        api.request<Unit>(
            Endpoint.post(ConcertEndpoints.DEDICATIONS_PURCHASE, body, idempotencyKey = idempotencyKey),
        )
    }

    /**
     * Prix minimum d'une dédicace de CONCERT (section `economic_config.dedication` — jamais
     * `dedication_live`, réservée aux lives — même logique que `concert.service.js#dedicationConfig`).
     */
    suspend fun dedicationMinPrice(): Double =
        runCatching {
            val v = api.request(Endpoint.get("/settings/public/economic_config"), ConcertEconomicConfigSetting.serializer()).value
            v?.dedication?.minPriceCredits ?: 10.0
        }.getOrDefault(10.0)

    // --- Room live du concert (chat, likes, cadeaux, billet, go-live) ---

    /** Id du caller (pour détecter si l'utilisateur est l'artiste organisateur → hôte). */
    suspend fun myUserId(): String? =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.user.UserEndpoints.ME),
                com.dualmusic.domain.auth.MeResponse.serializer(),
            ).user?.id
        }.getOrNull()

    /** Historique de chat du concert (`GET /concerts/:id/messages`). */
    suspend fun chatHistory(concertId: String): List<ConcertChatMessage> =
        api.request(
            Endpoint.get("/concerts/$concertId/messages", query = mapOf("limit" to "50")),
            ListSerializer(ConcertChatMessage.serializer()),
        )

    /** Poste un message (le backend diffuse via Socket.IO), optionnellement en réponse à `parentId`. */
    suspend fun postMessage(concertId: String, content: String, parentId: String? = null) {
        api.request<Unit>(Endpoint.post("/concerts/$concertId/messages", json.encodeToString(MessageBody.serializer(), MessageBody(content, parentId))))
    }

    /**
     * Bannit un spectateur de ce direct (modération). `POST /moderation/stream-bans` avec
     * `streamType: "concert"`. Il ne pourra plus écrire ni rejoindre le direct.
     */
    suspend fun createStreamBan(streamId: String, bannedUserId: String, reason: String?) {
        val r = reason?.let { ""","reason":${it.jsonQuoted()}""" } ?: ""
        api.request<Unit>(
            Endpoint.post(
                "/moderation/stream-bans",
                """{"streamId":"$streamId","streamType":"concert","bannedUserId":"$bannedUserId"$r}""",
            ),
        )
    }

    /** Liste des utilisateurs bannis de ce direct (ids). Best-effort. */
    suspend fun listStreamBans(streamId: String): List<String> =
        runCatching {
            api.request(
                Endpoint.get("/moderation/stream-bans", query = mapOf("streamId" to streamId, "streamType" to "concert")),
                ListSerializer(ConcertStreamBanRow.serializer()),
            ).mapNotNull { it.bannedUserId }
        }.getOrDefault(emptyList())

    /** Likes courants (réutilise l'endpoint des lives, clé = id du concert). */
    suspend fun likesCount(concertId: String): Int =
        runCatching { api.request(Endpoint.get("/lives/$concertId/likes"), LikesResponse.serializer()).likes }.getOrDefault(0)

    /** Ajoute un like (réutilise l'endpoint des lives). */
    suspend fun likeConcert(concertId: String) {
        runCatching { api.request<Unit>(Endpoint.post("/lives/$concertId/likes")) }
    }

    /** Passe le concert en direct (hôte artiste) : `PATCH /artist-concerts/:id {status:"live"}`. */
    suspend fun goLive(concertId: String) {
        api.request<Unit>(Endpoint.patch(ConcertEndpoints.artistDetail(concertId), """{"status":"live"}"""))
    }

    /** Termine le concert (hôte) : `PATCH /artist-concerts/:id {status:"ended"}`. */
    suspend fun endConcert(concertId: String) {
        api.request<Unit>(Endpoint.patch(ConcertEndpoints.artistDetail(concertId), """{"status":"ended"}"""))
    }

    /** Achète un billet (débit atomique + idempotent) : `POST /wallet/tickets/concert`. */
    suspend fun buyTicket(concertId: String, idempotencyKey: String = UUID.randomUUID().toString()) {
        api.request<Unit>(Endpoint.post("/wallet/tickets/concert", """{"concertId":"$concertId"}""", idempotencyKey = idempotencyKey))
    }

    /** Catalogue des cadeaux virtuels. */
    suspend fun giftCatalog(): List<VirtualGift> =
        api.request(Endpoint.get(com.dualmusic.domain.gift.GiftEndpoints.CATALOG), ListSerializer(VirtualGift.serializer()))

    /** Inventaire (cadeaux possédés). */
    suspend fun inventory(): List<OwnedConcertGift> =
        api.request(Endpoint.get("/gifts/inventory"), ListSerializer(OwnedConcertGift.serializer()))

    /** Achète un cadeau (débit wallet atomique). */
    suspend fun purchaseGift(giftId: String, quantity: Int = 1) {
        api.request<Unit>(Endpoint.post("/wallet/gifts/purchase", """{"giftId":"$giftId","quantity":$quantity}""", idempotencyKey = "buy-$giftId-$quantity-${System.nanoTime()}"))
    }

    /** Envoie un cadeau possédé à l'artiste dans le contexte du concert. */
    suspend fun sendGift(concertId: String, giftId: String, toUserId: String) {
        api.request<Unit>(
            Endpoint.post(
                "/wallet/gifts/send",
                """{"giftId":"$giftId","toUserId":"$toUserId","concertId":"$concertId"}""",
                idempotencyKey = "gift-$concertId-$giftId-$toUserId-${System.nanoTime()}",
            ),
        )
    }

    /** Classement des donateurs du concert (`GET /leaderboards/gifts?contextType=concert`). */
    suspend fun giftLeaderboard(concertId: String): List<ConcertDonorEntry> =
        api.request(
            Endpoint.get("/leaderboards/gifts", query = mapOf("contextType" to "concert", "contextId" to concertId)),
            ListSerializer(ConcertDonorEntry.serializer()),
        )

    // --- Modération par évènement (chat on/off + modérateurs désignés) ---

    /** Active/désactive le chat de ce concert (hôte artiste ou admin uniquement, 403 sinon). */
    suspend fun setChatEnabled(concertId: String, enabled: Boolean) {
        api.request<Unit>(Endpoint.patch(ConcertEndpoints.artistDetail(concertId), """{"chatEnabled":$enabled}"""))
    }

    /** Spectateurs actuellement connectés à la room (hôte uniquement) — vivier du picker de modérateurs. */
    suspend fun listCurrentViewers(concertId: String): List<DisplayProfile> =
        runCatching {
            api.request(
                Endpoint.get(ModerationEndpoints.viewers("concert", concertId)),
                ListSerializer(DisplayProfile.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Modérateurs désignés de ce concert (max [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS]). */
    suspend fun listEventModerators(concertId: String): List<EventModerator> =
        runCatching {
            api.request(
                Endpoint.get(ModerationEndpoints.moderators("concert", concertId)),
                ListSerializer(EventModerator.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Désigne un spectateur connecté comme modérateur (hôte uniquement). */
    suspend fun appointModerator(concertId: String, userId: String) {
        api.request<Unit>(
            Endpoint.post(
                ModerationEndpoints.moderators("concert", concertId),
                json.encodeToString(AppointModeratorBody.serializer(), AppointModeratorBody(userId)),
            ),
        )
    }

    /** Révoque un modérateur désigné (hôte uniquement). */
    suspend fun revokeModerator(concertId: String, userId: String) {
        api.request<Unit>(Endpoint.delete(ModerationEndpoints.revokeModerator("concert", concertId, userId)))
    }
}

/** Corps JSON `{message, parentId?}` d'un message de chat (clé `message` = colonne DB backend). */
@Serializable
private data class MessageBody(val message: String, val parentId: String? = null)

/** Échappe une chaîne pour l'insérer dans un corps JSON construit à la main. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Réponse de `GET /settings/public/economic_config` — on ne lit que la section dédicace. */
@Serializable
private data class ConcertEconomicConfigSetting(val value: ConcertEconomicConfigValue? = null)

@Serializable
private data class ConcertEconomicConfigValue(val dedication: ConcertDedicationConfigSection? = null)

@Serializable
private data class ConcertDedicationConfigSection(@SerialName("min_price_credits") val minPriceCredits: Double? = null)

package com.dualmusic.feature.live

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.moderation.AppointModeratorBody
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.moderation.ModerationEndpoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Message de chat affiché dans le live (auteur hydraté par le backend). */
@Serializable
data class LiveChatMessage(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    // Le backend utilise la clé `message` (colonne DB), pas `content`.
    @SerialName("message") val content: String,
    // Le backend renvoie l'auteur sous la clé `author` (REST + temps réel).
    @SerialName("author") val user: DisplayProfile? = null,
    // Réponse à un message parent (chat en fil) — présent quand c'est une réponse.
    @SerialName("parent_id") val parentId: String? = null,
) {
    val authorName: String get() = user?.displayName ?: com.dualmusic.core.ui.i18n.appStrings.fan
}

/** Ligne de bannissement (`GET /moderation/stream-bans`) — on n'extrait que l'utilisateur banni. */
@Serializable
data class LiveStreamBanRow(
    @SerialName("banned_user_id") val bannedUserId: String? = null,
)

/** Charge utile `stream:banned` — un spectateur vient d'être banni de ce direct. */
@Serializable
data class LiveStreamBannedPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("stream_id") val streamId: String? = null,
)

/**
 * Accès REST aux actions et à l'historique d'un live.
 * Le temps réel (messages, cadeaux, présence) passe par Socket.IO ; ce repository couvre
 * l'historique initial + les actions (message, cadeau).
 */
class LiveRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /**
     * Spectateurs actuellement connectés à ce live (hôte uniquement — 403 sinon), vivier du
     * picker de désignation. `GET /moderation/events/live/:id/viewers`.
     */
    suspend fun listCurrentViewers(liveId: String): List<DisplayProfile> =
        api.request(Endpoint.get(ModerationEndpoints.viewers("live", liveId)), ListSerializer(DisplayProfile.serializer()))

    /**
     * Modérateurs désignés de ce live (hôte + jusqu'à [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS]
     * spectateurs). `GET /moderation/events/live/:id/moderators`.
     */
    suspend fun listEventModerators(liveId: String): List<EventModerator> =
        api.request(Endpoint.get(ModerationEndpoints.moderators("live", liveId)), ListSerializer(EventModerator.serializer()))

    /** Hôte : désigne un spectateur modérateur. `POST /moderation/events/live/:id/moderators`. */
    suspend fun appointModerator(liveId: String, userId: String) {
        val body = json.encodeToString(AppointModeratorBody.serializer(), AppointModeratorBody(userId))
        api.request<Unit>(Endpoint.post(ModerationEndpoints.moderators("live", liveId), body))
    }

    /** Hôte : révoque un modérateur désigné. `DELETE /moderation/events/live/:id/moderators/:userId`. */
    suspend fun revokeModerator(liveId: String, userId: String) {
        api.request<Unit>(Endpoint.delete(ModerationEndpoints.revokeModerator("live", liveId, userId)))
    }

    /** Historique de chat (dernière page) pour amorcer l'overlay. */
    suspend fun chatHistory(liveId: String): List<LiveChatMessage> =
        api.request(
            Endpoint.get("/lives/$liveId/messages", query = mapOf("limit" to "50")),
            ListSerializer(LiveChatMessage.serializer()),
        )

    /** Poste un message (le backend diffuse ensuite via Socket.IO), optionnellement en réponse. */
    suspend fun postMessage(liveId: String, content: String, parentId: String? = null) {
        val parent = parentId?.let { ""","parentId":"$it"""" } ?: ""
        api.request<Unit>(Endpoint.post("/lives/$liveId/messages", """{"message":${content.jsonQuoted()}$parent}"""))
    }

    /**
     * Bannit un spectateur de ce direct (modération). `POST /moderation/stream-bans` avec
     * `streamType: "live"`. Il ne pourra plus écrire ni rejoindre le direct.
     */
    suspend fun createStreamBan(streamId: String, bannedUserId: String, reason: String?) {
        val r = reason?.let { ""","reason":${it.jsonQuoted()}""" } ?: ""
        api.request<Unit>(
            Endpoint.post(
                "/moderation/stream-bans",
                """{"streamId":"$streamId","streamType":"live","bannedUserId":"$bannedUserId"$r}""",
            ),
        )
    }

    /** Liste des utilisateurs bannis de ce direct (ids). Best-effort. */
    suspend fun listStreamBans(streamId: String): List<String> =
        runCatching {
            api.request(
                Endpoint.get("/moderation/stream-bans", query = mapOf("streamId" to streamId, "streamType" to "live")),
                ListSerializer(LiveStreamBanRow.serializer()),
            ).mapNotNull { it.bannedUserId }
        }.getOrDefault(emptyList())

    /** Termine le live (hôte). */
    suspend fun endLive(liveId: String) {
        api.request<Unit>(Endpoint.post("/lives/$liveId/end"))
    }

    /** Ajoute un like au live (le backend diffuse le nouveau total via `likes`). */
    suspend fun likeLive(liveId: String) {
        api.request<Unit>(Endpoint.post("/lives/$liveId/likes"))
    }

    /** Nombre de likes courant (amorçage). */
    suspend fun likesCount(liveId: String): Int =
        api.request(Endpoint.get("/lives/$liveId/likes"), LikesResponse.serializer()).likes

    /** Catalogue des cadeaux virtuels (pour la boutique d'achat). */
    suspend fun giftCatalog(): List<com.dualmusic.domain.model.VirtualGift> =
        api.request(
            Endpoint.get(com.dualmusic.domain.gift.GiftEndpoints.CATALOG),
            ListSerializer(com.dualmusic.domain.model.VirtualGift.serializer()),
        )

    /**
     * Inventaire du spectateur : cadeaux possédés (achetés) avec leur quantité.
     * L'envoi consomme un cadeau de l'inventaire (parité web — le backend exige la possession).
     */
    suspend fun inventory(): List<OwnedGift> =
        api.request(Endpoint.get("/gifts/inventory"), ListSerializer(OwnedGift.serializer()))

    /**
     * Achète `quantity` exemplaires d'un cadeau dans l'inventaire (débit wallet atomique).
     * `Idempotency-Key` anti double-débit.
     */
    suspend fun purchaseGift(giftId: String, quantity: Int = 1) {
        val body = """{"giftId":"$giftId","quantity":$quantity}"""
        val idem = "buy-$giftId-$quantity-${System.nanoTime()}"
        api.request<Unit>(Endpoint.post("/wallet/gifts/purchase", body, idempotencyKey = idem))
    }

    /** Classement des meilleurs donateurs du live (`GET /leaderboards/gifts`). */
    suspend fun giftLeaderboard(liveId: String): List<GiftLeaderboardEntry> =
        api.request(
            Endpoint.get("/leaderboards/gifts", query = mapOf("contextType" to "live", "contextId" to liveId)),
            ListSerializer(GiftLeaderboardEntry.serializer()),
        )

    /** Suit l'artiste hôte du live. */
    suspend fun followArtist(artistId: String) {
        api.request<Unit>(Endpoint.post("/users/$artistId/follow"))
    }

    /** Signale le live (modération) avec un motif. */
    suspend fun reportLive(liveId: String, reason: String) {
        api.request<Unit>(Endpoint.post("/moderation/reports/live", """{"liveId":"$liveId","reason":${reason.jsonQuoted()}}"""))
    }

    /**
     * Envoie une dédicace (message dédié) dans le contexte du live. `priceCredits` est
     * **requis** par le backend (débit atomique — RPC `purchase_concert_dedication_from_wallet`) ;
     * l'omettre fait échouer la requête en 400 SANS que le fan ne s'en aperçoive côté UI.
     */
    suspend fun sendDedication(liveId: String, message: String, priceCredits: Double) {
        api.request<Unit>(
            Endpoint.post(
                "/concerts/dedications",
                """{"concertId":"$liveId","concertType":"artist_live","message":${message.jsonQuoted()},"priceCredits":$priceCredits}""",
            ),
        )
    }

    /**
     * Prix minimum d'une dédicace de live, piloté par l'admin (`economic_config.dedication_live`,
     * repli `.dedication`, repli 10 — même logique que le backend/web).
     */
    suspend fun dedicationMinPrice(): Double =
        runCatching {
            val v = api.request(Endpoint.get("/settings/public/economic_config"), EconomicConfigSetting.serializer()).value
            (v?.dedicationLive?.minPriceCredits ?: v?.dedication?.minPriceCredits ?: 10.0)
        }.getOrDefault(10.0)

    /** Détail du live (réglages inclus : `allowsDedications`/`dedicationMinPriceCredits`/`allowGuests`). */
    suspend fun getLive(liveId: String): com.dualmusic.domain.model.Live =
        api.request(Endpoint.get("/lives/$liveId"), com.dualmusic.domain.model.Live.serializer())

    /**
     * Hôte (ou staff/modérateur) : bascule en direct les dédicaces on/off (+ prix minimum propre
     * au live), les demandes d'invité on/off, et/ou le chat on/off (chat = pouvoir EXCLUSIF de
     * l'hôte, jamais délégué aux modérateurs désignés — voir [com.dualmusic.domain.moderation.EventModerator]).
     * Seuls les champs fournis sont modifiés.
     */
    suspend fun updateLiveSettings(
        liveId: String,
        allowsDedications: Boolean? = null,
        dedicationMinPriceCredits: Double? = null,
        allowGuests: Boolean? = null,
        chatEnabled: Boolean? = null,
    ) {
        val fields = buildList {
            allowsDedications?.let { add(""""allowsDedications":$it""") }
            dedicationMinPriceCredits?.let { add(""""dedicationMinPriceCredits":$it""") }
            allowGuests?.let { add(""""allowGuests":$it""") }
            chatEnabled?.let { add(""""chatEnabled":$it""") }
        }
        if (fields.isEmpty()) return
        api.request<Unit>(Endpoint.patch("/lives/$liveId/settings", "{${fields.joinToString(",")}}"))
    }

    /**
     * Boîte de réception des dédicaces de l'artiste (TOUS ses événements, `GET
     * /concerts/dedications/artist/me`), enrichie du profil du fan. On filtre côté client sur
     * ce live précis (aucun endpoint scoping par événement ne renvoie le fan hydraté).
     */
    suspend fun artistDedications(): List<LiveDedication> =
        api.request(Endpoint.get("/concerts/dedications/artist/me"), ListSerializer(LiveDedication.serializer()))

    /** Marque une dédicace comme livrée (interprétée) — `POST /concerts/dedications/:id/deliver`. */
    suspend fun deliverDedication(id: String) {
        api.request<Unit>(Endpoint.post("/concerts/dedications/$id/deliver", "{}"))
    }

    /** Accepte une dédicace EN ATTENTE : débite le fan MAINTENANT — `POST .../:id/accept`. */
    suspend fun acceptDedication(id: String) {
        api.request<Unit>(Endpoint.post("/concerts/dedications/$id/accept", "{}"))
    }

    /** Rejette une dédicace EN ATTENTE : aucun débit — `POST .../:id/reject`. */
    suspend fun rejectDedication(id: String) {
        api.request<Unit>(Endpoint.post("/concerts/dedications/$id/reject", "{}"))
    }

    /** Spectateur : demande à rejoindre le live (renvoie l'id de la demande). */
    suspend fun requestJoin(liveId: String): String? =
        runCatching {
            api.request(Endpoint.post("/lives/$liveId/join", "{}"), LiveJoinRequest.serializer()).id
        }.getOrNull()

    /** Spectateur : annule sa demande. */
    suspend fun cancelJoin(requestId: String) {
        api.request<Unit>(Endpoint.delete("/lives/join-requests/$requestId"))
    }

    /** Hôte : liste des demandes (par défaut en attente). */
    suspend fun joinRequests(liveId: String, status: String = "pending"): List<LiveJoinRequest> =
        api.request(
            Endpoint.get("/lives/$liveId/join-requests", query = mapOf("status" to status)),
            ListSerializer(LiveJoinRequest.serializer()),
        )

    /** Hôte : répond à une demande (accepter/refuser). */
    suspend fun respondJoin(requestId: String, accept: Boolean) =
        respondJoinStatus(requestId, if (accept) "accepted" else "rejected")

    /** Hôte : change l'état d'une demande (`accepted` | `rejected` | `ended`). `ended` = retirer un invité. */
    suspend fun respondJoinStatus(requestId: String, status: String) {
        api.request<Unit>(Endpoint.post("/lives/join-requests/$requestId/respond", """{"status":"$status"}"""))
    }

    /** Id du caller (pour détecter l'acceptation de sa propre demande d'invité). */
    suspend fun myUserId(): String? =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.user.UserEndpoints.ME),
                com.dualmusic.domain.auth.MeResponse.serializer(),
            ).user?.id
        }.getOrNull()

    /**
     * Envoie un cadeau au host dans le contexte du live. Débit atomique côté backend ;
     * `Idempotency-Key` anti double-débit.
     */
    suspend fun sendGift(liveId: String, giftId: String, toUserId: String) {
        val body = """{"giftId":"$giftId","toUserId":"$toUserId","liveId":"$liveId"}"""
        val idem = "gift-$liveId-$giftId-$toUserId-${System.nanoTime()}"
        api.request<Unit>(Endpoint.post("/wallet/gifts/send", body, idempotencyKey = idem))
    }
}

/** Réponse de `GET /lives/:id/likes`. */
@Serializable
data class LikesResponse(val likes: Int = 0)

/** Section `dedication`/`dedication_live` de `economic_config` (`GET /settings/public/economic_config`). */
@Serializable
data class EconomicConfigSetting(val value: EconomicConfigValue? = null)

@Serializable
data class EconomicConfigValue(
    @SerialName("dedication_live") val dedicationLive: DedicationConfigSection? = null,
    val dedication: DedicationConfigSection? = null,
)

@Serializable
data class DedicationConfigSection(@SerialName("min_price_credits") val minPriceCredits: Double? = null)

/**
 * Dédicace payante reçue par l'artiste (`concert_dedications`, `concert_type="artist_live"`).
 * `status` : `paid` (en attente, à interpréter) | `delivered` (déjà lue) | `rejected`.
 */
@Serializable
data class LiveDedication(
    val id: String,
    @SerialName("fan_id") val fanId: String = "",
    val message: String = "",
    @SerialName("price_credits") val priceCredits: Double = 0.0,
    val status: String = "paid",
    @SerialName("concert_id") val concertId: String? = null,
    @SerialName("concert_type") val concertType: String? = null,
    val fan: DisplayProfile? = null,
) {
    val fanName: String get() = fan?.displayName ?: "Fan"
}

/** Cadeau possédé dans l'inventaire (`GET /gifts/inventory`). */
@Serializable
data class OwnedGift(
    @SerialName("gift_id") val id: String,
    val name: String = "",
    val price: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    val quantity: Int = 0,
)

/** Entrée du classement des donateurs (`GET /leaderboards/gifts`). Champs souples. */
@Serializable
data class GiftLeaderboardEntry(
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

/**
 * Demande d'un spectateur pour rejoindre le live en invité (`live_join_requests`).
 * Le backend renvoie les lignes brutes (pas de profil hydraté) → repli d'affichage.
 */
@Serializable
data class LiveJoinRequest(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    val status: String = "pending",
    val user: DisplayProfile? = null,
) {
    val displayName: String get() = user?.displayName ?: "Spectateur"
}

/** Échappe une chaîne pour un littéral JSON minimal. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

package com.dualmusic.feature.live

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.model.DisplayProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Message de chat affiché dans le live (auteur hydraté par le backend). */
@Serializable
data class LiveChatMessage(
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
 * Accès REST aux actions et à l'historique d'un live.
 * Le temps réel (messages, cadeaux, présence) passe par Socket.IO ; ce repository couvre
 * l'historique initial + les actions (message, cadeau).
 */
class LiveRepository(private val api: ApiClient) {

    /** Historique de chat (dernière page) pour amorcer l'overlay. */
    suspend fun chatHistory(liveId: String): List<LiveChatMessage> =
        api.request(
            Endpoint.get("/lives/$liveId/messages", query = mapOf("limit" to "50")),
            ListSerializer(LiveChatMessage.serializer()),
        )

    /** Poste un message (le backend diffuse ensuite via Socket.IO). Clé `message` (colonne DB). */
    suspend fun postMessage(liveId: String, content: String) {
        api.request<Unit>(Endpoint.post("/lives/$liveId/messages", """{"message":${content.jsonQuoted()}}"""))
    }

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

    /** Envoie une dédicace (message dédié) dans le contexte du live. */
    suspend fun sendDedication(liveId: String, message: String) {
        api.request<Unit>(Endpoint.post("/concerts/dedications", """{"concertId":"$liveId","concertType":"artist_live","message":${message.jsonQuoted()}}"""))
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

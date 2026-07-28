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
    val content: String,
    val user: DisplayProfile? = null,
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

    /** Poste un message (le backend diffuse ensuite via Socket.IO). */
    suspend fun postMessage(liveId: String, content: String) {
        api.request<Unit>(Endpoint.post("/lives/$liveId/messages", """{"content":${content.jsonQuoted()}}"""))
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

    /** Catalogue des cadeaux virtuels (pour le panneau de sélection). */
    suspend fun giftCatalog(): List<com.dualmusic.domain.model.VirtualGift> =
        api.request(
            Endpoint.get(com.dualmusic.domain.gift.GiftEndpoints.CATALOG),
            ListSerializer(com.dualmusic.domain.model.VirtualGift.serializer()),
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
    suspend fun respondJoin(requestId: String, accept: Boolean) {
        val status = if (accept) "accepted" else "rejected"
        api.request<Unit>(Endpoint.post("/lives/join-requests/$requestId/respond", """{"status":"$status"}"""))
    }

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

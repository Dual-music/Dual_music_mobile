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

/** Échappe une chaîne pour un littéral JSON minimal. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

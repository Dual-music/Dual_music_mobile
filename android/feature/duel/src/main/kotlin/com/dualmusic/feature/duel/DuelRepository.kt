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

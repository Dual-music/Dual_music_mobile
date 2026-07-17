package com.dualmusic.domain.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Notification in-app (`notifications`).
 *
 * @property type type métier (ex. `duel_invite`, `gift_received`) — sert aussi au routage
 *   deeplink quand un `data` cible est fourni.
 * @property read vrai si déjà lue.
 * @property data charge utile libre (cible du deeplink, ids…). `null` si absente.
 */
@Serializable
data class AppNotification(
    val id: String,
    val title: String? = null,
    val message: String? = null,
    val type: String? = null,
    val read: Boolean = false,
    val data: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Réponse de `GET /notifications/unread-count`. */
@Serializable
data class UnreadCount(val count: Int = 0)

/** Chemins REST des notifications (source unique, partagée). */
object NotificationEndpoints {
    const val LIST = "/notifications"
    const val UNREAD_COUNT = "/notifications/unread-count"
    const val READ_ALL = "/notifications/read-all"
    fun read(id: String) = "/notifications/$id/read"
    fun remove(id: String) = "/notifications/$id"
}

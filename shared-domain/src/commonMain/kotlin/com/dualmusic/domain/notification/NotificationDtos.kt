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
    // Le backend renvoie `read` en 0/1 (MySQL TINYINT) → booléen tolérant, sinon la liste
    // entière échoue à se désérialiser (cloche = 1 mais liste vide).
    @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class)
    val read: Boolean = false,
    val data: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Réponse de `GET /notifications/unread-count`. Le backend renvoie `{ unread }`. */
@Serializable
data class UnreadCount(@SerialName("unread") val count: Int = 0)

/** Corps de `POST/DELETE /notifications/devices` — jeton d'appareil FCM (push mobile). */
@Serializable
data class DeviceTokenRequest(val token: String)

/**
 * Préférences d'emails de notification (`email_notification_preferences`) —
 * `GET /notifications/preferences` (lecture) et `PUT /notifications/preferences/email`
 * (mise à jour). Toutes booléennes, défaut `true`. `emailSystem` est requis (non désactivable).
 */
@Serializable
data class NotificationPreferences(
    @SerialName("email_concerts") val emailConcerts: Boolean = true,
    @SerialName("email_duels") val emailDuels: Boolean = true,
    @SerialName("email_lives") val emailLives: Boolean = true,
    @SerialName("email_gifts") val emailGifts: Boolean = true,
    @SerialName("email_votes") val emailVotes: Boolean = true,
    @SerialName("email_requests") val emailRequests: Boolean = true,
    @SerialName("email_assignments") val emailAssignments: Boolean = true,
    @SerialName("email_system") val emailSystem: Boolean = true,
    /** Préférence push (opt-out global). */
    @SerialName("push_enabled") val pushEnabled: Boolean = true,
)

/** Chemins REST des notifications (source unique, partagée). */
object NotificationEndpoints {
    const val LIST = "/notifications"
    const val UNREAD_COUNT = "/notifications/unread-count"
    const val READ_ALL = "/notifications/read-all"
    /** Enregistrement/suppression d'un jeton FCM (mobile). */
    const val DEVICES = "/notifications/devices"
    /** Préférences email (lecture). */
    const val PREFERENCES = "/notifications/preferences"
    /** Mise à jour des préférences email (`PUT`). */
    const val PREFERENCES_EMAIL = "/notifications/preferences/email"
    fun read(id: String) = "/notifications/$id/read"
    fun remove(id: String) = "/notifications/$id"
}

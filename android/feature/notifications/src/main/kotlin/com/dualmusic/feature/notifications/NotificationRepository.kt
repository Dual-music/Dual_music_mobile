package com.dualmusic.feature.notifications

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.notification.AppNotification
import com.dualmusic.domain.notification.NotificationEndpoints
import com.dualmusic.domain.notification.NotificationPreferences
import com.dualmusic.domain.notification.UnreadCount
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Accès REST au centre de notifications in-app.
 *
 * Le flux temps réel (nouvelles notifications) passe par Socket.IO `/notifications` ; ce
 * repository couvre l'historique + les actions (marquer lu, tout lu).
 */
class NotificationRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Préférences email de notification du caller. */
    suspend fun emailPreferences(): NotificationPreferences =
        api.request(Endpoint.get(NotificationEndpoints.PREFERENCES), NotificationPreferences.serializer())

    /** Met à jour les préférences email (PUT), renvoie l'état à jour. */
    suspend fun updateEmailPreferences(prefs: NotificationPreferences): NotificationPreferences {
        val body = json.encodeToString(NotificationPreferences.serializer(), prefs)
        return api.request(Endpoint.put(NotificationEndpoints.PREFERENCES_EMAIL, body), NotificationPreferences.serializer())
    }

    /** Dernière page de notifications, les plus récentes d'abord. */
    suspend fun list(limit: Int = 50): List<AppNotification> =
        api.request(
            Endpoint.get(NotificationEndpoints.LIST, query = mapOf("limit" to limit.toString())),
            ListSerializer(AppNotification.serializer()),
        )

    /** Nombre de notifications non lues (pour le badge). */
    suspend fun unreadCount(): Int =
        api.request(Endpoint.get(NotificationEndpoints.UNREAD_COUNT), UnreadCount.serializer()).count

    /** Marque une notification comme lue. */
    suspend fun markRead(id: String) {
        api.request<Unit>(Endpoint.post(NotificationEndpoints.read(id)))
    }

    /** Marque toutes les notifications comme lues. */
    suspend fun markAllRead() {
        api.request<Unit>(Endpoint.post(NotificationEndpoints.READ_ALL))
    }
}

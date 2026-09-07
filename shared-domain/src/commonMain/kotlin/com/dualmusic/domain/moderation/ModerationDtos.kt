package com.dualmusic.domain.moderation

import com.dualmusic.domain.model.DisplayProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modération par évènement — hôte principal (artiste pour live/concert, manager pour
 * duel/compétition) + jusqu'à 2 spectateurs qu'il désigne pour l'accompagner (ban, masquer un
 * message — jamais le pouvoir d'activer/désactiver le chat, réservé à l'hôte). Miroir de
 * `moderation.service.js` côté backend (`GET/POST/DELETE /moderation/events/:type/:id/...`).
 */

/** Un modérateur désigné, hydraté avec son profil d'affichage. */
@Serializable
data class EventModerator(
    val id: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("appointed_by") val appointedBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    val user: DisplayProfile? = null,
) {
    val displayName: String get() = user?.displayName ?: userId.take(8)
}

/** Nombre maximum de modérateurs désignés par évènement (miroir du backend). */
const val MAX_EVENT_MODERATORS = 2

/** Chemins REST de la modération par évènement. */
object ModerationEndpoints {
    /** Spectateurs actuellement connectés à la room (hôte uniquement) — vivier du picker. */
    fun viewers(type: String, id: String) = "/moderation/events/$type/$id/viewers"
    /** Modérateurs désignés de cet évènement. */
    fun moderators(type: String, id: String) = "/moderation/events/$type/$id/moderators"
    /** Révoque un modérateur désigné (hôte uniquement). */
    fun revokeModerator(type: String, id: String, userId: String) = "/moderation/events/$type/$id/moderators/$userId"
}

/** Corps de `POST /moderation/events/:type/:id/moderators`. */
@Serializable
data class AppointModeratorBody(val userId: String)

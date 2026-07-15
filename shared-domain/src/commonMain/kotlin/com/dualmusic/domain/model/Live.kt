package com.dualmusic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Live spontané d'un artiste (élément du feed vertical).
 *
 * Aligné sur `artist_lives` + l'`artist` hydraté par le backend. `roomId` est le nom de
 * room LiveKit à rejoindre ; `id` sert au chat/cadeaux (contexte `live:id`).
 */
@Serializable
data class Live(
    val id: String,
    val title: String? = null,
    val status: EventStatus = EventStatus.LIVE,
    @SerialName("artist_id") val artistId: String,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("recording_url") val recordingUrl: String? = null,
    /** Profil d'affichage du host (nom, avatar, nom de scène). */
    val artist: DisplayProfile? = null,
) {
    /** Room LiveKit effective (repli sur `live:id` si `roomId` absent). */
    val liveKitRoom: String get() = roomId ?: "live:$id"
}

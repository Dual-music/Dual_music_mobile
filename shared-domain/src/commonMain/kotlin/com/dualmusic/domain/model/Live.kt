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
    /**
     * Room LiveKit effective. DOIT correspondre exactement à la convention du web
     * (`live-<id>`, cf. LiveStream.tsx) pour que web et mobile publient/regardent la
     * MÊME room. Le backend laisse `room_id` à null → on dérive toujours `live-<id>`.
     */
    val liveKitRoom: String get() = "live-$id"
}

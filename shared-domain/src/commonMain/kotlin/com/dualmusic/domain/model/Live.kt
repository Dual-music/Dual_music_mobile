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
    /** Dédicaces activées pour CE live (défaut true côté backend). */
    @SerialName("allows_dedications") val allowsDedications: Boolean = true,
    /** Prix minimum propre à CE live (crédits) — `null` = utilise le défaut global de la plateforme. */
    @SerialName("dedication_min_price_credits") val dedicationMinPriceCredits: Double? = null,
    /** Demandes d'invité (« lever la main ») activées pour CE live (défaut true côté backend). */
    @SerialName("allow_guests") val allowGuests: Boolean = true,
    /** L'artiste (hôte) a-t-il activé le chat pour ce live (défaut true côté backend). */
    @SerialName("chat_enabled") @Serializable(with = com.dualmusic.domain.serialization.FlexibleBoolSerializer::class) val chatEnabled: Boolean = true,
) {
    /**
     * Room LiveKit effective. DOIT correspondre exactement à la convention du web
     * (`live-<id>`, cf. LiveStream.tsx) pour que web et mobile publient/regardent la
     * MÊME room. Le backend laisse `room_id` à null → on dérive toujours `live-<id>`.
     */
    val liveKitRoom: String get() = "live-$id"
}

package com.dualmusic.domain.replay

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rediffusion (replay) d'un duel/concert/compétition (`replay_videos`).
 *
 * @property replayPrice prix de déblocage en crédits (0 = gratuit).
 * @property isPremium vrai si l'accès requiert un déblocage payant.
 * @property videoUrl URL de la vidéo (HLS/MP4) — lue par ExoPlayer une fois l'accès obtenu.
 */
@Serializable
data class ReplayVideo(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("replay_price") val replayPrice: Double = 0.0,
    @SerialName("is_premium") val isPremium: Boolean = false,
    val duration: Int? = null,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("source_type") val sourceType: String? = null,
) {
    /** Vrai si un déblocage payant est nécessaire (premium + prix > 0). */
    val requiresUnlock: Boolean get() = isPremium && replayPrice > 0
}

/** Réponse de `GET /replays/:id/access` — le caller a-t-il accès à la vidéo. */
@Serializable
data class ReplayAccess(
    val hasAccess: Boolean = false,
)

/** Corps de `POST /wallet/replays/unlock` — déblocage payant d'un replay. */
@Serializable
data class UnlockReplayRequest(val replayId: String)

/** Chemins REST des replays (source unique, partagée). */
object ReplayEndpoints {
    const val LIST = "/replays"
    /** Déblocage : opération de portefeuille (débit atomique). */
    const val UNLOCK = "/wallet/replays/unlock"
    fun detail(id: String) = "/replays/$id"
    fun access(id: String) = "/replays/$id/access"
    fun views(id: String) = "/replays/$id/views"
    fun likes(id: String) = "/replays/$id/likes"
}

package com.dualmusic.domain.creator

import com.dualmusic.domain.model.DisplayProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs des **profils publics créateurs** (artiste / manager) — alignés sur le backend
 * (`PATCH /artists/me`, `GET/PATCH /managers/me`, lecture via `GET /users/:id`).
 *
 * Les liens sociaux sont stockés dans une colonne JSON libre `social_links` (clés non
 * contraintes côté serveur) ; on partage ici la liste **canonique** des plateformes
 * ([SocialPlatform]) pour garder web et mobile cohérents. Les endpoints vivent dans
 * [CreatorEndpoints].
 */

/** Profil public d'un artiste (colonnes de `artist_profiles`). */
@Serializable
data class ArtistProfile(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("social_links") val socialLinks: Map<String, String> = emptyMap(),
)

/** Profil public d'un manager (colonnes de `manager_profiles`). */
@Serializable
data class ManagerProfile(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val experience: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("social_links") val socialLinks: Map<String, String> = emptyMap(),
)

/** Réponse de `GET /users/:id` — profil de compte + profil artiste éventuel + suivi. */
@Serializable
data class PublicProfileResponse(
    val profile: DisplayProfile? = null,
    val artistProfile: ArtistProfile? = null,
    val followerCount: Int = 0,
    val isFollowing: Boolean = false,
)

/**
 * Corps de `PATCH /artists/me` — n'envoyer que les champs modifiés (le backend exige ≥ 1
 * champ ; les `null` sont omis grâce à `explicitNulls = false`).
 */
@Serializable
data class UpdateArtistProfileRequest(
    @SerialName("stage_name") val stageName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = null,
    @SerialName("social_links") val socialLinks: Map<String, String>? = null,
)

/** Corps de `PATCH /managers/me`. */
@Serializable
data class UpdateManagerProfileRequest(
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val experience: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean? = null,
    @SerialName("social_links") val socialLinks: Map<String, String>? = null,
)

/**
 * Plateformes sociales canoniques éditables sur le profil public.
 *
 * `key` = clé stockée dans `social_links` (identique au web), `label` = libellé affiché,
 * `hint` = exemple d'URL complète (on privilégie des **URL complètes** pour que les liens
 * soient cliquables partout). L'artiste expose Spotify en plus ; le manager non.
 */
enum class SocialPlatform(val key: String, val label: String, val hint: String) {
    INSTAGRAM("instagram", "Instagram", "https://instagram.com/…"),
    TIKTOK("tiktok", "TikTok", "https://tiktok.com/@…"),
    YOUTUBE("youtube", "YouTube", "https://youtube.com/@…"),
    TWITTER("twitter", "X (Twitter)", "https://x.com/…"),
    FACEBOOK("facebook", "Facebook", "https://facebook.com/…"),
    SPOTIFY("spotify", "Spotify", "https://open.spotify.com/artist/…");

    companion object {
        /** Plateformes proposées à un artiste (6). */
        val ARTIST: List<SocialPlatform> = listOf(INSTAGRAM, TIKTOK, YOUTUBE, TWITTER, FACEBOOK, SPOTIFY)

        /** Plateformes proposées à un manager (5 — pas de Spotify). */
        val MANAGER: List<SocialPlatform> = listOf(INSTAGRAM, TWITTER, FACEBOOK, YOUTUBE, TIKTOK)
    }
}

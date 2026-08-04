package com.dualmusic.domain.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Vidéo lifestyle d'un artiste (`lifestyle_videos`). */
@Serializable
data class LifestyleVideo(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    /** Durée préformatée (ex. `0:11`). */
    val duration: String? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("views_count") val viewsCount: Int = 0,
)

/**
 * Corps de `POST /lifestyle` — publication d'une vidéo lifestyle (camelCase, Joi strict).
 * `duration` est une chaîne libre (ex. `1:23`), requise côté backend.
 */
@Serializable
data class CreateLifestyleRequest(
    val artistName: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val duration: String,
)

/** Article de blog (`blogs`). */
@Serializable
data class BlogPost(
    val id: String,
    val title: String? = null,
    val excerpt: String? = null,
    /** Contenu complet (HTML/Markdown) — chargé au détail. */
    val content: String? = null,
    val category: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Chemins REST du contenu (source unique, partagée). */
object ContentEndpoints {
    const val LIFESTYLE = "/lifestyle"
    const val BLOGS = "/blogs"
    fun lifestyleViews(id: String) = "/lifestyle/$id/views"
    fun lifestyleLikes(id: String) = "/lifestyle/$id/likes"
    fun blog(id: String) = "/blogs/$id"
    fun blogViews(id: String) = "/blogs/$id/views"
}

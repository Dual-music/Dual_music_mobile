package com.dualmusic.feature.content

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.content.BlogPost
import com.dualmusic.domain.content.ContentEndpoints
import com.dualmusic.domain.content.LifestyleVideo
import kotlinx.serialization.builtins.ListSerializer

/**
 * Accès REST au contenu : vidéos lifestyle + articles de blog.
 * Lecture + interactions légères (like, vue).
 */
class ContentRepository(private val api: ApiClient) {

    /** Vidéos lifestyle (les plus récentes). */
    suspend fun lifestyle(limit: Int = 50): List<LifestyleVideo> =
        api.request(
            Endpoint.get(ContentEndpoints.LIFESTYLE, query = mapOf("limit" to limit.toString())),
            ListSerializer(LifestyleVideo.serializer()),
        )

    /** Articles de blog publiés. */
    suspend fun blogs(limit: Int = 50): List<BlogPost> =
        api.request(
            Endpoint.get(ContentEndpoints.BLOGS, query = mapOf("limit" to limit.toString())),
            ListSerializer(BlogPost.serializer()),
        )

    /** Détail d'un article (contenu complet). */
    suspend fun blog(id: String): BlogPost =
        api.request(Endpoint.get(ContentEndpoints.blog(id)), BlogPost.serializer())

    /** Ids des vidéos déjà likées par le caller (`GET /lifestyle/liked/mine`). */
    suspend fun likedMine(): List<String> =
        runCatching {
            api.request(
                Endpoint.get("/lifestyle/liked/mine"),
                ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
            )
        }.getOrDefault(emptyList())

    /** Bascule le like d'une vidéo (best-effort). */
    suspend fun toggleLike(videoId: String) {
        api.request<Unit>(Endpoint.post(ContentEndpoints.lifestyleLikes(videoId)))
    }

    /** Enregistre une vue de vidéo (best-effort). */
    suspend fun registerVideoView(videoId: String) {
        api.request<Unit>(Endpoint.post(ContentEndpoints.lifestyleViews(videoId)))
    }
}

package com.dualmusic.feature.feed

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.model.Live
import kotlinx.serialization.builtins.ListSerializer

/** Page de feed : items + curseur suivant. */
data class FeedPage(val items: List<Live>, val nextCursor: String?)

/**
 * Charge le feed des lives (`GET /lives`).
 *
 * NB : le curseur de pagination est dans `meta.pagination.nextCursor` de l'enveloppe. Le
 * client actuel expose `data` ; pour un keyset complet, exposer aussi `meta` (amélioration
 * ciblée). Ici on pagine par `page` incrémentale en repli.
 */
class FeedRepository(private val api: ApiClient) {

    /** Récupère une page de lives actifs (les plus récents d'abord). */
    suspend fun lives(page: Int = 1): List<Live> =
        api.request(
            Endpoint.get("/lives", query = mapOf("status" to "live", "limit" to "10", "page" to page.toString())),
            ListSerializer(Live.serializer()),
        )
}

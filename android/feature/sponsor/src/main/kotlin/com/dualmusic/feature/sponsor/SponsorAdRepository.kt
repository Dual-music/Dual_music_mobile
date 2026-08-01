package com.dualmusic.feature.sponsor

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.realtime.SponsorAdPlay
import com.dualmusic.domain.realtime.SponsorAdVideo
import kotlinx.serialization.builtins.ListSerializer

/**
 * Accès REST à la **diffusion de pubs sponsor** pendant un événement (parité web/backend).
 *
 * - `GET /sponsors/ads` : pubs actives éligibles pour cet événement.
 * - `POST /sponsors/ads/play` : lance une diffusion (le backend émet `sponsor:ad` à la room).
 * - `POST /sponsors/ads/plays/:id/stop` : arrête la diffusion (émet `sponsor:ad` stop).
 *
 * L'autorisation (seul l'hôte/manager/admin peut lancer) est **imposée côté serveur**.
 *
 * @param api client HTTP authentifié.
 */
class SponsorAdRepository(private val api: ApiClient) {

    /** Pubs actives éligibles pour l'événement `eventType:eventId`. */
    suspend fun listAds(eventType: String, eventId: String): List<SponsorAdVideo> =
        api.request(
            Endpoint.get("/sponsors/ads", query = mapOf("eventType" to eventType, "eventId" to eventId)),
            ListSerializer(SponsorAdVideo.serializer()),
        )

    /** Lance la diffusion d'une pub. Renvoie l'enregistrement de diffusion (pour l'arrêter). */
    suspend fun play(eventType: String, eventId: String, adVideoId: String): SponsorAdPlay {
        val body = """{"eventType":"$eventType","eventId":"$eventId","adVideoId":"$adVideoId"}"""
        return api.request(Endpoint.post("/sponsors/ads/play", body), SponsorAdPlay.serializer())
    }

    /** Arrête une diffusion en cours. */
    suspend fun stop(playId: String) {
        api.request<Unit>(Endpoint.post("/sponsors/ads/plays/$playId/stop", "{}"))
    }
}

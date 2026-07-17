package com.dualmusic.feature.concert

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.concert.ConcertEndpoints
import com.dualmusic.domain.concert.ConcertTicketInfo
import com.dualmusic.domain.concert.DedicationRequest
import com.dualmusic.domain.model.Concert
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès REST aux concerts d'artistes.
 *
 * Lectures (catalogue, détail, billetterie) et achat de dédicace. L'achat du **billet**
 * est un débit du portefeuille (`POST /wallet/tickets/concert`) — voir `feature:wallet`.
 */
class ConcertRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Catalogue public des concerts d'artistes (approuvés). */
    suspend fun concerts(limit: Int = 50): List<Concert> =
        api.request(
            Endpoint.get(ConcertEndpoints.ARTIST_LIST, query = mapOf("limit" to limit.toString())),
            ListSerializer(Concert.serializer()),
        )

    /** Détail d'un concert. */
    suspend fun concert(id: String): Concert =
        api.request(Endpoint.get(ConcertEndpoints.artistDetail(id)), Concert.serializer())

    /** Billetterie : prix, places restantes, et si le caller a déjà son billet. */
    suspend fun ticketInfo(id: String): ConcertTicketInfo =
        api.request(Endpoint.get(ConcertEndpoints.ticketInfo(id)), ConcertTicketInfo.serializer())

    /**
     * Achète une dédicace pour un concert (débit atomique + idempotent).
     * @param message texte lu par l'artiste pendant le concert.
     */
    suspend fun purchaseDedication(
        concertId: String,
        message: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            DedicationRequest.serializer(),
            DedicationRequest(concertId = concertId, message = message),
        )
        api.request<Unit>(
            Endpoint.post(ConcertEndpoints.DEDICATIONS_PURCHASE, body, idempotencyKey = idempotencyKey),
        )
    }
}

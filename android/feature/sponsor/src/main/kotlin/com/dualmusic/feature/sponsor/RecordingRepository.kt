package com.dualmusic.feature.sponsor

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.recording.RecordingEndpoints
import com.dualmusic.domain.recording.RecordingStatus

/**
 * Pilotage de l'enregistrement serveur (LiveKit Egress) d'un direct.
 *
 * Colocalisé dans le module partagé des contrôles live (comme SponsorAd*), car utilisé par
 * toutes les rooms (live/duel/concert/compétition). Le mode par type est choisi par l'admin
 * (`recording_config`) ; l'autorisation est imposée côté serveur.
 *
 * @param api client HTTP authentifié.
 */
class RecordingRepository(private val api: ApiClient) {

    /** État : mode admin (off/auto/manual) + enregistrement en cours. */
    suspend fun status(sourceType: String, sourceId: String): RecordingStatus =
        api.request(
            Endpoint.get(
                RecordingEndpoints.STATUS,
                query = mapOf("sourceType" to sourceType, "sourceId" to sourceId),
            ),
            RecordingStatus.serializer(),
        )

    /** L'hôte/manager lance l'enregistrement (mode manual). */
    suspend fun start(sourceType: String, sourceId: String): RecordingStatus =
        api.request(Endpoint.post(RecordingEndpoints.START, body(sourceType, sourceId)), RecordingStatus.serializer())

    /** Met en pause (arrête le segment en cours ; l'egress LiveKit n'a pas de vraie pause). */
    suspend fun pause(sourceType: String, sourceId: String): RecordingStatus =
        api.request(Endpoint.post(RecordingEndpoints.PAUSE, body(sourceType, sourceId)), RecordingStatus.serializer())

    /** Reprend un enregistrement en pause (nouveau segment sous la même session). */
    suspend fun resume(sourceType: String, sourceId: String): RecordingStatus =
        api.request(Endpoint.post(RecordingEndpoints.RESUME, body(sourceType, sourceId)), RecordingStatus.serializer())

    /** Annule tout l'enregistrement en cours — aucun replay n'est créé. */
    suspend fun cancel(sourceType: String, sourceId: String): RecordingStatus =
        api.request(Endpoint.post(RecordingEndpoints.CANCEL, body(sourceType, sourceId)), RecordingStatus.serializer())

    /** L'hôte/manager sauvegarde l'enregistrement (les segments sont recollés en un seul fichier). */
    suspend fun stop(sourceType: String, sourceId: String): RecordingStatus =
        api.request(Endpoint.post(RecordingEndpoints.STOP, body(sourceType, sourceId)), RecordingStatus.serializer())

    private fun body(sourceType: String, sourceId: String) =
        """{"sourceType":"$sourceType","sourceId":"$sourceId"}"""
}

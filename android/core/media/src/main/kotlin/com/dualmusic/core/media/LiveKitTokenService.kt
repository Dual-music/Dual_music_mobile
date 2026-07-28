package com.dualmusic.core.media

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.media.LiveKitToken
import com.dualmusic.domain.media.LiveKitTokenRequest
import com.dualmusic.domain.media.MediaEndpoints
import kotlinx.serialization.json.Json

/**
 * Récupère les jetons LiveKit via `POST /livekit/token`.
 *
 * Le backend est la seule autorité qui émet des jetons signés (grants selon le rôle) :
 * l'app ne fabrique jamais de jeton localement.
 */
class LiveKitTokenService(private val api: ApiClient) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Demande un jeton pour rejoindre une room.
     * @param roomName nom de room fourni par l'API.
     * @param isHost vrai pour l'artiste/host (droits de publication).
     */
    suspend fun token(roomName: String, isHost: Boolean = false, canPublish: Boolean = false): LiveKitToken {
        val body = json.encodeToString(
            LiveKitTokenRequest.serializer(),
            // canPublish omis (défaut false, encodeDefaults=false) → le backend applique isHost ;
            // envoyé à true pour un invité accepté (publication sans droits d'admin).
            LiveKitTokenRequest(roomName = roomName, isHost = isHost, canPublish = canPublish),
        )
        return api.request(Endpoint.post(MediaEndpoints.LIVEKIT_TOKEN, body))
    }
}

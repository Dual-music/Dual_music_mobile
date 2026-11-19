package com.dualmusic.core.media

import android.content.Context
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.media.LiveKitToken
import com.dualmusic.domain.media.LiveKitTokenRequest
import com.dualmusic.domain.media.MediaEndpoints
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Récupère les jetons LiveKit via `POST /livekit/token`.
 *
 * Le backend est la seule autorité qui émet des jetons signés (grants selon le rôle) :
 * l'app ne fabrique jamais de jeton localement.
 *
 * @param deviceId identifiant stable de cet appareil (voir [DeviceIdentity]) : rend l'identité
 *   LiveKit unique par appareil, pour que le même compte sur mobile + web ne s'éjecte plus.
 */
class LiveKitTokenService(private val api: ApiClient, private val deviceId: String? = null) {

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
            LiveKitTokenRequest(roomName = roomName, isHost = isHost, canPublish = canPublish, deviceId = deviceId),
        )
        return api.request(Endpoint.post(MediaEndpoints.LIVEKIT_TOKEN, body))
    }
}

/** Identifiant d'installation stable (UUID généré une fois, conservé dans les préférences). */
object DeviceIdentity {
    private const val PREFS = "dm_device"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}

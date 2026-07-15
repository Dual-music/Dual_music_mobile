package com.dualmusic.domain.media

import kotlinx.serialization.Serializable

/*
 * DTOs LiveKit — partagés iOS/Android, alignés sur `POST /livekit/token` du backend.
 * Le pipeline vidéo lui-même (décodage HW, rendu) reste 100 % natif par plateforme.
 */

/** Corps de `POST /livekit/token`. */
@Serializable
data class LiveKitTokenRequest(
    val roomName: String,
    val isHost: Boolean = false,
    val participantName: String? = null,
    val canPublish: Boolean = false,
)

/** Réponse : jeton d'accès à la room + URL du SFU + identité du participant. */
@Serializable
data class LiveKitToken(
    val token: String,
    val url: String,
    val identity: String,
)

/** Chemin REST du service de jetons LiveKit. */
object MediaEndpoints {
    const val LIVEKIT_TOKEN = "/livekit/token"
}

package com.dualmusic.core.media

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État de connexion simplifié pour l'UI. */
sealed interface LiveConnectionState {
    data object Idle : LiveConnectionState
    data object Connecting : LiveConnectionState
    data object Connected : LiveConnectionState
    data object Reconnecting : LiveConnectionState
    data class Failed(val message: String) : LiveConnectionState
}

/**
 * Client d'une room live LiveKit (viewer) — Android.
 *
 * Enveloppe le `Room` LiveKit : connexion via jeton backend, souscription automatique, et
 * exposition de la **piste vidéo primaire** (le host) en [StateFlow] pour un rendu Compose
 * (`io.livekit.android.compose` dans la feature). Décodage matériel (MediaCodec) géré par
 * le SDK.
 *
 * @param scope portée coroutine (viewModelScope) où sont collectés les événements de room.
 */
class LiveRoomClient(
    context: Context,
    private val tokenService: LiveKitTokenService,
    private val scope: CoroutineScope,
) {
    /** Room LiveKit sous-jacente (exposée pour le rendu Compose / diagnostics). */
    val room: Room = LiveKit.create(context.applicationContext)

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Idle)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _primaryVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val primaryVideoTrack: StateFlow<VideoTrack?> = _primaryVideoTrack.asStateFlow()

    /**
     * Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
     * connecte au SFU.
     * @param roomName nom de room fourni par l'API.
     * @param isHost vrai pour l'artiste (publication), faux pour un viewer.
     * @param prewarmedToken jeton déjà obtenu par le feed (prefetch) — évite un aller-retour
     *   réseau au scroll, réduisant la latence d'entrée-live.
     */
    suspend fun join(
        roomName: String,
        isHost: Boolean = false,
        prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
    ) {
        _connectionState.value = LiveConnectionState.Connecting
        try {
            // Collecte des événements de room pour rafraîchir l'état + la piste primaire.
            scope.launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed,
                        is RoomEvent.TrackUnsubscribed,
                        is RoomEvent.ParticipantDisconnected -> refreshPrimaryTrack()
                        is RoomEvent.Reconnecting -> _connectionState.value = LiveConnectionState.Reconnecting
                        is RoomEvent.Reconnected -> _connectionState.value = LiveConnectionState.Connected
                        is RoomEvent.Disconnected -> _connectionState.value = LiveConnectionState.Idle
                        else -> Unit
                    }
                }
            }

            val creds = prewarmedToken ?: tokenService.token(roomName = roomName, isHost = isHost)
            room.connect(creds.url, creds.token)
            _connectionState.value = LiveConnectionState.Connected
            refreshPrimaryTrack()
        } catch (e: Throwable) {
            _connectionState.value = LiveConnectionState.Failed(e.message ?: "Connexion impossible")
        }
    }

    /** Quitte la room (arrière-plan / scroll hors du live). */
    fun leave() {
        room.disconnect()
        _primaryVideoTrack.value = null
        _connectionState.value = LiveConnectionState.Idle
    }

    /** Piste vidéo primaire = première piste vidéo distante souscrite (le host). */
    private fun refreshPrimaryTrack() {
        _primaryVideoTrack.value = room.remoteParticipants.values
            .flatMap { it.videoTrackPublications }
            .mapNotNull { it.second as? VideoTrack }
            .firstOrNull()
    }
}

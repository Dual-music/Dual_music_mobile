package com.dualmusic.core.media

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.lifecycle.ProcessLifecycleOwner
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.video.CameraCapturerUtils
import io.livekit.android.track.processing.video.VirtualBackgroundVideoProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.CameraXHelper
import livekit.org.webrtc.EglBase

/** État de connexion simplifié pour l'UI. */
sealed interface LiveConnectionState {
    data object Idle : LiveConnectionState
    data object Connecting : LiveConnectionState
    data object Connected : LiveConnectionState
    data object Reconnecting : LiveConnectionState
    data class Failed(val message: String) : LiveConnectionState
}

/**
 * Client d'une room live LiveKit — Android.
 *
 * Enveloppe le `Room` LiveKit : connexion via jeton backend, souscription automatique, et
 * exposition des pistes vidéo (host + invités) en [StateFlow] pour un rendu Compose.
 *
 * Mode hôte : publie la caméra via un `LocalVideoTrack` auquel est attaché un
 * [VirtualBackgroundVideoProcessor] (flou d'arrière-plan togglable, passthrough sinon).
 *
 * @param scope portée coroutine (viewModelScope) où sont collectés les événements de room.
 */
class LiveRoomClient(
    context: Context,
    private val tokenService: LiveKitTokenService,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val eglBase: EglBase = EglBase.create()

    /** Room LiveKit sous-jacente (créée avec notre EglBase pour le processor vidéo). */
    val room: Room = LiveKit.create(appContext, overrides = LiveKitOverrides(eglBase = eglBase))

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Idle)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _primaryVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val primaryVideoTrack: StateFlow<VideoTrack?> = _primaryVideoTrack.asStateFlow()

    /** TOUTES les pistes vidéo distantes souscrites (hôte + invités sur scène). */
    private val _remoteVideos = MutableStateFlow<List<VideoTrack>>(emptyList())
    val remoteVideos: StateFlow<List<VideoTrack>> = _remoteVideos.asStateFlow()

    /** Piste vidéo LOCALE (aperçu de l'hôte quand il diffuse). `null` en mode viewer. */
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    /** Micro activé (mode hôte). */
    private val _micEnabled = MutableStateFlow(true)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    /** Caméra activée (mode hôte). */
    private val _camEnabled = MutableStateFlow(true)
    val camEnabled: StateFlow<Boolean> = _camEnabled.asStateFlow()

    /** Flou d'arrière-plan actif (mode hôte). */
    private val _blurEnabled = MutableStateFlow(false)
    val blurEnabled: StateFlow<Boolean> = _blurEnabled.asStateFlow()

    // --- Traitement vidéo (flou d'arrière-plan) ---
    private var processor: VirtualBackgroundVideoProcessor? = null
    private var cameraProvider: CameraCapturerUtils.CameraProvider? = null
    private var cameraTrack: LocalVideoTrack? = null

    /**
     * Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
     * connecte au SFU.
     */
    suspend fun join(
        roomName: String,
        isHost: Boolean = false,
        prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
        canPublish: Boolean = false,
    ) {
        _connectionState.value = LiveConnectionState.Connecting
        try {
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

            val creds = prewarmedToken ?: tokenService.token(roomName = roomName, isHost = isHost, canPublish = canPublish)
            room.connect(creds.url, creds.token)
            _connectionState.value = LiveConnectionState.Connected
            // La publication caméra/micro (hôte) est déclenchée par l'UI via [startBroadcast].
            refreshPrimaryTrack()
        } catch (e: Throwable) {
            _connectionState.value = LiveConnectionState.Failed(e.message ?: "Connexion impossible")
        }
    }

    /**
     * Prépare (une fois) le processor de flou + le provider CameraX qui l'alimente.
     * Si CameraX n'est pas supporté, on retombe sur la caméra par défaut (sans flou).
     */
    private fun ensureProcessor() {
        if (processor != null) return
        val p = VirtualBackgroundVideoProcessor(eglBase, Dispatchers.IO)
        p.enabled = false // passthrough par défaut (vidéo normale ; flou activé via toggleBlur)
        processor = p
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
            .apply { setAnalyzer(Dispatchers.IO.asExecutor(), p.imageAnalyzer) }
        val provider = CameraXHelper.createCameraProvider(ProcessLifecycleOwner.get(), arrayOf(imageAnalysis))
        if (provider.isSupported(appContext)) {
            CameraCapturerUtils.registerCameraProvider(provider)
            cameraProvider = provider
        }
    }

    /**
     * Démarre la DIFFUSION (hôte) : micro + publication d'une piste caméra dotée du
     * processor (flou désactivé au départ). Rafraîchit l'aperçu local.
     */
    suspend fun startBroadcast() {
        room.localParticipant.setMicrophoneEnabled(true)
        _micEnabled.value = true
        ensureProcessor()
        val track = room.localParticipant.createVideoTrack(
            options = LocalVideoTrackOptions(position = CameraPosition.FRONT),
            videoProcessor = processor,
        )
        track.startCapture()
        room.localParticipant.publishVideoTrack(track)
        cameraTrack = track
        _camEnabled.value = true
        var tries = 0
        while (_localVideoTrack.value == null && tries < 12) {
            refreshLocalTrack()
            if (_localVideoTrack.value != null) break
            kotlinx.coroutines.delay(150)
            tries++
        }
    }

    /** Quitte la room + libère la caméra / le processor. */
    fun leave() {
        room.disconnect()
        cameraTrack?.let { runCatching { it.stopCapture() } }
        cameraTrack = null
        cameraProvider?.let { runCatching { CameraCapturerUtils.unregisterCameraProvider(it) } }
        cameraProvider = null
        runCatching { processor?.dispose() }
        processor = null
        _primaryVideoTrack.value = null
        _remoteVideos.value = emptyList()
        _localVideoTrack.value = null
        _blurEnabled.value = false
        _connectionState.value = LiveConnectionState.Idle
    }

    /** Active/désactive le micro (mode hôte). */
    suspend fun setMicEnabled(enabled: Boolean) {
        room.localParticipant.setMicrophoneEnabled(enabled)
        _micEnabled.value = enabled
    }

    /** Active/désactive la caméra (mode hôte) : suspend/reprend la capture de la piste. */
    fun setCamEnabled(enabled: Boolean) {
        val t = cameraTrack ?: return
        if (enabled) t.startCapture() else t.stopCapture()
        _camEnabled.value = enabled
        refreshLocalTrack()
    }

    /** Bascule caméra avant/arrière (mode hôte). */
    fun switchCamera() {
        cameraTrack?.switchCamera()
    }

    /** Active/désactive le flou d'arrière-plan (sans republier la piste). */
    fun toggleBlur() {
        processor?.let {
            it.enabled = !it.enabled
            _blurEnabled.value = it.enabled
        }
    }

    /** Rafraîchit la liste des pistes distantes + la piste primaire (première = hôte). */
    private fun refreshPrimaryTrack() {
        val videos = room.remoteParticipants.values
            .flatMap { it.videoTrackPublications }
            .mapNotNull { it.second as? VideoTrack }
        _remoteVideos.value = videos
        _primaryVideoTrack.value = videos.firstOrNull()
    }

    /** Piste vidéo locale = caméra publiée par l'hôte (aperçu). */
    private fun refreshLocalTrack() {
        _localVideoTrack.value = room.localParticipant.videoTrackPublications
            .mapNotNull { it.second as? VideoTrack }
            .firstOrNull()
    }
}

package com.dualmusic.core.media

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.lifecycle.ProcessLifecycleOwner
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.DisconnectReason
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
    // EGL partagé (multi-room : duel = 3 clients). Un contexte EGL COMMUN aux 3 rooms + leurs
    // renderers est indispensable, sinon les pistes distantes sont souscrites mais NE S'AFFICHENT
    // PAS (case transparente). Null → contexte propre (room unique : concert/compétition/live).
    sharedEglBase: EglBase? = null,
) {
    private val appContext = context.applicationContext
    private val ownsEgl: Boolean = sharedEglBase == null
    private val eglBase: EglBase = sharedEglBase ?: EglBase.create()

    /**
     * Room LiveKit sous-jacente (créée avec notre EglBase pour le processor vidéo).
     * Routage audio PARTAGÉ entre toutes les rooms (voir [SharedAudioRouting]) : la fermeture
     * d'une room d'invité ne doit plus « restaurer » l'audio du téléphone pendant que les autres
     * rooms jouent encore.
     */
    val room: Room = LiveKit.create(
        appContext,
        overrides = LiveKitOverrides(
            eglBase = eglBase,
            audioOptions = AudioOptions(audioHandler = SharedAudioRouting.forRoom(appContext)),
        ),
    )

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Idle)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _primaryVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val primaryVideoTrack: StateFlow<VideoTrack?> = _primaryVideoTrack.asStateFlow()

    /** TOUTES les pistes vidéo distantes souscrites (hôte + invités sur scène). */
    private val _remoteVideos = MutableStateFlow<List<VideoTrack>>(emptyList())
    val remoteVideos: StateFlow<List<VideoTrack>> = _remoteVideos.asStateFlow()

    /**
     * Pistes distantes avec l'**identité LiveKit** du participant (= userId côté backend).
     * Permet le focus imposé synchronisé : le manager épingle une identité, tous les clients
     * mettent la piste correspondante en avant.
     */
    private val _remoteTiles = MutableStateFlow<List<Pair<String, VideoTrack>>>(emptyList())
    val remoteTiles: StateFlow<List<Pair<String, VideoTrack>>> = _remoteTiles.asStateFlow()

    /** Au moins un participant distant a le micro ouvert (piste audio publiée et non coupée) — indicateur UI. */
    private val _remoteMicOn = MutableStateFlow(false)
    val remoteMicOn: StateFlow<Boolean> = _remoteMicOn.asStateFlow()

    /** État micro de CHAQUE participant distant, par `userId` (room unique : hôte + invités). */
    private val _remoteMicByUser = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteMicByUser: StateFlow<Map<String, Boolean>> = _remoteMicByUser.asStateFlow()

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

    /** Fond du direct : `none` | `blur` | `image` (mode hôte). */
    private val _backgroundMode = MutableStateFlow("none")
    val backgroundMode: StateFlow<String> = _backgroundMode.asStateFlow()

    /** Id du filtre couleur actif (mode hôte). `none` = aucun. */
    private val _activeFilter = MutableStateFlow("none")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    // --- Traitement vidéo (filtre couleur + flou/fond d'arrière-plan) ---
    // Chaîne : caméra → colorProcessor (filtre couleur) → processor (fond virtuel) → SFU.
    private var processor: VirtualBackgroundVideoProcessor? = null
    private var colorProcessor: ColorFilterVideoProcessor? = null
    private var cameraProvider: CameraCapturerUtils.CameraProvider? = null
    private var cameraTrack: LocalVideoTrack? = null
    private var currentPosition: CameraPosition = CameraPosition.FRONT

    /**
     * Job du collecteur `room.events` — annulé avant d'en relancer un nouveau à chaque [join].
     * Sans ça, un client qui quitte puis rejoint (invité qui monte/descend de scène) empile un
     * collecteur supplémentaire à chaque appel, sans jamais libérer le précédent.
     */
    private var eventsJob: kotlinx.coroutines.Job? = null

    /** Paramètres du dernier [join] — pour rejoindre automatiquement après une coupure. */
    private data class JoinParams(val roomName: String, val isHost: Boolean, val canPublish: Boolean)
    private var lastJoin: JoinParams? = null
    /** Vrai entre [leave] et le prochain [join] : aucune reconnexion automatique. */
    @Volatile private var intentionalLeave = false
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var reconnectAttempt = 0
    /** Vrai pendant que CETTE room publie micro/caméra (service de premier plan actif). */
    private var broadcasting = false

    /**
     * Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
     * connecte au SFU. En cas d'échec ou de coupure non définitive, rejoint automatiquement
     * (backoff progressif) — avant, un `Disconnected` laissait la room morte sans recours :
     * spectateur sans son, ou invité qui n'entendait plus UN participant précis.
     */
    suspend fun join(
        roomName: String,
        isHost: Boolean = false,
        prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
        canPublish: Boolean = false,
    ) {
        intentionalLeave = false
        lastJoin = JoinParams(roomName, isHost, canPublish)
        reconnectJob?.cancel()
        _connectionState.value = if (reconnectAttempt > 0) LiveConnectionState.Reconnecting else LiveConnectionState.Connecting
        try {
            // Un seul collecteur `room.events` actif à la fois : un client qui quitte puis rejoint
            // (invité montant/descendant de scène) ne doit PAS empiler des collecteurs successifs.
            eventsJob?.cancel()
            eventsJob = scope.launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.ParticipantConnected,
                        is RoomEvent.TrackPublished,
                        is RoomEvent.TrackUnpublished,
                        is RoomEvent.TrackSubscribed,
                        is RoomEvent.TrackUnsubscribed,
                        is RoomEvent.TrackMuted,
                        is RoomEvent.TrackUnmuted,
                        is RoomEvent.ParticipantDisconnected -> refreshPrimaryTrack()
                        is RoomEvent.Reconnecting -> _connectionState.value = LiveConnectionState.Reconnecting
                        is RoomEvent.Reconnected -> _connectionState.value = LiveConnectionState.Connected
                        is RoomEvent.Disconnected -> {
                            _connectionState.value = LiveConnectionState.Idle
                            scheduleReconnect(event.reason)
                        }
                        else -> Unit
                    }
                }
            }

            val creds = prewarmedToken ?: tokenService.token(roomName = roomName, isHost = isHost, canPublish = canPublish)
            room.connect(creds.url, creds.token)
            reconnectAttempt = 0
            _connectionState.value = LiveConnectionState.Connected
            // La publication caméra/micro (hôte) est déclenchée par l'UI via [startBroadcast].
            refreshPrimaryTrack()
        } catch (e: Throwable) {
            _connectionState.value = LiveConnectionState.Failed(e.message ?: "Connexion impossible")
            // Jeton pré-chauffé périmé ou réseau instable : on retente avec un jeton frais.
            scheduleReconnect(null)
        }
    }

    /**
     * Programme une reconnexion après une coupure NON définitive. `DUPLICATE_IDENTITY` (connecté
     * ailleurs), expulsion, room fermée ou départ volontaire → on n'insiste pas.
     */
    private fun scheduleReconnect(reason: DisconnectReason?) {
        if (intentionalLeave) return
        val params = lastJoin ?: return
        if (reason != null && reason in FINAL_DISCONNECT_REASONS) {
            if (reason != DisconnectReason.CLIENT_INITIATED) {
                _connectionState.value = LiveConnectionState.Failed("Déconnecté : ${reason.name.lowercase().replace('_', ' ')}")
            }
            return
        }
        if (reconnectAttempt >= RECONNECT_DELAYS_MS.size) {
            _connectionState.value = LiveConnectionState.Failed("Connexion au direct perdue")
            return
        }
        val delayMs = RECONNECT_DELAYS_MS[reconnectAttempt]
        reconnectAttempt += 1
        _connectionState.value = LiveConnectionState.Reconnecting
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (intentionalLeave) return@launch
            val wasBroadcasting = broadcasting
            join(roomName = params.roomName, isHost = params.isHost, canPublish = params.canPublish)
            // Une déconnexion complète dépublie nos pistes : on republie si on diffusait, en
            // conservant les états micro/caméra choisis par l'utilisateur.
            if (wasBroadcasting && _connectionState.value is LiveConnectionState.Connected) {
                val mic = _micEnabled.value
                val cam = _camEnabled.value
                cameraTrack?.let { runCatching { it.stopCapture() } }
                cameraTrack = null
                runCatching { startBroadcast() }
                runCatching { setMicEnabled(mic) }
                if (!cam) runCatching { setCamEnabled(false) }
            }
        }
    }

    companion object {
        /** `userId` porté par une identité LiveKit (`userId` ou `userId#deviceId`). */
        fun userIdOf(identity: String?): String? = identity?.substringBefore('#')

        private val RECONNECT_DELAYS_MS = longArrayOf(1500, 3000, 6000, 10000, 15000, 30000)
        private val FINAL_DISCONNECT_REASONS = setOf(
            DisconnectReason.CLIENT_INITIATED,
            DisconnectReason.DUPLICATE_IDENTITY,
            DisconnectReason.PARTICIPANT_REMOVED,
            DisconnectReason.ROOM_DELETED,
            DisconnectReason.ROOM_CLOSED,
            DisconnectReason.USER_REJECTED,
        )
    }

    /**
     * Prépare (une fois) le processor de flou + le provider CameraX qui l'alimente.
     * Si CameraX n'est pas supporté, on retombe sur la caméra par défaut (sans flou).
     */
    private fun ensureProcessor() {
        if (processor != null) return
        val p = VirtualBackgroundVideoProcessor(eglBase, Dispatchers.IO)
        p.enabled = false // passthrough par défaut (vidéo normale ; fond activé via setBackground*)
        processor = p
        // Filtre couleur en tête de chaîne, alimente le fond virtuel (les deux combinables).
        val cp = ColorFilterVideoProcessor(eglBase)
        cp.childVideoProcessor = p
        colorProcessor = cp
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
        // Service de premier plan « micro/caméra » : sans lui, Android coupe le micro dès que
        // l'app passe en arrière-plan (l'orateur entend tout le monde, personne ne l'entend).
        if (!broadcasting) {
            broadcasting = true
            LiveBroadcastService.acquire(appContext)
        }
        room.localParticipant.setMicrophoneEnabled(true)
        _micEnabled.value = true
        ensureProcessor()
        val track = room.localParticipant.createVideoTrack(
            options = LocalVideoTrackOptions(position = CameraPosition.FRONT),
            videoProcessor = colorProcessor ?: processor,
        )
        track.startCapture()
        room.localParticipant.publishVideoTrack(track)
        cameraTrack = track
        currentPosition = CameraPosition.FRONT
        _camEnabled.value = true
        var tries = 0
        while (_localVideoTrack.value == null && tries < 12) {
            refreshLocalTrack()
            if (_localVideoTrack.value != null) break
            kotlinx.coroutines.delay(150)
            tries++
        }
    }

    /** Vrai si le jeton courant (ou une mise à jour à chaud du SFU) autorise la publication. */
    fun hasPublishPermission(): Boolean = room.localParticipant.permissions?.canPublish == true

    /**
     * Arrête de PUBLIER (micro + caméra) SANS quitter la room : un invité qui descend de scène
     * (ou est retiré) redevient simple spectateur dans la même room unique et continue de
     * voir/entendre tout le monde. Libère la caméra et le service de premier plan.
     */
    suspend fun stopBroadcast() {
        runCatching { room.localParticipant.setMicrophoneEnabled(false) }
        _micEnabled.value = false
        cameraTrack?.let { t ->
            runCatching { room.localParticipant.unpublishTrack(t) }
            runCatching { t.stopCapture() }
        }
        cameraTrack = null
        _localVideoTrack.value = null
        _camEnabled.value = false
        if (broadcasting) {
            broadcasting = false
            LiveBroadcastService.release(appContext)
        }
    }

    /** Quitte la room + libère la caméra / le processor. Aucune reconnexion automatique ensuite. */
    fun leave() {
        intentionalLeave = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        if (broadcasting) {
            broadcasting = false
            LiveBroadcastService.release(appContext)
        }
        room.disconnect()
        cameraTrack?.let { runCatching { it.stopCapture() } }
        cameraTrack = null
        cameraProvider?.let { runCatching { CameraCapturerUtils.unregisterCameraProvider(it) } }
        cameraProvider = null
        runCatching { colorProcessor?.dispose() }
        colorProcessor = null
        runCatching { processor?.dispose() }
        processor = null
        _primaryVideoTrack.value = null
        _remoteVideos.value = emptyList()
        _remoteTiles.value = emptyList()
        _localVideoTrack.value = null
        _blurEnabled.value = false
        _connectionState.value = LiveConnectionState.Idle
    }

    /** Active/désactive le micro (mode hôte). */
    suspend fun setMicEnabled(enabled: Boolean) {
        room.localParticipant.setMicrophoneEnabled(enabled)
        _micEnabled.value = enabled
    }

    /**
     * Active/désactive la caméra (mode hôte).
     *
     * On **dépublie** la piste quand la caméra est coupée (au lieu d'un simple `stopCapture` qui
     * laisse la dernière image FIGÉE chez les spectateurs) : les autres ne reçoivent alors plus
     * rien → la case redevient « sans caméra ». On republie à la réactivation. Ma preview locale
     * est vidée/rétablie immédiatement pour ne jamais montrer une image gelée.
     */
    fun setCamEnabled(enabled: Boolean) {
        val t = cameraTrack ?: return
        // `setCameraEnabled` = mute/unmute LiveKit de la caméra (comme `setMicrophoneEnabled` pour
        // le micro) : SIGNALE l'état aux autres (TrackMuted/Unmuted) ET reprend proprement les frames
        // à la réactivation (fini l'écran noir de l'ancien cycle unpublish/stopCapture). La piste
        // reste publiée ; les distants excluent les pistes mutées → case placeholder, pas figée.
        scope.launch { runCatching { room.localParticipant.setCameraEnabled(enabled) } }
        _camEnabled.value = enabled
        _localVideoTrack.value = if (enabled) t else null
    }

    /**
     * Bascule caméra avant/arrière (mode hôte).
     *
     * On NE passe PAS par `LocalVideoTrack.switchCamera()` : avec un provider CameraX enregistré
     * (pour le flou), son chemin interne peut lever une exception ASYNCHRONE (thread capturer)
     * qui échappe au runCatching de l'appelant et fait crasher l'app. On recrée proprement la
     * piste avec la position opposée (même processor), ce qui est robuste.
     */
    suspend fun switchCamera() {
        val old = cameraTrack ?: return
        val newPos = if (currentPosition == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT
        runCatching { room.localParticipant.unpublishTrack(old) }
        runCatching { old.stopCapture() }
        val newTrack = room.localParticipant.createVideoTrack(
            options = LocalVideoTrackOptions(position = newPos),
            videoProcessor = colorProcessor ?: processor,
        )
        runCatching { newTrack.startCapture() }
        room.localParticipant.publishVideoTrack(newTrack)
        cameraTrack = newTrack
        currentPosition = newPos
        _camEnabled.value = true
        // On pose DIRECTEMENT la nouvelle piste (au lieu de refreshLocalTrack qui lit les
        // publications de façon asynchrone → il récupérait null/ancienne piste juste après le
        // flip → la preview locale se fermait). Le renderer est keyé sur la piste → il se recrée.
        _localVideoTrack.value = newTrack
    }

    /** Active/désactive le flou d'arrière-plan (sans republier la piste). */
    fun toggleBlur() {
        if (_backgroundMode.value == "blur") clearBackground() else setBackgroundBlur()
    }

    /** Applique un filtre couleur (matrice `null` = aucun). Sans republier la piste. */
    fun setColorFilter(id: String, matrix: FloatArray?) {
        colorProcessor?.matrix = matrix
        _activeFilter.value = id
    }

    /** Fond : flou d'arrière-plan (segmentation ML). */
    fun setBackgroundBlur() {
        processor?.let {
            it.backgroundImage = null
            it.enabled = true
        }
        _backgroundMode.value = "blur"
        _blurEnabled.value = true
    }

    /** Fond : remplace l'arrière-plan par une image (segmentation ML). */
    fun setBackgroundImage(bitmap: android.graphics.Bitmap) {
        processor?.let {
            it.backgroundImage = bitmap
            it.enabled = true
        }
        _backgroundMode.value = "image"
        _blurEnabled.value = false
    }

    /** Fond : aucun (vidéo normale). */
    fun clearBackground() {
        processor?.let {
            it.enabled = false
            it.backgroundImage = null
        }
        _backgroundMode.value = "none"
        _blurEnabled.value = false
    }

    /**
     * Identité locale ramenée au `userId` — pour épingler sa propre caméra (focus imposé).
     * L'identité LiveKit brute est `userId#deviceId` (voir `LiveKitTokenRequest.deviceId`) ; on
     * expose partout le `userId` seul pour que les comparaisons avec les ids métier (candidats,
     * bannis, focus imposé) restent valables.
     */
    fun localIdentity(): String? = userIdOf(room.localParticipant.identity?.value)

    /** Rafraîchit la liste des pistes distantes (+ userId) + la piste primaire (première = hôte). */
    private fun refreshPrimaryTrack() {
        val tiles = room.remoteParticipants.values.flatMap { p ->
            val id = userIdOf(p.identity?.value) ?: ""
            // On EXCLUT les pistes vidéo MUTÉES (caméra coupée) → la case redevient placeholder au
            // lieu de figer la dernière image. TrackMuted/Unmuted déclenche ce refresh.
            p.videoTrackPublications
                .filter { !it.first.muted }
                .mapNotNull { pub -> (pub.second as? VideoTrack)?.let { id to it } }
        }
        _remoteTiles.value = tiles
        val videos = tiles.map { it.second }
        _remoteVideos.value = videos
        _primaryVideoTrack.value = videos.firstOrNull()
        // Micro distant : AU MOINS un participant distant publie une piste audio non coupée.
        // (Avant : `firstOrNull()` — dans une room d'invité, le « premier » distant est souvent un
        // simple spectateur sans piste → badge « muet » alors que l'invité parlait.)
        val micByUser = room.remoteParticipants.values
            .mapNotNull { p -> userIdOf(p.identity?.value)?.let { it to p.audioTrackPublications.any { pub -> !pub.first.muted } } }
            .toMap()
        _remoteMicByUser.value = micByUser
        _remoteMicOn.value = micByUser.values.any { it }
    }

    /** Piste vidéo locale = caméra publiée par l'hôte (aperçu). */
    private fun refreshLocalTrack() {
        _localVideoTrack.value = room.localParticipant.videoTrackPublications
            .mapNotNull { it.second as? VideoTrack }
            .firstOrNull()
    }
}

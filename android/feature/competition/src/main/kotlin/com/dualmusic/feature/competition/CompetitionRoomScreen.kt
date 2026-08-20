package com.dualmusic.feature.competition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.dualmusic.core.ui.celebration.WinnerCelebration
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.overlay.FloatingReactionsLayer
import com.dualmusic.core.ui.overlay.TopDonorBubble
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.feature.sponsor.SponsorAdLayer
import com.dualmusic.domain.competition.CompetitionCandidate
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la room de compétition : classement des candidats + vote payant.
 *
 * Choix d'implémentation : après un vote, on **recharge les candidats depuis le serveur**
 * plutôt que d'incrémenter localement — les tallies font foi côté backend (procédures
 * atomiques), et on évite tout optimisme sur l'argent.
 *
 * @param competitionId identifiant de la compétition.
 * @param repository lectures + débits de la compétition.
 * @param realtime client Socket.IO (écoute des changements de statut).
 */
/** Performeur courant d'une compétition : id du candidat + instant de fin du slot (ISO, pour le chrono). */
data class CompetitionPerformer(val performerId: String?, val endsAtIso: String?)

class CompetitionRoomViewModel(
    private val competitionId: String,
    private val repository: CompetitionRepository,
    private val realtime: RealtimeClient,
    val media: LiveRoomClient,
    sponsorAds: com.dualmusic.feature.sponsor.SponsorAdRepository,
    recording: com.dualmusic.feature.sponsor.RecordingRepository,
) : ViewModel() {

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle organisateur). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("competition", competitionId, sponsorAds, viewModelScope)

    /** État + action d'enregistrement serveur (bouton hôte en mode manual). */
    val recordingCtl = com.dualmusic.feature.sponsor.RecordingHolder("competition", competitionId, recording, viewModelScope)

    /** Candidats triés par score décroissant (= classement courant). */
    private val _candidates = MutableStateFlow<List<CompetitionCandidate>>(emptyList())
    val candidates: StateFlow<List<CompetitionCandidate>> = _candidates.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Compteur local de J'aime + flux de réactions flottantes (relayé à tous). */
    private val _likes = MutableStateFlow(0)
    val likes: StateFlow<Int> = _likes.asStateFlow()
    private val _emojiFeed = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val emojiFeed: StateFlow<List<Pair<Long, String>>> = _emojiFeed.asStateFlow()
    private var emojiCounter = 0L

    /** Impulsion de cadeau : incrémentée à chaque event `gift` → (re)joue le burst central. */
    private val _giftPulse = MutableStateFlow(0L)
    val giftPulse: StateFlow<Long> = _giftPulse.asStateFlow()

    /** Inventaire de cadeaux du caller (pour l'offrande à un candidat). */
    private val _inventory = MutableStateFlow<List<com.dualmusic.domain.gift.InventoryItem>>(emptyList())
    val inventory: StateFlow<List<com.dualmusic.domain.gift.InventoryItem>> = _inventory.asStateFlow()

    /** Vrai si le caller est le manager (organisateur) → contrôles en direct. */
    private val _isManager = MutableStateFlow(false)
    val isManager: StateFlow<Boolean> = _isManager.asStateFlow()

    /** Vrai si le caller peut diffuser sa caméra (manager, ou candidat approuvé en mode online). */
    private val _canPublish = MutableStateFlow(false)
    val canPublish: StateFlow<Boolean> = _canPublish.asStateFlow()

    /** Diffusion caméra/micro en cours (publieur). */
    private val _broadcasting = MutableStateFlow(false)
    val broadcasting: StateFlow<Boolean> = _broadcasting.asStateFlow()

    /** Nombre de spectateurs en direct (présence Socket.IO). */
    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    /** Chat de la compétition (parité duel/concert). */
    private val _messages = MutableStateFlow<List<CompetitionChatMessage>>(emptyList())
    val messages: StateFlow<List<CompetitionChatMessage>> = _messages.asStateFlow()

    /** Meilleur donateur courant (bulle top-donateur). Rechargé à chaque cadeau. */
    private val _topDonor = MutableStateFlow<com.dualmusic.core.ui.overlay.TopDonor?>(null)
    val topDonor: StateFlow<com.dualmusic.core.ui.overlay.TopDonor?> = _topDonor.asStateFlow()

    /** Performeur courant désigné par le manager (id candidat + fin du slot ISO) → chrono visuel. */
    private val _performer = MutableStateFlow(CompetitionPerformer(null, null))
    val performer: StateFlow<CompetitionPerformer> = _performer.asStateFlow()

    /** Caméra épinglée par le manager (identité LiveKit = userId) → focus imposé à tous. */
    private val _forcedFocusId = MutableStateFlow<String?>(null)
    val forcedFocusId: StateFlow<String?> = _forcedFocusId.asStateFlow()

    /** Billet requis pour regarder : compétition payante, ni organisateur ni candidat, sans billet. */
    private val _needsTicket = MutableStateFlow(false)
    val needsTicket: StateFlow<Boolean> = _needsTicket.asStateFlow()

    /** Prix du billet spectateur (crédits) — pour l'écran de blocage. */
    private val _ticketPrice = MutableStateFlow(0.0)
    val ticketPrice: StateFlow<Double> = _ticketPrice.asStateFlow()

    /** Room LiveKit (identique au web : `livekit_room` ou repli `comp-<id>`). */
    private var roomName: String = "comp-$competitionId"

    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /** Charge l'inventaire de cadeaux du caller. */
    fun loadInventory() {
        viewModelScope.launch {
            runCatching { repository.inventory() }.getOrNull()?.let { _inventory.value = it }
        }
    }

    /** Recharge le classement des donateurs → alimente la bulle top-donateur (parité concert/duel). */
    fun loadGiftLeaderboard() {
        viewModelScope.launch {
            val list = runCatching { repository.giftLeaderboard(competitionId) }.getOrDefault(emptyList())
            _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        }
    }

    /**
     * Offre un cadeau à un candidat (alimente son score). Débit atomique côté serveur ; on
     * resynchronise le classement et on recharge l'inventaire (quantité restante).
     */
    fun sendGift(candidateId: String, giftId: String, credits: Int) {
        viewModelScope.launch {
            runCatching {
                repository.sendGift(
                    competitionId,
                    com.dualmusic.domain.competition.CompetitionGiftRequest(candidateId = candidateId, giftId = giftId, credits = credits),
                )
            }
                .onSuccess { refresh(); loadInventory() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Démarre : classement + organisateur + room vidéo LiveKit + chat + présence + temps réel. */
    fun start() {
        refresh()
        loadInventory()
        loadGiftLeaderboard()
        recordingCtl.refresh()
        viewModelScope.launch {
            val comp = runCatching { repository.competition(competitionId) }.getOrNull()
            _status.value = comp?.status
            _forcedFocusId.value = comp?.forcedFocusParticipantId
            // Room partagée avec le web : mobile et web rejoignent la MÊME room LiveKit.
            roomName = comp?.livekitRoom?.takeIf { it.isNotBlank() } ?: "comp-$competitionId"
            val myId = runCatching { repository.myUserId() }.getOrNull()
            val manager = comp?.managerId != null && comp.managerId == myId
            _isManager.value = manager
            // Multi-cam : en mode « online », un candidat APPROUVÉ diffuse aussi sa caméra.
            val cands = runCatching { repository.candidates(competitionId) }.getOrNull().orEmpty()
            val approvedCandidate = comp?.mode == "online" && myId != null &&
                cands.any { it.artistId == myId && it.status == "approved" }
            val publish = manager || approvedCandidate
            _canPublish.value = publish
            // Billetterie : une compétition payante exige un billet pour les spectateurs (hors publieurs).
            _ticketPrice.value = comp?.viewerTicketPrice ?: 0.0
            val needsTicket = comp?.isPublicPaid == true && !publish &&
                runCatching { repository.ticketInfo(competitionId) }.getOrNull()?.hasTicket != true
            _needsTicket.value = needsTicket
            // Rejoint la room (sauf blocage billet) : les spectateurs consomment, les publieurs diffusent.
            if (!needsTicket) runCatching { media.join(roomName = roomName, isHost = manager, canPublish = publish) }
            // Historique du chat.
            runCatching { repository.chatHistory(competitionId) }.getOrNull()?.let { _messages.value = it }
        }
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }
        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.COMPETITION, competitionId)
                live.emit("broadcast:join", "competition-emojis-$competitionId")
            }
            chat.onConnect { chat.join(Realtime.RoomType.COMPETITION, competitionId) }
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, com.dualmusic.domain.realtime.ChatMessagePayload.serializer()) { p ->
                _messages.update { it + CompetitionChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { p ->
                _status.value = p.status
                // Un changement d'état peut clore les votes → on resynchronise le classement.
                refresh()
            }
            // Présence : compteur de spectateurs en direct (parité web usePresence).
            live.on(Realtime.RealtimeEvent.PRESENCE, com.dualmusic.domain.realtime.PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            // Performeur désigné par le manager : on calcule la fin du slot (maintenant + durée) → chrono.
            live.on(Realtime.RealtimeEvent.PERFORMER, com.dualmusic.domain.realtime.PerformerPayload.serializer()) { p ->
                _performer.value = if (p.performerId != null)
                    CompetitionPerformer(p.performerId, java.time.Instant.now().plusSeconds(p.durationSec.toLong()).toString())
                else CompetitionPerformer(null, null)
            }
            // Focus caméra imposé par le manager → tous les clients mettent cette identité en avant.
            live.on(Realtime.RealtimeEvent.FOCUS, com.dualmusic.domain.realtime.FocusPayload.serializer()) { p ->
                _forcedFocusId.value = p.participantId
            }
            // Réactions emojis relayées (canal competition-emojis-<id>) — émetteur exclu.
            live.on("broadcast", com.dualmusic.domain.realtime.BroadcastEnvelope.serializer()) { env ->
                if (env.event == "emoji_reaction") env.payload?.emoji?.let { pushEmoji(it) }
            }
            // Cadeaux : burst central pour tous + resync du classement (un gift à un candidat
            // alimente son score). Parité live/duel/concert.
            live.on(Realtime.RealtimeEvent.GIFT, com.dualmusic.domain.realtime.GiftPayload.serializer()) { _ ->
                _giftPulse.update { it + 1 }
                refresh()
                loadGiftLeaderboard() // met à jour la bulle top-donateur en direct
            }
            // Pub sponsor (start/stop) diffusée à toute la room.
            live.on(Realtime.RealtimeEvent.SPONSOR_AD, com.dualmusic.domain.realtime.SponsorAdPayload.serializer()) { p ->
                sponsor.onEvent(p)
            }
            live.connect()
            chat.connect()
        }
    }

    /** Publieur : démarre la diffusion caméra/micro. */
    fun startBroadcast() {
        viewModelScope.launch {
            runCatching { media.startBroadcast() }
            _broadcasting.value = true
        }
    }

    /** Publieur : coupe/rétablit la caméra. */
    fun toggleCamera() { media.setCamEnabled(!media.camEnabled.value) }

    /** Publieur : coupe/rétablit le micro. */
    fun toggleMic() { viewModelScope.launch { runCatching { media.setMicEnabled(!media.micEnabled.value) } } }

    /** Publieur : bascule caméra avant/arrière. */
    fun flipCamera() { viewModelScope.launch { runCatching { media.switchCamera() } } }

    /** Poste un message de chat. */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(competitionId, content) } }
    }

    /** Signale ce direct à la modération (best-effort ; l'échec reste silencieux). */
    fun report(reason: String) {
        viewModelScope.launch { runCatching { repository.reportLive(competitionId, reason) } }
    }

    /** Achète le billet spectateur puis rejoint la room vidéo. */
    fun buyTicket() {
        viewModelScope.launch {
            runCatching { repository.buyTicket(competitionId) }
                .onSuccess {
                    _needsTicket.value = false
                    runCatching { media.join(roomName = roomName, isHost = false, canPublish = false) }
                }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** J'aime : incrémente le compteur local + fait flotter un cœur pour tous. */
    fun sendLike() {
        _likes.value += 1
        sendReaction("❤️")
    }

    /** Envoie une réaction emoji : effet local + relais aux autres membres du canal. */
    fun sendReaction(emoji: String) {
        pushEmoji(emoji)
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-emojis-$competitionId",
                    "event" to "emoji_reaction",
                    "payload" to org.json.JSONObject(mapOf("emoji" to emoji)),
                ),
            ),
        )
    }

    private fun pushEmoji(emoji: String) {
        _emojiFeed.update { (it + (emojiCounter++ to emoji)).takeLast(12) }
    }

    /** Arrête l'écoute temps réel + libère la room vidéo. */
    fun stop() {
        liveSession?.disconnect()
        chatSession?.disconnect()
        media.leave()
    }

    /** Recharge le classement depuis le serveur (source de vérité des tallies). */
    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.candidates(competitionId) }
                .getOrNull()
                ?.let { list -> _candidates.value = list.sortedByDescending { it.score } }
        }
    }

    /** Vote payant pour un candidat, puis resynchronise le classement. */
    fun vote(candidateId: String, credits: Int) {
        viewModelScope.launch {
            runCatching { repository.vote(competitionId, candidateId, credits) }
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.errVoteFailed }
        }
    }

    // --- Contrôles MANAGER (organisateur) ---

    /** Valide/rejette une candidature puis recharge la liste. */
    fun reviewCandidate(candidateId: String, approve: Boolean) {
        viewModelScope.launch {
            runCatching { repository.reviewCandidate(candidateId, approve) }.onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Publie la compétition (ouvre les votes). */
    fun publish() {
        viewModelScope.launch {
            runCatching { repository.publish(competitionId) }.onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Désigne le performeur courant (ou `null` pour arrêter). */
    fun setPerformer(candidateId: String?, durationSec: Int) {
        viewModelScope.launch {
            runCatching { repository.setPerformer(competitionId, candidateId, durationSec) }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Finalise le classement (clôture). */
    fun finalize() {
        viewModelScope.launch {
            runCatching { repository.finalize(competitionId) }.onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Manager : impose (ou libère avec `null`) la caméra épinglée pour tous. Optimiste + diffusé. */
    fun setFocus(participantId: String?) {
        _forcedFocusId.value = participantId // retour immédiat ; l'event `focus` confirmera pour tous
        viewModelScope.launch {
            runCatching { repository.setFocus(competitionId, participantId) }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Efface l'erreur affichée. */
    fun clearError() { _error.value = null }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

/**
 * Room de compétition : classement en direct + vote payant par candidat.
 *
 * @param viewModel état + actions.
 * @param voteCredits montant (crédits entiers) d'un vote rapide.
 */
@Composable
fun CompetitionRoomScreen(
    viewModel: CompetitionRoomViewModel,
    voteCredits: Int = 10,
    onLeave: () -> Unit = {},
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftPulse by viewModel.giftPulse.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val performer by viewModel.performer.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val canPublish by viewModel.canPublish.collectAsStateWithLifecycle()
    val broadcasting by viewModel.broadcasting.collectAsStateWithLifecycle()
    val needsTicket by viewModel.needsTicket.collectAsStateWithLifecycle()
    val ticketPrice by viewModel.ticketPrice.collectAsStateWithLifecycle()
    val primaryTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val remoteTiles by viewModel.media.remoteTiles.collectAsStateWithLifecycle()
    val localTrack by viewModel.media.localVideoTrack.collectAsStateWithLifecycle()
    val forcedFocusId by viewModel.forcedFocusId.collectAsStateWithLifecycle()
    val camOn by viewModel.media.camEnabled.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val sponsorAd by viewModel.sponsor.activeAd.collectAsStateWithLifecycle()
    val sponsorAds by viewModel.sponsor.ads.collectAsStateWithLifecycle()
    val sponsorBusy by viewModel.sponsor.busy.collectAsStateWithLifecycle()
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()
    val isManager by viewModel.isManager.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    var giftTargetCandidate by remember { mutableStateOf<String?>(null) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // Caméra mise en avant (focus local, tap sur une vignette). Défaut = piste primaire.
    var focusedTrack by remember { mutableStateOf<VideoTrack?>(null) }

    // Permissions caméra/micro requises avant de diffuser (publieur).
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasPerms() = perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val broadcastLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) viewModel.startBroadcast()
    }

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    // Multi-cam avec identité LiveKit (focus imposé synchronisé). Tuile = (identité publieur, piste).
    // La tuile locale porte l'identité locale (= userId) pour être épinglable comme les autres.
    val localIdentity = viewModel.media.localIdentity()
    val allTiles = remember(remoteTiles, localTrack, localIdentity) {
        remoteTiles + (localTrack?.let { listOf((localIdentity ?: "local") to it) } ?: emptyList())
    }
    // Piste principale : focus imposé par le manager > focus local (tap) > piste primaire.
    val mainTile = forcedFocusId?.let { fid -> allTiles.find { it.first == fid } }
        ?: focusedTrack?.let { ft -> allTiles.find { it.second === ft } }
        ?: allTiles.firstOrNull()
    val mainTrack = mainTile?.second ?: primaryTrack ?: localTrack
    val thumbs = allTiles.filter { it.second !== mainTrack }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Couche vidéo principale ---
        if (mainTrack != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                update = { renderer -> mainTrack.addRenderer(renderer) },
            )
        } else {
            // Pas encore de flux : fond dégradé + trophée (parité web « en attente du direct »).
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
                Text("🏆", fontSize = 54.sp)
            }
        }

        // Dégradés haut + bas pour la lisibilité des overlays.
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.6f))),
        ))

        // Réactions montantes + cadeau burst + célébration du vainqueur.
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)
        // Bulle du meilleur donateur (parité web), pilotée par les préférences visuelles.
        TopDonorBubble(donor = topDonor, mode = uiPrefs.topDonorMode, animation = uiPrefs.topDonorAnimation)
        if (!uiPrefs.reduceAnimations && giftPulse > 0L) {
            key(giftPulse) { GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
        }
        if (status == "finished" && candidates.isNotEmpty()) {
            WinnerCelebration(
                winnerName = candidates.first().artist?.displayName ?: strings.winnerGeneric,
                title = strings.winnerTitle,
                subtitle = strings.winnerCongrats,
                reduceAnimations = uiPrefs.reduceAnimations,
            )
        }

        // --- Header live unifié (mêmes icônes que le web) ---
        com.dualmusic.core.ui.live.LiveHeader(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(DualMusicTheme.spacing.md),
            eventLabel = "COMPÉTITION",
            viewerCount = viewerCount,
            likes = likes,
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, strings.shareLiveText) }
                context.startActivity(Intent.createChooser(send, null))
            },
            onReport = { showReport = true },
            onClose = onLeave,
        )

        // Chrono du performeur courant (parité web CompetitionPerformerTimer), centré sous le header.
        performer.endsAtIso?.let { endsAt ->
            val performerName = candidates.find { it.id == performer.performerId }?.artist?.displayName
            com.dualmusic.core.ui.live.LiveCountdown(
                endsAtIso = endsAt,
                label = performerName,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 50.dp),
            )
        }

        // Épinglage manager (PinOff) sur la caméra principale : libère le focus imposé.
        if (isManager && forcedFocusId != null) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 96.dp, end = DualMusicTheme.spacing.md)
                    .size(40.dp).background(colors.primary, CircleShape).clickable { viewModel.setFocus(null) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PushPin, contentDescription = "Libérer le focus", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }

        // Vignettes multi-cam (sous le header) : tap = focus local ; icône épingle (manager) = focus imposé.
        if (thumbs.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(top = 56.dp, start = DualMusicTheme.spacing.md, end = DualMusicTheme.spacing.md)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thumbs.forEach { tile ->
                    key(tile.first, tile.second) {
                        Box(modifier = Modifier.width(72.dp).height(96.dp)) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .clickable { focusedTrack = tile.second },
                                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                                update = { renderer -> tile.second.addRenderer(renderer) },
                            )
                            // Manager : épingle cette caméra pour TOUS les spectateurs.
                            if (isManager) {
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                        .clickable { viewModel.setFocus(tile.first) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.PushPin, contentDescription = "Imposer cette caméra", tint = Color.White, modifier = Modifier.size(13.dp)) }
                            }
                        }
                    }
                }
            }
        }

        // --- Bas : erreur + chat + réactions + barre d'action ---
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            error?.let { Text(it, color = colors.destructive, fontSize = 12.sp) }

            // Chat (semi-transparent, auto-scroll).
            val chatState = rememberLazyListState()
            LaunchedEffect(messages.size) { if (messages.isNotEmpty()) chatState.animateScrollToItem(messages.size - 1) }
            LazyColumn(state = chatState, modifier = Modifier.fillMaxWidth(0.68f).height(160.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(messages) { msg ->
                    Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(msg.authorName, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("  ${msg.content}", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Barre de réactions : J'aime + emojis (flottent pour tous).
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Box(modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendLike() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(20.dp))
                }
                if (likes > 0) Text("$likes", color = Color.White, fontSize = 12.sp)
                CompetitionReactionEmojis.forEach { e ->
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text(e) }
                }
            }

            // Barre d'action : message + classement.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(strings.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = "" }),
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { showLeaderboard = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = strings.ranking, tint = Color(0xFFFFC107))
                }
            }
        }

        // --- Contrôles publieur (manager / candidat approuvé) : démarrer + caméra/micro/flip + REC ---
        if (canPublish) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(end = DualMusicTheme.spacing.md, top = 120.dp),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                if (!broadcasting) {
                    CompCircleBtn(Icons.Filled.Podcasts, colors.primary) {
                        if (hasPerms()) viewModel.startBroadcast() else broadcastLauncher.launch(perms)
                    }
                } else {
                    CompCircleBtn(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, Color.Black.copy(alpha = 0.4f)) { viewModel.toggleCamera() }
                    CompCircleBtn(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, Color.Black.copy(alpha = 0.4f)) { viewModel.toggleMic() }
                    CompCircleBtn(Icons.Filled.Cameraswitch, Color.Black.copy(alpha = 0.4f)) { viewModel.flipCamera() }
                }
                if (isManager) {
                    com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = { viewModel.recordingCtl.toggle() })
                }
            }
        }

        // --- Classement (panneau bas) : candidats + vote/cadeau + actions organisateur ---
        if (showLeaderboard) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showLeaderboard = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.6f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(strings.ranking, color = colors.foreground, fontWeight = FontWeight.Bold)
                if (isManager) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        DMButton(strings.publishAction, modifier = Modifier.weight(1f), onClick = { viewModel.publish() })
                        DMButton(strings.finalizeAction, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { viewModel.finalize() })
                    }
                }
                if (candidates.isEmpty()) Text(strings.noCandidatesHint, color = colors.mutedForeground, fontSize = 13.sp)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    itemsIndexed(candidates) { index, candidate ->
                        CandidateRow(
                            rank = index + 1,
                            candidate = candidate,
                            voteCredits = voteCredits,
                            isManager = isManager,
                            onVote = { viewModel.vote(candidate.id, voteCredits) },
                            onGift = { giftTargetCandidate = candidate.id },
                            onApprove = { viewModel.reviewCandidate(candidate.id, true) },
                            onReject = { viewModel.reviewCandidate(candidate.id, false) },
                            onPerformer = { viewModel.setPerformer(candidate.id, 120) },
                        )
                    }
                }
            }
        }

        // Sélecteur de cadeau : offrande à un candidat (alimente son score). Parité live/duel/web.
        giftTargetCandidate?.let { candidateId ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { giftTargetCandidate = null })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(strings.myGifts, color = colors.foreground, fontWeight = FontWeight.Bold)
                if (inventory.isEmpty()) {
                    Text(strings.noGiftsBuyInShop, color = colors.mutedForeground, fontSize = 13.sp)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    inventory.forEach { g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.sendGift(candidateId, g.giftId, g.price.toInt())
                                    giftTargetCandidate = null
                                }
                                .padding(DualMusicTheme.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${g.imageUrl ?: "🎁"}  ${g.name ?: "Cadeau"}", color = colors.foreground)
                            Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Signalement du direct (parité web LiveReportButton).
        if (showReport) {
            com.dualmusic.core.ui.live.ReportDialog(
                onDismiss = { showReport = false },
                onSubmit = { reason ->
                    viewModel.report(reason)
                    android.widget.Toast.makeText(context, strings.reportSent, android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }

        // Blocage billet : compétition payante sans billet → écran d'achat (parité web).
        if (needsTicket) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🎟️", fontSize = 48.sp)
                Text(strings.buyTicket, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                DMButton("${strings.buyTicket} · ${ticketPrice.toInt()} ${strings.credits}", onClick = { viewModel.buyTicket() })
            }
        }

        // Diffusion pub sponsor : overlay vidéo pour tous + contrôle pour l'organisateur.
        SponsorAdLayer(
            activeAd = sponsorAd,
            canTrigger = isManager,
            ads = sponsorAds,
            busy = sponsorBusy,
            onLoadAds = { viewModel.sponsor.loadAds() },
            onPlay = { viewModel.sponsor.play(it) },
            onStop = { viewModel.sponsor.stop() },
        )
    }
}

/** Bouton d'action circulaire (contrôles de diffusion : caméra/micro/flip/démarrer). */
@Composable
private fun CompCircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** Emojis de réaction (identiques au live/duel/web). */
private val CompetitionReactionEmojis = listOf("🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

/** Ligne de classement : rang, artiste, score, vote — + actions manager (valider/rejeter/performeur). */
@Composable
private fun CandidateRow(
    rank: Int,
    candidate: CompetitionCandidate,
    voteCredits: Int,
    isManager: Boolean,
    onVote: () -> Unit,
    onGift: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPerformer: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "${medal(rank)} ${candidate.artist?.displayName ?: strings.artistSingular}",
                        color = colors.foreground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${candidate.score.toInt()} pts · ${candidate.status}", color = colors.mutedForeground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    // Offrir un cadeau à ce candidat (bouton compact, alimente son score).
                    Box(
                        modifier = Modifier.size(40.dp).background(colors.primary.copy(alpha = 0.25f), CircleShape).clickable { onGift() },
                        contentAlignment = Alignment.Center,
                    ) { Text("🎁", fontSize = 18.sp) }
                    DMButton("${strings.vote} ($voteCredits)", onClick = onVote)
                }
            }
            // Actions de l'organisateur : valider/rejeter (candidat en attente) ou désigner le performeur.
            if (isManager) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), modifier = Modifier.padding(top = DualMusicTheme.spacing.sm)) {
                    when (candidate.status) {
                        "pending" -> {
                            DMButton(strings.approveAction, modifier = Modifier.weight(1f), onClick = onApprove)
                            DMButton(strings.rejectAction, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onReject)
                        }
                        "approved" -> DMButton(strings.setPerformerAction, style = DMButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), onClick = onPerformer)
                    }
                }
            }
        }
    }
}

/** Médaille pour le podium, numéro sinon. */
private fun medal(rank: Int): String = when (rank) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "$rank."
}

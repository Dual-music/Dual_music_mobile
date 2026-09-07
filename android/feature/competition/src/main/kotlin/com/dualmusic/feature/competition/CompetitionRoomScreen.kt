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
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.realtime.EventModeratorPayload
import com.dualmusic.domain.realtime.EventSettingsPayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/** Vainqueur annoncé par le manager (célébration plein écran synchronisée — parité duel). */
data class CompetitionWinner(val name: String, val avatar: String?, val votes: Int, val percent: Int = 0)

/** État micro/caméra d'un publieur (indexé par identité LiveKit) → badges des tuiles multi-cam. */
data class CompMediaState(val isMicOn: Boolean, val isCameraOn: Boolean, val isStreaming: Boolean)

/** Cadeau reçu en direct → carte glissante + fil (parité duel). */
data class CompGift(val key: Long, val fromUserId: String?, val fromUserName: String?, val name: String?, val image: String?, val value: Double)

/** Emojis rapides pour le commentaire (parité web/concert/live). */
private val ChatComposeEmojis = listOf("😀", "😂", "❤️", "🔥", "👏", "🎵", "🎤", "💯", "😍", "🙌", "💪", "🎉", "😮", "👀", "✨", "🥳")

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

    /** Classement complet des donateurs (parité concert/duel) — panneau dédié, distinct du
     *  classement des candidats (`candidates`, alimenté par les votes/cadeaux REÇUS par eux). */
    private val _leaderboard = MutableStateFlow<List<CompetitionDonorEntry>>(emptyList())
    val leaderboard: StateFlow<List<CompetitionDonorEntry>> = _leaderboard.asStateFlow()

    /** Masquer les autres cases pour TOUS (diffusé par l'organisateur, parité web) — distinct
     *  du masquage local existant quand un focus est imposé (qui ne concernait que les non-managers). */
    private val _forcedHideOthers = MutableStateFlow(false)
    val forcedHideOthers: StateFlow<Boolean> = _forcedHideOthers.asStateFlow()

    /** Organisateur : bascule le masquage des autres cases pour TOUS (diffusion éphémère). */
    fun toggleHideOthers() {
        val next = !_forcedHideOthers.value
        _forcedHideOthers.value = next
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-hide-others-$competitionId",
                    "event" to if (next) "HIDE_OTHERS" else "SHOW_OTHERS",
                    "payload" to org.json.JSONObject(emptyMap<String, Any>()),
                ),
            ),
        )
    }

    /** Performeur courant désigné par le manager (id candidat + fin du slot ISO) → chrono visuel. */
    private val _performer = MutableStateFlow(CompetitionPerformer(null, null))
    val performer: StateFlow<CompetitionPerformer> = _performer.asStateFlow()

    /** Caméra épinglée par le manager (identité LiveKit = userId) → focus imposé à tous. */
    private val _forcedFocusId = MutableStateFlow<String?>(null)
    val forcedFocusId: StateFlow<String?> = _forcedFocusId.asStateFlow()

    /**
     * Type de compétition : `"online"` (les candidats approuvés diffusent leur caméra, façon meet)
     * ou `"onsite"` / présentiel (seul l'appareil du manager filme la scène ; les spectateurs
     * votent les candidats via un sélecteur de noms). Pilote le rendu du direct.
     */
    private val _mode = MutableStateFlow<String?>(null)
    val mode: StateFlow<String?> = _mode.asStateFlow()

    /** Id de l'utilisateur courant (pour mute de soi, bannissement, cadeau reçu, détection modérateur). */
    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    /** Chat activé/désactivé par le manager (bascule `PATCH /competitions/:id {chatEnabled}`). */
    private val _chatEnabled = MutableStateFlow(true)
    val chatEnabled: StateFlow<Boolean> = _chatEnabled.asStateFlow()

    /** Modérateurs désignés par le manager (max 2, mêmes pouvoirs de bannissement que lui). */
    private val _moderators = MutableStateFlow<List<EventModerator>>(emptyList())
    val moderators: StateFlow<List<EventModerator>> = _moderators.asStateFlow()

    /** Spectateurs actuellement connectés (vivier du picker de modérateurs — manager uniquement). */
    private val _viewers = MutableStateFlow<List<DisplayProfile>>(emptyList())
    val viewers: StateFlow<List<DisplayProfile>> = _viewers.asStateFlow()

    /** Candidats coupés d'autorité par le manager (hard-mute mic uniquement) — ids utilisateur. */
    private val _mutedArtists = MutableStateFlow<Set<String>>(emptySet())
    val mutedArtists: StateFlow<Set<String>> = _mutedArtists.asStateFlow()

    /** Vainqueur annoncé par le manager (overlay plein écran + applaudissements). */
    private val _winnerInfo = MutableStateFlow<CompetitionWinner?>(null)
    val winnerInfo: StateFlow<CompetitionWinner?> = _winnerInfo.asStateFlow()

    /** État micro/caméra des publieurs, indexé par identité LiveKit → badges des tuiles. */
    private val _mediaStates = MutableStateFlow<Map<String, CompMediaState>>(emptyMap())
    val mediaStates: StateFlow<Map<String, CompMediaState>> = _mediaStates.asStateFlow()

    /** Spectateurs bannis de ce direct (ids). Masque leurs messages + bloque le rejoint. */
    private val _bannedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val bannedUserIds: StateFlow<Set<String>> = _bannedUserIds.asStateFlow()

    /** Vrai si MOI je suis banni → écran de blocage plein écran. */
    val iAmBanned: StateFlow<Boolean> =
        combine(_bannedUserIds, _myUserId) { banned, id -> id != null && banned.contains(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Fil des cadeaux reçus (carte glissante, seule animation — parité concert/live). */
    private val _giftFeed = MutableStateFlow<List<CompGift>>(emptyList())
    val giftFeed: StateFlow<List<CompGift>> = _giftFeed.asStateFlow()
    private var giftCounter = 0L

    /** Prix d'un vote (crédits), piloté par l'admin (parité duel). */
    private val _votePrice = MutableStateFlow(1)
    val votePrice: StateFlow<Int> = _votePrice.asStateFlow()

    /** Filtre couleur vidéo actif du publieur (parité duel). */
    private val _activeFilter = MutableStateFlow("none")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

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
        viewModelScope.launch { refreshGiftLeaderboard() }
    }

    /** Recharge le classement (met à jour la bulle top-donateur + le panneau complet) ; retourne la liste fraîche. */
    private suspend fun refreshGiftLeaderboard(): List<CompetitionDonorEntry> {
        val list = runCatching { repository.giftLeaderboard(competitionId) }.getOrDefault(emptyList())
        _leaderboard.value = list
        _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        return list
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
        recordingCtl.startPolling()
        viewModelScope.launch {
            val comp = runCatching { repository.competition(competitionId) }.getOrNull()
            _status.value = comp?.status
            _forcedFocusId.value = comp?.forcedFocusParticipantId
            _mode.value = comp?.mode
            _chatEnabled.value = comp?.chatEnabled ?: true
            // Room partagée avec le web : mobile et web rejoignent la MÊME room LiveKit.
            roomName = comp?.livekitRoom?.takeIf { it.isNotBlank() } ?: "comp-$competitionId"
            val myId = runCatching { repository.myUserId() }.getOrNull()
            _myUserId.value = myId
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
            // Likes persistés + prix du vote (admin) + spectateurs déjà bannis (parité duel).
            _likes.value = runCatching { repository.likesCount(competitionId) }.getOrDefault(0)
            _votePrice.value = runCatching { repository.votePricePerVote() }.getOrDefault(1.0).toInt().coerceAtLeast(1)
            _bannedUserIds.value = runCatching { repository.listStreamBans(competitionId) }.getOrDefault(emptyList()).toSet()
        }
        loadModerators()
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }
        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.COMPETITION, competitionId)
                live.emit("broadcast:join", "competition-emojis-$competitionId")
                live.emit("broadcast:join", "competition-mute-$competitionId")
                live.emit("broadcast:join", "competition-winner-$competitionId")
                live.emit("broadcast:join", "competition-likes-$competitionId")
                live.emit("broadcast:join", "competition-media-$competitionId")
                live.emit("broadcast:join", "competition-hide-others-$competitionId")
                // (Re)diffuse mon état média à la (re)connexion pour les tuiles des autres.
                if (_broadcasting.value) broadcastMediaState()
            }
            chat.onConnect { chat.join(Realtime.RoomType.COMPETITION, competitionId) }
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, com.dualmusic.domain.realtime.ChatMessagePayload.serializer()) { p ->
                _messages.update { it + CompetitionChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user, parentId = p.parentId) }
            }
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { p ->
                _status.value = p.status
                // Un changement d'état peut clore les votes → on resynchronise le classement.
                refresh()
            }
            // Réglage chat basculé par le manager (parité web) — même room pour tous les spectateurs.
            live.on(Realtime.RealtimeEvent.SETTINGS, EventSettingsPayload.serializer()) { p ->
                if (p.competitionId == null || p.competitionId == competitionId) p.chatEnabled?.let { _chatEnabled.value = it }
            }
            // Modérateur désigné/révoqué par le manager → resynchronise la liste (droit de bannir).
            live.on(Realtime.RealtimeEvent.MODERATOR_APPOINTED, EventModeratorPayload.serializer()) { p ->
                if (p.eventType == "competition" && p.eventId == competitionId) loadModerators()
            }
            live.on(Realtime.RealtimeEvent.MODERATOR_REVOKED, EventModeratorPayload.serializer()) { p ->
                if (p.eventType == "competition" && p.eventId == competitionId) loadModerators()
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
            // Relais broadcast éphémères (emojis, mute, vainqueur, likes, media-state) — émetteur exclu.
            live.on("broadcast", com.dualmusic.domain.realtime.BroadcastEnvelope.serializer()) { env ->
                when (env.event) {
                    "emoji_reaction" -> env.payload?.emoji?.let { pushEmoji(it) }
                    // Hard-mute d'un candidat imposé par le manager → coupe/réactive le micro partout.
                    "FORCE_MUTE" -> env.payload?.artistId?.let { applyMuteState(it, true) }
                    "FORCE_UNMUTE" -> env.payload?.artistId?.let { applyMuteState(it, false) }
                    // Masquer/réafficher les autres cases pour TOUS, imposé par l'organisateur.
                    "HIDE_OTHERS" -> _forcedHideOthers.value = true
                    "SHOW_OTHERS" -> _forcedHideOthers.value = false
                    // Vainqueur annoncé/arrêté par le manager (célébration plein écran synchronisée).
                    "winner_announced" -> _winnerInfo.value = env.payload?.let {
                        CompetitionWinner(it.name ?: "Vainqueur", it.avatar, it.votes ?: 0, it.percent ?: 0)
                    }
                    "winner_stopped" -> _winnerInfo.value = null
                    // Compteur de J'aime partagé (on ne recule jamais).
                    "like" -> env.payload?.count?.let { if (it > _likes.value) _likes.value = it }
                    // État micro/caméra d'un publieur → badges des tuiles chez tous les spectateurs.
                    "media-state" -> {
                        val id = env.payload?.identity
                        if (id != null) _mediaStates.update { m ->
                            m + (id to CompMediaState(
                                isMicOn = env.payload?.isMicOn ?: false,
                                isCameraOn = env.payload?.isCameraOn ?: false,
                                isStreaming = env.payload?.isStreaming ?: false,
                            ))
                        }
                    }
                }
            }
            // Bannissement d'un spectateur ou candidat poussé par le serveur — canal DÉDIÉ
            // compétition (parité web `useStreamBan`, voir CompetitionRepository#createStreamBan).
            live.on("competition:banned", CompetitionStreamBannedPayload.serializer()) { p ->
                if (p.competitionId == null || p.competitionId == competitionId) _bannedUserIds.update { it + p.userId }
                // Si c'est MOI et que je diffusais (candidat) : coupe caméra + micro — ma
                // diffusion s'arrête pour tous, pas seulement mon propre écran (l'écran de
                // blocage plein écran, lui, ne fait que masquer MA vue).
                if (p.userId == _myUserId.value && _broadcasting.value) {
                    media.setCamEnabled(false)
                    viewModelScope.launch { runCatching { media.setMicEnabled(false) }; broadcastMediaState() }
                }
            }
            // Cadeaux : burst central pour tous + resync du classement (un gift à un candidat
            // alimente son score). Parité live/duel/concert.
            live.on(Realtime.RealtimeEvent.GIFT, com.dualmusic.domain.realtime.GiftPayload.serializer()) { p ->
                // Recharge le classement (met à jour la bulle top-donateur) ET en profite pour
                // résoudre le NOM de l'expéditeur (déjà hydraté avec les profils) → affiché dans
                // la carte glissante « cadeau reçu » (SEULE animation — parité concert/live, plus
                // de burst central ni de bannière séparée).
                viewModelScope.launch {
                    val list = refreshGiftLeaderboard()
                    val senderName = list.find { it.userId == p.fromUserId }?.displayName
                    _giftFeed.update { it + CompGift(giftCounter++, p.fromUserId, senderName, p.giftName, p.giftImage, p.value) }
                }
                refresh()
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
            // `_broadcasting` ne doit passer à true QUE si la caméra/micro a vraiment démarré —
            // sinon l'interface affichait les icônes "en direct" (Réglages, filtres…) alors
            // qu'aucun flux n'était réellement publié, sans le moindre message d'erreur (bug
            // signalé : le manager clique sur Démarrer, rien ne se passe, aucun retour).
            runCatching { media.startBroadcast() }
                .onSuccess { _broadcasting.value = true; broadcastMediaState() }
                .onFailure {
                    // Diagnostic temporaire (affiché à l'écran, pas seulement en log) — permet de
                    // confirmer ce qui a réellement été envoyé au join sans dépendre d'un logcat.
                    _error.value = (it.message ?: "Impossible de démarrer la diffusion (caméra/micro)") +
                        "  [debug: room=$roomName isManager=${_isManager.value} canPublish=${_canPublish.value}]"
                }
        }
    }

    /** Publieur : coupe/rétablit la caméra (n'affecte QUE la caméra). */
    fun toggleCamera() {
        media.setCamEnabled(!media.camEnabled.value)
        broadcastMediaState()
    }

    /** Publieur : coupe/rétablit le micro. Un candidat coupé par le manager ne peut pas se réactiver. */
    fun toggleMic() {
        val next = !media.micEnabled.value
        val me = _myUserId.value
        if (next && me != null && _mutedArtists.value.contains(me)) {
            _error.value = "Micro coupé par le manager"
            return
        }
        viewModelScope.launch { runCatching { media.setMicEnabled(next) }; broadcastMediaState() }
    }

    /** Publieur : bascule caméra avant/arrière. */
    fun flipCamera() { viewModelScope.launch { runCatching { media.switchCamera() } } }

    /** Publieur : PAUSE (coupe caméra + micro) / REPRENDRE. Distinct du mute (qui ne touche que le micro). */
    fun togglePause() {
        val resume = !media.camEnabled.value && !media.micEnabled.value
        media.setCamEnabled(resume)
        viewModelScope.launch { runCatching { media.setMicEnabled(resume) }; broadcastMediaState() }
    }

    /** Publieur : applique un filtre couleur vidéo (parité duel). */
    fun setColorFilter(id: String, matrix: FloatArray?) {
        media.setColorFilter(id, matrix)
        _activeFilter.value = id
    }

    /**
     * Diffuse MON état micro/caméra sur `competition-media-<id>` (event `media-state`), indexé par
     * mon identité LiveKit → les autres affichent les bons badges sur ma tuile. Met aussi à jour
     * la map locale pour un rendu immédiat.
     */
    private fun broadcastMediaState() {
        val id = media.localIdentity() ?: return
        val streaming = _broadcasting.value
        val mic = streaming && media.micEnabled.value
        val cam = streaming && media.camEnabled.value
        _mediaStates.update { it + (id to CompMediaState(mic, cam, streaming)) }
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-media-$competitionId",
                    "event" to "media-state",
                    "payload" to org.json.JSONObject(
                        mapOf("identity" to id, "isMicOn" to mic, "isCameraOn" to cam, "isStreaming" to streaming),
                    ),
                ),
            ),
        )
    }

    /**
     * Manager : coupe/réactive d'autorité le micro d'un candidat (hard-mute), diffusé à tous via
     * `competition-mute-<id>`. NE touche JAMAIS la caméra.
     */
    fun toggleMuteArtist(artistId: String) {
        val shouldMute = !_mutedArtists.value.contains(artistId)
        applyMuteState(artistId, shouldMute)
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-mute-$competitionId",
                    "event" to if (shouldMute) "FORCE_MUTE" else "FORCE_UNMUTE",
                    "payload" to org.json.JSONObject(mapOf("artistId" to artistId)),
                ),
            ),
        )
    }

    /**
     * Applique l'état de coupure d'un candidat : map partagée + hard-mute réel de MON micro si
     * c'est moi. Le mute ne concerne QUE le micro (`setMicEnabled`) — aucune opération caméra.
     */
    private fun applyMuteState(artistId: String, muted: Boolean) {
        _mutedArtists.update { if (muted) it + artistId else it - artistId }
        if (artistId == _myUserId.value) {
            viewModelScope.launch {
                runCatching { media.setMicEnabled(!muted) }
                broadcastMediaState()
            }
        }
    }

    /**
     * Manager : annonce le VAINQUEUR (candidat en tête du classement) sans clôturer le direct.
     * Célébration plein écran + applaudissements chez tout le monde via `competition-winner-<id>`.
     */
    fun announceWinnerAuto() {
        val ranked = _candidates.value.filter { it.status == "approved" }.ifEmpty { _candidates.value }
            .sortedByDescending { it.score }
        val top = ranked.firstOrNull() ?: return
        val total = ranked.sumOf { it.score }
        val percent = if (total > 0) ((top.score / total) * 100).toInt() else 100
        val w = CompetitionWinner(top.artist?.displayName ?: "Vainqueur", top.artist?.avatarUrl, top.score.toInt(), percent)
        _winnerInfo.value = w
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-winner-$competitionId",
                    "event" to "winner_announced",
                    "payload" to org.json.JSONObject(
                        mapOf("name" to w.name, "avatar" to (w.avatar ?: ""), "votes" to w.votes, "percent" to w.percent),
                    ),
                ),
            ),
        )
    }

    /** Manager : arrête la célébration du vainqueur pour tous (ne clôture PAS le direct). */
    fun stopWinnerAnnouncement() {
        _winnerInfo.value = null
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(mapOf("channel" to "competition-winner-$competitionId", "event" to "winner_stopped")),
        )
    }

    /** Manager : bannit un spectateur (optimiste + persistant). Il ne peut plus écrire ni rejoindre. */
    fun banUser(userId: String, reason: String?) {
        _bannedUserIds.update { it + userId }
        viewModelScope.launch {
            runCatching { repository.createStreamBan(competitionId, userId, reason) }
                .onFailure {
                    _bannedUserIds.update { ids -> ids - userId }
                    _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed
                }
        }
    }

    /** Manager : active/désactive le chat pour tous (optimiste + persistant). */
    fun toggleChat(enabled: Boolean) {
        _chatEnabled.value = enabled
        viewModelScope.launch {
            runCatching { repository.setChatEnabled(competitionId, enabled) }
                .onFailure { _chatEnabled.value = !enabled }
        }
    }

    /** Recharge les modérateurs désignés de cette compétition. */
    fun loadModerators() {
        viewModelScope.launch { _moderators.value = repository.listEventModerators(competitionId) }
    }

    /** Manager : recharge les spectateurs actuellement connectés (vivier du picker). */
    fun loadViewers() {
        viewModelScope.launch { _viewers.value = repository.listCurrentViewers(competitionId) }
    }

    /** Manager : désigne un spectateur connecté comme modérateur (max 2). */
    fun appointModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.appointModerator(competitionId, userId) }.onSuccess { loadModerators() }
        }
    }

    /** Manager : révoque un modérateur désigné. */
    fun revokeModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.revokeModerator(competitionId, userId) }.onSuccess { loadModerators() }
        }
    }

    /** Poste un message de chat (optionnellement une réponse à `parentId`). */
    fun sendMessage(text: String, parentId: String? = null) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(competitionId, content, parentId) } }
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

    /** J'aime : incrémente le compteur (persisté + diffusé) + fait flotter un cœur pour tous. */
    fun sendLike() {
        _likes.value += 1
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "competition-likes-$competitionId",
                    "event" to "like",
                    "payload" to org.json.JSONObject(mapOf("count" to _likes.value)),
                ),
            ),
        )
        sendReaction("❤️")
        viewModelScope.launch { runCatching { repository.like(competitionId) } }
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
                .onSuccess { list -> _candidates.value = list.sortedByDescending { it.score } }
                .onFailure {
                    // `refresh()` est aussi appelé en continu (tally socket) : ne pas spammer une
                    // erreur à chaque hoquet réseau. Mais si la liste n'a JAMAIS chargé (premier
                    // appel, à l'entrée dans la salle), l'échec restait invisible pour toujours —
                    // ni cadeau ni vote n'apparaissaient, sans aucun indice pour comprendre pourquoi.
                    if (_candidates.value.isEmpty()) {
                        _error.value = it.message ?: "Impossible de charger les candidats"
                    }
                }
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

    /** Fixe les voix de jury d'un candidat puis recharge le classement (voir CompetitionRepository). */
    fun setJuryVotes(candidateId: String, juryVotes: Int) {
        viewModelScope.launch {
            runCatching { repository.setJuryVotes(candidateId, juryVotes) }.onSuccess { refresh() }
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
    onOpenArtist: (String) -> Unit = {},
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val donorLeaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val forcedHideOthers by viewModel.forcedHideOthers.collectAsStateWithLifecycle()
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
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val mutedArtists by viewModel.mutedArtists.collectAsStateWithLifecycle()
    val winnerInfo by viewModel.winnerInfo.collectAsStateWithLifecycle()
    val mediaStates by viewModel.mediaStates.collectAsStateWithLifecycle()
    val bannedUserIds by viewModel.bannedUserIds.collectAsStateWithLifecycle()
    val iAmBanned by viewModel.iAmBanned.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val votePrice by viewModel.votePrice.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val camOn by viewModel.media.camEnabled.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val sponsorAd by viewModel.sponsor.activeAd.collectAsStateWithLifecycle()
    val sponsorAds by viewModel.sponsor.ads.collectAsStateWithLifecycle()
    val sponsorBusy by viewModel.sponsor.busy.collectAsStateWithLifecycle()
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recPaused by viewModel.recordingCtl.paused.collectAsStateWithLifecycle()
    val recFinalizing by viewModel.recordingCtl.finalizing.collectAsStateWithLifecycle()
    val recFailed by viewModel.recordingCtl.failed.collectAsStateWithLifecycle()
    val recError by viewModel.recordingCtl.error.collectAsStateWithLifecycle()
    val recAccumulatedSeconds by viewModel.recordingCtl.accumulatedSeconds.collectAsStateWithLifecycle()
    val recRunStartedAt by viewModel.recordingCtl.runStartedAt.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()
    val isManager by viewModel.isManager.collectAsStateWithLifecycle()
    val chatEnabled by viewModel.chatEnabled.collectAsStateWithLifecycle()
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val viewers by viewModel.viewers.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val isModerator = myUserId != null && moderators.any { it.userId == myUserId }
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    var giftTargetCandidate by remember { mutableStateOf<String?>(null) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showCompSettings by remember { mutableStateOf(false) }
    var showVotePanel by remember { mutableStateOf(false) }
    var showGiftPicker by remember { mutableStateOf(false) }
    var showReactionBar by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // Cible de bannissement (tap photo manager) + réponse en cours + popup commentaire + filtres.
    var banTarget by remember { mutableStateOf<CompetitionChatMessage?>(null) }
    var replyingTo by remember { mutableStateOf<CompetitionChatMessage?>(null) }
    var showCommentPopup by remember { mutableStateOf(false) }
    var showChatEmoji by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showModerators by remember { mutableStateOf(false) }
    var showRecordingSheet by remember { mutableStateOf(false) }
    var showCancelRecordingConfirm by remember { mutableStateOf(false) }
    var hideOverlay by remember { mutableStateOf(false) }
    var showAdPicker by remember { mutableStateOf(false) }
    // Caméra mise en avant (focus local, tap sur une vignette). Défaut = piste primaire.
    var focusedTrack by remember { mutableStateOf<VideoTrack?>(null) }

    // Retour visible sur un échec d'action d'enregistrement — sans ça, un clic sur Pause/
    // Reprendre/Sauvegarder qui échoue ne montrait RIEN : le bouton semblait « ne pas prendre ».
    fun showRecordingError(message: String) {
        android.widget.Toast.makeText(context, "Enregistrement : $message", android.widget.Toast.LENGTH_SHORT).show()
    }
    androidx.compose.runtime.LaunchedEffect(recFailed) {
        if (recFailed) showRecordingError(recError ?: "échec, aucun segment récupérable")
    }

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

    // Carte « cadeau reçu » glissante (fil) — état hissé AVANT le gate `hideOverlay` (plus bas) :
    // sinon masquer/démasquer l'œil détruisait puis recréait ce sous-arbre Compose, ce qui
    // relançait le LaunchedEffect(lastGift?.key) et REJOUAIT l'animation du DERNIER cadeau reçu
    // (même si aucun nouveau cadeau n'avait été envoyé entre-temps) — parité concert.
    val lastGift = giftFeed.lastOrNull()
    var shownGift by remember { mutableStateOf<CompGift?>(null) }
    var giftVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lastGift?.key) {
        if (lastGift != null) { shownGift = lastGift; giftVisible = true; kotlinx.coroutines.delay(3200); giftVisible = false }
    }

    // Pluie d'emojis d'acclamation pendant l'annonce du vainqueur (parité duel).
    var winnerEmojis by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    LaunchedEffect(winnerInfo) {
        winnerEmojis = emptyList()
        if (winnerInfo != null) {
            val acc = listOf("👏", "🎉", "🔥", "🎊", "⭐", "💜", "🏆", "😍", "🙌")
            var i = 0L
            while (true) {
                winnerEmojis = (winnerEmojis + (i to acc[(i % acc.size).toInt()])).takeLast(24); i++
                kotlinx.coroutines.delay(220)
            }
        }
    }
    // Applaudissements en boucle tant que le vainqueur est annoncé.
    val winnerActive = winnerInfo != null
    DisposableEffect(winnerActive) {
        val player = if (winnerActive) {
            runCatching {
                android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource("https://assets.mixkit.co/active_storage/sfx/1011/1011-preview.mp3")
                    isLooping = true
                    setOnPreparedListener { runCatching { start() } }
                    prepareAsync()
                }
            }.getOrNull()
        } else {
            null
        }
        onDispose { runCatching { player?.stop() }; runCatching { player?.release() } }
    }
    // Multi-cam avec identité LiveKit (focus imposé synchronisé). Tuile = (identité publieur, piste).
    // La tuile locale porte l'identité locale (= userId) pour être épinglable comme les autres.
    val localIdentity = viewModel.media.localIdentity()
    val allTiles = remember(remoteTiles, localTrack, localIdentity, bannedUserIds) {
        // Un candidat banni disparaît de l'écran de TOUS (pas seulement du sien) — parité web.
        remoteTiles.filter { it.first !in bannedUserIds } +
            (localTrack?.let { listOf((localIdentity ?: "local") to it) } ?: emptyList())
    }
    // Piste principale : focus imposé par le manager > focus local (tap) > piste primaire.
    val mainTile = forcedFocusId?.let { fid -> allTiles.find { it.first == fid } }
        ?: focusedTrack?.let { ft -> allTiles.find { it.second === ft } }
        ?: allTiles.firstOrNull()
    val mainTrack = mainTile?.second ?: primaryTrack ?: localTrack
    val thumbs = allTiles.filter { it.second !== mainTrack }
    // Vignettes multi-cam affichées (compétition en ligne, plusieurs candidats) — décale la bulle
    // du meilleur donateur ET le rail d'icônes SOUS elles pour ne pas s'y superposer (les deux
    // partagent sinon la même bande top=56dp).
    val thumbsVisible = thumbs.isNotEmpty() && !forcedHideOthers && (forcedFocusId == null || isManager)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Couche vidéo principale ---
        if (mainTrack != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        viewModel.media.room.initVideoRenderer(this)
                        // Cadre ENTIER visible (jamais rogné) — parité avec ce que montre le PC.
                        setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    }
                },
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

        // Réactions montantes + célébration du vainqueur. (Le burst central de cadeau est
        // retiré — parité concert/live : la carte glissante au-dessus des messages suffit déjà,
        // le burst + la bannière « vous avez reçu un cadeau » faisaient doublon et encombraient
        // l'écran.)
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)
        if (status == "finished" && candidates.isNotEmpty()) {
            WinnerCelebration(
                winnerName = candidates.first().artist?.displayName ?: strings.winnerGeneric,
                title = strings.winnerTitle,
                subtitle = strings.winnerCongrats,
                reduceAnimations = uiPrefs.reduceAnimations,
            )
        }

        // Interface masquée (œil du rail) : seul un bouton de restauration reste visible — parité concert/live.
        if (hideOverlay) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(DualMusicTheme.spacing.md)
                    .size(44.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).clickable { hideOverlay = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Visibility, contentDescription = "Réafficher", tint = Color.White, modifier = Modifier.size(22.dp)) }
            return@Box
        }

        // Bulle du meilleur donateur (parité web) — APRÈS le early-return ci-dessus : elle doit
        // disparaître avec le reste de l'interface quand on masque tout (œil du rail), sinon elle
        // restait visible seule à l'écran (bug signalé). Décalée sous les vignettes multi-cam
        // quand elles sont affichées, pour ne pas s'y superposer.
        TopDonorBubble(
            donor = topDonor,
            mode = uiPrefs.topDonorMode,
            animation = uiPrefs.topDonorAnimation,
            topOffset = if (thumbsVisible) 152.dp else 56.dp,
        )

        // --- Header live unifié (mêmes icônes que le web) ---
        com.dualmusic.core.ui.live.LiveHeader(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(DualMusicTheme.spacing.md),
            // Le badge rouge affiche directement "COMPÉTITION" (pas "LIVE" + un second pill
            // redondant à côté — parité concert, qui utilise le même paramètre `badgeText`).
            badgeText = "COMPÉTITION",
            eventLabel = "",
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
        // Quand le manager a ÉPINGLÉ une caméra (focus imposé), les autres petites cases sont masquées
        // pour TOUT LE MONDE (vue « spotlight » nette) ; seul le manager les garde pour pouvoir
        // ré-épingler une autre caméra ou libérer le focus. (`thumbsVisible` calculé plus haut,
        // réutilisé aussi par la bulle donateur et le rail vertical pour ne pas s'y superposer.)
        if (thumbsVisible) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(top = 56.dp, start = DualMusicTheme.spacing.md, end = DualMusicTheme.spacing.md)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thumbs.forEach { tile ->
                    key(tile.first, tile.second) {
                        val tileName = candidates.find { it.artistId == tile.first }?.artist?.displayName
                        val ms = mediaStates[tile.first]
                        // Micro ON = état média diffusé (défaut : présumé ON si pas d'info) ; cam ON = piste présente.
                        val tileMicOn = ms?.isMicOn ?: true
                        Box(modifier = Modifier.width(72.dp).height(96.dp)) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .clickable { focusedTrack = tile.second },
                                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        viewModel.media.room.initVideoRenderer(this)
                        // Cadre ENTIER visible (jamais rogné) — parité avec ce que montre le PC.
                        setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    }
                },
                                update = { renderer -> tile.second.addRenderer(renderer) },
                            )
                            // Badge nom + micro/caméra (parité duel) — état diffusé par le publieur.
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    if (tileMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                                    contentDescription = null,
                                    tint = if (tileMicOn) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    modifier = Modifier.size(10.dp),
                                )
                                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(10.dp))
                                if (tileName != null) Text(tileName, color = Color.White, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
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
                items(messages.filter { it.userId !in bannedUserIds }) { msg ->
                    // Message parent (réponse) → citation grisée façon TikTok.
                    val parent = msg.parentId?.let { pid -> messages.find { it.id == pid } }
                    // Bannissable = spectateur (ni manager ni candidat) et je suis le manager OU un modérateur désigné.
                    val canBan = (isManager || isModerator) && candidates.none { it.artistId == msg.userId }
                    Row(
                        verticalAlignment = Alignment.Top,
                        // Tap sur le message → y répondre (parité duel).
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { replyingTo = msg; showCommentPopup = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        // Avatar rond — tap sur la PHOTO = bannir (manager seulement).
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.55f))
                                .then(if (canBan) Modifier.clickable { banTarget = msg } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avatar = msg.user?.avatarUrl
                            if (!avatar.isNullOrBlank()) {
                                com.dualmusic.core.ui.components.DMRemoteImage(url = avatar, contentDescription = msg.authorName, modifier = Modifier.fillMaxSize(), fallbackEmoji = "👤")
                            } else {
                                Text(msg.authorName.take(1).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            if (parent != null) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.width(2.dp).height(13.dp).background(colors.mutedForeground))
                                    Text("  ↩ ${parent.authorName} : ${parent.content}", color = colors.mutedForeground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(msg.authorName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(msg.content, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Barre de réactions (repliable) : emojis flottants — parité concert/live.
            if (showReactionBar) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    CompetitionReactionEmojis.forEach { e ->
                        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text(e) }
                    }
                }
            }

            // Barre d'action (parité concert/live) : pastille message (ouvre le popup) + j'aime +
            // bouton emoji (replie la barre de réactions) + cadeau + vote + classements.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 90.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = chatEnabled) { showCommentPopup = true }
                        .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
                ) {
                    if (!chatEnabled) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Text("  Chat désactivé", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(strings.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape).clickable { viewModel.sendLike() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(20.dp))
                }
                Box(modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape).clickable { showReactionBar = !showReactionBar }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.EmojiEmotions, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // Cadeau : ouvre un sélecteur de candidat (champ « select », parité web) PUIS
                // l'inventaire — au lieu d'exiger de d'abord ouvrir le classement pour taper sur
                // un candidat précis.
                if (candidates.isNotEmpty()) {
                    Box(modifier = Modifier.size(48.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPicker = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = strings.sendGift, tint = Color.White)
                    }
                }
                // Voter (spectateur) : ouvre un sélecteur de candidats + crédits. INDISPENSABLE en
                // mode présentiel (onsite) où les candidats n'ont pas de caméra à taper, et pratique
                // en ligne quand ils sont nombreux — parité web CompetitionVotePanel.
                if (!isManager && candidates.isNotEmpty()) {
                    Box(modifier = Modifier.size(44.dp).background(colors.primary, CircleShape).clickable { showVotePanel = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.HowToVote, contentDescription = strings.vote, tint = Color.White)
                    }
                }
                // Classements compétiteurs + donateurs FUSIONNÉS en un seul panneau à onglets
                // (au lieu de 2 icônes séparées) : la rangée d'action débordait de l'écran sur
                // mobile et masquait le bouton Voter, invisible sans faire défiler — bug signalé.
                Box(modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { showLeaderboard = true; viewModel.loadGiftLeaderboard() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = strings.ranking, tint = Color(0xFFFFC107))
                }
            }
        }

        // Rail vertical GAUCHE (parité concert/live) : œil « masquer tout » pour TOUT LE MONDE,
        // puis démarrer/Réglages (regroupe caméra/micro/flip/pause, parité concert), filtres,
        // pub (organisateur) et enregistrement (organisateur) — décalé SOUS le nom du meilleur
        // donateur (qui défile juste sous la barre du haut) pour ne plus s'y mélanger, ET SOUS
        // les vignettes multi-cam quand elles sont affichées (compétition en ligne, plusieurs
        // candidats — vignettes top=56dp sur 96dp, puis la bulle donateur décalée à top=152dp
        // dans ce cas) — sinon rail/vignettes/bulle se chevauchaient tous au même bord gauche.
        Column(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                .padding(start = DualMusicTheme.spacing.md, top = if (thumbsVisible) 200.dp else 120.dp),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            CompCircleBtn(Icons.Filled.VisibilityOff, Color.Black.copy(alpha = 0.4f)) { hideOverlay = true }
            // Modérateurs (organisateur uniquement) : accessible dès l'entrée dans la room, avant
            // même le début de la diffusion — contrairement aux icônes ci-dessous (publieur only).
            if (isManager) {
                CompCircleBtn(Icons.Filled.Shield, Color.Black.copy(alpha = 0.4f)) { showModerators = true }
            }
            if (canPublish) {
                if (!broadcasting) {
                    CompCircleBtn(Icons.Filled.Podcasts, colors.primary) {
                        if (hasPerms()) viewModel.startBroadcast() else broadcastLauncher.launch(perms)
                    }
                } else {
                    CompCircleBtn(Icons.Filled.Settings, Color.Black.copy(alpha = 0.4f)) { showCompSettings = true }
                    CompCircleBtn(Icons.Filled.AutoAwesome, Color.Black.copy(alpha = 0.4f)) { showFilters = true }
                }
                // Pub sponsor : icône du rail (au lieu du bouton texte qui recouvrait la ligne de
                // message) — réservée à l'organisateur (parité web `canTrigger = isManager`).
                if (isManager) {
                    CompCircleBtn(Icons.Filled.Campaign, Color.Black.copy(alpha = 0.4f)) {
                        if (sponsorAd != null) viewModel.sponsor.stop() else { viewModel.sponsor.loadAds(); showAdPicker = true }
                    }
                }
                // Enregistrement : icône DÉDIÉE (distincte du bouton « Pause » des Réglages, avec
                // lequel elle était confondue — même correctif que concert/live) — pastille
                // rouge/orange dès qu'un segment tourne ou est en pause. Masquée si l'admin a
                // coupé l'enregistrement pour les compétitions.
                if (isManager && recMode == "manual") {
                    Box {
                        CompCircleBtn(Icons.Filled.FiberManualRecord, Color.Black.copy(alpha = 0.4f)) { showRecordingSheet = true }
                        com.dualmusic.feature.sponsor.RecordingRailBadge(
                            active = recActive, paused = recPaused,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                    }
                } else if (isManager && recMode == "auto") {
                    com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = {})
                }
            }
        }

        // Réglages (organisateur / candidat publieur) : caméra/micro/flip/pause regroupés dans
        // une feuille — parité concert/live, évite un rail à rallonge qui chevauchait le nom du
        // meilleur donateur.
        if (showCompSettings) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCompSettings = false })
            val paused = !camOn && !micOn
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎛️ Réglages", color = colors.foreground, fontWeight = FontWeight.Bold)
                CompSettingsRow(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (camOn) "Caméra activée" else "Caméra coupée", if (camOn) colors.primary else colors.destructive) { viewModel.toggleCamera() }
                CompSettingsRow(Icons.Filled.Cameraswitch, "Retourner la caméra", Color.Black.copy(alpha = 0.35f)) { viewModel.flipCamera() }
                CompSettingsRow(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (micOn) "Micro activé" else "Micro coupé", if (micOn) colors.primary else colors.destructive) { viewModel.toggleMic() }
                CompSettingsRow(if (paused) Icons.Filled.Podcasts else Icons.Filled.Pause, if (paused) "Reprendre caméra/micro" else "Couper caméra & micro", Color(0xFFEAB308)) { viewModel.togglePause() }
                // Chat activé/désactivé pour TOUS — réservé à l'organisateur (jamais aux modérateurs désignés).
                if (isManager) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.15f)).padding(horizontal = DualMusicTheme.spacing.md, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (chatEnabled) "Chat activé" else "Chat désactivé", color = colors.foreground, fontWeight = FontWeight.Bold)
                        Switch(checked = chatEnabled, onCheckedChange = { viewModel.toggleChat(it) })
                    }
                }
                // Masquer/réafficher les autres cases pour TOUS (spotlight imposé, distinct du
                // masquage auto quand une caméra est épinglée) — réservé à l'organisateur.
                if (isManager && thumbs.isNotEmpty()) {
                    CompSettingsRow(
                        if (forcedHideOthers) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        if (forcedHideOthers) "Réafficher les autres cases (pour tous)" else "Masquer les autres cases (pour tous)",
                        Color.Black.copy(alpha = 0.35f),
                    ) { viewModel.toggleHideOthers() }
                }
                // « Terminer la compétition » : réservé à l'organisateur, clôture le classement
                // pour TOUS (finalise + annonce le vainqueur) — différent du Quitter (Logout) de
                // la barre du haut qui ne fait que fermer l'écran (parité concert).
                if (isManager) {
                    CompSettingsRow(Icons.Filled.Close, "Terminer la compétition", colors.destructive) { showCompSettings = false; viewModel.finalize() }
                }
            }
        }

        // Modérateurs désignés (organisateur) : liste + révocation, et picker des spectateurs connectés.
        if (showModerators) {
            CompetitionEventModeratorsDialog(
                isManager = isManager,
                moderators = moderators,
                viewers = viewers,
                onLoadViewers = { viewModel.loadViewers() },
                onAppoint = { viewModel.appointModerator(it) },
                onRevoke = { viewModel.revokeModerator(it) },
                onDismiss = { showModerators = false },
            )
        }

        // Sélecteur de pub sponsor (organisateur) — ouvert par l'icône 📢 du rail.
        if (showAdPicker) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showAdPicker = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("📢 Lancer une pub", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (sponsorAds.isEmpty()) Text("Aucune pub disponible.", color = colors.mutedForeground, fontSize = 13.sp)
                sponsorAds.forEach { ad ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.15f))
                            .clickable { viewModel.sponsor.play(ad.id); showAdPicker = false }.padding(DualMusicTheme.spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ad.title, color = colors.foreground)
                        Text("${ad.durationSeconds}s", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }


        // Feuille DÉDIÉE à l'enregistrement (manager) : chrono + Pause/Reprendre/Annuler/Sauvegarder.
        // Extraite en fonction locale (comme les autres feuilles de cet écran) pour rester sous la
        // limite de taille de méthode JVM (64 Ko) une fois toutes les fonctionnalités cumulées.
        @Composable fun RecordingSheet() {
        if (showRecordingSheet) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showRecordingSheet = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🔴 Enregistrement de la compétition", color = colors.foreground, fontWeight = FontWeight.Bold)
                com.dualmusic.feature.sponsor.RecordingSessionControls(
                    mode = recMode,
                    active = recActive,
                    paused = recPaused,
                    finalizing = recFinalizing,
                    accumulatedSeconds = recAccumulatedSeconds,
                    runStartedAt = recRunStartedAt,
                    busy = recBusy,
                    onStart = { viewModel.recordingCtl.start(onError = ::showRecordingError) },
                    onPause = { viewModel.recordingCtl.pause(onError = ::showRecordingError) },
                    onResume = { viewModel.recordingCtl.resume(onError = ::showRecordingError) },
                    onCancel = { showCancelRecordingConfirm = true },
                    onSave = { viewModel.recordingCtl.save(onError = ::showRecordingError); showRecordingSheet = false },
                )
            }
        }
        }
        RecordingSheet()

        // Confirmation d'annulation d'enregistrement (destructif : rien n'est recollé/publié).
        @Composable fun CancelRecordingConfirmSheet() {
        if (showCancelRecordingConfirm) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { showCancelRecordingConfirm = false })
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp))
                    .background(colors.background).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Text("Annuler l'enregistrement ?", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Tout ce qui a été enregistré jusqu'ici sera définitivement perdu — aucun replay ne sera créé.",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Box(modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp)).clickable { showCancelRecordingConfirm = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Retour", color = colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.weight(1f).background(colors.destructive, RoundedCornerShape(999.dp)).clickable { viewModel.recordingCtl.cancel(onError = ::showRecordingError); showCancelRecordingConfirm = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Annuler l'enregistrement", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
        CancelRecordingConfirmSheet()

        // --- Classement (panneau bas) : candidats + vote/cadeau + actions organisateur ---
        if (showLeaderboard) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showLeaderboard = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.6f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(strings.ranking, color = colors.foreground, fontWeight = FontWeight.Bold)
                // Onglets Compétiteurs / Donateurs (fusion des 2 panneaux séparés d'avant — voir
                // le commentaire sur l'icône Trophy ci-dessus).
                var leaderboardTab by remember { mutableStateOf("candidates") }
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(
                        strings.ranking,
                        style = if (leaderboardTab == "candidates") DMButtonStyle.PRIMARY else DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = { leaderboardTab = "candidates" },
                    )
                    DMButton(
                        "Donateurs",
                        style = if (leaderboardTab == "donors") DMButtonStyle.PRIMARY else DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = { leaderboardTab = "donors" },
                    )
                }
                if (leaderboardTab == "donors") {
                    if (donorLeaderboard.isEmpty()) {
                        Text(strings.noCandidatesHint, color = colors.mutedForeground, fontSize = 13.sp)
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                            donorLeaderboard.forEachIndexed { i, entry ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${medal(i + 1)} ${entry.displayName}", color = colors.foreground)
                                    Text("${entry.value} ${strings.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    return@Column
                }
                if (isManager) {
                    // Le score de chaque candidat inclut déjà les voix en ligne + les voix de
                    // jury saisies ci-dessous (parité web) — utile pour situer les compétiteurs
                    // en ligne comme en présentiel avant d'annoncer ou de clôturer.
                    Text(
                        "Ajoutez les voix cumulées du jury à chaque candidat ci-dessous : elles s'ajoutent aux votes en ligne dans le classement.",
                        color = colors.mutedForeground, fontSize = 11.sp,
                    )
                    // Annonce du vainqueur (célébration synchronisée + applaudissements, sans clôturer) —
                    // désigne automatiquement le candidat en tête du score (votes + jury).
                    DMButton("🏆 Annoncer le vainqueur", modifier = Modifier.fillMaxWidth(), onClick = { viewModel.announceWinnerAuto(); showLeaderboard = false })
                    // « Finaliser » : verrouille le classement définitif et CLÔTURE la compétition
                    // (contrairement à l'annonce ci-dessus, qui ne fait que célébrer sans rien clore).
                    // Le bouton « Publier » n'a plus sa place ici : une compétition déjà en direct
                    // est par définition déjà publiée (sinon on ne serait pas dans cette salle) —
                    // il ne faisait qu'échouer silencieusement, d'où la confusion signalée.
                    DMButton(
                        "🏁 ${strings.finalizeAction} (clôture définitive)",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.finalize() },
                    )
                }
                if (candidates.isEmpty()) Text(strings.noCandidatesHint, color = colors.mutedForeground, fontSize = 13.sp)
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    itemsIndexed(candidates) { index, candidate ->
                        CandidateRow(
                            rank = index + 1,
                            candidate = candidate,
                            voteCredits = votePrice,
                            isManager = isManager,
                            muted = mutedArtists.contains(candidate.artistId),
                            onVote = { viewModel.vote(candidate.id, votePrice) },
                            onGift = { giftTargetCandidate = candidate.id },
                            onApprove = { viewModel.reviewCandidate(candidate.id, true) },
                            onReject = { viewModel.reviewCandidate(candidate.id, false) },
                            onPerformer = { viewModel.setPerformer(candidate.id, 120) },
                            onToggleMute = { viewModel.toggleMuteArtist(candidate.artistId) },
                            onOpenArtist = { onOpenArtist(candidate.artistId) },
                            onSetJuryVotes = { v -> viewModel.setJuryVotes(candidate.id, v) },
                            onBan = { viewModel.banUser(candidate.artistId, "Banni par l'organisateur") },
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
                    // Icône ✉️ dédiée pour envoyer (le reste de la ligne n'envoie plus rien au
                    // tap — évite la confusion « je clique pour voir » vs « pour envoyer »,
                    // parité concert/live).
                    inventory.forEach { g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(DualMusicTheme.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${g.imageUrl ?: "🎁"}  ${g.name ?: "Cadeau"}", color = colors.foreground)
                                Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier.size(38.dp).background(colors.primary, CircleShape)
                                    .clickable {
                                        viewModel.sendGift(candidateId, g.giftId, g.price.toInt())
                                        giftTargetCandidate = null
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Send, contentDescription = "Envoyer", tint = Color.White, modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }

        // Panneau « à qui envoyer ce cadeau » : sélecteur de candidat par NOM (champ « select »,
        // parité web CompetitionGiftPanel) — étape préalable à l'inventaire (`giftTargetCandidate`),
        // pour ne plus obliger à ouvrir le classement et taper un candidat précis.
        if (showGiftPicker) {
            // Seuls les candidats RETENUS sont ciblables — même repli que le panneau de vote.
            val giftable = candidates.filter { it.status == "approved" }.ifEmpty { candidates }
            var giftPickerExpanded by remember { mutableStateOf(false) }
            var giftPickCandidateId by remember(giftable) { mutableStateOf(giftable.firstOrNull()?.id) }
            val giftPickName = giftable.find { it.id == giftPickCandidateId }?.artist?.displayName
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showGiftPicker = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎁 ${strings.sendGift}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).clickable { giftPickerExpanded = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(giftPickName ?: "Choisir un candidat", color = colors.foreground)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.mutedForeground)
                    }
                    DropdownMenu(expanded = giftPickerExpanded, onDismissRequest = { giftPickerExpanded = false }) {
                        giftable.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.artist?.displayName ?: "Candidat") },
                                onClick = { giftPickCandidateId = c.id; giftPickerExpanded = false },
                            )
                        }
                    }
                }
                DMButton(strings.sendGift, modifier = Modifier.fillMaxWidth(), onClick = {
                    giftPickCandidateId?.let { giftTargetCandidate = it }
                    showGiftPicker = false
                })
            }
        }

        // Panneau de VOTE (spectateur) : sélecteur de candidat par NOM + crédits. Indispensable en
        // présentiel (onsite) où il n'y a pas de caméra à taper, et pratique en ligne quand les
        // candidats sont nombreux — parité web CompetitionVotePanel.
        if (showVotePanel) {
            // Seuls les candidats RETENUS (approuvés) sont votables — repli sur toute la liste si
            // l'approbation n'est pas utilisée pour cette compétition (parité web).
            val votable = candidates.filter { it.status == "approved" }.ifEmpty { candidates }
            var voteExpanded by remember { mutableStateOf(false) }
            var selectedCandidateId by remember(votable) { mutableStateOf(votable.firstOrNull()?.id) }
            var creditsText by remember { mutableStateOf(votePrice.toString()) }
            val selectedName = votable.find { it.id == selectedCandidateId }?.artist?.displayName
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showVotePanel = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(strings.vote, color = colors.foreground, fontWeight = FontWeight.Bold)
                if (mode == "onsite") {
                    Text("Présentiel : choisis un candidat dans la liste pour voter.", color = colors.mutedForeground, fontSize = 12.sp)
                }
                // Champ « select » : nom du candidat choisi + menu déroulant des candidats.
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).clickable { voteExpanded = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selectedName ?: "Choisir un candidat", color = colors.foreground)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.mutedForeground)
                    }
                    DropdownMenu(expanded = voteExpanded, onDismissRequest = { voteExpanded = false }) {
                        votable.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.artist?.displayName ?: "Candidat"}  ·  ${c.score.toInt()} pts") },
                                onClick = { selectedCandidateId = c.id; voteExpanded = false },
                            )
                        }
                    }
                }
                // Montant en crédits (numérique) + raccourcis 1 / 5 / 10.
                OutlinedTextField(
                    value = creditsText,
                    onValueChange = { v -> creditsText = v.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(strings.credits) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    listOf(1, 5, 10).forEach { q ->
                        DMButton("$q", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { creditsText = q.toString() })
                    }
                }
                DMButton(strings.vote, modifier = Modifier.fillMaxWidth(), onClick = {
                    val cid = selectedCandidateId
                    val credits = creditsText.toIntOrNull() ?: votePrice
                    if (cid != null && credits > 0) { viewModel.vote(cid, credits); showVotePanel = false }
                })
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

        // Diffusion pub sponsor : overlay vidéo pour tous ; le déclencheur est l'icône 📢 du rail
        // gauche (showTriggerButton = false) — évite le bouton texte qui recouvrait le chat (parité concert).
        SponsorAdLayer(
            activeAd = sponsorAd,
            canTrigger = isManager,
            showTriggerButton = false,
            ads = sponsorAds,
            busy = sponsorBusy,
            onLoadAds = { viewModel.sponsor.loadAds() },
            onPlay = { viewModel.sponsor.play(it) },
            onStop = { viewModel.sponsor.stop() },
        )

        // Carte « cadeau reçu » glissante (fil) — état hissé HORS de ce `Box` (voir plus haut,
        // juste après `DisposableEffect(Unit){ viewModel.start() }`) : parité concert.
        AnimatedVisibility(
            visible = giftVisible && !uiPrefs.reduceAnimations,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 300.dp),
        ) { shownGift?.let { GiftReceivedCard(it) } }

        // (La bannière « vous avez reçu un cadeau » est retirée — parité concert/live : la carte
        // glissante ci-dessus, avec le nom de l'expéditeur, suffit déjà comme seule animation.)

        // Célébration du vainqueur annoncé par le manager (n'arrête PAS le direct) + applaudissements.
        winnerInfo?.let { w ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                WinnerCelebration(
                    winnerName = w.name,
                    title = strings.winnerTitle,
                    subtitle = "${w.votes} voix · ${w.percent}%",
                    reduceAnimations = uiPrefs.reduceAnimations,
                )
                if (!uiPrefs.reduceAnimations) ScatteredEmojiLayer(winnerEmojis)
                if (isManager) {
                    DMButton(
                        "Arrêter l'annonce",
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 150.dp),
                    ) { viewModel.stopWinnerAnnouncement() }
                }
            }
        }

        // Écran de blocage : le manager m'a banni → je ne peux plus participer ni rejoindre.
        if (iAmBanned) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding().navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(64.dp))
                Text("Accès bloqué", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "Le manager vous a banni de cette compétition. Vous ne pouvez plus y participer ni la rejoindre.",
                    color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                DMButton("Quitter", modifier = Modifier.fillMaxWidth(0.7f), onClick = onLeave)
            }
        }

        // Confirmation de bannissement d'un spectateur (manager).
        banTarget?.let { target ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { banTarget = null })
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp))
                    .background(colors.background).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Text("🚫 Bannir ${target.authorName} ?", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Ce spectateur ne pourra plus écrire dans ce direct ni le rejoindre, et ses messages seront masqués pour tous.",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton("Annuler", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f)) { banTarget = null }
                    DMButton("Bannir", modifier = Modifier.weight(1f)) {
                        viewModel.banUser(target.userId, target.content.take(200)); banTarget = null
                    }
                }
            }
        }

        // Popup commentaire / réponse (parité duel) : cite le message parent + envoie.
        if (showCommentPopup) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCommentPopup = false; replyingTo = null })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background)
                    .navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (replyingTo != null) "Répondre" else "Commenter", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "Emojis", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showChatEmoji = !showChatEmoji })
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showCommentPopup = false; replyingTo = null })
                    }
                }
                // Sélecteur d'emojis : ajoute l'emoji au texte du commentaire (parité web/concert/live).
                if (showChatEmoji) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                    ) {
                        ChatComposeEmojis.forEach { e ->
                            Text(e, fontSize = 22.sp, modifier = Modifier.clickable { draft += e })
                        }
                    }
                }
                replyingTo?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(30.dp).background(colors.accent))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("↩ ${r.authorName}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(r.content, color = colors.mutedForeground, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Filled.Close, contentDescription = "Annuler la réponse", tint = colors.mutedForeground, modifier = Modifier.size(18.dp).clickable { replyingTo = null })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(strings.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            if (!iAmBanned && chatEnabled && draft.isNotBlank()) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }
                            showCommentPopup = false; replyingTo = null
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    // Icône flèche d'envoi (au lieu d'un gros bouton texte pleine largeur).
                    Box(
                        modifier = Modifier.size(44.dp).background(colors.primary, CircleShape)
                            .clickable(enabled = draft.isNotBlank() && chatEnabled) {
                                if (!iAmBanned && chatEnabled && draft.isNotBlank()) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }
                                showCommentPopup = false; replyingTo = null
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Send, contentDescription = if (replyingTo != null) "Répondre" else "Envoyer", tint = Color.White) }
                }
            }
        }

        // Sélecteur de filtres vidéo (publieur) — parité duel.
        if (showFilters && broadcasting) {
            Box(Modifier.fillMaxSize().clickable { showFilters = false })
            Column(
                modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 62.dp)
                    .width(250.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Filtres", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dualmusic.core.media.VideoFilterPresets.all.forEach { f ->
                        val selected = activeFilter == f.id
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.45f))
                                .clickable { viewModel.setColorFilter(f.id, f.matrix); showFilters = false },
                            contentAlignment = Alignment.Center,
                        ) { Text(f.emoji, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}

/** Emojis d'acclamation ÉPARPILLÉS et animés sur tout l'écran pendant l'annonce du vainqueur. */
@Composable
private fun ScatteredEmojiLayer(emojis: List<Pair<Long, String>>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        emojis.forEach { (id, e) ->
            key(id) {
                val transition = rememberInfiniteTransition(label = "sc")
                val drift by transition.animateFloat(
                    initialValue = 0f, targetValue = -36f,
                    animationSpec = infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse),
                    label = "scy",
                )
                val bx = ((id * 73) % 100).toFloat() / 100f * (w - 40f)
                val by = ((id * 137) % 100).toFloat() / 100f * (h - 60f)
                Text(e, fontSize = 30.sp, modifier = Modifier.graphicsLayer { translationX = bx; translationY = by + drift })
            }
        }
    }
}

/** Carte glissante « cadeau reçu » (image réelle + valeur) — parité duel. */
@Composable
private fun GiftReceivedCard(g: CompGift) {
    val infinite = rememberInfiniteTransition(label = "gift")
    val scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "giftScale",
    )
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xF2FF4FA3), Color(0xF27C3AED))))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.dualmusic.core.ui.components.DMRemoteImage(
            url = g.image?.takeIf { it.startsWith("http") },
            contentDescription = g.name,
            modifier = Modifier.size(34.dp).scale(scale),
            fallbackEmoji = g.image?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "🎁",
        )
        Column {
            Text(g.name ?: "Cadeau reçu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(
                "${g.fromUserName ?: "Quelqu'un"} · +${g.value.toInt()} crédits",
                color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
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

/** Ligne pleine largeur de la feuille « Réglages » (icône + libellé, fond coloré) — parité concert/live. */
@Composable
private fun CompSettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text("  $label", color = Color.White, fontWeight = FontWeight.Bold)
    }
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
    muted: Boolean,
    onVote: () -> Unit,
    onGift: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPerformer: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenArtist: () -> Unit,
    onSetJuryVotes: (Int) -> Unit,
    onBan: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    var juryDraft by remember(candidate.id, candidate.juryVotes) { mutableStateOf(candidate.juryVotes.toString()) }
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    // Tap sur le nom → profil public de l'artiste (parité duel).
                    Text(
                        "${medal(rank)} ${candidate.artist?.displayName ?: strings.artistSingular}",
                        color = colors.foreground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenArtist() },
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
            // Actions de l'organisateur : valider/rejeter (candidat en attente) ou désigner le performeur + mute.
            if (isManager) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), modifier = Modifier.padding(top = DualMusicTheme.spacing.sm)) {
                    when (candidate.status) {
                        "pending" -> {
                            DMButton(strings.approveAction, modifier = Modifier.weight(1f), onClick = onApprove)
                            DMButton(strings.rejectAction, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onReject)
                        }
                        "approved" -> {
                            DMButton(strings.setPerformerAction, style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f), onClick = onPerformer)
                            // Couper/réactiver le micro de ce candidat (hard-mute, ne touche pas sa caméra).
                            DMButton(
                                if (muted) "🔊" else "🔇",
                                style = if (muted) DMButtonStyle.SECONDARY else DMButtonStyle.OUTLINE,
                                onClick = onToggleMute,
                            )
                            // Bannir : arrête sa diffusion pour tous, il ne peut plus rejoindre.
                            DMButton("🚫", style = DMButtonStyle.OUTLINE, onClick = onBan)
                        }
                    }
                }
            }
            // Voix cumulées d'un jury hors ligne — additionnées au score (en ligne ET présentiel).
            if (isManager && candidate.status == "approved") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = DualMusicTheme.spacing.xs),
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = juryDraft,
                        onValueChange = { v -> juryDraft = v.filter { it.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Voix jury", fontSize = 11.sp) },
                        modifier = Modifier.width(110.dp),
                    )
                    DMButton(
                        "OK",
                        style = DMButtonStyle.OUTLINE,
                        onClick = { onSetJuryVotes(juryDraft.toIntOrNull() ?: 0) },
                    )
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

/**
 * Modérateurs désignés de la compétition (jusqu'à [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS])
 * — mêmes pouvoirs de bannissement/masquage que l'organisateur, JAMAIS le toggle chat (réservé au
 * manager). Parité web (panneau modération) : liste des modérateurs + révocation ; côté manager
 * uniquement, un picker des spectateurs actuellement connectés pour en désigner de nouveaux.
 */
@Composable
private fun CompetitionEventModeratorsDialog(
    isManager: Boolean,
    moderators: List<EventModerator>,
    viewers: List<DisplayProfile>,
    onLoadViewers: () -> Unit,
    onAppoint: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    LaunchedEffect(Unit) { if (isManager) onLoadViewers() }
    val moderatorIds = remember(moderators) { moderators.map { it.userId }.toSet() }
    val atLimit = moderators.size >= com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛡️ Modérateurs") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("Modérateurs désignés (${moderators.size}/${com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS})", color = colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (moderators.isEmpty()) {
                    Text("Aucun modérateur désigné.", color = colors.mutedForeground, fontSize = 13.sp)
                } else {
                    moderators.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.displayName, color = colors.foreground, fontSize = 13.sp)
                            if (isManager) {
                                Icon(
                                    Icons.Filled.Close, contentDescription = "Révoquer", tint = colors.destructive,
                                    modifier = Modifier.size(18.dp).clickable { onRevoke(m.userId) },
                                )
                            }
                        }
                    }
                }
                if (isManager) {
                    Text("Spectateurs connectés", color = colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val eligible = viewers.filter { it.id !in moderatorIds }
                    if (eligible.isEmpty()) {
                        Text("Aucun spectateur éligible.", color = colors.mutedForeground, fontSize = 13.sp)
                    } else {
                        eligible.forEach { v ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v.displayName, color = colors.foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                CompModeratorAppointButton(enabled = !atLimit) { onAppoint(v.id) }
                            }
                        }
                    }
                    if (atLimit) Text("Limite de ${com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS} modérateurs atteinte.", color = colors.mutedForeground, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Box(modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)) { Text("Fermer", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
    )
}

/** Petit bouton texte pilule (« Nommer ») — évite d'importer `DMButton` juste pour ce cas ponctuel. */
@Composable
private fun CompModeratorAppointButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (enabled) colors.primary else colors.mutedForeground.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text("Nommer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

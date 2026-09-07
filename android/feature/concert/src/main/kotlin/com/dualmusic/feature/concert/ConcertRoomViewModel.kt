package com.dualmusic.feature.concert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.model.VirtualGift
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.realtime.BroadcastEnvelope
import com.dualmusic.domain.realtime.ChatMessagePayload
import com.dualmusic.domain.realtime.EventModeratorPayload
import com.dualmusic.domain.realtime.EventSettingsPayload
import com.dualmusic.domain.realtime.GiftPayload
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cadeau reçu en direct dans le concert (burst + carte glissante). */
data class ConcertGift(
    val id: Long,
    val fromUserId: String?,
    /** Nom de l'expéditeur (résolu via le classement des donateurs, hydraté avec les profils). */
    val fromUserName: String?,
    val name: String?,
    val image: String?,
    val value: Double,
)

/**
 * ViewModel de la room de concert (viewer + hôte artiste).
 *
 * Miroir du live, contexte `concert` : vidéo LiveKit (room `concert-<id>`), chat
 * (`/concerts/:id/messages` + `/chat` room `concert:<id>`), cadeaux (contextType concert),
 * réactions (`concert-emojis-<id>`), présence, likes (réutilise l'endpoint des lives) et
 * **billetterie** (un billet est requis pour regarder un concert payant, sauf l'hôte).
 *
 * @param concertId id du concert.
 * @param media client média (une connexion SFU par room).
 * @param realtime client Socket.IO partagé.
 * @param repository lectures + actions REST du concert.
 * @param hostUserId id de l'artiste (destinataire des cadeaux + qui a le droit de diffuser).
 * @param ticketPrice prix du billet (0 = gratuit).
 */
class ConcertRoomViewModel(
    private val concertId: String,
    val media: LiveRoomClient,
    private val realtime: RealtimeClient,
    private val repository: ConcertRepository,
    val hostUserId: String,
    private val ticketPrice: Double,
    sponsorAds: com.dualmusic.feature.sponsor.SponsorAdRepository,
    recording: com.dualmusic.feature.sponsor.RecordingRepository,
) : ViewModel() {

    val roomName: String = "concert-$concertId"

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle hôte artiste). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("concert", concertId, sponsorAds, viewModelScope)

    /** État + action d'enregistrement serveur (bouton hôte en mode manual). */
    val recordingCtl = com.dualmusic.feature.sponsor.RecordingHolder("concert", concertId, recording, viewModelScope)

    /** Vrai si l'utilisateur courant est l'artiste organisateur (déterminé au démarrage). */
    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _messages = MutableStateFlow<List<ConcertChatMessage>>(emptyList())
    val messages: StateFlow<List<ConcertChatMessage>> = _messages.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _likes = MutableStateFlow(0)
    val likes: StateFlow<Int> = _likes.asStateFlow()

    private val _emojiFeed = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val emojiFeed: StateFlow<List<Pair<Long, String>>> = _emojiFeed.asStateFlow()
    private var emojiCounter = 0L

    private val _giftFeed = MutableStateFlow<List<ConcertGift>>(emptyList())
    val giftFeed: StateFlow<List<ConcertGift>> = _giftFeed.asStateFlow()
    private var giftCounter = 0L

    private val _giftCatalog = MutableStateFlow<List<VirtualGift>>(emptyList())
    val giftCatalog: StateFlow<List<VirtualGift>> = _giftCatalog.asStateFlow()

    private val _inventory = MutableStateFlow<List<OwnedConcertGift>>(emptyList())
    val inventory: StateFlow<List<OwnedConcertGift>> = _inventory.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<ConcertDonorEntry>>(emptyList())
    val leaderboard: StateFlow<List<ConcertDonorEntry>> = _leaderboard.asStateFlow()

    /** Meilleur donateur courant (bulle top-donateur). Rechargé à chaque cadeau. */
    private val _topDonor = MutableStateFlow<com.dualmusic.core.ui.overlay.TopDonor?>(null)
    val topDonor: StateFlow<com.dualmusic.core.ui.overlay.TopDonor?> = _topDonor.asStateFlow()

    /** Billet requis pour regarder : concert payant, non-hôte, pas encore de billet. */
    private val _needsTicket = MutableStateFlow(false)
    val needsTicket: StateFlow<Boolean> = _needsTicket.asStateFlow()

    val ticketPriceValue: Double get() = ticketPrice

    /** Diffusion en cours (hôte). */
    private val _broadcasting = MutableStateFlow(false)
    val broadcasting: StateFlow<Boolean> = _broadcasting.asStateFlow()

    /** Id de l'utilisateur courant (pour bannissement + cadeau reçu + détection modérateur). */
    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    /** Spectateurs bannis de ce direct (ids). Masque leurs messages + bloque le rejoint. */
    private val _bannedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val bannedUserIds: StateFlow<Set<String>> = _bannedUserIds.asStateFlow()

    /** Vrai si MOI je suis banni → écran de blocage plein écran. */
    val iAmBanned: StateFlow<Boolean> =
        combine(_bannedUserIds, _myUserId) { banned, id -> id != null && banned.contains(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Filtre couleur vidéo actif de l'hôte (parité duel). */
    private val _activeFilter = MutableStateFlow("none")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    /** Chat activé/désactivé par l'hôte (bascule `PATCH /artist-concerts/:id {chatEnabled}`). */
    private val _chatEnabled = MutableStateFlow(true)
    val chatEnabled: StateFlow<Boolean> = _chatEnabled.asStateFlow()

    /** Modérateurs désignés par l'hôte (max 2, mêmes pouvoirs de bannissement que lui). */
    private val _moderators = MutableStateFlow<List<EventModerator>>(emptyList())
    val moderators: StateFlow<List<EventModerator>> = _moderators.asStateFlow()

    /** Spectateurs actuellement connectés (vivier du picker de modérateurs — hôte uniquement). */
    private val _viewers = MutableStateFlow<List<DisplayProfile>>(emptyList())
    val viewers: StateFlow<List<DisplayProfile>> = _viewers.asStateFlow()

    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /** Démarre : détermine l'hôte, gère le billet, rejoint la vidéo, charge le contenu + temps réel. */
    fun start() {
        viewModelScope.launch {
            // Hôte = l'artiste organisateur (droit de diffuser, exempté de billet).
            val myId = runCatching { repository.myUserId() }.getOrNull()
            _myUserId.value = myId
            val host = myId != null && myId == hostUserId
            _isHost.value = host
            // Billetterie : re-vérifie côté serveur (le billet a pu être acheté ailleurs).
            if (!host && ticketPrice > 0.0) {
                val info = runCatching { repository.ticketInfo(concertId) }.getOrNull()
                _needsTicket.value = info?.hasTicket != true
            }
            if (!_needsTicket.value) joinMedia(host)
            runCatching { repository.chatHistory(concertId) }.getOrNull()?.let { _messages.value = it }
            _likes.value = repository.likesCount(concertId)
            _bannedUserIds.value = runCatching { repository.listStreamBans(concertId) }.getOrDefault(emptyList()).toSet()
            runCatching { repository.giftCatalog() }.getOrNull()?.let { _giftCatalog.value = it }
            // Réglage chat + modérateurs désignés (état initial ; le temps réel prend le relais ensuite).
            runCatching { repository.concert(concertId) }.getOrNull()?.let { _chatEnabled.value = it.chatEnabled }
            loadInventory()
            loadGiftLeaderboard()
            loadModerators()
        }
        recordingCtl.startPolling()
        connectRealtime()
    }

    private fun joinMedia(host: Boolean = _isHost.value) {
        viewModelScope.launch { runCatching { media.join(roomName = roomName, isHost = host, canPublish = host) } }
    }

    /** Arrête tout (sortie d'écran). */
    fun stop() {
        liveSession?.disconnect()
        chatSession?.disconnect()
        media.leave()
    }

    /** Achète un billet puis rejoint la vidéo. */
    fun buyTicket() {
        viewModelScope.launch {
            runCatching { repository.buyTicket(concertId) }.onSuccess {
                _needsTicket.value = false
                joinMedia()
            }
        }
    }

    /** Hôte : démarre la diffusion caméra/micro et passe le concert en direct. */
    fun startBroadcast() {
        viewModelScope.launch {
            // Le join initial (dans start()) peut avoir échoué SILENCIEUSEMENT (réseau lent,
            // timing — `LiveRoomClient.join()` avale ses propres erreurs et se contente de
            // passer `connectionState` à `Failed`, sans jamais lever d'exception). `startBroadcast()`
            // appelait alors la publication caméra sur une room jamais connectée, qui échouait à
            // son tour — MAIS `_broadcasting` passait quand même à `true` (le `runCatching` avalait
            // aussi CET échec) : l'écran se croyait en direct sans qu'aucune caméra ne soit publiée,
            // et rien ne permettait de réessayer sans quitter puis rerejoindre l'écran. On (re)joint
            // explicitement si la room n'est pas connectée, et on ne bascule `broadcasting` que si
            // la publication a RÉELLEMENT réussi — sinon le bouton « Démarrer » reste affiché pour
            // un nouvel essai, sur place.
            if (media.connectionState.value !is com.dualmusic.core.media.LiveConnectionState.Connected) {
                // Appel direct (et non `joinMedia()`, qui relance sa propre coroutine en tâche de
                // fond sans l'attendre) : il faut que la connexion soit VRAIMENT établie avant de
                // tenter la publication caméra juste en dessous.
                media.join(roomName = roomName, isHost = true, canPublish = true)
            }
            val started = media.connectionState.value is com.dualmusic.core.media.LiveConnectionState.Connected &&
                runCatching { media.startBroadcast() }.isSuccess
            if (started) {
                runCatching { repository.goLive(concertId) }
                _broadcasting.value = true
            }
        }
    }

    /** Hôte : termine le concert (statut `ended`) puis notifie l'appelant. */
    fun endConcert(onEnded: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.endConcert(concertId) }
            onEnded()
        }
    }

    /** Hôte : coupe/rétablit la caméra. */
    fun toggleCamera() {
        media.setCamEnabled(!media.camEnabled.value)
    }

    /** Hôte : coupe/rétablit le micro. */
    fun toggleMic() {
        viewModelScope.launch { runCatching { media.setMicEnabled(!media.micEnabled.value) } }
    }

    /** Hôte : bascule caméra avant/arrière. */
    fun flipCamera() {
        viewModelScope.launch { runCatching { media.switchCamera() } }
    }

    /** Hôte : PAUSE (coupe caméra + micro) / REPRENDRE. */
    fun togglePause() {
        val resume = !media.camEnabled.value && !media.micEnabled.value
        media.setCamEnabled(resume)
        viewModelScope.launch { runCatching { media.setMicEnabled(resume) } }
    }

    /** Hôte : applique un filtre couleur vidéo (parité duel). */
    fun setColorFilter(id: String, matrix: FloatArray?) {
        media.setColorFilter(id, matrix)
        _activeFilter.value = id
    }

    /** Hôte : bannit un spectateur (optimiste + persistant). Il ne peut plus écrire ni rejoindre. */
    fun banUser(userId: String, reason: String?) {
        _bannedUserIds.update { it + userId }
        viewModelScope.launch {
            runCatching { repository.createStreamBan(concertId, userId, reason) }
                .onFailure { _bannedUserIds.update { ids -> ids - userId } }
        }
    }

    /** Hôte : active/désactive le chat pour tous (optimiste + persistant). */
    fun toggleChat(enabled: Boolean) {
        _chatEnabled.value = enabled
        viewModelScope.launch {
            runCatching { repository.setChatEnabled(concertId, enabled) }
                .onFailure { _chatEnabled.value = !enabled }
        }
    }

    /** Recharge les modérateurs désignés de ce concert. */
    fun loadModerators() {
        viewModelScope.launch { _moderators.value = repository.listEventModerators(concertId) }
    }

    /** Hôte : recharge les spectateurs actuellement connectés (vivier du picker). */
    fun loadViewers() {
        viewModelScope.launch { _viewers.value = repository.listCurrentViewers(concertId) }
    }

    /** Hôte : désigne un spectateur connecté comme modérateur (max 2). */
    fun appointModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.appointModerator(concertId, userId) }.onSuccess { loadModerators() }
        }
    }

    /** Hôte : révoque un modérateur désigné. */
    fun revokeModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.revokeModerator(concertId, userId) }.onSuccess { loadModerators() }
        }
    }

    fun sendMessage(text: String, parentId: String? = null) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(concertId, content, parentId) } }
    }

    /** Signale ce direct à la modération (best-effort ; l'échec reste silencieux). */
    fun report(reason: String) {
        viewModelScope.launch { runCatching { repository.reportLive(concertId, reason) } }
    }

    /** J'aime : compteur + persistance + cœur flottant + compteur partagé (parité web). */
    fun sendLike() {
        _likes.value += 1
        // Compteur partagé sur le canal `concert-likes-<id>` (même mécanisme que le web).
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "concert-likes-$concertId",
                    "event" to "like",
                    "payload" to org.json.JSONObject(mapOf("count" to _likes.value)),
                ),
            ),
        )
        sendReaction("❤️")
        viewModelScope.launch { runCatching { repository.likeConcert(concertId) } }
    }

    fun sendReaction(emoji: String) {
        pushEmoji(emoji)
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "concert-emojis-$concertId",
                    "event" to "emoji_reaction",
                    "payload" to org.json.JSONObject(mapOf("emoji" to emoji)),
                ),
            ),
        )
    }

    private fun pushEmoji(emoji: String) {
        _emojiFeed.update { (it + (emojiCounter++ to emoji)).takeLast(12) }
    }

    fun loadInventory() {
        viewModelScope.launch { runCatching { repository.inventory() }.getOrNull()?.let { _inventory.value = it } }
    }

    fun sendGift(giftId: String, toUserId: String) {
        viewModelScope.launch {
            runCatching { repository.sendGift(concertId, giftId, toUserId) }.onSuccess { loadInventory() }
        }
    }

    fun purchaseGift(giftId: String) {
        viewModelScope.launch { runCatching { repository.purchaseGift(giftId, 1) }.onSuccess { loadInventory() } }
    }

    fun loadGiftLeaderboard() {
        viewModelScope.launch { refreshGiftLeaderboard() }
    }

    /** Recharge le classement (met à jour la bulle top-donateur) ; retourne la liste fraîche. */
    private suspend fun refreshGiftLeaderboard(): List<ConcertDonorEntry> {
        val list = runCatching { repository.giftLeaderboard(concertId) }.getOrDefault(emptyList())
        _leaderboard.value = list
        _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        return list
    }

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }
        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.CONCERT, concertId)
                live.emit("broadcast:join", "concert-emojis-$concertId")
                live.emit("broadcast:join", "concert-likes-$concertId")
            }
            chat.onConnect { chat.join(Realtime.RoomType.CONCERT, concertId) }

            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + ConcertChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user, parentId = p.parentId) }
            }
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                viewModelScope.launch {
                    // Recharge le classement (met à jour la bulle top-donateur) ET en profite pour
                    // résoudre le NOM de l'expéditeur (déjà hydraté avec les profils) → affiché
                    // dans la carte glissante « cadeau reçu » (seule animation ; pas de bannière
                    // en plus, pas de doublon avec la notif push — celle-ci suffit).
                    val list = refreshGiftLeaderboard()
                    val senderName = list.find { it.userId == p.fromUserId }?.displayName
                    _giftFeed.update { it + ConcertGift(giftCounter++, p.fromUserId, senderName, p.giftName, p.giftImage, p.value) }
                }
            }
            // Bannissement d'un spectateur poussé par le serveur (parité duel `stream:banned`).
            live.on("stream:banned", ConcertStreamBannedPayload.serializer()) { p ->
                if (p.streamId == null || p.streamId == concertId) _bannedUserIds.update { it + p.userId }
            }
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            live.on("broadcast", BroadcastEnvelope.serializer()) { env ->
                when (env.event) {
                    "emoji_reaction" -> env.payload?.emoji?.let { pushEmoji(it) }
                    "like" -> env.payload?.count?.let { if (it > _likes.value) _likes.value = it }
                }
            }
            // Statut : fin du concert → on pourrait fermer, mais on laisse l'UI décider.
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { /* statut concert */ }
            // Réglage chat basculé par l'hôte (parité web) — même room pour tous les spectateurs.
            live.on(Realtime.RealtimeEvent.SETTINGS, EventSettingsPayload.serializer()) { p ->
                if (p.concertId == null || p.concertId == concertId) p.chatEnabled?.let { _chatEnabled.value = it }
            }
            // Modérateur désigné/révoqué par l'hôte → resynchronise la liste (droit de bannir).
            live.on(Realtime.RealtimeEvent.MODERATOR_APPOINTED, EventModeratorPayload.serializer()) { p ->
                if (p.eventType == "concert" && p.eventId == concertId) loadModerators()
            }
            live.on(Realtime.RealtimeEvent.MODERATOR_REVOKED, EventModeratorPayload.serializer()) { p ->
                if (p.eventType == "concert" && p.eventId == concertId) loadModerators()
            }
            // Pub sponsor (start/stop) diffusée à toute la room.
            live.on(Realtime.RealtimeEvent.SPONSOR_AD, com.dualmusic.domain.realtime.SponsorAdPayload.serializer()) { p ->
                sponsor.onEvent(p)
            }

            live.connect()
            chat.connect()
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

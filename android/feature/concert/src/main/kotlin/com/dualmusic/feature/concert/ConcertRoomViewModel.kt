package com.dualmusic.feature.concert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.domain.model.VirtualGift
import com.dualmusic.domain.realtime.BroadcastEnvelope
import com.dualmusic.domain.realtime.ChatMessagePayload
import com.dualmusic.domain.realtime.GiftPayload
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cadeau reçu en direct dans le concert (pour l'animation GPU). */
data class ConcertGift(val id: Long, val fromUserId: String?, val value: Double)

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
) : ViewModel() {

    val roomName: String = "concert-$concertId"

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle hôte artiste). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("concert", concertId, sponsorAds, viewModelScope)

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

    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /** Démarre : détermine l'hôte, gère le billet, rejoint la vidéo, charge le contenu + temps réel. */
    fun start() {
        viewModelScope.launch {
            // Hôte = l'artiste organisateur (droit de diffuser, exempté de billet).
            val myId = runCatching { repository.myUserId() }.getOrNull()
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
            runCatching { repository.giftCatalog() }.getOrNull()?.let { _giftCatalog.value = it }
            loadInventory()
            loadGiftLeaderboard()
        }
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
            runCatching { media.startBroadcast() }
            runCatching { repository.goLive(concertId) }
            _broadcasting.value = true
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

    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(concertId, content) } }
    }

    /** J'aime : compteur + persistance + cœur flottant pour tous. */
    fun sendLike() {
        _likes.value += 1
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
        viewModelScope.launch {
            val list = runCatching { repository.giftLeaderboard(concertId) }.getOrDefault(emptyList())
            _leaderboard.value = list
            _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        }
    }

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }
        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.CONCERT, concertId)
                live.emit("broadcast:join", "concert-emojis-$concertId")
            }
            chat.onConnect { chat.join(Realtime.RoomType.CONCERT, concertId) }

            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + ConcertChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                _giftFeed.update { it + ConcertGift(giftCounter++, p.fromUserId, p.value) }
                loadGiftLeaderboard() // met à jour la bulle top-donateur en direct
            }
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            live.on("broadcast", BroadcastEnvelope.serializer()) { env ->
                if (env.event == "emoji_reaction") env.payload?.emoji?.let { pushEmoji(it) }
            }
            // Statut : fin du concert → on pourrait fermer, mais on laisse l'UI décider.
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { /* statut concert */ }
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

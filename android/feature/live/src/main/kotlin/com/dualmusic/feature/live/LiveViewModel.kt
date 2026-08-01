package com.dualmusic.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.domain.realtime.ChatMessagePayload
import com.dualmusic.domain.realtime.GiftPayload
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Payload de l'événement temps réel `likes` (`/live` room event) : total courant. */
@kotlinx.serialization.Serializable
data class LikesPayload(
    @kotlinx.serialization.SerialName("live_id") val liveId: String? = null,
    val likes: Int = 0,
)

/** Enveloppe du relais de broadcast éphémère (`{channel,event,payload}`). */
@kotlinx.serialization.Serializable
data class BroadcastEnvelope(
    val channel: String? = null,
    val event: String? = null,
    val payload: BroadcastPayload? = null,
)

/**
 * Charge utile du relais broadcast — champs souples selon l'événement :
 * `emoji_reaction` → emoji ; `guest_action` → action/targetUserId/targetUserName/value.
 * `value` est polymorphe (secondes du chrono = nombre ; mute = booléen) → JsonElement.
 */
@kotlinx.serialization.Serializable
data class BroadcastPayload(
    val emoji: String? = null,
    val action: String? = null,
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val value: kotlinx.serialization.json.JsonElement? = null,
    val status: String? = null,
)

/** Événement de demande d'invité (`join:new` / `join:update`). */
@kotlinx.serialization.Serializable
data class JoinEventPayload(
    @kotlinx.serialization.SerialName("live_id") val liveId: String? = null,
    @kotlinx.serialization.SerialName("user_id") val userId: String? = null,
    val status: String? = null,
)

/** Emoji flottant à animer (réaction). */
data class FloatingEmoji(val id: Long, val emoji: String)

/** Cadeau reçu à animer dans le live. */
data class LiveGift(
    val id: Long,
    val fromUserId: String?,
    val giftName: String?,
    val giftImage: String?,
    val value: Double,
)

/**
 * Orchestre l'expérience d'un live (viewer) : vidéo LiveKit + chat/cadeaux/présence temps
 * réel (Socket.IO) + actions (message, cadeau).
 *
 * Expose les flux d'état ; l'écran Compose les collecte. La vidéo est exposée via [media]
 * (le rendu utilise les composants LiveKit Compose).
 */
class LiveViewModel(
    private val liveId: String,
    private val roomName: String,
    val media: LiveRoomClient,
    private val realtime: RealtimeClient,
    private val repository: LiveRepository,
    sponsorAds: com.dualmusic.feature.sponsor.SponsorAdRepository,
    /** Vrai pour l'artiste qui DIFFUSE (publie caméra/micro) ; faux pour un spectateur. */
    val isHost: Boolean = false,
) : ViewModel() {

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle hôte). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("live", liveId, sponsorAds, viewModelScope)

    private val _messages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val messages: StateFlow<List<LiveChatMessage>> = _messages.asStateFlow()

    private val _giftFeed = MutableStateFlow<List<LiveGift>>(emptyList())
    val giftFeed: StateFlow<List<LiveGift>> = _giftFeed.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _likes = MutableStateFlow(0)
    val likes: StateFlow<Int> = _likes.asStateFlow()

    /** Compteur d'impulsions pour déclencher l'animation de cœur (incrémenté à chaque like). */
    private val _heartTick = MutableStateFlow(0L)
    val heartTick: StateFlow<Long> = _heartTick.asStateFlow()

    private val _emojiFeed = MutableStateFlow<List<FloatingEmoji>>(emptyList())
    val emojiFeed: StateFlow<List<FloatingEmoji>> = _emojiFeed.asStateFlow()
    private var emojiCounter = 0L

    private val _giftCatalog = MutableStateFlow<List<com.dualmusic.domain.model.VirtualGift>>(emptyList())
    val giftCatalog: StateFlow<List<com.dualmusic.domain.model.VirtualGift>> = _giftCatalog.asStateFlow()

    /** Inventaire (cadeaux possédés) — l'envoi consomme un cadeau d'ici (parité web). */
    private val _inventory = MutableStateFlow<List<OwnedGift>>(emptyList())
    val inventory: StateFlow<List<OwnedGift>> = _inventory.asStateFlow()

    /** Classement des donateurs (chargé à l'ouverture du trophée). */
    private val _giftLeaderboard = MutableStateFlow<List<GiftLeaderboardEntry>>(emptyList())
    val giftLeaderboard: StateFlow<List<GiftLeaderboardEntry>> = _giftLeaderboard.asStateFlow()

    /** Meilleur donateur courant (bulle top-donateur). Rechargé à chaque cadeau. */
    private val _topDonor = MutableStateFlow<com.dualmusic.core.ui.overlay.TopDonor?>(null)
    val topDonor: StateFlow<com.dualmusic.core.ui.overlay.TopDonor?> = _topDonor.asStateFlow()

    /** Spectateur : id de sa demande d'invité en attente (non-null = en attente). */
    private val _myJoinRequestId = MutableStateFlow<String?>(null)
    val myJoinRequestId: StateFlow<String?> = _myJoinRequestId.asStateFlow()

    /** Hôte : demandes d'invités en attente. */
    private val _joinRequests = MutableStateFlow<List<LiveJoinRequest>>(emptyList())
    val joinRequests: StateFlow<List<LiveJoinRequest>> = _joinRequests.asStateFlow()

    /** Hôte : invités acceptés (sur scène). Alimente le badge vert + la liste de gestion. */
    private val _acceptedGuests = MutableStateFlow<List<LiveJoinRequest>>(emptyList())
    val acceptedGuests: StateFlow<List<LiveJoinRequest>> = _acceptedGuests.asStateFlow()

    /** Chrono de temps de parole par invité (userId → secondes restantes). Vu par tous. */
    private val _guestTimers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val guestTimers: StateFlow<Map<String, Int>> = _guestTimers.asStateFlow()

    /** Spectateur : l'hôte a quitté sans terminer → le live est « en attente ». */
    private val _liveWaiting = MutableStateFlow(false)
    val liveWaiting: StateFlow<Boolean> = _liveWaiting.asStateFlow()

    /** Spectateur : sa demande a été acceptée → il peut monter sur scène (publier). */
    private val _isGuestAccepted = MutableStateFlow(false)
    val isGuestAccepted: StateFlow<Boolean> = _isGuestAccepted.asStateFlow()

    /** Id du caller (résolu au démarrage) pour détecter l'acceptation de SA demande. */
    private var myUserId: String? = null

    private var giftCounter = 0L
    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /**
     * Démarre : vidéo, historique de chat, rooms temps réel.
     * @param prewarmedToken jeton LiveKit pré-obtenu par le feed (réduit la latence).
     */
    fun start(prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null) {
        viewModelScope.launch { media.join(roomName = roomName, isHost = isHost, prewarmedToken = prewarmedToken) }
        viewModelScope.launch {
            runCatching { repository.chatHistory(liveId) }.getOrNull()?.let { _messages.value = it }
        }
        viewModelScope.launch {
            runCatching { repository.likesCount(liveId) }.getOrNull()?.let { _likes.value = it }
        }
        viewModelScope.launch {
            runCatching { repository.giftCatalog() }.getOrNull()?.let { _giftCatalog.value = it }
        }
        loadInventory()
        loadGiftLeaderboard() // amorce la bulle top-donateur
        if (isHost) loadJoinRequests()
        if (!isHost) viewModelScope.launch { myUserId = repository.myUserId() }
        connectRealtime()
    }

    /**
     * Envoie un like : incrément du compteur + cœur flottant **visible par tous** (relayé
     * comme une réaction emoji ❤️) + persistance du total.
     */
    fun sendLike() {
        _likes.value += 1
        sendReaction("❤️")
        viewModelScope.launch { runCatching { repository.likeLive(liveId) } }
    }

    /** Suit l'artiste hôte. */
    fun follow(artistId: String) {
        viewModelScope.launch { runCatching { repository.followArtist(artistId) } }
    }

    /** Signale le live avec un motif (modération). */
    fun report(reason: String) {
        viewModelScope.launch { runCatching { repository.reportLive(liveId, reason) } }
    }

    /** Envoie une dédicace (message dédié) dans le live. */
    fun dedicate(message: String) {
        val m = message.trim()
        if (m.isEmpty()) return
        viewModelScope.launch { runCatching { repository.sendDedication(liveId, m) } }
    }

    /** Spectateur : demande à rejoindre en invité. */
    fun requestJoin() {
        viewModelScope.launch { _myJoinRequestId.value = repository.requestJoin(liveId) }
    }

    /** Spectateur : annule sa demande. */
    fun cancelJoin() {
        val rid = _myJoinRequestId.value ?: return
        viewModelScope.launch { runCatching { repository.cancelJoin(rid) }; _myJoinRequestId.value = null }
    }

    /** Hôte : (re)charge les demandes en attente. */
    fun loadJoinRequests() {
        viewModelScope.launch {
            _joinRequests.value = runCatching { repository.joinRequests(liveId, "pending") }.getOrDefault(emptyList())
            _acceptedGuests.value = runCatching { repository.joinRequests(liveId, "accepted") }.getOrDefault(emptyList())
        }
    }

    /**
     * Hôte : accorde un temps de parole (chrono) à un invité. Diffuse `start_timer` à tout le
     * monde (canal `live-controls-<id>`) — chaque client décompte localement et l'affiche.
     */
    fun grantGuestTimer(userId: String, name: String?, seconds: Int = 120) {
        _guestTimers.update { it + (userId to seconds) }
        emitGuestAction("start_timer", userId, name, org.json.JSONObject().put("value", seconds))
    }

    /** Hôte : coupe/rétablit le micro d'un invité (diffusé ; l'invité ciblé applique à sa piste). */
    fun toggleGuestMic(userId: String, mute: Boolean) {
        emitGuestAction("toggle_mic", userId, null, org.json.JSONObject().put("value", mute))
    }

    /** Hôte : retire un invité (state `ended` persistant + diffusion `kick`). */
    fun kickGuest(requestId: String, userId: String) {
        viewModelScope.launch {
            runCatching { repository.respondJoinStatus(requestId, "ended") }
            emitGuestAction("kick", userId, null, null)
            _guestTimers.update { it - userId }
            loadJoinRequests()
        }
    }

    /** Hôte : signale aux spectateurs que le live passe « en attente » (il quitte sans terminer). */
    fun broadcastLiveWaiting() {
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "live-controls-$liveId",
                    "event" to "live_status",
                    "payload" to org.json.JSONObject().put("status", "waiting"),
                ),
            ),
        )
    }

    /** Construit et émet une action invité sur le canal `live-controls-<id>`. */
    private fun emitGuestAction(action: String, targetUserId: String, targetUserName: String?, extra: org.json.JSONObject?) {
        val payload = (extra ?: org.json.JSONObject())
            .put("action", action)
            .put("targetUserId", targetUserId)
        targetUserName?.let { payload.put("targetUserName", it) }
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "live-controls-$liveId",
                    "event" to "guest_action",
                    "payload" to payload,
                ),
            ),
        )
    }

    /** Applique une action invité reçue (émetteur exclu côté serveur). */
    private fun onGuestAction(p: BroadcastPayload) {
        val target = p.targetUserId ?: return
        when (p.action) {
            "start_timer" -> {
                val secs = (p.value as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 120
                _guestTimers.update { it + (target to secs) }
            }
            "timer_ended" -> _guestTimers.update { it - target }
            "toggle_mic" -> {
                val mute = (p.value as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false
                if (target == myUserId) viewModelScope.launch { runCatching { media.setMicEnabled(!mute) } }
            }
            "kick" -> if (target == myUserId) {
                _isGuestAccepted.value = false
                viewModelScope.launch { runCatching { media.leave() } }
            }
        }
    }

    /** Décompte des chronos de parole : -1s/s ; émet `timer_ended` (hôte) à échéance. */
    private fun startGuestTimerTicker() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val current = _guestTimers.value
                if (current.isEmpty()) continue
                val next = mutableMapOf<String, Int>()
                current.forEach { (uid, rem) ->
                    val r = rem - 1
                    if (r > 0) next[uid] = r
                    else if (isHost) emitGuestAction("timer_ended", uid, null, null)
                }
                _guestTimers.value = next
            }
        }
    }

    /** Hôte : accepte/refuse une demande, puis recharge. */
    fun respondJoin(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            runCatching { repository.respondJoin(requestId, accept) }
            loadJoinRequests()
        }
    }

    /**
     * Invité accepté : monte sur scène — rejoint la room en **publisher** (canPublish) puis
     * publie sa caméra. La caméra locale apparaît alors à tous (multi-participant).
     * La permission caméra/micro est demandée par l'écran avant l'appel.
     */
    fun goOnStage() {
        viewModelScope.launch {
            media.leave()
            media.join(roomName = roomName, canPublish = true)
            media.startBroadcast()
        }
    }

    /** Envoie une réaction emoji : effet local + relais aux autres membres du canal. */
    fun sendReaction(emoji: String) {
        pushEmoji(emoji)
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "live-emojis-$liveId",
                    "event" to "emoji_reaction",
                    "payload" to org.json.JSONObject(mapOf("emoji" to emoji)),
                ),
            ),
        )
    }

    private fun pushEmoji(emoji: String) {
        _emojiFeed.update { (it + FloatingEmoji(emojiCounter++, emoji)).takeLast(12) }
    }

    /** Démarre la diffusion caméra/micro (hôte) — déclenché par « Démarrer le Live ». */
    fun startBroadcast() {
        viewModelScope.launch { runCatching { media.startBroadcast() } }
    }

    /** Coupe/rétablit le micro (mode hôte). */
    fun toggleMic() {
        viewModelScope.launch { runCatching { media.setMicEnabled(!media.micEnabled.value) } }
    }

    /** Coupe/rétablit la caméra (mode hôte). */
    fun toggleCamera() {
        viewModelScope.launch { runCatching { media.setCamEnabled(!media.camEnabled.value) } }
    }

    /** Bascule caméra avant/arrière (mode hôte). */
    fun switchCamera() {
        viewModelScope.launch { runCatching { media.switchCamera() } }
    }

    /** Pause/reprise du direct : coupe (ou rétablit) caméra + micro ensemble. */
    fun setPaused(paused: Boolean) {
        viewModelScope.launch {
            runCatching { media.setCamEnabled(!paused); media.setMicEnabled(!paused) }
        }
    }

    /** Active/désactive le flou d'arrière-plan (filtre). */
    fun toggleBlur() {
        runCatching { media.toggleBlur() }
    }

    /** Termine le live côté backend puis notifie l'appelant (mode hôte). */
    fun endLive(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.endLive(liveId) }
            stop()
            onDone()
        }
    }

    /** Arrête tout (sortie d'écran). */
    fun stop() {
        liveSession?.disconnect()
        chatSession?.disconnect()
        media.leave()
    }

    /** Envoie un message de chat (le serveur diffuse ensuite). */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(liveId, content) } }
    }

    /** Charge l'inventaire (cadeaux possédés). */
    fun loadInventory() {
        viewModelScope.launch {
            runCatching { repository.inventory() }.getOrNull()?.let { _inventory.value = it }
        }
    }

    /**
     * Envoie un cadeau possédé au host. Le backend débite l'inventaire, distribue les crédits
     * et diffuse l'animation `gift` à toute la room (tous les spectateurs — dont l'émetteur —
     * la voient via l'event temps réel `gift`). Recharge l'inventaire pour la quantité restante.
     */
    fun sendGift(giftId: String, toUserId: String) {
        viewModelScope.launch {
            runCatching { repository.sendGift(liveId, giftId, toUserId) }.onSuccess { loadInventory() }
        }
    }

    /** Achète un cadeau (boutique) puis recharge l'inventaire. */
    fun purchaseGift(giftId: String) {
        viewModelScope.launch {
            runCatching { repository.purchaseGift(giftId, 1) }.onSuccess { loadInventory() }
        }
    }

    /** Charge le classement des donateurs du live (trophée + bulle top-donateur). */
    fun loadGiftLeaderboard() {
        viewModelScope.launch {
            val list = runCatching { repository.giftLeaderboard(liveId) }.getOrDefault(emptyList())
            _giftLeaderboard.value = list
            _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        }
    }

    // MARK: Temps réel

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }

        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.LIVE, liveId)
                live.emit("broadcast:join", "live-emojis-$liveId")
                live.emit("broadcast:join", "live-controls-$liveId")
            }
            chat.onConnect { chat.join(Realtime.RoomType.LIVE, liveId) }

            // Nouveaux messages
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + LiveChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            // Cadeaux
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                _giftFeed.update { it + LiveGift(giftCounter++, p.fromUserId, p.giftName, p.giftImage, p.value) }
                loadGiftLeaderboard() // met à jour la bulle top-donateur en direct
            }
            // Présence (viewers)
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            // Likes (total diffusé par le backend)
            live.on("likes", LikesPayload.serializer()) { p ->
                if (p.likes > _likes.value) _likes.value = p.likes
            }
            // Relais broadcast : réactions emojis (live-emojis-<id>) + actions invités (live-controls-<id>).
            live.on("broadcast", BroadcastEnvelope.serializer()) { env ->
                when (env.event) {
                    "emoji_reaction" -> env.payload?.emoji?.let { pushEmoji(it) }
                    "guest_action" -> env.payload?.let { onGuestAction(it) }
                    "live_status" -> if (!isHost) _liveWaiting.value = env.payload?.status == "waiting"
                }
            }
            // Demandes d'invités (hôte) : rafraîchir la liste à chaque nouvelle demande / MAJ.
            live.on("join:new", JoinEventPayload.serializer()) { if (isHost) loadJoinRequests() }
            live.on("join:update", JoinEventPayload.serializer()) { p ->
                if (isHost) loadJoinRequests()
                // Spectateur : sa propre demande a changé d'état.
                if (!isHost && p.userId != null && p.userId == myUserId) {
                    when (p.status) {
                        "accepted" -> _isGuestAccepted.value = true
                        "rejected", "ended" -> _isGuestAccepted.value = false
                    }
                }
            }
            // Pub sponsor (start/stop) diffusée à toute la room.
            live.on(Realtime.RealtimeEvent.SPONSOR_AD, com.dualmusic.domain.realtime.SponsorAdPayload.serializer()) { p ->
                sponsor.onEvent(p)
            }

            live.connect()
            chat.connect()
        }
        startGuestTimerTicker()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

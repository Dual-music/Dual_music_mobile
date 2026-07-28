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
    val payload: EmojiPayload? = null,
)

/** Charge utile d'une réaction emoji. */
@kotlinx.serialization.Serializable
data class EmojiPayload(val emoji: String? = null)

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
    /** Vrai pour l'artiste qui DIFFUSE (publie caméra/micro) ; faux pour un spectateur. */
    val isHost: Boolean = false,
) : ViewModel() {

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
        connectRealtime()
    }

    /** Envoie un like : incrément optimiste + animation de cœur + persistance. */
    fun sendLike() {
        _likes.value += 1
        _heartTick.value += 1
        viewModelScope.launch { runCatching { repository.likeLive(liveId) } }
    }

    /** Suit l'artiste hôte. */
    fun follow(artistId: String) {
        viewModelScope.launch { runCatching { repository.followArtist(artistId) } }
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

    /** Coupe/rétablit le micro (mode hôte). */
    fun toggleMic() {
        viewModelScope.launch { runCatching { media.setMicEnabled(!media.micEnabled.value) } }
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

    /** Envoie un cadeau au host. */
    fun sendGift(giftId: String, toUserId: String) {
        viewModelScope.launch { runCatching { repository.sendGift(liveId, giftId, toUserId) } }
    }

    // MARK: Temps réel

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }

        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.LIVE, liveId)
                live.emit("broadcast:join", "live-emojis-$liveId")
            }
            chat.onConnect { chat.join(Realtime.RoomType.LIVE, liveId) }

            // Nouveaux messages
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + LiveChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            // Cadeaux
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                _giftFeed.update { it + LiveGift(giftCounter++, p.fromUserId, p.giftName, p.giftImage, p.value) }
            }
            // Présence (viewers)
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            // Likes (total diffusé par le backend)
            live.on("likes", LikesPayload.serializer()) { p ->
                if (p.likes > _likes.value) _likes.value = p.likes
            }
            // Réactions emojis relayées (canal live-emojis-<id>) — l'émetteur est exclu.
            live.on("broadcast", BroadcastEnvelope.serializer()) { env ->
                if (env.event == "emoji_reaction") env.payload?.emoji?.let { pushEmoji(it) }
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

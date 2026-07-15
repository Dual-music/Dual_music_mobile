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
) : ViewModel() {

    private val _messages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val messages: StateFlow<List<LiveChatMessage>> = _messages.asStateFlow()

    private val _giftFeed = MutableStateFlow<List<LiveGift>>(emptyList())
    val giftFeed: StateFlow<List<LiveGift>> = _giftFeed.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private var giftCounter = 0L
    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /**
     * Démarre : vidéo, historique de chat, rooms temps réel.
     * @param prewarmedToken jeton LiveKit pré-obtenu par le feed (réduit la latence).
     */
    fun start(prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null) {
        viewModelScope.launch { media.join(roomName = roomName, isHost = false, prewarmedToken = prewarmedToken) }
        viewModelScope.launch {
            runCatching { repository.chatHistory(liveId) }.getOrNull()?.let { _messages.value = it }
        }
        connectRealtime()
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
            live.onConnect { live.join(Realtime.RoomType.LIVE, liveId) }
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

            live.connect()
            chat.connect()
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

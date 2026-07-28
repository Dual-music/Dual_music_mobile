package com.dualmusic.feature.duel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.realtime.GiftPayload
import com.dualmusic.domain.realtime.ChatMessagePayload
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
import com.dualmusic.domain.realtime.TimerPayload
import com.dualmusic.domain.realtime.VotePayload
import com.dualmusic.feature.wallet.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cadeau reçu en direct dans le duel (pour l'animation GPU). */
data class DuelGift(
    val key: Long,
    val fromUserId: String?,
    val name: String?,
    val image: String?,
    val value: Double,
)

/** État du minuteur du duel (persisté serveur → visible aussi pour les arrivants tardifs). */
data class DuelTimer(
    val endsAt: String? = null,
    val targetId: String? = null,
) {
    val isRunning: Boolean get() = endsAt != null
}

/**
 * ViewModel de la room de duel.
 *
 * Orchestre : vidéo LiveKit (via [media]), tallies de votes + minuteur + statut + cadeaux
 * (Socket.IO `/live`), chat (Socket.IO `/chat`), et le **vote payant** délégué au
 * portefeuille (procédure atomique serveur — aucun calcul d'argent ici).
 *
 * @param duelId identifiant du duel (contexte chat/cadeaux/votes).
 * @param roomName room LiveKit à rejoindre.
 * @param media client média (une connexion SFU par room).
 * @param realtime client Socket.IO partagé.
 * @param repository lectures REST du duel.
 * @param wallet opérations de débit (vote).
 */
class DuelViewModel(
    private val duelId: String,
    private val roomName: String,
    val media: LiveRoomClient,
    private val realtime: RealtimeClient,
    private val repository: DuelRepository,
    private val wallet: WalletRepository,
) : ViewModel() {

    private val _duel = MutableStateFlow<Duel?>(null)
    val duel: StateFlow<Duel?> = _duel.asStateFlow()

    /** Total de crédits votés par artiste (`artistId` → total). Mis à jour en direct. */
    private val _voteTotals = MutableStateFlow<Map<String, Double>>(emptyMap())
    val voteTotals: StateFlow<Map<String, Double>> = _voteTotals.asStateFlow()

    private val _timer = MutableStateFlow(DuelTimer())
    val timer: StateFlow<DuelTimer> = _timer.asStateFlow()

    private val _messages = MutableStateFlow<List<DuelChatMessage>>(emptyList())
    val messages: StateFlow<List<DuelChatMessage>> = _messages.asStateFlow()

    private val _giftFeed = MutableStateFlow<List<DuelGift>>(emptyList())
    val giftFeed: StateFlow<List<DuelGift>> = _giftFeed.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private var giftCounter = 0L
    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /** Démarre : détail du duel, tallies, vidéo, chat, temps réel. */
    fun start() {
        viewModelScope.launch { media.join(roomName = roomName, isHost = false) }
        viewModelScope.launch {
            runCatching { repository.duel(duelId) }.getOrNull()?.let { d ->
                _duel.value = d
                // Réhydrate le minuteur persisté (arrivants tardifs).
                _timer.value = DuelTimer(d.currentTimerEndsAt, d.currentTimerTargetId)
            }
            runCatching { repository.voteTotals(duelId) }.getOrNull()?.let { totals ->
                _voteTotals.value = totals.associate { it.artistId to it.total }
            }
            runCatching { repository.chatHistory(duelId) }.getOrNull()?.let { _messages.value = it }
        }
        connectRealtime()
    }

    /** Arrête tout (sortie d'écran). */
    fun stop() {
        liveSession?.disconnect()
        chatSession?.disconnect()
        media.leave()
    }

    /**
     * Vote payant pour un artiste. Débit atomique côté serveur ; le tally se met à jour
     * via l'événement temps réel `vote` (pas d'optimisme local sur l'argent).
     */
    fun vote(artistId: String, amount: Double) {
        viewModelScope.launch {
            runCatching { wallet.vote(duelId = duelId, artistId = artistId, amount = amount) }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.errVoteFailed }
        }
    }

    /** Envoie un message de chat (le serveur diffuse ensuite). */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(duelId, content) } }
    }

    /** Efface l'erreur affichée. */
    fun clearError() { _error.value = null }

    // MARK: Temps réel

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }

        viewModelScope.launch {
            live.onConnect { live.join(Realtime.RoomType.DUEL, duelId) }
            chat.onConnect { chat.join(Realtime.RoomType.DUEL, duelId) }

            // Vote payant enregistré → on cumule le tally de l'artiste visé.
            live.on(Realtime.RealtimeEvent.VOTE, VotePayload.serializer()) { p ->
                _voteTotals.update { current ->
                    current + (p.artistId to (current[p.artistId] ?: 0.0) + p.amount)
                }
            }
            // Minuteur (start/stop) piloté par le manager.
            live.on(Realtime.RealtimeEvent.TIMER, TimerPayload.serializer()) { p ->
                _timer.value = DuelTimer(endsAt = p.endsAt, targetId = p.targetId)
            }
            // Statut / vainqueur.
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { p ->
                _duel.update { d -> d?.copy(winnerId = p.winnerId ?: d.winnerId) }
            }
            // Cadeaux (alimente l'animation GPU).
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                _giftFeed.update { it + DuelGift(giftCounter++, p.fromUserId, p.giftName, p.giftImage, p.value) }
            }
            // Chat.
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + DuelChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            // Présence (spectateurs).
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

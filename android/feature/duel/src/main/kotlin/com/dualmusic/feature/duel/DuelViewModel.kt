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
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    // Base de room LiveKit (ex. "duel-<id>" ou duel.roomId). Les rooms réelles sont
    // "$baseRoom-artist1", "$baseRoom-artist2", "$baseRoom-manager" — parité web : chaque
    // acteur publie dans SA room de slot, tout le monde s'abonne aux 3.
    private val baseRoom: String,
    private val mediaFactory: () -> LiveRoomClient,
    private val realtime: RealtimeClient,
    private val repository: DuelRepository,
    private val wallet: WalletRepository,
    sponsorAds: com.dualmusic.feature.sponsor.SponsorAdRepository,
    recording: com.dualmusic.feature.sponsor.RecordingRepository,
) : ViewModel() {

    // Un client LiveKit par slot (une connexion SFU par room de slot).
    val mediaA1: LiveRoomClient = mediaFactory()
    val mediaA2: LiveRoomClient = mediaFactory()
    val mediaMgr: LiveRoomClient = mediaFactory()

    /** Client du slot du caller (celui où IL publie) — null pour un spectateur. */
    private var myMedia: LiveRoomClient? = null

    /** Piste vidéo à afficher pour un slot = ma caméra locale si je publie là, sinon le distant. */
    private fun slotVideo(client: LiveRoomClient): StateFlow<VideoTrack?> =
        combine(client.localVideoTrack, client.primaryVideoTrack) { local, remote -> local ?: remote }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val artist1Video: StateFlow<VideoTrack?> = slotVideo(mediaA1)
    val artist2Video: StateFlow<VideoTrack?> = slotVideo(mediaA2)
    val managerVideo: StateFlow<VideoTrack?> = slotVideo(mediaMgr)

    /** État micro/caméra du caller (reflète myMedia après le lancement de la diffusion). */
    private val _micOn = MutableStateFlow(true)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()
    private val _camOn = MutableStateFlow(true)
    val camOn: StateFlow<Boolean> = _camOn.asStateFlow()

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle hôte/manager). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("duel", duelId, sponsorAds, viewModelScope)

    /** État + action d'enregistrement serveur (bouton hôte en mode manual). */
    val recordingCtl = com.dualmusic.feature.sponsor.RecordingHolder("duel", duelId, recording, viewModelScope)

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

    /** Message éphémère « vous avez reçu un cadeau » (destinataire uniquement) → bannière in-app. */
    private val _giftReceived = MutableStateFlow<String?>(null)
    val giftReceived: StateFlow<String?> = _giftReceived.asStateFlow()
    fun clearGiftReceived() { _giftReceived.value = null }

    /** Compteur local de J'aime (le cœur flotte pour tous via le relais). */
    private val _likes = MutableStateFlow(0)
    val likes: StateFlow<Int> = _likes.asStateFlow()

    /** Flux de réactions flottantes `(id, emoji)` — relayé à tous les spectateurs. */
    private val _emojiFeed = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val emojiFeed: StateFlow<List<Pair<Long, String>>> = _emojiFeed.asStateFlow()
    private var emojiCounter = 0L

    /** Meilleur donateur courant (bulle top-donateur). Rechargé à chaque cadeau. */
    private val _topDonor = MutableStateFlow<com.dualmusic.core.ui.overlay.TopDonor?>(null)
    val topDonor: StateFlow<com.dualmusic.core.ui.overlay.TopDonor?> = _topDonor.asStateFlow()

    private fun loadTopDonor() {
        viewModelScope.launch {
            val list = runCatching { repository.giftLeaderboard(duelId) }.getOrDefault(emptyList())
            _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        }
    }

    /** Catalogue des cadeaux virtuels (boutique). */
    private val _giftCatalog = MutableStateFlow<List<com.dualmusic.domain.model.VirtualGift>>(emptyList())
    val giftCatalog: StateFlow<List<com.dualmusic.domain.model.VirtualGift>> = _giftCatalog.asStateFlow()

    /** Inventaire (cadeaux possédés) du caller. */
    private val _inventory = MutableStateFlow<List<com.dualmusic.domain.gift.InventoryItem>>(emptyList())
    val inventory: StateFlow<List<com.dualmusic.domain.gift.InventoryItem>> = _inventory.asStateFlow()

    /** Classement des donateurs (panneau + bulle top-donateur). */
    private val _leaderboard = MutableStateFlow<List<DuelDonorEntry>>(emptyList())
    val leaderboard: StateFlow<List<DuelDonorEntry>> = _leaderboard.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    /** Vrai si le caller est le manager (arbitre) de ce duel → contrôles en direct. */
    private val _isManager = MutableStateFlow(false)
    val isManager: StateFlow<Boolean> = _isManager.asStateFlow()
    private var myUserId: String? = null

    /** Case épinglée par le manager (focus imposé, synchronisé via broadcast) :
     *  "artist1" / "artist2" / "manager", ou null = pas de focus imposé (focus local libre). */
    private val _forcedFocus = MutableStateFlow<String?>(null)
    val forcedFocus: StateFlow<String?> = _forcedFocus.asStateFlow()

    /** Vrai si le caller est un PARTICIPANT (artiste 1/2 ou manager) → peut diffuser caméra/micro. */
    private val _canPublish = MutableStateFlow(false)
    val canPublish: StateFlow<Boolean> = _canPublish.asStateFlow()

    /** Diffusion caméra/micro en cours (participant). */
    private val _broadcasting = MutableStateFlow(false)
    val broadcasting: StateFlow<Boolean> = _broadcasting.asStateFlow()

    private var giftCounter = 0L
    private var liveSession: NamespaceSession? = null
    private var chatSession: NamespaceSession? = null

    /** Démarre : détail du duel, tallies, vidéo, chat, temps réel. */
    fun start() {
        viewModelScope.launch {
            val d = runCatching { repository.duel(duelId) }.getOrNull()
            myUserId = myUserId ?: runCatching { repository.myUserId() }.getOrNull()
            if (d != null) {
                _duel.value = d
                // Réhydrate le minuteur persisté (arrivants tardifs).
                _timer.value = DuelTimer(d.currentTimerEndsAt, d.currentTimerTargetId)
                _isManager.value = d.managerId != null && d.managerId == myUserId
                // Mon slot selon mon rôle → le client où JE publie (null = spectateur).
                myMedia = when (myUserId) {
                    d.artist1Id -> mediaA1
                    d.artist2Id -> mediaA2
                    d.managerId -> mediaMgr
                    else -> null
                }
                _canPublish.value = myMedia != null
                android.util.Log.i(
                    "DuelVM",
                    "role myUserId=$myUserId a1=${d.artist1Id} a2=${d.artist2Id} mgr=${d.managerId} canPublish=${_canPublish.value} base=$baseRoom",
                )
            }
            if (_duel.value == null) android.util.Log.e("DuelVM", "duel() a renvoye NULL (pas de bouton demarrer)")
            // Rejoint les 3 rooms de slot (parité web) : publieur sur MA room, spectateur sur les 2 autres.
            joinSlot(mediaA1, "artist1")
            joinSlot(mediaA2, "artist2")
            joinSlot(mediaMgr, "manager")
            runCatching { repository.voteTotals(duelId) }.getOrNull()?.let { totals ->
                _voteTotals.value = totals.associate { it.artistId to it.total }
            }
            runCatching { repository.chatHistory(duelId) }.getOrNull()?.let { _messages.value = it }
        }
        loadTopDonor()
        viewModelScope.launch { runCatching { repository.giftCatalog() }.getOrNull()?.let { _giftCatalog.value = it } }
        loadInventory()
        loadGiftLeaderboard()
        recordingCtl.refresh()
        connectRealtime()
    }

    /** Recharge l'inventaire (après achat/envoi). */
    fun loadInventory() {
        viewModelScope.launch { runCatching { repository.inventory() }.getOrNull()?.let { _inventory.value = it } }
    }

    // --- Diffusion caméra/micro (PARTICIPANT : artiste 1/2 ou manager) — parité web ---

    /** Rejoint la room d'un slot : publieur si c'est MON slot, spectateur sinon. */
    private fun joinSlot(client: LiveRoomClient, slot: String) {
        val publish = client === myMedia
        // Log de l'ETAT de connexion réel (le join capture ses erreurs en interne → état Failed).
        viewModelScope.launch {
            client.connectionState.collect { st -> android.util.Log.i("DuelVM", "conn room=$baseRoom-$slot publish=$publish -> $st") }
        }
        viewModelScope.launch {
            runCatching { client.join(roomName = "$baseRoom-$slot", isHost = publish, canPublish = publish) }
        }
    }

    /** Participant : démarre la diffusion caméra + micro dans MA room de slot. */
    fun startBroadcast() {
        val m = myMedia ?: return
        viewModelScope.launch {
            runCatching { m.startBroadcast() }.onSuccess {
                _broadcasting.value = true; _micOn.value = true; _camOn.value = true
            }
        }
    }

    /** Participant : coupe/rétablit la caméra. */
    fun toggleCamera() {
        val m = myMedia ?: return
        val next = !m.camEnabled.value
        m.setCamEnabled(next); _camOn.value = next
    }

    /** Participant : coupe/rétablit le micro. */
    fun toggleMic() {
        val m = myMedia ?: return
        val next = !m.micEnabled.value
        viewModelScope.launch { runCatching { m.setMicEnabled(next) }; _micOn.value = next }
    }

    /** Participant : bascule caméra avant/arrière. */
    fun flipCamera() {
        val m = myMedia ?: return
        viewModelScope.launch { runCatching { m.switchCamera() } }
    }

    /** Participant : Pause (coupe caméra + micro) / Reprendre (les réactive). */
    fun togglePause() {
        val m = myMedia ?: return
        val resume = !m.camEnabled.value && !m.micEnabled.value
        m.setCamEnabled(resume); _camOn.value = resume
        viewModelScope.launch { runCatching { m.setMicEnabled(resume) }; _micOn.value = resume }
    }

    /** Envoie un cadeau possédé à un artiste/manager du duel (débit atomique serveur). */
    fun sendGift(giftId: String, toUserId: String) {
        viewModelScope.launch {
            runCatching { repository.sendGift(duelId, giftId, toUserId) }
                .onSuccess { loadInventory() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Achète un cadeau (boutique) puis recharge l'inventaire. */
    fun purchaseGift(giftId: String) {
        viewModelScope.launch {
            runCatching { repository.purchaseGift(giftId, 1) }
                .onSuccess { loadInventory() }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Recharge le classement des donateurs. */
    fun loadGiftLeaderboard() {
        viewModelScope.launch { runCatching { repository.giftLeaderboard(duelId) }.getOrNull()?.let { _leaderboard.value = it } }
    }

    /** Arrête tout (sortie d'écran) : temps réel + les 3 connexions LiveKit de slot. */
    fun stop() {
        liveSession?.disconnect()
        chatSession?.disconnect()
        mediaA1.leave()
        mediaA2.leave()
        mediaMgr.leave()
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

    /** J'aime : incrémente le compteur, fait flotter un cœur, et diffuse le compteur (parité web). */
    fun sendLike() {
        _likes.value += 1
        // Compteur partagé sur le canal `duel-likes-<id>` (même mécanisme que le web).
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "duel-likes-$duelId",
                    "event" to "like",
                    "payload" to org.json.JSONObject(mapOf("count" to _likes.value)),
                ),
            ),
        )
        sendReaction("❤️")
    }

    /** Envoie une réaction emoji : effet local + relais aux autres membres du canal. */
    fun sendReaction(emoji: String) {
        pushEmoji(emoji)
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "duel-emojis-$duelId",
                    "event" to "emoji_reaction",
                    "payload" to org.json.JSONObject(mapOf("emoji" to emoji)),
                ),
            ),
        )
    }

    private fun pushEmoji(emoji: String) {
        _emojiFeed.update { (it + (emojiCounter++ to emoji)).takeLast(12) }
    }

    /**
     * Manager : épingle (ou libère avec `null`) une case en plein écran pour TOUS. Relayé via le
     * canal broadcast `duel-focus-<id>` (comme les likes/emojis) — retour immédiat + synchronisé.
     */
    fun setFocus(slot: String?) {
        _forcedFocus.value = slot
        liveSession?.emit(
            "broadcast",
            org.json.JSONObject(
                mapOf(
                    "channel" to "duel-focus-$duelId",
                    "event" to "focus",
                    "payload" to org.json.JSONObject(mapOf("slot" to (slot ?: org.json.JSONObject.NULL))),
                ),
            ),
        )
    }

    /** Efface l'erreur affichée. */
    fun clearError() { _error.value = null }

    // MARK: Contrôles MANAGER (arbitre) — persistés + rediffusés par le backend

    /** Donne la parole à un artiste pendant [seconds] (minuteur). */
    fun startTimer(targetId: String, seconds: Int) {
        val endsAt = java.time.Instant.now().plusSeconds(seconds.toLong()).toString()
        patchDuel("""{"currentTimerEndsAt":"$endsAt","currentTimerTargetId":"$targetId"}""")
    }

    /** Arrête le minuteur de parole. */
    fun stopTimer() {
        patchDuel("""{"currentTimerEndsAt":null,"currentTimerTargetId":null}""")
    }

    /** Signale ce direct à la modération (best-effort ; l'échec reste silencieux). */
    fun report(reason: String) {
        viewModelScope.launch { runCatching { repository.reportLive(duelId, reason) } }
    }

    /** Annonce le vainqueur (ne termine pas le duel). */
    fun announceWinner(artistId: String) {
        patchDuel("""{"winnerId":"$artistId"}""")
    }

    /** Termine le duel puis notifie l'appelant (sortie d'écran). */
    fun endDuel(onEnded: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.updateDuel(duelId, """{"status":"ended"}""") }
            onEnded()
        }
    }

    private fun patchDuel(bodyJson: String) {
        viewModelScope.launch {
            runCatching { repository.updateDuel(duelId, bodyJson) }
                .onFailure { _error.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    // MARK: Temps réel

    private fun connectRealtime() {
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        val chat = realtime.session(Realtime.Namespace.CHAT).also { chatSession = it }

        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.DUEL, duelId)
                live.emit("broadcast:join", "duel-emojis-$duelId")
                live.emit("broadcast:join", "duel-likes-$duelId")
                live.emit("broadcast:join", "duel-focus-$duelId")
            }
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
            // Cadeaux (alimente l'animation GPU + la bulle top-donateur).
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                _giftFeed.update { it + DuelGift(giftCounter++, p.fromUserId, p.giftName, p.giftImage, p.value) }
                loadTopDonor()
                // Si le cadeau m'est destiné → bannière « vous avez reçu un cadeau » (en plus de la
                // notification durable créée côté backend).
                if (p.toUserId != null && p.toUserId == myUserId) {
                    _giftReceived.value = "🎁 Vous avez reçu un cadeau (${p.value.toInt()} crédits) !"
                }
            }
            // Chat.
            chat.on(Realtime.RealtimeEvent.CHAT_MESSAGE, ChatMessagePayload.serializer()) { p ->
                _messages.update { it + DuelChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user) }
            }
            // Réactions emojis (duel-emojis-<id>) + compteur de likes partagé (duel-likes-<id>).
            live.on("broadcast", com.dualmusic.domain.realtime.BroadcastEnvelope.serializer()) { env ->
                when (env.event) {
                    "emoji_reaction" -> env.payload?.emoji?.let { pushEmoji(it) }
                    "like" -> env.payload?.count?.let { if (it > _likes.value) _likes.value = it }
                    // Focus imposé par le manager (null = libéré) → tous mettent cette case en grand.
                    "focus" -> _forcedFocus.value = env.payload?.slot
                }
            }
            // Présence (spectateurs).
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
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

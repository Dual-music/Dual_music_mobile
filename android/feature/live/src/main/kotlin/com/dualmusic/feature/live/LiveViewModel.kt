package com.dualmusic.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.moderation.EventModerator
import com.dualmusic.domain.realtime.ChatMessagePayload
import com.dualmusic.domain.realtime.EventModeratorPayload
import com.dualmusic.domain.realtime.EventSettingsPayload
import com.dualmusic.domain.realtime.GiftPayload
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/** Événement de dédicace (`dedication:new` / `dedication:update`) — même live room que `join:*`. */
@kotlinx.serialization.Serializable
data class DedicationEventPayload(
    @kotlinx.serialization.SerialName("dedication_id") val dedicationId: String? = null,
    @kotlinx.serialization.SerialName("fan_id") val fanId: String? = null,
    val status: String? = null,
    @kotlinx.serialization.SerialName("price_credits") val priceCredits: Double? = null,
)

/** Réglages du live modifiés par l'artiste en direct (dédicaces/invités on-off + prix min). */
@kotlinx.serialization.Serializable
data class LiveSettingsPayload(
    @kotlinx.serialization.SerialName("allows_dedications") val allowsDedications: Boolean = true,
    @kotlinx.serialization.SerialName("dedication_min_price_credits") val dedicationMinPriceCredits: Double? = null,
    @kotlinx.serialization.SerialName("allow_guests") val allowGuests: Boolean = true,
)

/** Emoji flottant à animer (réaction). */
data class FloatingEmoji(val id: Long, val emoji: String)

/** Cadeau reçu à animer dans le live. */
data class LiveGift(
    val id: Long,
    val fromUserId: String?,
    /** Nom de l'expéditeur (résolu via le classement des donateurs, hydraté avec les profils). */
    val fromUserName: String?,
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
    /**
     * Fabrique un [LiveRoomClient] SUPPLÉMENTAIRE, partageant le même contexte EGL que [media]
     * (indispensable : sans EGL partagé, les pistes distantes d'une AUTRE room sont souscrites
     * mais ne s'affichent pas — case transparente). Sert à publier/s'abonner aux rooms **par
     * invité** (`live-guest-<liveId>-<userId>`), exactement comme le web (voir [guestMedia] et
     * [reconcileGuestSubscriptions]) — la room principale [media] est réservée à l'ARTISTE.
     */
    private val mediaFactory: () -> LiveRoomClient,
    private val realtime: RealtimeClient,
    private val repository: LiveRepository,
    sponsorAds: com.dualmusic.feature.sponsor.SponsorAdRepository,
    recording: com.dualmusic.feature.sponsor.RecordingRepository,
    /** Vrai pour l'artiste qui DIFFUSE (publie caméra/micro) ; faux pour un spectateur. */
    val isHost: Boolean = false,
) : ViewModel() {

    /**
     * Client dédié à MA PROPRE publication en tant qu'INVITÉ (room `live-guest-<liveId>-<monId>`)
     * — parité EXACTE avec le web (`GuestVideoBox` y publie sous ce même nom de room). Créé
     * paresseusement (jamais `.join()`é pour l'hôte ou un simple spectateur) pour que l'UI puisse
     * l'observer dès le départ sans gérer un état nullable transitoire.
     *
     * AVANT ce fix, un invité publiait dans la room PRINCIPALE (`media`, la même que l'hôte) —
     * une architecture différente de celle du web, qui isole chaque invité dans SA PROPRE room.
     * Résultat : le web (et tout spectateur qui régarde via le mécanisme web) ne recevait JAMAIS
     * la caméra/micro d'un invité mobile, quel que soit le réseau — elles n'étaient tout
     * simplement pas dans la room que le web écoutait.
     */
    val guestMedia: LiveRoomClient by lazy { mediaFactory() }

    /**
     * Pistes vidéo des AUTRES invités actifs (identité = userId), indexées par leur id. La
     * valeur est `null` quand l'invité est présent (abonné) mais caméra coupée — l'entrée reste
     * dans la map (au lieu d'être retirée) pour que sa case affiche un placeholder plutôt que de
     * disparaître : il est toujours invité, juste caméra off (parité duel `SlotContent`).
     */
    private val _guestVideos = MutableStateFlow<Map<String, io.livekit.android.room.track.VideoTrack?>>(emptyMap())
    val guestVideos: StateFlow<Map<String, io.livekit.android.room.track.VideoTrack?>> = _guestVideos.asStateFlow()

    /** Client LiveKit par invité (room de vue dédiée) — exposé pour lire `remoteMicOn` par tuile. */
    private val _guestClients = MutableStateFlow<Map<String, LiveRoomClient>>(emptyMap())
    val guestClients: StateFlow<Map<String, LiveRoomClient>> = _guestClients.asStateFlow()

    private val guestViewClients = mutableMapOf<String, LiveRoomClient>()
    private val guestViewJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /** Client de MA diffusion : la room principale si je suis l'hôte, ma room d'invité sinon. */
    private fun selfMedia(): LiveRoomClient = if (isHost) media else guestMedia

    /** État + actions de diffusion pub sponsor (overlay vidéo + contrôle hôte). */
    val sponsor = com.dualmusic.feature.sponsor.SponsorAdHolder("live", liveId, sponsorAds, viewModelScope)

    /** État + action d'enregistrement serveur (bouton hôte en mode manual). */
    val recordingCtl = com.dualmusic.feature.sponsor.RecordingHolder("live", liveId, recording, viewModelScope)

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

    /** Id du caller (résolu au démarrage) pour détecter l'acceptation de SA demande + bannissement. */
    private val _myUserId = MutableStateFlow<String?>(null)
    private val myUserId: String? get() = _myUserId.value
    /** Exposé pour l'UI (ex. afficher le chrono sur SA PROPRE case quand on est l'invité minuté). */
    val myUserIdFlow: StateFlow<String?> = _myUserId.asStateFlow()

    /** Spectateurs bannis de ce direct (ids). Masque leurs messages + bloque le rejoint. */
    private val _bannedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val bannedUserIds: StateFlow<Set<String>> = _bannedUserIds.asStateFlow()

    /** Vrai si MOI je suis banni → écran de blocage plein écran. */
    val iAmBanned: StateFlow<Boolean> =
        combine(_bannedUserIds, _myUserId) { banned, id -> id != null && banned.contains(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Bannière « vous avez reçu un cadeau » (destinataire). */
    private val _giftReceived = MutableStateFlow<String?>(null)
    val giftReceived: StateFlow<String?> = _giftReceived.asStateFlow()
    fun clearGiftReceived() { _giftReceived.value = null }

    /** Hôte : demandes de dédicace EN ATTENTE pour ce live (à accepter/rejeter) — alimente le badge. */
    private val _dedications = MutableStateFlow<List<LiveDedication>>(emptyList())
    val dedications: StateFlow<List<LiveDedication>> = _dedications.asStateFlow()

    /** Hôte : dédicaces déjà ACCEPTÉES ou LIVRÉES pour ce live (historique, sous les demandes). */
    private val _dedicationHistory = MutableStateFlow<List<LiveDedication>>(emptyList())
    val dedicationHistory: StateFlow<List<LiveDedication>> = _dedicationHistory.asStateFlow()

    /**
     * Prix minimum EFFECTIF d'une dédicace pour CE live (crédits) : la surcharge propre au live
     * si l'artiste en a fixé une, sinon le défaut global piloté par l'admin.
     */
    private val _dedicationMinPrice = MutableStateFlow(10.0)
    val dedicationMinPrice: StateFlow<Double> = _dedicationMinPrice.asStateFlow()

    /** Dédicaces activées pour CE live (réglage artiste, réactif en direct via `settings`). */
    private val _liveAllowsDedications = MutableStateFlow(true)
    val liveAllowsDedications: StateFlow<Boolean> = _liveAllowsDedications.asStateFlow()

    /** Demandes d'invité (« lever la main ») activées pour CE live (réglage artiste). */
    private val _liveAllowGuests = MutableStateFlow(true)
    val liveAllowGuests: StateFlow<Boolean> = _liveAllowGuests.asStateFlow()

    /** Chat activé pour CE live (réglage hôte, réactif en direct via `settings`) — jamais délégué. */
    private val _liveChatEnabled = MutableStateFlow(true)
    val liveChatEnabled: StateFlow<Boolean> = _liveChatEnabled.asStateFlow()

    /** Modérateurs désignés de ce live (hôte + jusqu'à 2 spectateurs) — visible par tous. */
    private val _moderators = MutableStateFlow<List<EventModerator>>(emptyList())
    val moderators: StateFlow<List<EventModerator>> = _moderators.asStateFlow()

    /** Spectateurs actuellement connectés (hôte uniquement — chargé à l'ouverture du picker). */
    private val _viewers = MutableStateFlow<List<DisplayProfile>>(emptyList())
    val viewers: StateFlow<List<DisplayProfile>> = _viewers.asStateFlow()

    /** Confirmation « dédicace envoyée » (ou message d'échec) affichée au fan. */
    private val _dedicationFeedback = MutableStateFlow<String?>(null)
    val dedicationFeedback: StateFlow<String?> = _dedicationFeedback.asStateFlow()
    fun clearDedicationFeedback() { _dedicationFeedback.value = null }

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
        loadModerators()
        recordingCtl.startPolling()
        loadLiveSettings()
        if (isHost) {
            loadJoinRequests(); loadDedications()
        } else {
            loadAcceptedGuests()
        }
        // Id du caller (toujours résolu : sert au bannissement + « cadeau reçu » + acceptation invité).
        viewModelScope.launch {
            _myUserId.value = repository.myUserId()
            _bannedUserIds.value = runCatching { repository.listStreamBans(liveId) }.getOrDefault(emptyList()).toSet()
        }
        // Réconcilie mes abonnements aux rooms des AUTRES invités actifs (hôte, spectateur ou
        // invité — tout le monde doit les voir/entendre) à chaque changement de la liste ou
        // dès que MON id est connu (pour ne pas s'auto-abonner à ma propre room).
        viewModelScope.launch {
            combine(_acceptedGuests, _myUserId) { list, _ -> list }.collect { reconcileGuestSubscriptions(it) }
        }
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

    /**
     * Charge les réglages de CE live (dédicaces on/off + prix minimum effectif, invités on/off).
     * Pour tout le monde (hôte, invité, spectateur) : chacun doit voir les mêmes règles.
     */
    private fun loadLiveSettings() {
        viewModelScope.launch {
            val globalDefault = repository.dedicationMinPrice()
            runCatching { repository.getLive(liveId) }
                .onSuccess { l ->
                    _liveAllowsDedications.value = l.allowsDedications
                    _liveAllowGuests.value = l.allowGuests
                    _liveChatEnabled.value = l.chatEnabled
                    _dedicationMinPrice.value = l.dedicationMinPriceCredits ?: globalDefault
                }
                .onFailure { _dedicationMinPrice.value = globalDefault }
        }
    }

    /** Hôte : active/désactive les dédicaces pour ce live, en direct (visible par tous). */
    fun setDedicationsEnabled(enabled: Boolean) {
        _liveAllowsDedications.value = enabled
        viewModelScope.launch { runCatching { repository.updateLiveSettings(liveId, allowsDedications = enabled) } }
    }

    /** Hôte : fixe le prix minimum d'une dédicace pour CE live (surcharge le défaut global). */
    fun setDedicationMinPrice(price: Double) {
        if (price <= 0) return
        _dedicationMinPrice.value = price
        viewModelScope.launch { runCatching { repository.updateLiveSettings(liveId, dedicationMinPriceCredits = price) } }
    }

    /** Hôte : active/désactive les demandes d'invité (« lever la main ») pour ce live. */
    fun setGuestsEnabled(enabled: Boolean) {
        _liveAllowGuests.value = enabled
        viewModelScope.launch { runCatching { repository.updateLiveSettings(liveId, allowGuests = enabled) } }
    }

    /** Hôte : active/désactive le chat pour ce live, en direct (visible par tous). Pouvoir
     *  EXCLUSIF de l'hôte — jamais délégué aux modérateurs désignés. */
    fun setChatEnabled(enabled: Boolean) {
        _liveChatEnabled.value = enabled
        viewModelScope.launch { runCatching { repository.updateLiveSettings(liveId, chatEnabled = enabled) } }
    }

    /** Recharge les modérateurs désignés (appelé au chargement initial + sur événement temps réel). */
    fun loadModerators() {
        viewModelScope.launch { runCatching { repository.listEventModerators(liveId) }.getOrNull()?.let { _moderators.value = it } }
    }

    /** Hôte : (re)charge les spectateurs connectés (vivier du picker « désigner un modérateur »). */
    fun loadViewers() {
        viewModelScope.launch { _viewers.value = runCatching { repository.listCurrentViewers(liveId) }.getOrDefault(emptyList()) }
    }

    /** Hôte : désigne un spectateur modérateur (ban/masquer message — jamais le chat on/off). */
    fun appointModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.appointModerator(liveId, userId) }
                .onSuccess { loadModerators() }
                .onFailure { _dedicationFeedback.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Hôte : révoque un modérateur désigné. */
    fun revokeModerator(userId: String) {
        viewModelScope.launch {
            runCatching { repository.revokeModerator(liveId, userId) }
                .onSuccess { loadModerators() }
                .onFailure { _dedicationFeedback.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /**
     * Envoie une dédicace (message dédié) dans le live. `price` est choisi par le fan (≥ prix
     * minimum effectif — le champ de saisie le clamp déjà, revalidé ici par sécurité). L'échec
     * (ex. solde insuffisant, message trop court) est SIGNALÉ au fan plutôt que silencieux.
     */
    fun dedicate(message: String, price: Double) {
        val m = message.trim()
        if (m.isEmpty()) return
        val p = price.coerceAtLeast(_dedicationMinPrice.value)
        viewModelScope.launch {
            runCatching { repository.sendDedication(liveId, m, p) }
                .onSuccess { _dedicationFeedback.value = "🎤 Dédicace envoyée à l'artiste !" }
                .onFailure { _dedicationFeedback.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /**
     * Hôte : (re)charge les dédicaces de CE live — séparées en « en attente » (badge + actions
     * accepter/rejeter) et « acceptées/livrées » (historique, affiché en dessous dans la même
     * feuille). Auto-rafraîchi via realtime (`dedication:new`/`dedication:update`, voir
     * connectRealtime) — pas besoin d'ouvrir la feuille pour que le badge se mette à jour.
     */
    fun loadDedications() {
        if (!isHost) return
        viewModelScope.launch {
            val all = runCatching { repository.artistDedications() }.getOrDefault(emptyList())
            val mine = all.filter { it.concertId == liveId }
            _dedications.value = mine.filter { it.status == "pending" }
            _dedicationHistory.value = mine.filter { it.status == "paid" || it.status == "delivered" }
        }
    }

    /** Hôte : accepte une demande EN ATTENTE — débite le fan MAINTENANT, puis recharge. */
    fun acceptDedication(id: String) {
        viewModelScope.launch {
            runCatching { repository.acceptDedication(id) }
                .onSuccess { loadDedications() }
                .onFailure { _dedicationFeedback.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Hôte : rejette une demande EN ATTENTE — aucun débit, puis recharge. */
    fun rejectDedication(id: String) {
        viewModelScope.launch {
            runCatching { repository.rejectDedication(id) }
                .onSuccess { loadDedications() }
                .onFailure { _dedicationFeedback.value = it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed }
        }
    }

    /** Hôte : marque une dédicace ACCEPTÉE comme livrée (interprétée) puis recharge la liste. */
    fun deliverDedication(id: String) {
        viewModelScope.launch {
            runCatching { repository.deliverDedication(id) }.onSuccess { loadDedications() }
        }
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

    /** Hôte : (re)charge les demandes en attente + les invités actifs. */
    fun loadJoinRequests() {
        viewModelScope.launch {
            _joinRequests.value = runCatching { repository.joinRequests(liveId, "pending") }.getOrDefault(emptyList())
            _acceptedGuests.value = runCatching { repository.joinRequests(liveId, "accepted") }.getOrDefault(emptyList())
        }
    }

    /**
     * TOUT LE MONDE (spectateur ou invité) : (re)charge la liste des invités actifs — nécessaire
     * pour savoir à QUELLES rooms d'invités s'abonner (voir [reconcileGuestSubscriptions]).
     * Contrairement à [loadJoinRequests] (réservé à l'hôte), ne charge pas les demandes en attente
     * (non actionnables par un simple spectateur).
     */
    private fun loadAcceptedGuests() {
        viewModelScope.launch {
            _acceptedGuests.value = runCatching { repository.joinRequests(liveId, "accepted") }.getOrDefault(emptyList())
        }
    }

    /**
     * Recalcule mes connexions d'ABONNEMENT (vue seule) aux rooms des invités actifs, hors
     * moi-même : ouvre une room `live-guest-<liveId>-<userId>` par invité et en extrait la piste
     * vidéo primaire. Ferme/retire celles des invités qui ne sont plus actifs — c'est ce qui fait
     * disparaître la case d'un invité qui descend ou est retiré, chez TOUT LE MONDE (y compris
     * l'hôte, qui suit désormais la MÊME logique qu'un spectateur pour voir les invités).
     */
    private fun reconcileGuestSubscriptions(list: List<LiveJoinRequest>) {
        val wanted = list.map { it.userId }.filterNot { it == myUserId }.toSet()
        val stale = guestViewClients.keys - wanted
        stale.forEach { uid ->
            guestViewJobs.remove(uid)?.cancel()
            guestViewClients.remove(uid)?.leave()
            _guestVideos.update { it - uid }
            _guestClients.update { it - uid }
        }
        val toAdd = wanted - guestViewClients.keys
        toAdd.forEach { uid ->
            val client = mediaFactory()
            guestViewClients[uid] = client
            _guestClients.update { it + (uid to client) }
            // Case créée tout de suite (piste `null` = caméra coupée) : reste affichée en
            // placeholder tant que l'invité est présent, au lieu de disparaître.
            _guestVideos.update { it + (uid to null) }
            guestViewJobs[uid] = viewModelScope.launch {
                runCatching { client.join(roomName = "live-guest-$liveId-$uid", canPublish = false) }
                client.primaryVideoTrack.collect { track ->
                    _guestVideos.update { m -> m + (uid to track) }
                }
            }
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
                if (target == myUserId) viewModelScope.launch { runCatching { selfMedia().setMicEnabled(!mute) } }
            }
            "kick" -> if (target == myUserId) {
                // Retiré par l'artiste : j'arrête de publier dans ma room d'invité (ma tuile
                // disparaît chez tous). Je reste connecté à la room principale (jamais quittée) →
                // je continue de regarder le live sans interruption.
                _isGuestAccepted.value = false
                _myJoinRequestId.value = null
                viewModelScope.launch { runCatching { guestMedia.leave() } }
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
     * Invité accepté : monte sur scène — publie caméra/micro dans MA PROPRE room d'invité
     * (`live-guest-<liveId>-<monId>`, [guestMedia]), **sans jamais toucher** [media] (je reste
     * connecté à la room principale comme spectateur, je continue donc de voir/entendre
     * l'artiste pendant que je diffuse). C'est cette room dédiée — et non la room principale —
     * que le web (et tout autre spectateur) écoute pour un invité, via la même convention de nom.
     * La permission caméra/micro est demandée par l'écran avant l'appel.
     */
    fun goOnStage() {
        val uid = myUserId ?: return
        viewModelScope.launch {
            runCatching { guestMedia.join(roomName = "live-guest-$liveId-$uid", canPublish = true) }
            runCatching { guestMedia.startBroadcast() }
            // Micro + caméra COUPÉS par défaut à l'entrée en scène (`startBroadcast` les active
            // toujours) : c'est à l'invité d'activer consciemment ce qu'il veut montrer, pour
            // éviter toute surprise (parité avec l'esprit « accepté ≠ diffusé publiquement »).
            runCatching { guestMedia.setMicEnabled(false) }
            runCatching { guestMedia.setCamEnabled(false) }
        }
    }

    /**
     * Invité : DESCEND du direct sans le quitter — arrête de publier dans sa room d'invité (sa
     * caméra/tuile disparaît chez TOUS, via [reconcileGuestSubscriptions] qui suit la liste des
     * invités actifs), clôt sa demande (l'hôte le retire de la liste). Il reste connecté à la
     * room principale (jamais quittée) → il continue de regarder le live sans interruption.
     */
    fun leaveStage() {
        _isGuestAccepted.value = false
        val rid = _myJoinRequestId.value
        _myJoinRequestId.value = null
        viewModelScope.launch {
            // `respondJoinStatus` (POST .../respond) est réservé à L'HÔTE côté backend (403 sinon,
            // silencieusement avalé par runCatching) — un invité qui clôt SA PROPRE demande doit
            // passer par l'endpoint d'ANNULATION (DELETE), qui autorise le demandeur lui-même quel
            // que soit le statut courant (pending OU accepted). Sans ce bon endpoint, la demande
            // restait "accepted" côté serveur → case fantôme chez tous, ré-acceptation au retour.
            rid?.let { runCatching { repository.cancelJoin(it) } }
            runCatching { guestMedia.leave() }
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

    /** Coupe/rétablit le micro (hôte : room principale ; invité : sa room dédiée). */
    fun toggleMic() {
        viewModelScope.launch { runCatching { val m = selfMedia(); m.setMicEnabled(!m.micEnabled.value) } }
    }

    /** Coupe/rétablit la caméra (hôte : room principale ; invité : sa room dédiée). */
    fun toggleCamera() {
        viewModelScope.launch { runCatching { val m = selfMedia(); m.setCamEnabled(!m.camEnabled.value) } }
    }

    /** Bascule caméra avant/arrière (hôte : room principale ; invité : sa room dédiée). */
    fun switchCamera() {
        viewModelScope.launch { runCatching { selfMedia().switchCamera() } }
    }

    /** Pause/reprise du direct : coupe (ou rétablit) caméra + micro ensemble. */
    fun setPaused(paused: Boolean) {
        viewModelScope.launch {
            val m = selfMedia()
            runCatching { m.setCamEnabled(!paused); m.setMicEnabled(!paused) }
        }
    }

    /** Active/désactive le flou d'arrière-plan (filtre). */
    fun toggleBlur() {
        runCatching { selfMedia().toggleBlur() }
    }

    /** Termine le live côté backend puis notifie l'appelant (mode hôte). */
    fun endLive(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.endLive(liveId) }
            stop()
            onDone()
        }
    }

    /** Arrête tout (sortie d'écran) : room principale, ma room d'invité + tous les abonnements. */
    fun stop() {
        // Quitter le live EST une expulsion : si j'étais un invité ACCEPTÉ, ma demande passe
        // "ended" côté serveur — sinon ma case restait visible pour tout le monde (placeholder
        // permanent, caméra coupée = jamais retirée) et, à mon retour, j'étais ré-accepté sans
        // nouvelle demande. Symétrique avec `leaveStage()`/le kick de l'artiste.
        if (!isHost && _isGuestAccepted.value) {
            _myJoinRequestId.value?.let { rid ->
                // Même endpoint que `leaveStage()` (voir son commentaire) — `respondJoinStatus`
                // est host-only et échouerait silencieusement pour un invité qui se retire lui-même.
                viewModelScope.launch { runCatching { repository.cancelJoin(rid) } }
            }
        }
        liveSession?.disconnect()
        chatSession?.disconnect()
        media.leave()
        runCatching { guestMedia.leave() }
        guestViewJobs.values.forEach { it.cancel() }
        guestViewJobs.clear()
        guestViewClients.values.forEach { it.leave() }
        guestViewClients.clear()
    }

    /** Envoie un message de chat (le serveur diffuse ensuite), optionnellement en réponse à `parentId`. */
    fun sendMessage(text: String, parentId: String? = null) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch { runCatching { repository.postMessage(liveId, content, parentId) } }
    }

    /** Hôte : bannit un spectateur (optimiste + persistant). Il ne peut plus écrire ni rejoindre. */
    fun banUser(userId: String, reason: String?) {
        _bannedUserIds.update { it + userId }
        viewModelScope.launch {
            runCatching { repository.createStreamBan(liveId, userId, reason) }
                .onFailure { _bannedUserIds.update { ids -> ids - userId } }
        }
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
        viewModelScope.launch { refreshGiftLeaderboard() }
    }

    /** Recharge le classement et renvoie la liste fraîche (réutilisée pour résoudre un nom d'expéditeur). */
    private suspend fun refreshGiftLeaderboard(): List<GiftLeaderboardEntry> {
        val list = runCatching { repository.giftLeaderboard(liveId) }.getOrDefault(emptyList())
        _giftLeaderboard.value = list
        _topDonor.value = list.firstOrNull()?.let { com.dualmusic.core.ui.overlay.TopDonor(it.displayName, it.value) }
        return list
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
                _messages.update { it + LiveChatMessage(id = p.id, userId = p.userId, content = p.content, user = p.user, parentId = p.parentId) }
            }
            // Cadeaux
            live.on(Realtime.RealtimeEvent.GIFT, GiftPayload.serializer()) { p ->
                if (p.toUserId != null && p.toUserId == myUserId) {
                    _giftReceived.value = "🎁 Vous avez reçu un cadeau (${p.value.toInt()} crédits) !"
                }
                viewModelScope.launch {
                    // Recharge le classement (met à jour la bulle top-donateur) ET en profite pour
                    // résoudre le NOM de l'expéditeur (déjà hydraté avec les profils) → affiché
                    // dans la carte glissante « cadeau reçu ».
                    val list = refreshGiftLeaderboard()
                    val senderName = list.find { it.userId == p.fromUserId }?.displayName
                    _giftFeed.update { it + LiveGift(giftCounter++, p.fromUserId, senderName, p.giftName, p.giftImage, p.value) }
                }
            }
            // Bannissement d'un spectateur poussé par le serveur (parité duel `stream:banned`).
            live.on("stream:banned", LiveStreamBannedPayload.serializer()) { p ->
                if (p.streamId == null || p.streamId == liveId) _bannedUserIds.update { it + p.userId }
            }
            // Présence (viewers)
            live.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                _viewerCount.value = p.count
            }
            // Likes (total diffusé par le backend)
            live.on("likes", LikesPayload.serializer()) { p ->
                if (p.likes > _likes.value) _likes.value = p.likes
            }
            // Réglages du live modifiés par l'artiste en direct — tout le monde réagit aussitôt
            // (masque/affiche le bouton main levée, la feuille de dédicace, etc.).
            live.on("settings", LiveSettingsPayload.serializer()) { p ->
                _liveAllowsDedications.value = p.allowsDedications
                _liveAllowGuests.value = p.allowGuests
                // `null` = pas de surcharge sur ce live → garde le prix effectif déjà chargé
                // (défaut global) au lieu de l'écraser par une valeur absente.
                p.dedicationMinPriceCredits?.let { _dedicationMinPrice.value = it }
            }
            // Chat on/off (même événement `settings` — `chat_enabled` n'existe pas sur
            // [LiveSettingsPayload], décodé ici séparément via le DTO partagé).
            live.on(Realtime.RealtimeEvent.SETTINGS, EventSettingsPayload.serializer()) { p ->
                p.chatEnabled?.let { _liveChatEnabled.value = it }
            }
            // Modération : un modérateur a été désigné/révoqué par l'hôte → recharge pour tous.
            live.on(Realtime.RealtimeEvent.MODERATOR_APPOINTED, EventModeratorPayload.serializer()) { loadModerators() }
            live.on(Realtime.RealtimeEvent.MODERATOR_REVOKED, EventModeratorPayload.serializer()) { loadModerators() }
            // Dédicaces : (a) hôte — badge + liste auto-rafraîchis SANS avoir à ouvrir la feuille
            // (parité demandes d'invité) ; (b) fan concerné — bannière immédiate de la décision de
            // l'artiste (accepté = montant débité MAINTENANT, rejeté = aucun débit), pour ne jamais
            // laisser croire qu'il a encore un solde qu'il n'a plus (synchronisé, pas besoin de
            // recharger l'écran).
            live.on("dedication:new", DedicationEventPayload.serializer()) { if (isHost) loadDedications() }
            live.on("dedication:update", DedicationEventPayload.serializer()) { p ->
                if (isHost) loadDedications()
                if (p.fanId != null && p.fanId == myUserId) {
                    _dedicationFeedback.value = when (p.status) {
                        "paid" -> "🎤 Ta dédicace a été acceptée — ${p.priceCredits?.toInt() ?: ""} crédits débités."
                        "rejected" -> "Ta dédicace a été refusée — aucun crédit débité."
                        "delivered" -> "🎉 Ta dédicace vient d'être interprétée en direct !"
                        else -> null
                    }
                }
            }
            // Relais broadcast : réactions emojis (live-emojis-<id>) + actions invités (live-controls-<id>).
            live.on("broadcast", BroadcastEnvelope.serializer()) { env ->
                when (env.event) {
                    "emoji_reaction" -> env.payload?.emoji?.let { pushEmoji(it) }
                    "guest_action" -> env.payload?.let { onGuestAction(it) }
                    "live_status" -> if (!isHost) _liveWaiting.value = env.payload?.status == "waiting"
                }
            }
            // Demandes d'invités : rafraîchir la liste (TOUT LE MONDE — pas que l'hôte — pour que
            // chacun sache à quelles rooms d'invités s'abonner, voir reconcileGuestSubscriptions).
            live.on("join:new", JoinEventPayload.serializer()) { if (isHost) loadJoinRequests() else loadAcceptedGuests() }
            live.on("join:update", JoinEventPayload.serializer()) { p ->
                if (isHost) loadJoinRequests() else loadAcceptedGuests()
                // Spectateur : sa propre demande a changé d'état.
                if (!isHost && p.userId != null && p.userId == myUserId) {
                    when (p.status) {
                        "accepted" -> _isGuestAccepted.value = true
                        // "cancelled" = auto-retrait (leaveStage/stop, endpoint DELETE — seul autorisé
                        // pour le demandeur lui-même) ; "ended"/"rejected" = décision de l'hôte.
                        "rejected", "ended", "cancelled" -> _isGuestAccepted.value = false
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

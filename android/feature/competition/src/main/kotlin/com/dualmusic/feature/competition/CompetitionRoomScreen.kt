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
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.feature.sponsor.SponsorAdLayer
import com.dualmusic.domain.competition.CompetitionCandidate
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.realtime.StatusPayload
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
class CompetitionRoomViewModel(
    private val competitionId: String,
    private val repository: CompetitionRepository,
    private val realtime: RealtimeClient,
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

    private var liveSession: NamespaceSession? = null

    /** Charge l'inventaire de cadeaux du caller. */
    fun loadInventory() {
        viewModelScope.launch {
            runCatching { repository.inventory() }.getOrNull()?.let { _inventory.value = it }
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

    /** Démarre : charge le classement + détermine l'organisateur + écoute le temps réel. */
    fun start() {
        refresh()
        loadInventory()
        recordingCtl.refresh()
        viewModelScope.launch {
            val comp = runCatching { repository.competition(competitionId) }.getOrNull()
            _status.value = comp?.status
            val myId = repository.myUserId()
            _isManager.value = comp?.managerId != null && comp.managerId == myId
        }
        val live = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        viewModelScope.launch {
            live.onConnect {
                live.join(Realtime.RoomType.COMPETITION, competitionId)
                live.emit("broadcast:join", "competition-emojis-$competitionId")
            }
            live.on(Realtime.RealtimeEvent.STATUS, StatusPayload.serializer()) { p ->
                _status.value = p.status
                // Un changement d'état peut clore les votes → on resynchronise le classement.
                refresh()
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
            }
            // Pub sponsor (start/stop) diffusée à toute la room.
            live.on(Realtime.RealtimeEvent.SPONSOR_AD, com.dualmusic.domain.realtime.SponsorAdPayload.serializer()) { p ->
                sponsor.onEvent(p)
            }
            live.connect()
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

    /** Arrête l'écoute temps réel. */
    fun stop() { liveSession?.disconnect() }

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
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftPulse by viewModel.giftPulse.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
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
    // Candidat ciblé par l'offrande de cadeau (ouvre le sélecteur d'inventaire).
    var giftTargetCandidate by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DualMusicTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
        ) {
            Text(strings.ranking, color = colors.foreground, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = colors.destructive) }
            // Contrôles de l'organisateur (manager) : publier + finaliser + enregistrement.
            if (isManager) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(strings.publishAction, modifier = Modifier.weight(1f), onClick = { viewModel.publish() })
                    DMButton(strings.finalizeAction, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { viewModel.finalize() })
                }
                com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = { viewModel.recordingCtl.toggle() })
            }
            if (candidates.isEmpty()) {
                DMEmptyState(
                    title = strings.noCandidates,
                    subtitle = strings.noCandidatesHint,
                    icon = Icons.Filled.Star,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
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

            // Barre de réactions : J'aime + emojis (flottent pour tous les spectateurs).
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendLike() },
                    contentAlignment = Alignment.Center,
                ) { Text("❤️", fontSize = 18.sp) }
                if (likes > 0) Text("$likes", color = colors.foreground, fontSize = 12.sp)
                CompetitionReactionEmojis.forEach { e ->
                    Box(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(e) }
                }
            }
        }

        // Réactions flottantes montantes (vues par tous), respecte « Réduire les animations ».
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

        // Cadeau reçu : burst central (halo GPU), masqué si « Réduire les animations ». Parité live.
        if (!uiPrefs.reduceAnimations && giftPulse > 0L) {
            androidx.compose.runtime.key(giftPulse) {
                GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center))
            }
        }

        // Classement finalisé : célébration du vainqueur (rang 1 = plus haut score). Vue par tous
        // les spectateurs dès que l'organisateur finalise (event `status` = finished).
        if (status == "finished" && candidates.isNotEmpty()) {
            WinnerCelebration(
                winnerName = candidates.first().artist?.displayName ?: strings.winnerGeneric,
                title = strings.winnerTitle,
                subtitle = strings.winnerCongrats,
                reduceAnimations = uiPrefs.reduceAnimations,
            )
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

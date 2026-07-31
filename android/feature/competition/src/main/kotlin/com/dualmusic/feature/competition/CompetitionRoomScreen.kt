package com.dualmusic.feature.competition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.overlay.FloatingReactionsLayer
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
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
) : ViewModel() {

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

    private var liveSession: NamespaceSession? = null

    /** Démarre : charge le classement + écoute le temps réel. */
    fun start() {
        refresh()
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current

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
                        onVote = { viewModel.vote(candidate.id, voteCredits) },
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
    }
}

/** Emojis de réaction (identiques au live/duel/web). */
private val CompetitionReactionEmojis = listOf("🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

/** Ligne de classement : rang, artiste, score, bouton de vote. */
@Composable
private fun CandidateRow(
    rank: Int,
    candidate: CompetitionCandidate,
    voteCredits: Int,
    onVote: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
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
                Text("${candidate.score.toInt()} pts", color = colors.mutedForeground)
            }
            DMButton("${strings.vote} ($voteCredits)", onClick = onVote)
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

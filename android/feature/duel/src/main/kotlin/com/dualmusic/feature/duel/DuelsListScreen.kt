package com.dualmusic.feature.duel

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.EventStatus
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.replay.ReplayVideo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la page « Duels Musicaux » (3 onglets) — parité web :
 *  - En direct / À venir : `GET /duels` filtré par statut + `GET /duels/votes/batch`.
 *  - Replays : `GET /replays?sourceType=duel&isPublic=true`.
 *  - Spectateurs en temps réel par carte : présence Socket.IO `/live` (rooms `duel:<id>`).
 */
class DuelsListViewModel(
    private val repository: DuelRepository,
    private val realtime: RealtimeClient,
) : ViewModel() {

    data class UiState(
        val live: List<Duel> = emptyList(),
        val upcoming: List<Duel> = emptyList(),
        val replays: List<ReplayVideo> = emptyList(),
        /** duelId → (votes artiste1, votes artiste2). */
        val votes: Map<String, Pair<Double, Double>> = emptyMap(),
        /** duelId → spectateurs (présence temps réel). */
        val presence: Map<String, Int> = emptyMap(),
        val loading: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var liveSession: NamespaceSession? = null

    fun load() {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val all = runCatching { repository.duels(limit = 100) }.getOrDefault(emptyList())
            val live = all.filter { it.status == EventStatus.LIVE }
            val upcoming = all.filter { it.status == EventStatus.UPCOMING }
            val batch = runCatching { repository.votesBatch(all.map { it.id }) }.getOrDefault(emptyList())
            val byDuel = batch.groupBy { it.duelId }
            val votes = all.associate { d ->
                val vs = byDuel[d.id].orEmpty()
                d.id to ((vs.firstOrNull { it.artistId == d.artist1Id }?.total ?: 0.0) to
                    (vs.firstOrNull { it.artistId == d.artist2Id }?.total ?: 0.0))
            }
            val replays = runCatching { repository.duelReplays() }.getOrDefault(emptyList())
            _ui.update { it.copy(live = live, upcoming = upcoming, replays = replays, votes = votes, loading = false) }
            connectPresence(live)
        }
    }

    private fun connectPresence(duels: List<Duel>) {
        if (duels.isEmpty()) return
        val session = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        viewModelScope.launch {
            session.onConnect { duels.forEach { session.join(Realtime.RoomType.DUEL, it.id) } }
            session.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                val id = p.room?.substringAfter("duel:", "")?.takeIf { it.isNotBlank() }
                if (id != null) _ui.update { it.copy(presence = it.presence + (id to p.count)) }
            }
            session.connect()
        }
    }

    override fun onCleared() {
        liveSession?.disconnect()
        super.onCleared()
    }
}

/**
 * Page « Duels Musicaux » : titre + recherche + 3 onglets (En direct / À venir / Replays).
 *
 * @param onOpen ouvre une room de duel.
 * @param onOpenReplay ouvre le lecteur d'un replay de duel.
 */
@Composable
fun DuelsListScreen(
    viewModel: DuelsListViewModel,
    onOpen: (Duel) -> Unit,
    onOpenReplay: (ReplayVideo) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()

    fun matchDuel(d: Duel) = q.isEmpty() ||
        (d.artist1?.displayName?.lowercase()?.contains(q) == true) ||
        (d.artist2?.displayName?.lowercase()?.contains(q) == true)
    fun matchReplay(r: ReplayVideo) = q.isEmpty() || (r.title?.lowercase()?.contains(q) == true)

    val live = ui.live.filter(::matchDuel)
    val upcoming = ui.upcoming.filter(::matchDuel)
    val replays = ui.replays.filter(::matchReplay)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.duelsPageTitle, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(s.duelsSubtitle, color = colors.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        androidx.compose.material3.OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(s.searchPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Onglets.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            TabPill("${s.duelTabLive} (${ui.live.size})", tab == 0) { tab = 0 }
            TabPill("${s.duelTabUpcoming} (${ui.upcoming.size})", tab == 1) { tab = 1 }
            TabPill("${s.duelTabReplays} (${ui.replays.size})", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> if (live.isEmpty()) EmptyDuels(s.noDuelsLive) else live.forEach { DuelCard(it, ui.votes[it.id], ui.presence[it.id], isLive = true, onOpen = onOpen) }
            1 -> if (upcoming.isEmpty()) EmptyDuels(s.noDuelsUpcoming) else upcoming.forEach { DuelCard(it, ui.votes[it.id], null, isLive = false, onOpen = onOpen) }
            else -> if (replays.isEmpty()) EmptyDuels(s.noDuelReplays) else replays.forEach { ReplayCard(it, onOpenReplay) }
        }
    }
}

@Composable
private fun EmptyDuels(title: String) {
    DMEmptyState(title = title, icon = Icons.Filled.DateRange, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier
            .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) Color.White else colors.mutedForeground, fontSize = 13.sp) }
}

/** Carte d'un duel (En direct / À venir). */
@Composable
private fun DuelCard(duel: Duel, votes: Pair<Double, Double>?, viewers: Int?, isLive: Boolean, onOpen: (Duel) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val a1 = votes?.first ?: 0.0
    val a2 = votes?.second ?: 0.0
    val total = a1 + a2

    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(duel) }) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            // Badge statut.
            Box(
                modifier = Modifier
                    .background(if (isLive) Color(0xFFB91C1C) else Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(if (isLive) "🔥 ${s.liveBadge}" else "📅 ${s.upcomingBadge}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }

            duel.scheduledTime?.let { Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMM yyyy HH:mm")} GMT", color = colors.mutedForeground, fontSize = 12.sp) }

            // Badge payant/gratuit.
            if (duel.ticketPrice > 0) {
                Box(modifier = Modifier.background(Color(0x33F59E0B), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${s.duelPaid} • ${duel.ticketPrice.toInt()} ${s.creditUnit}", color = Color(0xFFF59E0B), fontSize = 11.sp)
                }
            } else {
                Box(modifier = Modifier.background(Color(0x3310B981), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(s.duelFree, color = Color(0xFF10B981), fontSize = 11.sp)
                }
            }

            // Artistes + trophée.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ArtistColumn(duel.artist1?.displayName ?: s.artist1, duel.artist1?.avatarUrl, a1.toInt(), Modifier.weight(1f))
                Text("🏆", fontSize = 20.sp)
                ArtistColumn(duel.artist2?.displayName ?: s.artist2, duel.artist2?.avatarUrl, a2.toInt(), Modifier.weight(1f))
            }

            // Barre de répartition des votes.
            if (total > 0) {
                val leftShare = (a1 / total).toFloat().coerceIn(0.02f, 0.98f)
                Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp))) {
                    Box(Modifier.weight(leftShare).fillMaxHeight().background(DualMusicTheme.gradients.primary))
                    Box(Modifier.weight(1f - leftShare).fillMaxHeight().background(colors.electricBlue))
                }
            }

            if (isLive && viewers != null) {
                Text("👥 $viewers ${s.spectators}", color = colors.mutedForeground, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            DMButton(if (isLive) s.voteBtn else s.viewDuelBtn, modifier = Modifier.fillMaxWidth()) { onOpen(duel) }
        }
    }
}

@Composable
private fun ArtistColumn(name: String, avatarUrl: String?, votes: Int, modifier: Modifier = Modifier) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DMRemoteImage(url = avatarUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), fallbackEmoji = "🎤")
        Text(name, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("$votes ${s.votesWord}", color = colors.accent, fontSize = 12.sp)
    }
}

/** Carte d'un replay de duel. */
@Composable
private fun ReplayCard(replay: ReplayVideo, onOpen: (ReplayVideo) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(replay) }, padded = false) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
            DMRemoteImage(url = replay.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp), fallbackEmoji = "🎬")
            // Badge VS.
            Box(modifier = Modifier.align(Alignment.TopStart).padding(DualMusicTheme.spacing.sm).background(Color(0xFFB91C1C), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("🏆 VS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text("▶", color = Color.White, fontSize = 40.sp)
            // Durée.
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(DualMusicTheme.spacing.sm).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("🕒 ${replay.duration?.let { "${it}s" } ?: "N/A"}", color = Color.White, fontSize = 11.sp)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(replay.title ?: "Duel", color = colors.foreground, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("👁 ${replay.viewsCount} ${s.viewsWord}", color = colors.mutedForeground, fontSize = 12.sp)
                replay.recordedDate?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it, "dd MMM yyyy"), color = colors.mutedForeground, fontSize = 12.sp) }
            }
        }
    }
}

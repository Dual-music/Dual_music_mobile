package com.dualmusic.feature.leaderboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.leaderboard.LeaderboardEndpoints
import com.dualmusic.domain.leaderboard.LeaderboardEntry
import com.dualmusic.domain.leaderboard.LeaderboardSeason
import com.dualmusic.domain.leaderboard.SeasonReward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** ViewModel des classements (artistes + donateurs + saisons). */
class LeaderboardViewModel(private val api: ApiClient) : ViewModel() {

    private val _artists = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val artists: StateFlow<List<LeaderboardEntry>> = _artists.asStateFlow()

    private val _donors = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val donors: StateFlow<List<LeaderboardEntry>> = _donors.asStateFlow()

    private val _seasons = MutableStateFlow<List<LeaderboardSeason>>(emptyList())
    val seasons: StateFlow<List<LeaderboardSeason>> = _seasons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _artists.value = fetch(LeaderboardEndpoints.ARTISTS)
            _donors.value = fetch(LeaderboardEndpoints.DONORS)
            _seasons.value = runCatching {
                api.request(Endpoint.get(LeaderboardEndpoints.SEASONS), ListSerializer(LeaderboardSeason.serializer()))
            }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }

    private suspend fun fetch(path: String): List<LeaderboardEntry> =
        runCatching { api.request(Endpoint.get(path), ListSerializer(LeaderboardEntry.serializer())) }.getOrDefault(emptyList())
}

/** Page publique « Classement des Artistes » — 3 onglets (parité web). */
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val donors by viewModel.donors.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("🏆 ${s.leaderboardTitle}", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(s.leaderboardSubtitle, color = colors.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            TabPill("⚔ ${s.artists}", tab == 0) { tab = 0 }
            TabPill("❤ ${s.donors}", tab == 1) { tab = 1 }
            TabPill("📅 ${s.periodic}", tab == 2) { tab = 2 }
        }

        if (isLoading) {
            DMLoadingBox(Modifier.fillMaxWidth())
        } else when (tab) {
            0 -> ArtistsTab(artists)
            1 -> DonorsTab(donors)
            else -> SeasonsTab(seasons)
        }
    }
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

// ── Artistes ──

@Composable
private fun ArtistsTab(list: List<LeaderboardEntry>) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    if (list.isEmpty()) {
        DMEmptyState(title = s.emptyRanking, subtitle = s.emptyRankingHint, icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
        return
    }
    Text(s.allArtists, color = colors.foreground, fontWeight = FontWeight.Bold)
    list.forEachIndexed { i, e ->
        RankRow(
            rank = i + 1,
            name = e.displayName,
            avatarUrl = e.avatarUrl,
            stats = "↗ 0 ${s.votesWord} · 🎁 0 ${s.giftsWord} · ⚔ 0 ${s.winsWord}",
            value = "${e.value.toInt()}",
            valueLabel = s.pointsWord,
        )
    }
}

// ── Donateurs ──

@Composable
private fun DonorsTab(list: List<LeaderboardEntry>) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    if (list.isEmpty()) {
        DMEmptyState(title = s.emptyRanking, subtitle = s.emptyRankingHint, icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
        return
    }
    // Podium top-3.
    if (list.size >= 3) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            PodiumCard(2, list[1], Modifier.weight(1f))
            PodiumCard(1, list[0], Modifier.weight(1f))
            PodiumCard(3, list[2], Modifier.weight(1f))
        }
    }
    Text(s.topDonors, color = colors.foreground, fontWeight = FontWeight.Bold)
    list.forEachIndexed { i, e ->
        RankRow(
            rank = i + 1,
            name = e.displayName,
            avatarUrl = e.avatarUrl,
            stats = "🎁 0 ${s.giftsWord} · ↗ 0 ${s.votesWord}",
            value = "${e.value.toInt()}",
            valueLabel = s.creditsWord,
        )
    }
}

@Composable
private fun PodiumCard(rank: Int, entry: LeaderboardEntry, modifier: Modifier = Modifier) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text(medal(rank), fontSize = 22.sp)
            DMRemoteImage(url = entry.avatarUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), fallbackEmoji = "🙂")
            Text(entry.displayName, color = colors.foreground, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
            Text("${entry.value.toInt()}", color = colors.primary, fontWeight = FontWeight.Bold)
            Text(s.creditsWord, color = colors.mutedForeground, fontSize = 10.sp)
        }
    }
}

/** Ligne de classement générique : rang, avatar, nom, stats, valeur. */
@Composable
private fun RankRow(rank: Int, name: String, avatarUrl: String?, stats: String, value: String, valueLabel: String) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text(medal(rank), fontSize = 18.sp)
                DMRemoteImage(url = avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), fallbackEmoji = "🙂")
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = colors.foreground, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(stats, color = colors.mutedForeground, fontSize = 11.sp, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(value, color = colors.primary, fontWeight = FontWeight.Bold)
                Text(valueLabel, color = colors.mutedForeground, fontSize = 10.sp)
            }
        }
    }
}

// ── Périodique (saisons) ──

@Composable
private fun SeasonsTab(seasons: List<LeaderboardSeason>) {
    val s = LocalStrings.current
    var sub by remember { mutableIntStateOf(0) }
    val active = seasons.filter { it.isActive }
    val ended = seasons.filterNot { it.isActive }

    if (seasons.isEmpty()) {
        DMEmptyState(title = s.emptyRanking, subtitle = s.emptyRankingHint, icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
        TabPill("${s.duelTabLive} (${active.size})", sub == 0) { sub = 0 }
        TabPill("${s.seasonEnded} (${ended.size})", sub == 1) { sub = 1 }
    }
    val shown = if (sub == 0) active else ended
    if (shown.isEmpty()) {
        DMEmptyState(title = s.emptyRanking, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
    } else {
        shown.forEach { SeasonCard(it) }
    }
}

@Composable
private fun SeasonCard(season: LeaderboardSeason) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text(season.name, color = colors.foreground, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.background(if (season.isActive) colors.primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(if (season.isActive) s.seasonActive else s.seasonEnded, color = if (season.isActive) colors.primary else colors.mutedForeground, fontSize = 11.sp)
                }
            }
            Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(if (season.type == "donor") s.seasonTypeDonor else s.seasonTypeArtist, color = colors.accent, fontSize = 11.sp)
            }
            val period = listOfNotNull(
                season.startDate?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMM yyyy HH:mm") + " GMT" },
                season.endDate?.let { "→ " + com.dualmusic.core.ui.datetime.formatTz(it, "dd MMM yyyy HH:mm") + " GMT" },
            ).joinToString(" ")
            if (period.isNotBlank()) Text(period, color = colors.mutedForeground, fontSize = 12.sp)

            if (season.rewards.isNotEmpty()) {
                Text("⭐ ${s.rewardsByRank}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                season.rewards.sortedBy { it.rankPosition }.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(DualMusicTheme.radii.sm)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        Text(medal(r.rankPosition), fontSize = 16.sp)
                        Text("#${r.rankPosition}", color = colors.mutedForeground, fontSize = 12.sp)
                        Text(rewardLabel(r, s.creditsWord), color = colors.foreground, fontSize = 12.sp)
                    }
                }
            }
            Text(s.noParticipants, color = colors.mutedForeground, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Libellé d'une récompense selon son type. */
private fun rewardLabel(r: SeasonReward, creditsWord: String): String = when (r.rewardType) {
    "credits" -> "💰 ${(r.creditsAmount ?: 0.0).toInt()} $creditsWord"
    "physical" -> "📦 ${r.physicalDescription ?: ""}"
    else -> "🎁 ${r.physicalDescription ?: ""}".trim()
}

/** Médaille pour le podium, numéro sinon. */
private fun medal(rank: Int): String = when (rank) {
    1 -> "👑"
    2 -> "🥈"
    3 -> "🥉"
    else -> "$rank."
}

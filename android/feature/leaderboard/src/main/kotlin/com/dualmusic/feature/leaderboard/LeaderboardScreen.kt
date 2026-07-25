package com.dualmusic.feature.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.leaderboard.LeaderboardEndpoints
import com.dualmusic.domain.leaderboard.LeaderboardEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import com.dualmusic.domain.leaderboard.LeaderboardSeason
import kotlinx.serialization.builtins.ListSerializer

/**
 * ViewModel des classements (artistes + donateurs).
 *
 * @param api client HTTP (les endpoints leaderboard sont de simples lectures).
 */
class LeaderboardViewModel(private val api: ApiClient) : ViewModel() {

    private val _artists = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val artists: StateFlow<List<LeaderboardEntry>> = _artists.asStateFlow()

    private val _donors = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val donors: StateFlow<List<LeaderboardEntry>> = _donors.asStateFlow()

    private val _seasons = MutableStateFlow<List<com.dualmusic.domain.leaderboard.LeaderboardSeason>>(emptyList())
    val seasons: StateFlow<List<com.dualmusic.domain.leaderboard.LeaderboardSeason>> = _seasons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge les classements (artistes, donateurs) + les saisons (périodique). */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _artists.value = fetch(LeaderboardEndpoints.ARTISTS)
            _donors.value = fetch(LeaderboardEndpoints.DONORS)
            _seasons.value = runCatching {
                api.request(
                    Endpoint.get(LeaderboardEndpoints.SEASONS),
                    ListSerializer(com.dualmusic.domain.leaderboard.LeaderboardSeason.serializer()),
                )
            }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }

    private suspend fun fetch(path: String): List<LeaderboardEntry> =
        runCatching {
            api.request(Endpoint.get(path), ListSerializer(LeaderboardEntry.serializer()))
        }.getOrDefault(emptyList())
}

/**
 * Écran des classements avec deux onglets : Artistes / Donateurs.
 *
 * @param viewModel source des classements.
 */
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val donors by viewModel.donors.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(strings.artists) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(strings.donors) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(strings.periodic) })
        }

        if (isLoading) DMLoadingBox(Modifier.fillMaxWidth().weight(1f))

        if (tab == 2) {
            if (!isLoading && seasons.isEmpty()) {
                DMEmptyState(
                    title = strings.emptyRanking,
                    subtitle = strings.emptyRankingHint,
                    icon = Icons.Filled.Star,
                    modifier = Modifier.weight(1f),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(seasons) { season -> SeasonRow(season) }
            }
        } else {
            val list = if (tab == 0) artists else donors
            if (!isLoading && list.isEmpty()) {
                DMEmptyState(
                    title = strings.emptyRanking,
                    subtitle = strings.emptyRankingHint,
                    icon = Icons.Filled.Star,
                    modifier = Modifier.weight(1f),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                itemsIndexed(list) { index, entry -> EntryRow(index + 1, entry) }
            }
        }
    }
}

/** Ligne de classement : rang (médaille au podium), nom, total. */
@Composable
private fun EntryRow(rank: Int, entry: LeaderboardEntry) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${medal(rank)}  ${entry.displayName}", color = colors.foreground, fontWeight = FontWeight.Bold)
            Text("${entry.total.toInt()}", color = colors.accent, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ligne d'une saison (classement périodique) : nom, période, statut, récompense. */
@Composable
private fun SeasonRow(season: LeaderboardSeason) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column {
                Text(season.name, color = colors.foreground, fontWeight = FontWeight.Bold)
                val period = listOfNotNull(season.startDate?.take(10), season.endDate?.take(10)).joinToString(" → ")
                if (period.isNotBlank()) Text(period, color = colors.mutedForeground)
                if (season.isMysteryReward) Text(strings.mysteryReward, color = colors.accent)
            }
            Text(
                if (season.isActive) strings.seasonActive else strings.seasonEnded,
                color = if (season.isActive) colors.primary else colors.mutedForeground,
                fontWeight = FontWeight.Bold,
            )
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

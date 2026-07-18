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
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.leaderboard.LeaderboardEndpoints
import com.dualmusic.domain.leaderboard.LeaderboardEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge les deux classements. */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _artists.value = fetch(LeaderboardEndpoints.ARTISTS)
            _donors.value = fetch(LeaderboardEndpoints.DONORS)
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Classements", color = colors.foreground, fontWeight = FontWeight.Bold)

        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Artistes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Donateurs") })
        }

        if (isLoading) CircularProgressIndicator(color = colors.primary)

        val list = if (tab == 0) artists else donors
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            itemsIndexed(list) { index, entry -> EntryRow(index + 1, entry) }
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

/** Médaille pour le podium, numéro sinon. */
private fun medal(rank: Int): String = when (rank) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "$rank."
}

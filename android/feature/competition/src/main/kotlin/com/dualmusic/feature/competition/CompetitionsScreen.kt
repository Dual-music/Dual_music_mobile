package com.dualmusic.feature.competition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Competition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du catalogue de compétitions.
 *
 * @param repository lectures REST des compétitions.
 */
class CompetitionsViewModel(private val repository: CompetitionRepository) : ViewModel() {

    private val _competitions = MutableStateFlow<List<Competition>>(emptyList())
    val competitions: StateFlow<List<Competition>> = _competitions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge le catalogue. */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _competitions.value = runCatching { repository.competitions() }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

/**
 * Catalogue des compétitions.
 *
 * @param viewModel source du catalogue.
 * @param onOpen callback à l'ouverture d'une compétition (room + classement).
 */
@Composable
fun CompetitionsListScreen(
    viewModel: CompetitionsViewModel,
    onOpen: (Competition) -> Unit,
) {
    val competitions by viewModel.competitions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Compétitions", color = colors.foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        if (isLoading) CircularProgressIndicator(color = colors.primary)
        if (!isLoading && competitions.isEmpty()) {
            DMEmptyState(
                title = "Aucune compétition pour le moment",
                subtitle = "Les compétitions ouvertes apparaîtront ici.",
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(competitions) { competition ->
                CompetitionRow(competition) { onOpen(competition) }
            }
        }
    }
}

/** Carte d'une compétition : titre, période, récompense. */
@Composable
private fun CompetitionRow(competition: Competition, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(competition.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                competition.startAt?.let { Text(it.take(10), color = colors.mutedForeground) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(competition.status.replaceFirstChar { it.uppercase() }, color = colors.mutedForeground)
                if (competition.rewardAmount > 0) {
                    Text("🏆 ${competition.rewardAmount.toInt()}", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

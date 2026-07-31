package com.dualmusic.feature.competition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.competition.MyCandidacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de « Mes compétitions » : liste les candidatures de l'artiste
 * (`GET /competitions/candidacies/mine`), chacune enrichie de sa compétition.
 *
 * @param repository lectures REST des compétitions.
 */
class MyCompetitionsViewModel(private val repository: CompetitionRepository) : ViewModel() {

    private val _candidacies = MutableStateFlow<List<MyCandidacy>>(emptyList())
    val candidacies: StateFlow<List<MyCandidacy>> = _candidacies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge les candidatures du caller. */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _candidacies.value = runCatching { repository.myCandidacies() }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

/**
 * Écran « Mes compétitions » — liste des candidatures de l'artiste avec statut + score,
 * équivalent mobile de `MyCompetitions` du web.
 *
 * @param viewModel source des candidatures.
 */
@Composable
fun MyCompetitionsScreen(viewModel: MyCompetitionsViewModel) {
    val candidacies by viewModel.candidacies.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        when {
            isLoading -> DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
            candidacies.isEmpty() -> DMEmptyState(
                title = s.noCandidacies,
                subtitle = s.noCandidaciesHint,
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(candidacies) { c -> CandidacyRow(c) }
            }
        }
    }
}

/** Ligne d'une candidature : titre de la compétition, statut, score. */
@Composable
private fun CandidacyRow(candidacy: MyCandidacy) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val statusLabel = when (candidacy.status) {
        "approved" -> s.candStatusApproved
        "rejected" -> s.candStatusRejected
        else -> s.candStatusPending
    }
    val statusColor = when (candidacy.status) {
        "approved" -> colors.primary
        "rejected" -> colors.destructive
        else -> colors.mutedForeground
    }
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = DualMusicTheme.spacing.sm)) {
                Text(
                    candidacy.competition?.title ?: "—",
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                )
                val date = candidacy.competition?.startAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy") }
                if (!date.isNullOrBlank()) Text(date, color = colors.mutedForeground)
                Text("${s.candScore} : ${candidacy.score.toInt()}", color = colors.accent)
            }
            Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold)
        }
    }
}

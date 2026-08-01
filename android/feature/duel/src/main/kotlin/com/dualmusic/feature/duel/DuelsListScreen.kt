package com.dualmusic.feature.duel

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
import com.dualmusic.core.ui.components.DMLoadingBox
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
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.EventStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la liste des duels (catalogue).
 *
 * @param repository lectures REST des duels.
 */
class DuelsListViewModel(private val repository: DuelRepository) : ViewModel() {

    private val _duels = MutableStateFlow<List<Duel>>(emptyList())
    val duels: StateFlow<List<Duel>> = _duels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge le catalogue (les duels en direct d'abord côté serveur). */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _duels.value = runCatching { repository.duels() }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

/**
 * Liste des duels — point d'entrée vers une room de duel.
 *
 * @param viewModel source du catalogue.
 * @param onOpen callback quand l'utilisateur ouvre un duel.
 */
@Composable
fun DuelsListScreen(
    viewModel: DuelsListViewModel,
    onOpen: (Duel) -> Unit,
) {
    val duels by viewModel.duels.collectAsStateWithLifecycle()
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
        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.screenDuels, color = colors.foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        if (isLoading) {
            DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
        }
        if (!isLoading && duels.isEmpty()) {
            DMEmptyState(
                title = "Aucun duel pour le moment",
                subtitle = "Les duels à venir s'afficheront ici.",
                icon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(duels) { duel ->
                DuelRow(duel = duel, onClick = { onOpen(duel) })
            }
        }
    }
}

/** Case d'un duel : statut + les deux artistes + bouton « Regarder ». */
@Composable
private fun DuelRow(duel: Duel, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = com.dualmusic.core.ui.i18n.LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    statusLabel(duel.status),
                    color = if (duel.status == EventStatus.LIVE) colors.accent else colors.mutedForeground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                duel.scheduledTime?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it), color = colors.mutedForeground, fontSize = 12.sp) }
            }
            Text(
                "${duel.artist1?.displayName ?: s.artist1}  🆚  ${duel.artist2?.displayName ?: s.artist2}",
                color = colors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            DMButton(
                if (duel.status == EventStatus.LIVE) "👁  ${s.watchAction}" else s.watchAction,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
            )
        }
    }
}

/** Libellé lisible du statut d'un duel. */
private fun statusLabel(status: EventStatus): String = when (status) {
    EventStatus.LIVE -> "🔴 EN DIRECT"
    EventStatus.UPCOMING -> "À venir"
    EventStatus.ENDED -> "Terminé"
    else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
}

package com.dualmusic.feature.concert

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DateRange
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.model.EventStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du catalogue de concerts.
 *
 * @param repository lectures REST des concerts d'artistes.
 */
class ConcertsViewModel(private val repository: ConcertRepository) : ViewModel() {

    private val _concerts = MutableStateFlow<List<Concert>>(emptyList())
    val concerts: StateFlow<List<Concert>> = _concerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge le catalogue. */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _concerts.value = runCatching { repository.concerts() }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

/**
 * Catalogue des concerts d'artistes.
 *
 * @param viewModel source du catalogue.
 * @param onOpen callback à l'ouverture d'un concert (détail/billetterie).
 */
@Composable
fun ConcertsListScreen(
    viewModel: ConcertsViewModel,
    onOpen: (Concert) -> Unit = {},
) {
    val concerts by viewModel.concerts.collectAsStateWithLifecycle()
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
        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.screenConcerts, color = colors.foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        if (isLoading) DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
        if (!isLoading && concerts.isEmpty()) {
            DMEmptyState(
                title = "Aucun concert programmé",
                subtitle = "Les concerts à venir apparaîtront ici.",
                icon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(concerts) { concert -> ConcertRow(concert) { onOpen(concert) } }
        }
    }
}

/** Carte d'un concert : titre, date, statut et prix du billet. */
@Composable
private fun ConcertRow(concert: Concert, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(concert.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                concert.scheduledDate?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it), color = colors.mutedForeground) }
                if (concert.allowsDedications) {
                    Text("💌 Dédicaces ouvertes", color = colors.accent)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (concert.status == EventStatus.LIVE) "🔴 EN DIRECT" else statusLabel(concert.status),
                    color = if (concert.status == EventStatus.LIVE) colors.accent else colors.mutedForeground,
                    fontWeight = FontWeight.Bold,
                )
                if (concert.ticketPrice > 0) {
                    Text("${concert.ticketPrice.toInt()} crédits", color = colors.foreground)
                } else {
                    Text("Gratuit", color = colors.mutedForeground)
                }
            }
        }
    }
}

/** Libellé lisible du statut d'un concert. */
private fun statusLabel(status: EventStatus): String = when (status) {
    EventStatus.UPCOMING -> "À venir"
    EventStatus.ENDED -> "Terminé"
    EventStatus.CANCELLED -> "Annulé"
    else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
}

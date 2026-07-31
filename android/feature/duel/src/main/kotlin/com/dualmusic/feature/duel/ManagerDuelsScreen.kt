package com.dualmusic.feature.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DateTimePickerField
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.artist.ArtistSummary
import com.dualmusic.domain.model.Duel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'espace « Mes Duels » du MANAGER (organisateur).
 *
 * Le manager crée un duel entre DEUX artistes (il s'assigne arbitre via `manager_id`) et voit
 * la liste des duels qu'il gère. Les contrôles en direct (minuteur/vainqueur/fin) se font
 * dans la room du duel.
 */
class ManagerDuelsViewModel(private val repository: DuelRepository) : ViewModel() {

    data class UiState(
        val duels: List<Duel> = emptyList(),
        val artists: List<ArtistSummary> = emptyList(),
        val myId: String? = null,
        val creating: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val myId = repository.myUserId()
            val duels = myId?.let { runCatching { repository.managedDuels(it) }.getOrDefault(emptyList()) } ?: emptyList()
            val artists = runCatching { repository.artists() }.getOrDefault(emptyList())
            _ui.update { it.copy(myId = myId, duels = duels, artists = artists) }
        }
    }

    fun createDuel(artist1Id: String, artist2Id: String, scheduled: String?) {
        val myId = _ui.value.myId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, message = null) }
            runCatching { repository.createDuel(artist1Id, artist2Id, scheduled?.ifBlank { null }, myId) }
                .onSuccess {
                    _ui.update { it.copy(creating = false, message = com.dualmusic.core.ui.i18n.appStrings.duelCreated) }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(creating = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}

/**
 * Écran « Mes Duels » du manager : formulaire de création (2 artistes + date) + liste des
 * duels gérés (avec accès direct si en direct).
 */
@Composable
fun ManagerDuelsScreen(viewModel: ManagerDuelsViewModel, onOpenDuel: (Duel) -> Unit = {}) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var query by remember { mutableStateOf("") }
    var picking by remember { mutableIntStateOf(0) } // 0 = aucun, 1 = artiste A, 2 = artiste B
    var artist1 by remember { mutableStateOf<ArtistSummary?>(null) }
    var artist2 by remember { mutableStateOf<ArtistSummary?>(null) }
    var scheduled by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        // --- Créer un duel ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Text(s.createDuelTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(s.createDuelHint, color = colors.mutedForeground)

            // Deux emplacements d'artistes.
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm)) {
                ArtistSlot(label = "🅰 ${s.artist1}", artist = artist1, selected = picking == 1, modifier = Modifier.weight(1f)) { picking = if (picking == 1) 0 else 1 }
                ArtistSlot(label = "🅱 ${s.artist2}", artist = artist2, selected = picking == 2, modifier = Modifier.weight(1f)) { picking = if (picking == 2) 0 else 2 }
            }

            // Sélecteur d'artiste (visible quand on choisit un emplacement).
            if (picking != 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(s.searchArtist) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                )
                val candidates = ui.artists.filter {
                    val other = if (picking == 1) artist2 else artist1
                    it.opponentUserId != other?.opponentUserId &&
                        (query.isBlank() || it.displayName.contains(query, ignoreCase = true))
                }.take(8)
                candidates.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (picking == 1) artist1 = a else artist2 = a
                            picking = 0; query = ""
                        }.padding(vertical = DualMusicTheme.spacing.sm),
                    ) { Text("🎤  ${a.displayName}", color = colors.foreground) }
                }
            }

            DateTimePickerField(
                value = scheduled,
                onValueChange = { scheduled = it },
                label = s.proposedDateOptional,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
            )

            ui.message?.let { Text(it, color = colors.accent, modifier = Modifier.padding(top = DualMusicTheme.spacing.xs)) }

            DMButton(
                if (ui.creating) s.sending else s.createDuelAction,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                onClick = {
                    val a1 = artist1?.opponentUserId
                    val a2 = artist2?.opponentUserId
                    if (a1 != null && a2 != null && a1 != a2) {
                        viewModel.createDuel(a1, a2, scheduled)
                        artist1 = null; artist2 = null; scheduled = ""
                    }
                },
            )
        }

        // --- Duels gérés ---
        Text(s.managedDuels, color = colors.foreground, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(ui.duels) { duel -> ManagedDuelRow(duel, onOpen = { onOpenDuel(duel) }) }
        }
    }
}

@Composable
private fun ArtistSlot(label: String, artist: ArtistSummary?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = modifier
            .background(if (selected) colors.primary.copy(alpha = 0.25f) else colors.card, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = colors.mutedForeground, fontWeight = FontWeight.Bold)
        Text(artist?.displayName ?: "—", color = colors.foreground)
    }
}

@Composable
private fun ManagedDuelRow(duel: Duel, onOpen: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "${duel.artist1?.displayName ?: s.artist1}  vs  ${duel.artist2?.displayName ?: s.artist2}",
                    color = colors.foreground, fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    duel.scheduledTime?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it), color = colors.mutedForeground) }
                    Text(duel.status.name.lowercase(), color = colors.accent)
                }
            }
            if (duel.status == com.dualmusic.domain.model.EventStatus.LIVE) {
                DMButton(s.joinAction, style = DMButtonStyle.SECONDARY, onClick = onOpen)
            }
        }
    }
}

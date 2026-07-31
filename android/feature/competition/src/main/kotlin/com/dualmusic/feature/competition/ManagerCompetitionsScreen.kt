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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DateTimePickerField
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Competition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'espace « Mes compétitions » du MANAGER (organisateur).
 * Le manager crée des compétitions et voit celles qu'il gère (tous statuts).
 */
class ManagerCompetitionsViewModel(private val repository: CompetitionRepository) : ViewModel() {

    data class UiState(
        val competitions: List<Competition> = emptyList(),
        val myId: String? = null,
        val creating: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val myId = repository.myUserId()
            val comps = runCatching { repository.myCompetitions() }.getOrDefault(emptyList())
            _ui.update { it.copy(myId = myId, competitions = comps) }
        }
    }

    fun create(title: String, description: String, reward: Double, maxCandidates: Int, startAt: String, endAt: String) {
        val myId = _ui.value.myId ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, message = null) }
            runCatching { repository.createCompetition(myId, title.trim(), description.trim(), reward, maxCandidates, startAt, endAt) }
                .onSuccess {
                    _ui.update { it.copy(creating = false, message = com.dualmusic.core.ui.i18n.appStrings.competitionCreated) }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(creating = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed) } }
        }
    }
}

/** Écran « Mes compétitions » du manager : formulaire de création + liste des compétitions gérées. */
@Composable
fun ManagerCompetitionsScreen(viewModel: ManagerCompetitionsViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var maxCandidates by remember { mutableStateOf("10") }
    var startAt by remember { mutableStateOf("") }
    var endAt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Text(s.createCompetitionTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(s.createCompetitionHint, color = colors.mutedForeground)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(s.titleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(s.descriptionLabel) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                OutlinedTextField(value = reward, onValueChange = { reward = it.filter { c -> c.isDigit() } }, label = { Text(s.rewardLabel) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = maxCandidates, onValueChange = { maxCandidates = it.filter { c -> c.isDigit() } }, label = { Text(s.maxCandidatesLabel) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            DateTimePickerField(value = startAt, onValueChange = { startAt = it }, label = s.startDateLabel, modifier = Modifier.fillMaxWidth())
            DateTimePickerField(value = endAt, onValueChange = { endAt = it }, label = s.endDateLabel, modifier = Modifier.fillMaxWidth())
            ui.message?.let { Text(it, color = colors.accent, modifier = Modifier.padding(top = DualMusicTheme.spacing.xs)) }
            DMButton(
                if (ui.creating) s.sending else s.createCompetitionAction,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                onClick = {
                    viewModel.create(title, description, reward.toDoubleOrNull() ?: 0.0, maxCandidates.toIntOrNull() ?: 10, startAt, endAt)
                    title = ""; description = ""; reward = ""; startAt = ""; endAt = ""
                },
            )
        }

        Text(s.menuMyCompetitions, color = colors.foreground, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(ui.competitions) { comp -> ManagedCompetitionRow(comp) }
        }
    }
}

@Composable
private fun ManagedCompetitionRow(comp: Competition) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(comp.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    comp.startAt?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy"), color = colors.mutedForeground) }
                    Text(comp.status, color = colors.accent)
                }
            }
            if (comp.rewardAmount > 0) Text("${comp.rewardAmount.toInt()} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
        }
    }
}

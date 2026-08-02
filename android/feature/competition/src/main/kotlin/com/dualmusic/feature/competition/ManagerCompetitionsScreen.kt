package com.dualmusic.feature.competition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DateTimePickerField
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.geo.Countries
import com.dualmusic.domain.model.Competition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'espace « Mes compétitions » du MANAGER (organisateur).
 * Parité web : le manager crée des compétitions (formulaire complet) et voit celles qu'il gère.
 */
class ManagerCompetitionsViewModel(private val repository: CompetitionRepository) : ViewModel() {

    data class UiState(
        val competitions: List<Competition> = emptyList(),
        val myId: String? = null,
        val creating: Boolean = false,
        val showForm: Boolean = false,
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

    fun toggleForm() = _ui.update { it.copy(showForm = !it.showForm, message = null) }

    /** Crée la compétition (le managerId est injecté depuis l'état). */
    fun create(body: CreateCompetitionBody) {
        val myId = _ui.value.myId ?: return
        if (body.title.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, message = null) }
            runCatching { repository.createCompetition(body.copy(managerId = myId)) }
                .onSuccess {
                    _ui.update { it.copy(creating = false, showForm = false, message = com.dualmusic.core.ui.i18n.appStrings.competitionCreated) }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(creating = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed) } }
        }
    }
}

/**
 * Écran « Mes compétitions » du manager — parité stricte avec le web : en-tête + bouton, cartes
 * de statistiques (Publiée / En direct / Brouillon / Terminée), onglets de filtre, état vide, et
 * formulaire de création complet (image, mode, frais, pays éligibles, 4 dates GMT).
 */
@Composable
fun ManagerCompetitionsScreen(viewModel: ManagerCompetitionsViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var tab by remember { mutableStateOf("all") }

    val comps = ui.competitions
    val published = comps.count { it.status == "published" }
    val live = comps.count { it.status == "live" }
    val draft = comps.count { it.status == "draft" }
    val finished = comps.count { it.status == "finished" }
    val filtered = if (tab == "all") comps else comps.filter { it.status == tab }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        // En-tête + bouton nouvelle compétition.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🏆 ${s.menuMyCompetitions}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(s.compManagementSubtitle, color = colors.mutedForeground, fontSize = 12.sp)
            }
            DMButton(
                if (ui.showForm) s.compCancel else s.compNew,
                style = if (ui.showForm) DMButtonStyle.OUTLINE else DMButtonStyle.PRIMARY,
                onClick = { viewModel.toggleForm() },
            )
        }

        // Cartes de statistiques.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            StatCard(published, s.compStatusPublished, Modifier.weight(1f))
            StatCard(live, s.compStatusLive, Modifier.weight(1f))
            StatCard(draft, s.compStatusDraft, Modifier.weight(1f))
            StatCard(finished, s.compStatusFinished, Modifier.weight(1f))
        }

        ui.message?.let { Text(it, color = colors.accent) }

        // Formulaire complet (révélé par le bouton).
        if (ui.showForm) {
            CompetitionFormCard(creating = ui.creating, onSubmit = { viewModel.create(it) })
        }

        // Onglets de filtre.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
        ) {
            FilterTab("${s.compTabAll} (${comps.size})", tab == "all") { tab = "all" }
            FilterTab("${s.compStatusDraft} ($draft)", tab == "draft") { tab = "draft" }
            FilterTab("${s.compStatusPublished} ($published)", tab == "published") { tab = "published" }
            FilterTab("${s.compStatusLive} ($live)", tab == "live") { tab = "live" }
            FilterTab("${s.compStatusFinished} ($finished)", tab == "finished") { tab = "finished" }
        }

        // Liste ou état vide.
        if (filtered.isEmpty()) {
            DMEmptyState(
                title = s.compNoCompetitions,
                subtitle = s.compManagerEmptyDesc,
                icon = Icons.Filled.Star,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg),
            )
        } else {
            filtered.forEach { comp -> ManagedCompetitionRow(comp) }
        }
    }
}

/** Carte de statistique (nombre + libellé de statut). */
@Composable
private fun StatCard(count: Int, label: String, modifier: Modifier = Modifier) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = modifier) {
        Text("$count", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = colors.mutedForeground, fontSize = 11.sp)
    }
}

/** Onglet de filtre (pilule). */
@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier
            .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = if (selected) Color.White else colors.mutedForeground, fontSize = 12.sp) }
}

/** Ligne d'une compétition gérée. */
@Composable
private fun ManagedCompetitionRow(comp: Competition) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(comp.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Text(if (comp.mode == "onsite") s.compOnsite else s.compOnline, color = colors.mutedForeground, fontSize = 12.sp)
                    comp.startAt?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy"), color = colors.mutedForeground, fontSize = 12.sp) }
                    comp.maxCandidates?.let { Text("· $it ${s.maxCandidatesLabel.lowercase()}", color = colors.mutedForeground, fontSize = 12.sp) }
                }
            }
            Text(statusLabel(comp.status, s), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

private fun statusLabel(status: String, s: com.dualmusic.core.ui.i18n.Strings): String = when (status) {
    "published" -> s.compStatusPublished
    "live" -> s.compStatusLive
    "finished" -> s.compStatusFinished
    else -> s.compStatusDraft
}

/** Formulaire de création complet (parité web `CompetitionForm`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompetitionFormCard(creating: Boolean, onSubmit: (CreateCompetitionBody) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val tz = "GMT"

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("online") }
    var maxCandidates by remember { mutableStateOf("10") }
    var rewardDesc by remember { mutableStateOf("") }
    var rewardAmount by remember { mutableStateOf("0") }
    var entryFeeRequired by remember { mutableStateOf(false) }
    var entryFeeAmount by remember { mutableStateOf("0") }
    var eligibilityScope by remember { mutableStateOf("country") }
    val selectedCountries = remember { mutableStateListOf<String>() }
    var countrySearch by remember { mutableStateOf("") }
    var applicationOpensAt by remember { mutableStateOf("") }
    var applicationDeadline by remember { mutableStateOf("") }
    var startAt by remember { mutableStateOf("") }
    var endAt by remember { mutableStateOf("") }
    // Présentiel.
    var country by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var commune by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var venueName by remember { mutableStateOf("") }
    var venueAddress by remember { mutableStateOf("") }
    var venueContact by remember { mutableStateOf("") }

    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(s.compNew, color = colors.foreground, fontWeight = FontWeight.Bold)

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(s.titleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(s.descriptionLabel) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = coverUrl, onValueChange = { coverUrl = it }, label = { Text(s.compCover) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SelectField(
                label = s.compMode,
                selectedLabel = if (mode == "onsite") s.compOnsite else s.compOnline,
                options = listOf("online" to s.compOnline, "onsite" to s.compOnsite),
                onSelect = { mode = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                OutlinedTextField(value = maxCandidates, onValueChange = { maxCandidates = it.filter { c -> c.isDigit() } }, label = { Text(s.maxCandidatesLabel) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = rewardAmount, onValueChange = { rewardAmount = it.filter { c -> c.isDigit() } }, label = { Text(s.compRewardAmount) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = rewardDesc, onValueChange = { rewardDesc = it }, label = { Text(s.compRewardDesc) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // Frais d'inscription.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.compEntryFee, color = colors.foreground)
                Switch(checked = entryFeeRequired, onCheckedChange = { entryFeeRequired = it })
            }
            if (entryFeeRequired) {
                OutlinedTextField(value = entryFeeAmount, onValueChange = { entryFeeAmount = it.filter { c -> c.isDigit() } }, label = { Text(s.compEntryFeeAmount) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }

            // Éligibilité.
            SelectField(
                label = s.compEligibility,
                selectedLabel = when (eligibilityScope) {
                    "africa" -> s.compEligibilityAfrica
                    "world" -> s.compEligibilityWorld
                    else -> s.compEligibilityCountry
                },
                options = listOf("country" to s.compEligibilityCountry, "africa" to s.compEligibilityAfrica, "world" to s.compEligibilityWorld),
                onSelect = { eligibilityScope = it },
            )
            if (eligibilityScope == "country") {
                Text(s.compEligibleCountriesPick, color = colors.foreground, fontSize = 12.sp)
                OutlinedTextField(value = countrySearch, onValueChange = { countrySearch = it }, label = { Text(s.compSearchCountry) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val q = countrySearch.trim().lowercase()
                val list = if (q.isEmpty()) Countries.WORLD else Countries.WORLD.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    list.forEach { c ->
                        val sel = c.code in selectedCountries
                        Box(
                            modifier = Modifier
                                .background(if (sel) colors.primary else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                .clickable { if (sel) selectedCountries.remove(c.code) else selectedCountries.add(c.code) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) { Text(c.name, color = if (sel) Color.White else colors.mutedForeground, fontSize = 12.sp) }
                    }
                }
            }

            // Présentiel : lieu.
            if (mode == "onsite") {
                SelectField(
                    label = s.compCountry,
                    selectedLabel = Countries.WORLD.firstOrNull { it.code == country }?.name ?: s.compCountry,
                    options = Countries.WORLD.map { it.code to it.name },
                    onSelect = { country = it },
                )
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(s.compCity) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = commune, onValueChange = { commune = it }, label = { Text(s.compCommune) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text(s.compDistrict) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = venueName, onValueChange = { venueName = it }, label = { Text(s.compVenueName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = venueAddress, onValueChange = { venueAddress = it }, label = { Text(s.compVenueAddress) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = venueContact, onValueChange = { venueContact = it }, label = { Text(s.compVenueContact) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            // Fuseau + dates (GMT).
            Text("🕒 ${s.compTimezoneHint} $tz", color = colors.accent, fontSize = 12.sp)
            DateTimePickerField(value = applicationOpensAt, onValueChange = { applicationOpensAt = it }, label = "${s.compApplicationOpensAt} ($tz)", modifier = Modifier.fillMaxWidth())
            Text(s.compApplicationOpensAtHint, color = colors.mutedForeground, fontSize = 11.sp)
            DateTimePickerField(value = applicationDeadline, onValueChange = { applicationDeadline = it }, label = "${s.compApplicationDeadline} ($tz)", modifier = Modifier.fillMaxWidth())
            DateTimePickerField(value = startAt, onValueChange = { startAt = it }, label = "${s.compStartAtLabel} ($tz)", modifier = Modifier.fillMaxWidth())
            DateTimePickerField(value = endAt, onValueChange = { endAt = it }, label = "${s.compEndAtLabel} ($tz)", modifier = Modifier.fillMaxWidth())

            DMButton(
                if (creating) s.sending else s.createCompetitionAction,
                enabled = !creating,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                onClick = {
                    onSubmit(
                        CreateCompetitionBody(
                            managerId = "",
                            title = title.trim(),
                            description = description.trim().ifBlank { null },
                            coverUrl = coverUrl.trim().ifBlank { null },
                            mode = mode,
                            maxCandidates = maxCandidates.toIntOrNull() ?: 10,
                            rewardDescription = rewardDesc.trim().ifBlank { null },
                            rewardAmount = rewardAmount.toDoubleOrNull() ?: 0.0,
                            entryFeeRequired = entryFeeRequired,
                            entryFeeAmount = if (entryFeeRequired) (entryFeeAmount.toDoubleOrNull() ?: 0.0) else 0.0,
                            eligibilityScope = eligibilityScope,
                            eligibleCountries = if (eligibilityScope == "country") selectedCountries.toList() else emptyList(),
                            country = if (mode == "onsite") country.ifBlank { null } else null,
                            city = if (mode == "onsite") city.trim().ifBlank { null } else null,
                            commune = if (mode == "onsite") commune.trim().ifBlank { null } else null,
                            district = if (mode == "onsite") district.trim().ifBlank { null } else null,
                            venueName = if (mode == "onsite") venueName.trim().ifBlank { null } else null,
                            venueAddress = if (mode == "onsite") venueAddress.trim().ifBlank { null } else null,
                            venueContact = if (mode == "onsite") venueContact.trim().ifBlank { null } else null,
                            applicationOpensAt = applicationOpensAt.ifBlank { null },
                            applicationDeadline = applicationDeadline.ifBlank { null },
                            startAt = startAt.ifBlank { null },
                            endAt = endAt.ifBlank { null },
                        ),
                    )
                },
            )
        }
    }
}

/** Sélecteur déroulant (parité `<Select>` web) : champ en lecture seule + menu. */
@Composable
private fun SelectField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Le champ readOnly ne reçoit pas le clic → couche transparente pour ouvrir le menu.
        Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, disp) ->
                DropdownMenuItem(text = { Text(disp) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

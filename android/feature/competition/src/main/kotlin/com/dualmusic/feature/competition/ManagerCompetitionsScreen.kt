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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.clip
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
class ManagerCompetitionsViewModel(
    private val repository: CompetitionRepository,
    private val uploader: com.dualmusic.core.upload.MediaUploader,
) : ViewModel() {

    data class UiState(
        val competitions: List<Competition> = emptyList(),
        val myId: String? = null,
        val creating: Boolean = false,
        val showForm: Boolean = false,
        /** Compétition en cours d'édition (null = création). */
        val editing: Competition? = null,
        val coverUrl: String = "",
        val coverUploading: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Candidats de la compétition ouverte en détail. */
    private val _candidates = MutableStateFlow<List<com.dualmusic.domain.competition.CompetitionCandidate>>(emptyList())
    val candidates: StateFlow<List<com.dualmusic.domain.competition.CompetitionCandidate>> = _candidates.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val myId = repository.myUserId()
            runCatching { repository.myCompetitions() }
                .onSuccess { comps -> _ui.update { it.copy(myId = myId, competitions = comps, message = null) } }
                // DIAGNOSTIC : on affiche la vraie cause (au lieu de masquer par une liste vide).
                .onFailure { e -> _ui.update { it.copy(myId = myId, message = "⚠️ ${e::class.simpleName}: ${(e.message ?: "").take(200)}") } }
        }
    }

    /** Charge les candidats d'une compétition (page détail). */
    fun loadCandidates(competitionId: String) {
        viewModelScope.launch {
            _candidates.value = runCatching { repository.candidates(competitionId) }.getOrDefault(emptyList())
        }
    }

    /** Publie la compétition puis recharge la liste. */
    fun publish(competitionId: String) {
        viewModelScope.launch {
            runCatching { repository.publish(competitionId) }.onSuccess { load() }
        }
    }

    /** Valide/rejette un candidat puis recharge les candidats. */
    fun reviewCandidate(competitionId: String, candidateId: String, approve: Boolean) {
        viewModelScope.launch {
            runCatching { repository.reviewCandidate(candidateId, approve) }.onSuccess { loadCandidates(competitionId) }
        }
    }

    fun toggleForm() = _ui.update { it.copy(showForm = !it.showForm, message = null, coverUrl = "", editing = null) }

    /** Ouvre le formulaire en mode ÉDITION, pré-rempli avec la compétition. */
    fun startEdit(comp: Competition) = _ui.update {
        it.copy(showForm = true, editing = comp, coverUrl = comp.coverUrl ?: "", message = null)
    }

    /** URL de couverture (saisie manuelle ou upload). */
    fun setCover(url: String) = _ui.update { it.copy(coverUrl = url) }

    /** Upload l'image de couverture choisie (catégorie `image`), puis renseigne l'URL. */
    fun uploadCover(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _ui.update { it.copy(coverUploading = true, message = null) }
            runCatching { uploader.upload(media, com.dualmusic.domain.upload.UploadCategory.IMAGE) }
                .onSuccess { url -> _ui.update { it.copy(coverUploading = false, coverUrl = url) } }
                // Message clair : on ne montre jamais l'exception brute (timeout, IO…) à l'utilisateur.
                .onFailure { _ui.update { it.copy(coverUploading = false, message = com.dualmusic.core.ui.i18n.appStrings.imageUploadFriendly) } }
        }
    }

    /** Crée OU met à jour la compétition (managerId + coverUrl injectés depuis l'état). */
    fun create(body: CreateCompetitionBody) {
        val myId = _ui.value.myId ?: return
        if (body.title.isBlank()) return
        val editing = _ui.value.editing
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, message = null) }
            val full = body.copy(managerId = myId, coverUrl = _ui.value.coverUrl.ifBlank { null })
            val result = if (editing != null) {
                // Édition : on préserve le statut existant (ne pas repasser en "open").
                runCatching { repository.updateCompetition(editing.id, full.copy(status = editing.status)) }
            } else {
                runCatching { repository.createCompetition(full) }
            }
            result
                .onSuccess {
                    _ui.update { it.copy(creating = false, showForm = false, editing = null, coverUrl = "", message = com.dualmusic.core.ui.i18n.appStrings.competitionCreated) }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(creating = false, message = friendlyCreateError(e)) } }
        }
    }

    /** Traduit une erreur technique en message clair pour l'utilisateur (sans exception brute). */
    private fun friendlyCreateError(t: Throwable): String {
        val s = com.dualmusic.core.ui.i18n.appStrings
        val de = t as? com.dualmusic.domain.api.DomainError ?: return s.networkSlow
        return when {
            de.code == "VALIDATION_ERROR" -> s.competitionCheckFields
            // Message serveur court et lisible → on l'affiche tel quel (diagnostic utile).
            de.message.isNotBlank() && de.message.length <= 140 -> de.message
            else -> s.competitionCreateFailed
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

    // Page détail (parité web /competition/:id), ouverte via la flèche d'une carte.
    var openDetail by remember { mutableStateOf<Competition?>(null) }
    val detail = openDetail
    if (detail != null) {
        androidx.activity.compose.BackHandler { openDetail = null }
        CompetitionDetailScreen(
            competition = detail,
            isManager = detail.managerId != null && detail.managerId == ui.myId,
            viewModel = viewModel,
            onBack = { openDetail = null },
        )
        return
    }

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
        // En-tête (titre + sous-titre).
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("🏆 ${s.menuMyCompetitions}", color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(s.compManagementSubtitle, color = colors.mutedForeground, fontSize = 12.sp)
        }
        // Bouton nouvelle compétition (pleine largeur).
        DMButton(
            if (ui.showForm) s.compCancel else s.compNew,
            style = if (ui.showForm) DMButtonStyle.OUTLINE else DMButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.toggleForm() },
        )

        ui.message?.let { Text(it, color = colors.accent) }

        // Formulaire complet (révélé par le bouton).
        if (ui.showForm) {
            CompetitionFormCard(
                initial = ui.editing,
                creating = ui.creating,
                coverUrl = ui.coverUrl,
                coverUploading = ui.coverUploading,
                onCoverChange = { viewModel.setCover(it) },
                onUploadCover = { viewModel.uploadCover(it) },
                onSubmit = { viewModel.create(it) },
            )
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
            filtered.forEach { comp ->
                ManagedCompetitionRow(
                    comp = comp,
                    onOpen = { openDetail = comp; viewModel.loadCandidates(comp.id) },
                    onEdit = { viewModel.startEdit(comp) },
                )
            }
        }
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

/** Ligne compacte d'une compétition gérée : miniature + infos (1 ligne) + crayon + flèche. */
@Composable
private fun ManagedCompetitionRow(comp: Competition, onOpen: () -> Unit, onEdit: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            com.dualmusic.core.ui.components.DMRemoteImage(
                url = comp.coverUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(DualMusicTheme.radii.sm)),
                fallbackEmoji = "🏆",
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(comp.title, color = colors.foreground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(if (comp.mode == "onsite") s.compOnsite else s.compOnline)
                        comp.startAt?.let { append(" · ${com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy")}") }
                        comp.maxCandidates?.let { append(" · $it ${s.maxCandidatesLabel.lowercase()}") }
                    },
                    color = colors.mutedForeground, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(statusLabel(comp.status, s), color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            // Crayon d'édition — masqué en direct/terminée (comme le web).
            if (comp.status != "live" && comp.status != "finished") {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.clickable { onEdit() }.padding(4.dp))
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.mutedForeground)
        }
    }
}

private fun statusLabel(status: String, s: com.dualmusic.core.ui.i18n.Strings): String = when (status) {
    "published" -> s.compStatusPublished
    "live" -> s.compStatusLive
    "finished" -> s.compStatusFinished
    else -> s.compStatusDraft
}

/**
 * Page détail d'une compétition (parité web /competition/:id) : bannière, badges, description,
 * bloc d'informations, bouton Publier (manager), et liste des candidats (valider/rejeter).
 */
@Composable
private fun CompetitionDetailScreen(
    competition: Competition,
    isManager: Boolean,
    viewModel: ManagerCompetitionsViewModel,
    onBack: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero).verticalScroll(rememberScrollState()).padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        DMButton("← ${s.back}", style = DMButtonStyle.OUTLINE, onClick = onBack)

        com.dualmusic.core.ui.components.DMRemoteImage(
            url = competition.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(DualMusicTheme.radii.md)),
            fallbackEmoji = "🏆",
        )
        Text(competition.title, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            DetailBadge(statusLabel(competition.status, s), colors.accent)
            DetailBadge(if (competition.mode == "onsite") "📍 ${s.compOnsite}" else "🌐 ${s.compOnline}", colors.mutedForeground)
            if (competition.isPublicPaid) DetailBadge("${s.duelPaid} · ${competition.viewerTicketPrice.toInt()}", colors.foreground)
            else DetailBadge(s.duelFree, Color(0xFF10B981))
        }
        competition.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mutedForeground) }

        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                InfoRow("📅 ${s.compStartAtLabel}", competition.startAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoRow("📅 ${s.compEndAtLabel}", competition.endAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoRow("📅 ${s.compApplicationOpensAt}", competition.applicationOpensAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoRow("📅 ${s.compApplicationDeadline}", competition.applicationDeadline?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoRow("👥 ${s.maxCandidatesLabel}", competition.maxCandidates?.toString())
                InfoRow("🏆 ${s.compRewardDesc}", competition.rewardDescription)
                if (competition.mode == "onsite") {
                    val venue = listOfNotNull(competition.venueName, competition.venueAddress, competition.city, competition.country).joinToString(" — ")
                    InfoRow("📍", venue.ifBlank { null })
                    InfoRow("☎", competition.venueContact)
                }
            }
        }

        if (isManager && (competition.status == "draft" || competition.status == "open")) {
            DMButton(s.compPublishAction, modifier = Modifier.fillMaxWidth()) { viewModel.publish(competition.id); onBack() }
        }

        Text(s.compCandidates, color = colors.foreground, fontWeight = FontWeight.Bold)
        if (candidates.isEmpty()) {
            Text(s.noCandidates, color = colors.mutedForeground)
        } else {
            candidates.forEach { c ->
                DMCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.artist?.displayName ?: s.artistSingular, color = colors.foreground, fontWeight = FontWeight.Bold)
                            Text("${c.score.toInt()} pts · ${c.status}", color = colors.mutedForeground, fontSize = 12.sp)
                        }
                        if (isManager && c.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                                DMButton(s.approveAction, onClick = { viewModel.reviewCandidate(competition.id, c.id, true) })
                                DMButton(s.rejectAction, style = DMButtonStyle.OUTLINE, onClick = { viewModel.reviewCandidate(competition.id, c.id, false) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pilule de badge du détail. */
@Composable
private fun DetailBadge(label: String, color: Color) {
    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Ligne d'information « label : valeur » (masquée si valeur nulle). */
@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val colors = DualMusicTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        Text(label, color = colors.mutedForeground, fontSize = 13.sp)
        Text(value, color = colors.foreground, fontSize = 13.sp)
    }
}

/** Formulaire de création complet (parité web `CompetitionForm`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompetitionFormCard(
    initial: Competition?,
    creating: Boolean,
    coverUrl: String,
    coverUploading: Boolean,
    onCoverChange: (String) -> Unit,
    onUploadCover: (com.dualmusic.core.upload.LocalMedia) -> Unit,
    onSubmit: (CreateCompetitionBody) -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val tz = "GMT"
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Sélecteur d'image système → lecture en LocalMedia → upload (catégorie image).
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { com.dualmusic.core.upload.readLocalMedia(context, uri) }
                    .onSuccess { onUploadCover(it) }
            }
        }
    }

    // Pré-remplissage en ÉDITION (keyé sur l'id → réinitialise si on change de compétition).
    val k = initial?.id
    fun dt(iso: String?) = iso?.take(16) ?: "" // ISO → "yyyy-MM-ddTHH:mm" pour DateTimePickerField
    // "yyyy-MM-ddTHH:mm" (heure GMT saisie) → ISO UTC explicite (suffixe Z) pour que le backend
    // NE ré-interprète PAS l'heure en local (sinon 14:00 devient 12:00). Vide → null.
    fun gmt(s: String): String? {
        val t = s.trim()
        return if (t.isBlank()) null else if (t.endsWith("Z")) t else "${t.take(16)}:00.000Z"
    }
    var title by remember(k) { mutableStateOf(initial?.title ?: "") }
    var description by remember(k) { mutableStateOf(initial?.description ?: "") }
    var mode by remember(k) { mutableStateOf(initial?.mode ?: "online") }
    var maxCandidates by remember(k) { mutableStateOf(initial?.maxCandidates?.toString() ?: "10") }
    var rewardDesc by remember(k) { mutableStateOf(initial?.rewardDescription ?: "") }
    var rewardAmount by remember(k) { mutableStateOf(initial?.rewardAmount?.toInt()?.toString() ?: "0") }
    var entryFeeRequired by remember(k) { mutableStateOf(initial?.entryFeeRequired ?: false) }
    var entryFeeAmount by remember(k) { mutableStateOf(initial?.entryFeeAmount?.toInt()?.toString() ?: "0") }
    var acceptsSponsors by remember(k) { mutableStateOf(initial?.acceptsSponsors ?: true) }
    var eligibilityScope by remember(k) { mutableStateOf("country") }
    val selectedCountries = remember(k) { mutableStateListOf<String>() }
    var countrySearch by remember(k) { mutableStateOf("") }
    var applicationOpensAt by remember(k) { mutableStateOf(dt(initial?.applicationOpensAt)) }
    var applicationDeadline by remember(k) { mutableStateOf(dt(initial?.applicationDeadline)) }
    var startAt by remember(k) { mutableStateOf(dt(initial?.startAt)) }
    var endAt by remember(k) { mutableStateOf(dt(initial?.endAt)) }
    var sponsorDeadline by remember(k) { mutableStateOf(dt(initial?.sponsorSubmissionDeadline)) }
    // Présentiel.
    var country by remember(k) { mutableStateOf(initial?.country ?: "") }
    var city by remember(k) { mutableStateOf(initial?.city ?: "") }
    var commune by remember(k) { mutableStateOf(initial?.commune ?: "") }
    var district by remember(k) { mutableStateOf(initial?.district ?: "") }
    var venueName by remember(k) { mutableStateOf(initial?.venueName ?: "") }
    var venueAddress by remember(k) { mutableStateOf(initial?.venueAddress ?: "") }
    var venueContact by remember(k) { mutableStateOf(initial?.venueContact ?: "") }

    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(if (initial != null) s.compEditTitle else s.compNew, color = colors.foreground, fontWeight = FontWeight.Bold)

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(s.titleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(s.descriptionLabel) }, modifier = Modifier.fillMaxWidth())
            // Image de couverture : upload depuis la galerie OU saisie d'une URL (parité web).
            Text(s.compCover, color = colors.foreground, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                DMButton(
                    if (coverUploading) s.sending else s.compPickImage,
                    style = DMButtonStyle.SECONDARY,
                    enabled = !coverUploading,
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                if (coverUrl.isNotBlank()) Text("✓", color = colors.accent, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(value = coverUrl, onValueChange = onCoverChange, label = { Text(s.compCover) }, singleLine = true, modifier = Modifier.fillMaxWidth())

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

            // Accepter les sponsors : si désactivé, la compétition n'apparaît pas dans les pages sponsor.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.compAcceptSponsors, color = colors.foreground)
                Switch(checked = acceptsSponsors, onCheckedChange = { acceptsSponsors = it })
            }
            // Date limite des candidatures sponsor — proposée dès que la case est cochée.
            if (acceptsSponsors) {
                DateTimePickerField(value = sponsorDeadline, onValueChange = { sponsorDeadline = it }, label = "${s.compSponsorDeadline} ($tz)", modifier = Modifier.fillMaxWidth())
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
                if (creating) s.sending else if (initial != null) s.compEditTitle else s.createCompetitionAction,
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
                            acceptsSponsors = acceptsSponsors,
                            sponsorSubmissionDeadline = if (acceptsSponsors) gmt(sponsorDeadline) else null,
                            eligibilityScope = eligibilityScope,
                            eligibleCountries = if (eligibilityScope == "country") selectedCountries.toList() else emptyList(),
                            country = if (mode == "onsite") country.ifBlank { null } else null,
                            city = if (mode == "onsite") city.trim().ifBlank { null } else null,
                            commune = if (mode == "onsite") commune.trim().ifBlank { null } else null,
                            district = if (mode == "onsite") district.trim().ifBlank { null } else null,
                            venueName = if (mode == "onsite") venueName.trim().ifBlank { null } else null,
                            venueAddress = if (mode == "onsite") venueAddress.trim().ifBlank { null } else null,
                            venueContact = if (mode == "onsite") venueContact.trim().ifBlank { null } else null,
                            applicationOpensAt = gmt(applicationOpensAt),
                            applicationDeadline = gmt(applicationDeadline),
                            startAt = gmt(startAt),
                            endAt = gmt(endAt),
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

package com.dualmusic.feature.creator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DateTimePickerField
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.i18n.Strings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.concert.ConcertEndpoints
import com.dualmusic.domain.creator.CreateArtistConcert
import com.dualmusic.domain.creator.ChangeDuelDateRequest
import com.dualmusic.domain.creator.CreateDuelRequest
import com.dualmusic.domain.creator.CreatorEndpoints
import com.dualmusic.domain.creator.DuelRequestItem
import com.dualmusic.domain.creator.RespondDuelRequest
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.user.UserEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** État de l'espace créateur. */
data class CreatorUiState(
    val myUserId: String? = null,
    val duelRequests: List<DuelRequestItem> = emptyList(),
    /** Annuaire des artistes (pour rechercher un adversaire à défier). */
    val artists: List<com.dualmusic.domain.artist.ArtistSummary> = emptyList(),
    val concerts: List<Concert> = emptyList(),
    /** URL publique de la pochette uploadée (prête à être persistée). */
    val coverUrl: String? = null,
    val uploadingCover: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel des outils créateur : défis de duel (répondre), mes concerts, création de concert.
 *
 * @param api client HTTP (lectures + POST).
 * @param uploader upload de la pochette (presign → PUT → confirm).
 */
class CreatorViewModel(
    private val api: ApiClient,
    private val uploader: MediaUploader,
) : ViewModel() {

    private val json = Json { explicitNulls = false }

    private val _uiState = MutableStateFlow(CreatorUiState())
    val uiState: StateFlow<CreatorUiState> = _uiState.asStateFlow()

    /** Charge l'id du caller + les défis + les concerts. */
    fun load() {
        viewModelScope.launch {
            val me = runCatching { api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer()) }.getOrNull()
            val requests = runCatching {
                api.request(Endpoint.get(CreatorEndpoints.DUEL_REQUESTS_MINE), ListSerializer(DuelRequestItem.serializer()))
            }.getOrDefault(emptyList())
            val concerts = runCatching {
                api.request(Endpoint.get(CreatorEndpoints.MY_CONCERTS), ListSerializer(Concert.serializer()))
            }.getOrDefault(emptyList())
            val artists = runCatching {
                api.request(
                    Endpoint.get(com.dualmusic.domain.artist.ArtistEndpoints.LIST),
                    ListSerializer(com.dualmusic.domain.artist.ArtistSummary.serializer()),
                )
            }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(myUserId = me?.user?.id, duelRequests = requests, concerts = concerts, artists = artists)
            }
        }
    }

    /** Envoie une invitation de duel à un artiste (date + message optionnels), puis recharge. */
    fun createDuel(opponentId: String, proposedDate: String? = null, message: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            val body = json.encodeToString(
                CreateDuelRequest.serializer(),
                CreateDuelRequest(
                    opponentId = opponentId,
                    // Normalise l'horloge saisie en ISO UTC (Z) comme le web, sinon le backend
                    // réinterprète l'heure en local et l'écart web/mobile réapparaît.
                    proposedDate = com.dualmusic.core.ui.datetime.toWireUtc(proposedDate),
                    message = message?.takeIf { it.isNotBlank() },
                ),
            )
            runCatching { api.request<Unit>(Endpoint.post(CreatorEndpoints.DUEL_REQUEST_CREATE, body)) }
                .onSuccess {
                    _uiState.update { it.copy(submitting = false, message = com.dualmusic.core.ui.i18n.appStrings.duelRequestSent) }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errCreateFailed) } }
        }
    }

    /** Répond à un défi reçu (accepter/refuser), avec retour clair + recharge. */
    fun respond(id: String, accept: Boolean) {
        viewModelScope.launch {
            val body = json.encodeToString(RespondDuelRequest.serializer(), RespondDuelRequest(accept))
            runCatching { api.request<Unit>(Endpoint.post(CreatorEndpoints.duelRespond(id), body)) }
                .onSuccess {
                    _uiState.update { it.copy(message = if (accept) "Duel accepté ✅" else "Duel refusé") }
                    load()
                }
                // Sans ceci, un échec (403/409/réseau) était avalé silencieusement → l'utilisateur
                // croyait devoir encore répondre. On affiche désormais la vraie cause.
                .onFailure { e -> _uiState.update { it.copy(message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errCreateFailed) } }
        }
    }

    /** Change la date proposée d'un défi ENVOYÉ encore en attente (émetteur). Renotifie l'adversaire. */
    fun changeDuelDate(id: String, newDate: String) {
        if (newDate.isBlank()) return
        viewModelScope.launch {
            val wire = com.dualmusic.core.ui.datetime.toWireUtc(newDate) ?: newDate
            val body = json.encodeToString(ChangeDuelDateRequest.serializer(), ChangeDuelDateRequest(wire))
            runCatching { api.request<Unit>(Endpoint.patch(CreatorEndpoints.duelChangeDate(id), body)) }
                .onSuccess {
                    _uiState.update { it.copy(message = "Date du duel modifiée 📅") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errCreateFailed) } }
        }
    }

    /** Upload une [readLocalMedia] déjà lue comme pochette (catégorie `image`). */
    fun uploadCover(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingCover = true, message = null) }
            runCatching { uploader.upload(media, com.dualmusic.domain.upload.UploadCategory.IMAGE) }
                .onSuccess { url -> _uiState.update { it.copy(coverUrl = url, uploadingCover = false) } }
                .onFailure { e -> _uiState.update { it.copy(uploadingCover = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) } }
        }
    }

    /** Signale une erreur (ex. lecture/plafond de taille) à l'UI. */
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

    /**
     * Crée un concert d'artiste. La pochette (si présente) doit déjà être uploadée
     * ([uploadCover]) — on envoie son URL. Recharge la liste en cas de succès.
     */
    fun createConcert(
        title: String,
        description: String,
        scheduledDate: String,
        ticketPrice: Double,
        maxTickets: Int?,
        allowsDedications: Boolean,
        allowsSponsorAds: Boolean,
        sponsorSubmissionDeadline: String,
        dedicationSubmissionDeadline: String,
        onDone: () -> Unit,
    ) {
        if (title.isBlank() || scheduledDate.isBlank()) {
            _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errTitleDateRequired) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            val body = json.encodeToString(
                CreateArtistConcert.serializer(),
                CreateArtistConcert(
                    title = title.trim(),
                    description = description.ifBlank { null },
                    scheduledDate = normalizeIsoDate(scheduledDate.trim()),
                    ticketPrice = ticketPrice,
                    maxTickets = maxTickets,
                    coverImageUrl = _uiState.value.coverUrl,
                    allowsDedications = allowsDedications,
                    allowsSponsorAds = allowsSponsorAds,
                    sponsorSubmissionDeadline = if (allowsSponsorAds && sponsorSubmissionDeadline.isNotBlank())
                        normalizeIsoDate(sponsorSubmissionDeadline.trim()) else null,
                    dedicationSubmissionDeadline = if (allowsDedications && dedicationSubmissionDeadline.isNotBlank())
                        normalizeIsoDate(dedicationSubmissionDeadline.trim()) else null,
                ),
            )
            runCatching { api.request<Unit>(Endpoint.post(ConcertEndpoints.ARTIST_LIST, body)) }
                .onSuccess {
                    _uiState.update { it.copy(submitting = false, coverUrl = null, message = com.dualmusic.core.ui.i18n.appStrings.concertCreated) }
                    load()
                    onDone()
                }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errCreateFailed) } }
        }
    }

    /** Passe le concert en direct (PATCH `status:live`) puis recharge. */
    fun goLiveConcert(id: String) {
        viewModelScope.launch {
            runCatching { api.request<Unit>(Endpoint.patch(ConcertEndpoints.artistDetail(id), """{"status":"live"}""")) }.onSuccess { load() }
        }
    }

    /** Termine le concert (PATCH `status:ended`) puis recharge. */
    fun endConcert(id: String) {
        viewModelScope.launch {
            runCatching { api.request<Unit>(Endpoint.patch(ConcertEndpoints.artistDetail(id), """{"status":"ended"}""")) }.onSuccess { load() }
        }
    }

    /** Supprime le concert (DELETE) puis recharge. */
    fun deleteConcert(id: String) {
        viewModelScope.launch {
            runCatching { api.request<Unit>(Endpoint.delete(ConcertEndpoints.artistDetail(id))) }.onSuccess { load() }
        }
    }

    /** Horloge locale (fuseau préféré) → ISO UTC avec `Z`, cohérent web/mobile. */
    private fun normalizeIsoDate(input: String): String =
        com.dualmusic.core.ui.datetime.toWireUtc(input) ?: input
}

/**
 * Espace créateur : Défis de duel, Mes concerts, Créer un concert.
 *
 * @param viewModel source d'état.
 */
@Composable
fun CreatorScreen(viewModel: CreatorViewModel, initialTab: Int = 0) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        ui.message?.let { Text(it, color = colors.primary) }
        // Deux pages DISTINCTES sans onglets (parité web) : Duels (initialTab 0) OU Concerts.
        if (initialTab == 0) {
                var duelQuery by remember { mutableStateOf("") }
                var selectedArtist by remember { mutableStateOf<com.dualmusic.domain.artist.ArtistSummary?>(null) }
                var proposedDate by remember { mutableStateOf("") }
                var duelMessage by remember { mutableStateOf("") }
                // « Demander un Duel » : rechercher un artiste, le sélectionner, puis envoyer l'invitation.
                DMCard(modifier = Modifier.fillMaxWidth()) {
                    Text(strings.requestDuel, color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(strings.requestDuelHint, color = colors.mutedForeground)
                    OutlinedTextField(
                        value = duelQuery,
                        onValueChange = { duelQuery = it },
                        label = { Text(strings.searchArtist) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                    )
                    // On s'exclut soi-même via l'id UTILISATEUR (opponentUserId), pas l'id de profil.
                    val candidates = ui.artists.filter {
                        it.opponentUserId != ui.myUserId &&
                            (duelQuery.isBlank() || it.displayName.contains(duelQuery, ignoreCase = true))
                    }.take(10)
                    Text(
                        "${candidates.size} ${strings.artistsAvailable}",
                        color = colors.mutedForeground,
                        modifier = Modifier.padding(top = DualMusicTheme.spacing.sm),
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            strings.noArtistAvailable,
                            color = colors.mutedForeground,
                            modifier = Modifier.padding(top = DualMusicTheme.spacing.sm),
                        )
                    } else {
                        candidates.forEach { a ->
                            val isSel = selectedArtist?.opponentUserId == a.opponentUserId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = DualMusicTheme.spacing.sm)
                                    .background(
                                        if (isSel) colors.primary.copy(alpha = 0.15f) else colors.muted.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { selectedArtist = a }
                                    .padding(DualMusicTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🎤  ${a.displayName}", color = if (isSel) colors.primary else colors.foreground, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    // Formulaire d'invitation (apparaît quand un artiste est sélectionné) — parité web.
                    selectedArtist?.let { artist ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                        ) {
                            Text("${artist.displayName} · ${strings.selectedArtist}", color = colors.foreground, fontWeight = FontWeight.Bold)
                            DateTimePickerField(
                                value = proposedDate,
                                onValueChange = { proposedDate = it },
                                label = strings.proposedDateOptional,
                            )
                            OutlinedTextField(
                                value = duelMessage,
                                onValueChange = { duelMessage = it },
                                label = { Text(strings.messageOptional) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DMButton(
                                if (ui.submitting) strings.sending else strings.sendDuelRequest,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.createDuel(artist.opponentUserId, proposedDate, duelMessage)
                                    selectedArtist = null
                                    proposedDate = ""
                                    duelMessage = ""
                                },
                            )
                        }
                    }
                }
                // Séparation « Mes demandes envoyées » (émises) et « Demandes reçues » (parité web).
                val sent = ui.duelRequests.filter { it.requesterId == ui.myUserId }
                val received = ui.duelRequests.filter { it.opponentId == ui.myUserId }
                val nameFor: (String?) -> String = { uid -> ui.artists.firstOrNull { it.opponentUserId == uid }?.displayName ?: strings.artistSingular }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    item { Text(strings.mySentRequests, color = colors.foreground, fontWeight = FontWeight.Bold) }
                    if (sent.isEmpty()) {
                        item { Text(strings.noSentRequests, color = colors.mutedForeground) }
                    } else {
                        items(sent) { req ->
                            SentDuelRow(
                                opponentName = nameFor(req.opponentId),
                                request = req,
                                onChangeDate = { newDate -> viewModel.changeDuelDate(req.id, newDate) },
                            )
                        }
                    }
                    item {
                        Text(strings.receivedInvitations, color = colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = DualMusicTheme.spacing.md))
                    }
                    if (received.isEmpty()) {
                        item { Text(strings.noChallengesHint, color = colors.mutedForeground) }
                    } else {
                        items(received) { req ->
                            DuelRequestRow(
                                request = req,
                                canRespond = req.status == "pending",
                                onAccept = { viewModel.respond(req.id, true) },
                                onDecline = { viewModel.respond(req.id, false) },
                            )
                        }
                    }
                }
        } else {
            // ===== Page CONCERTS (Mes Concerts & Shows Live) — sans onglets, comme le web =====
            var showConcertForm by remember { mutableStateOf(false) }
            if (showConcertForm) {
                DMButton(strings.compCancel, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth()) { showConcertForm = false }
                CreateConcertForm(ui, viewModel, modifier = Modifier.weight(1f)) { showConcertForm = false }
            } else {
                DMButton("+ Planifier un concert", modifier = Modifier.fillMaxWidth()) { showConcertForm = true }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    // Cartes de statistiques de MÊME TAILLE (IntrinsicSize.Min → hauteur commune).
                    item {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                            ConcertStat("${ui.concerts.size}", "Concerts planifiés", Modifier.weight(1f).fillMaxHeight())
                            ConcertStat("${ui.concerts.sumOf { it.ticketsSold }}", "Tickets vendus", Modifier.weight(1f).fillMaxHeight())
                            ConcertStat("$${ui.concerts.sumOf { it.revenue }.toInt()}", "Revenus", Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    if (ui.concerts.isEmpty()) {
                        item {
                            DMEmptyState(
                                title = strings.noConcerts,
                                subtitle = strings.noConcertsHint,
                                icon = Icons.Filled.DateRange,
                                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg),
                            )
                        }
                    } else {
                        items(ui.concerts) { concert ->
                            ArtistConcertCard(
                                concert = concert,
                                onGoLive = { viewModel.goLiveConcert(concert.id) },
                                onEnd = { viewModel.endConcert(concert.id) },
                                onDelete = { viewModel.deleteConcert(concert.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Formulaire de création d'un concert (pochette optionnelle + champs). */
@Composable
private fun CreateConcertForm(
    ui: CreatorUiState,
    viewModel: CreatorViewModel,
    modifier: Modifier = Modifier,
    onCreated: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("0") }
    var maxTickets by remember { mutableStateOf("") }
    var dedications by remember { mutableStateOf(true) }
    var sponsorAds by remember { mutableStateOf(true) }
    var dedicationDeadline by remember { mutableStateOf("") }
    var sponsorDeadline by remember { mutableStateOf("") }

    // Pochette : image seule, ≤ 5 Mo (catégorie `image` côté serveur).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 5L * 1024 * 1024) }
                    .onSuccess { viewModel.uploadCover(it) }
                    .onFailure { viewModel.setMessage(it.message ?: strings.fileUnreadable) }
            }
        }
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(strings.titleRequired) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(strings.description) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            DateTimePickerField(
                value = date,
                onValueChange = { date = it },
                label = strings.dateFormatLabel,
            )
        }
        item {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(strings.ticketPriceLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = maxTickets,
                onValueChange = { maxTickets = it },
                label = { Text(strings.maxTicketsLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ToggleRow(strings.allowDedications, dedications) { dedications = it } }
        if (dedications) {
            item {
                DateTimePickerField(
                    value = dedicationDeadline,
                    onValueChange = { dedicationDeadline = it },
                    label = strings.dedicationDeadlineLabel,
                )
            }
        }
        item { ToggleRow(strings.allowSponsorAds, sponsorAds) { sponsorAds = it } }
        if (sponsorAds) {
            item {
                DateTimePickerField(
                    value = sponsorDeadline,
                    onValueChange = { sponsorDeadline = it },
                    label = strings.sponsorDeadlineLabel,
                )
            }
        }
        item {
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    Text(
                        when {
                            ui.uploadingCover -> strings.uploadingCover
                            ui.coverUrl != null -> strings.coverReady
                            else -> strings.noCover
                        },
                        color = colors.mutedForeground,
                    )
                    DMButton(
                        if (ui.coverUrl != null) strings.changeCover else strings.chooseCover,
                        style = DMButtonStyle.SECONDARY,
                        onClick = {
                            picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }
            }
        }
        item {
            DMButton(
                if (ui.submitting) strings.creating else strings.createConcert,
                onClick = {
                    viewModel.createConcert(
                        title = title,
                        description = description,
                        scheduledDate = date,
                        ticketPrice = price.toDoubleOrNull() ?: 0.0,
                        maxTickets = maxTickets.toIntOrNull(),
                        allowsDedications = dedications,
                        allowsSponsorAds = sponsorAds,
                        sponsorSubmissionDeadline = sponsorDeadline,
                        dedicationSubmissionDeadline = dedicationDeadline,
                        onDone = onCreated,
                    )
                },
            )
        }
    }
}

/** Ligne interrupteur libellé + Switch. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.foreground)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ligne d'un défi de duel : accepter/refuser si reçu, sinon statut. */
@Composable
private fun DuelRequestRow(
    request: DuelRequestItem,
    canRespond: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(request.message ?: strings.duelChallenge, color = colors.foreground, fontWeight = FontWeight.Bold)
            request.proposedDate?.let { Text("${strings.proposed} : ${com.dualmusic.core.ui.datetime.formatTz(it)}", color = colors.mutedForeground) }
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(strings.accept, modifier = Modifier.weight(1f), onClick = onAccept)
                    DMButton(strings.decline, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onDecline)
                }
            } else {
                Text(statusLabel(request.status, strings), color = colors.mutedForeground)
            }
        }
    }
}

/** Carte de statistique concert (valeur + libellé). */
@Composable
private fun ConcertStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = colors.mutedForeground, fontSize = 10.sp)
        }
    }
}

/** Carte riche d'un concert d'artiste (parité web) : pochette, badges, date, mini-stats, actions. */
@Composable
private fun ArtistConcertCard(concert: Concert, onGoLive: () -> Unit, onEnd: () -> Unit, onDelete: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth(), padded = false) {
        com.dualmusic.core.ui.components.DMRemoteImage(
            url = concert.cover,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            fallbackEmoji = "🎵",
        )
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                if (concert.approvalStatus == "pending") ConcertBadge("⏳ En attente de validation", colors.accent)
                ConcertBadge(concertStatusText(concert.status), colors.primary)
            }
            Text(concert.title, color = colors.foreground, fontWeight = FontWeight.Bold)
            concert.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mutedForeground, fontSize = 12.sp) }
            concert.scheduledDate?.let { Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm")}", color = colors.mutedForeground, fontSize = 12.sp) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ConcertMini("Prix", "$${concert.ticketPrice.toInt()}")
                ConcertMini("Vendus", "${concert.ticketsSold}")
                ConcertMini("Revenus", "$${concert.revenue.toInt()}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                when (concert.status) {
                    com.dualmusic.domain.model.EventStatus.LIVE ->
                        DMButton("Terminer", style = DMButtonStyle.DESTRUCTIVE, modifier = Modifier.weight(1f), onClick = onEnd)
                    com.dualmusic.domain.model.EventStatus.UPCOMING ->
                        DMButton("🔴 Lancer le direct", modifier = Modifier.weight(1f), onClick = onGoLive)
                    else -> Unit
                }
                DMButton("Supprimer", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ConcertBadge(label: String, color: Color) {
    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConcertMini(label: String, value: String) {
    val colors = DualMusicTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, color = colors.mutedForeground, fontSize = 10.sp)
    }
}

private fun concertStatusText(status: com.dualmusic.domain.model.EventStatus): String = when (status) {
    com.dualmusic.domain.model.EventStatus.LIVE -> "🔴 EN DIRECT"
    com.dualmusic.domain.model.EventStatus.UPCOMING -> "À venir"
    com.dualmusic.domain.model.EventStatus.ENDED -> "Terminé"
    else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Ligne d'une demande de duel ENVOYÉE : adversaire + date prévue + statut (parité web).
 * Tant que la demande est `pending`, l'émetteur peut reproposer une autre date
 * (le backend renotifie l'adversaire par notif + email).
 */
@Composable
private fun SentDuelRow(opponentName: String, request: DuelRequestItem, onChangeDate: (String) -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    var editing by remember { mutableStateOf(false) }
    var newDate by remember { mutableStateOf("") }
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🎤 $opponentName", color = colors.foreground, fontWeight = FontWeight.Bold)
                    request.proposedDate?.let { Text("${strings.duelPlanned} ${com.dualmusic.core.ui.datetime.formatTz(it)}", color = colors.mutedForeground, fontSize = 12.sp) }
                }
                Text(statusLabel(request.status, strings), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (request.status == "pending") {
                if (!editing) {
                    DMButton(strings.changeDate, style = DMButtonStyle.OUTLINE) { editing = true }
                } else {
                    DateTimePickerField(value = newDate, onValueChange = { newDate = it }, label = strings.proposedDateOptional)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        DMButton(strings.save, enabled = newDate.isNotBlank()) { onChangeDate(newDate); editing = false }
                        DMButton(strings.cancel, style = DMButtonStyle.OUTLINE) { editing = false }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String, strings: Strings): String = when (status) {
    "pending" -> strings.statusPending
    "accepted", "admin_pending" -> strings.statusAccepted
    "approved" -> strings.statusApproved
    "rejected", "declined" -> strings.statusRejected
    else -> status
}

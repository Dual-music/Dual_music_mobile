package com.dualmusic.feature.creator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    proposedDate = proposedDate?.takeIf { it.isNotBlank() },
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

    /** Répond à un défi reçu (accepter/refuser), puis recharge. */
    fun respond(id: String, accept: Boolean) {
        viewModelScope.launch {
            val body = json.encodeToString(RespondDuelRequest.serializer(), RespondDuelRequest(accept))
            runCatching { api.request<Unit>(Endpoint.post(CreatorEndpoints.duelRespond(id), body)) }
                .onSuccess { load() }
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

    /** Complète une saisie `YYYY-MM-DDTHH:MM` en ISO `…:00` si nécessaire. */
    private fun normalizeIsoDate(input: String): String =
        if (Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$""").matches(input)) "$input:00" else input
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
    var tab by remember { mutableIntStateOf(initialTab) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        ui.message?.let { Text(it, color = colors.primary) }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(strings.tabChallenges) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(strings.myConcerts) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(strings.create) })
        }

        when (tab) {
            0 -> {
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
                // Invitations reçues (accepter/refuser).
                Text(strings.receivedInvitations, color = colors.foreground, fontWeight = FontWeight.Bold)
                if (ui.duelRequests.isEmpty()) {
                    DMEmptyState(
                        title = strings.noChallenges,
                        subtitle = strings.noChallengesHint,
                        icon = Icons.Filled.Notifications,
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    items(ui.duelRequests) { req ->
                        DuelRequestRow(
                            request = req,
                            canRespond = req.opponentId == ui.myUserId && req.status == "pending",
                            onAccept = { viewModel.respond(req.id, true) },
                            onDecline = { viewModel.respond(req.id, false) },
                        )
                    }
                }
            }
            1 -> {
                if (ui.concerts.isEmpty()) {
                    DMEmptyState(
                        title = strings.noConcerts,
                        subtitle = strings.noConcertsHint,
                        icon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    items(ui.concerts) { concert -> ConcertRow(concert) }
                }
            }
            else -> CreateConcertForm(ui, viewModel) { tab = 1 }
        }
    }
}

/** Formulaire de création d'un concert (pochette optionnelle + champs). */
@Composable
private fun CreateConcertForm(
    ui: CreatorUiState,
    viewModel: CreatorViewModel,
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
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
        item { ToggleRow(strings.allowSponsorAds, sponsorAds) { sponsorAds = it } }
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
            request.proposedDate?.let { Text("${strings.proposed} : ${it.take(16)}", color = colors.mutedForeground) }
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

/** Ligne d'un concert de l'artiste. */
@Composable
private fun ConcertRow(concert: Concert) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(concert.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                concert.scheduledDate?.let { Text(it.take(16), color = colors.mutedForeground) }
            }
            Text(concert.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = colors.mutedForeground)
        }
    }
}

private fun statusLabel(status: String, strings: Strings): String = when (status) {
    "pending" -> strings.statusPending
    "accepted" -> strings.statusAccepted
    "declined" -> strings.statusDeclined
    else -> status
}

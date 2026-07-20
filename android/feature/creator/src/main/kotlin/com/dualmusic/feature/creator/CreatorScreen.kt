package com.dualmusic.feature.creator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.concert.ConcertEndpoints
import com.dualmusic.domain.creator.CreateArtistConcert
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
            _uiState.update { it.copy(myUserId = me?.user?.id, duelRequests = requests, concerts = concerts) }
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
                .onFailure { e -> _uiState.update { it.copy(uploadingCover = false, message = e.message ?: "Échec de l'upload.") } }
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
            _uiState.update { it.copy(message = "Titre et date sont requis.") }
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
                    _uiState.update { it.copy(submitting = false, coverUrl = null, message = "✅ Concert créé — en attente de validation.") }
                    load()
                    onDone()
                }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, message = e.message ?: "Création impossible.") } }
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
fun CreatorScreen(viewModel: CreatorViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Espace créateur", color = colors.foreground, fontWeight = FontWeight.Bold)
        ui.message?.let { Text(it, color = colors.primary) }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Défis") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mes concerts") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Créer") })
        }

        when (tab) {
            0 -> {
                if (ui.duelRequests.isEmpty()) {
                    DMEmptyState(
                        title = "Aucun défi",
                        subtitle = "Les défis de duel reçus apparaîtront ici.",
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
                        title = "Aucun concert",
                        subtitle = "Crée ton premier concert depuis l'onglet « Créer ».",
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
                    .onFailure { viewModel.setMessage(it.message ?: "Fichier illisible.") }
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre *") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date * (AAAA-MM-JJTHH:MM)") },
                placeholder = { Text("2026-08-01T20:00") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Prix du billet (crédits)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = maxTickets,
                onValueChange = { maxTickets = it },
                label = { Text("Places max (optionnel)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ToggleRow("Autoriser les dédicaces", dedications) { dedications = it } }
        item { ToggleRow("Autoriser les pubs sponsors", sponsorAds) { sponsorAds = it } }
        item {
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    Text(
                        when {
                            ui.uploadingCover -> "Upload de la pochette…"
                            ui.coverUrl != null -> "✅ Pochette prête."
                            else -> "Aucune pochette."
                        },
                        color = colors.mutedForeground,
                    )
                    DMButton(
                        if (ui.coverUrl != null) "Changer la pochette" else "Choisir une pochette",
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
                if (ui.submitting) "Création…" else "Créer le concert",
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
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(request.message ?: "Défi de duel", color = colors.foreground, fontWeight = FontWeight.Bold)
            request.proposedDate?.let { Text("Proposé : ${it.take(16)}", color = colors.mutedForeground) }
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton("Accepter", modifier = Modifier.weight(1f), onClick = onAccept)
                    DMButton("Refuser", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onDecline)
                }
            } else {
                Text(statusLabel(request.status), color = colors.mutedForeground)
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

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "En attente"
    "accepted" -> "Accepté"
    "declined" -> "Refusé"
    else -> status
}

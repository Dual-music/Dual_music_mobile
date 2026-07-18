package com.dualmusic.feature.sponsor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.LocalMedia
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.core.upload.videoDurationSeconds
import com.dualmusic.domain.competition.CompetitionEndpoints
import com.dualmusic.domain.concert.ConcertEndpoints
import com.dualmusic.domain.duel.DuelEndpoints
import com.dualmusic.domain.model.Competition
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.EventStatus
import com.dualmusic.domain.sponsor.CreateSponsorRequest
import com.dualmusic.domain.sponsor.SponsorEndpoints
import com.dualmusic.domain.sponsor.SponsorRequest
import com.dualmusic.domain.sponsor.SponsorTier
import com.dualmusic.domain.upload.UploadCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Événement sponsorisable (fusion des catalogues duels/concerts/compétitions). */
data class SponsorableEvent(val type: String, val id: String, val label: String)

/** État de l'écran sponsoring. */
data class SponsorUiState(
    val tiers: List<SponsorTier> = emptyList(),
    val requests: List<SponsorRequest> = emptyList(),
    val events: List<SponsorableEvent> = emptyList(),
    // Brouillon de création.
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaDurationSeconds: Int = 0,
    val uploadingMedia: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel du sponsoring : paliers, mes demandes, paiement, et création (média + événement).
 *
 * @param api client HTTP.
 * @param uploader upload du média de pub (presign → PUT → confirm, catégorie `sponsor`).
 */
class SponsorViewModel(
    private val api: ApiClient,
    private val uploader: MediaUploader,
) : ViewModel() {

    private val json = Json { explicitNulls = false }

    private val _uiState = MutableStateFlow(SponsorUiState())
    val uiState: StateFlow<SponsorUiState> = _uiState.asStateFlow()

    /** Charge paliers + demandes + événements sponsorisables. */
    fun load() {
        viewModelScope.launch {
            val tiers = runCatching {
                api.request(Endpoint.get(SponsorEndpoints.TIERS), ListSerializer(SponsorTier.serializer()))
            }.getOrDefault(emptyList())
            val requests = runCatching {
                api.request(Endpoint.get(SponsorEndpoints.MY_REQUESTS), ListSerializer(SponsorRequest.serializer()))
            }.getOrDefault(emptyList())
            val events = loadEvents()
            _uiState.update { it.copy(tiers = tiers, requests = requests, events = events) }
        }
    }

    /** Fusionne les catalogues (concerts d'artistes, compétitions, duels) en événements à venir. */
    private suspend fun loadEvents(): List<SponsorableEvent> {
        val q = mapOf("limit" to "50")
        val concerts = runCatching {
            api.request(Endpoint.get(ConcertEndpoints.ARTIST_LIST, q), ListSerializer(Concert.serializer()))
        }.getOrDefault(emptyList())
            .filter { it.status == EventStatus.UPCOMING || it.status == EventStatus.LIVE }
            .map { SponsorableEvent("artist_concert", it.id, it.title) }

        val competitions = runCatching {
            api.request(Endpoint.get(CompetitionEndpoints.LIST, q), ListSerializer(Competition.serializer()))
        }.getOrDefault(emptyList())
            .filter { it.status != "ended" && it.status != "cancelled" }
            .map { SponsorableEvent("competition", it.id, "🏆 ${it.title}") }

        val duels = runCatching {
            api.request(Endpoint.get(DuelEndpoints.LIST, q), ListSerializer(Duel.serializer()))
        }.getOrDefault(emptyList())
            .filter { it.status == EventStatus.UPCOMING || it.status == EventStatus.LIVE }
            .map {
                val a = it.artist1?.displayName ?: "?"
                val b = it.artist2?.displayName ?: "?"
                SponsorableEvent("duel", it.id, "⚔️ $a vs $b")
            }

        return concerts + competitions + duels
    }

    /** Paie une demande approuvée (débit idempotent via `Idempotency-Key`), puis recharge. */
    fun pay(id: String) {
        viewModelScope.launch {
            val key = java.util.UUID.randomUUID().toString()
            val ok = runCatching {
                api.request<Unit>(Endpoint.post(SponsorEndpoints.pay(id), idempotencyKey = key))
            }.isSuccess
            _uiState.update { it.copy(message = if (ok) "✅ Sponsoring payé." else "Paiement impossible.") }
            if (ok) load()
        }
    }

    /**
     * Upload le média de pub et mémorise l'URL/type/durée dans le brouillon.
     *
     * @param media fichier lu.
     * @param mediaType `image` ou `video`.
     * @param durationSeconds durée (vidéo : mesurée ; image : slot du plus petit palier).
     */
    fun uploadMedia(media: LocalMedia, mediaType: String, durationSeconds: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingMedia = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.SPONSOR) }
                .onSuccess { url ->
                    _uiState.update {
                        it.copy(
                            uploadingMedia = false,
                            mediaUrl = url,
                            mediaType = mediaType,
                            mediaDurationSeconds = durationSeconds.coerceIn(1, 600),
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(uploadingMedia = false, message = e.message ?: "Échec de l'upload.") } }
        }
    }

    /** Signale un message/erreur à l'UI. */
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

    /** Slot de durée par défaut pour une image = plus petit palier (min_seconds), sinon 5 s. */
    fun imageDurationSlot(): Int =
        _uiState.value.tiers.minOfOrNull { it.minSeconds }?.coerceAtLeast(1) ?: 5

    /**
     * Crée la demande de sponsoring pour [event] avec le média du brouillon.
     * Le prix est calculé serveur d'après la durée.
     */
    fun createRequest(event: SponsorableEvent, description: String, onDone: () -> Unit) {
        val s = _uiState.value
        if (s.mediaUrl == null || s.mediaType == null) {
            _uiState.update { it.copy(message = "Ajoutez d'abord un média.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            val body = json.encodeToString(
                CreateSponsorRequest.serializer(),
                CreateSponsorRequest(
                    eventType = event.type,
                    eventId = event.id,
                    mediaType = s.mediaType,
                    mediaUrl = s.mediaUrl,
                    mediaDurationSeconds = s.mediaDurationSeconds.coerceIn(1, 600),
                    description = description.ifBlank { null },
                ),
            )
            runCatching { api.request<Unit>(Endpoint.post(SponsorEndpoints.CREATE, body)) }
                .onSuccess {
                    _uiState.update {
                        it.copy(submitting = false, mediaUrl = null, mediaType = null, mediaDurationSeconds = 0, message = "✅ Demande envoyée — en attente de validation.")
                    }
                    load()
                    onDone()
                }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, message = e.message ?: "Envoi impossible.") } }
        }
    }
}

/**
 * Écran de sponsoring : Mes demandes (tarifs + demandes + paiement) et Nouvelle demande.
 *
 * @param viewModel source d'état.
 */
@Composable
fun SponsorScreen(viewModel: SponsorViewModel) {
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
        Text("Sponsoring", color = colors.foreground, fontWeight = FontWeight.Bold)
        ui.message?.let { Text(it, color = colors.primary) }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Mes demandes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Nouvelle") })
        }

        if (tab == 0) MyRequestsTab(ui, viewModel) else NewRequestTab(ui, viewModel) { tab = 0 }
    }
}

/** Onglet « Mes demandes » : grille tarifaire + demandes (paiement si approuvée). */
@Composable
private fun MyRequestsTab(ui: SponsorUiState, viewModel: SponsorViewModel) {
    val colors = DualMusicTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        item { Text("Tarifs (par durée)", color = colors.mutedForeground) }
        items(ui.tiers) { tier ->
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${tier.label ?: "Palier"} (${tier.minSeconds}-${tier.maxSeconds}s)", color = colors.foreground)
                    Text("${tier.priceCredits.toInt()} cr.", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("Mes demandes", color = colors.mutedForeground) }
        if (ui.requests.isEmpty()) item { Text("Aucune demande de sponsoring.", color = colors.mutedForeground) }
        items(ui.requests) { req -> RequestRow(req) { viewModel.pay(req.id) } }
    }
}

/** Onglet « Nouvelle » : choix de l'événement + média + description + envoi. */
@Composable
private fun NewRequestTab(ui: SponsorUiState, viewModel: SponsorViewModel, onSent: () -> Unit) {
    val colors = DualMusicTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<SponsorableEvent?>(null) }
    var description by remember { mutableStateOf("") }

    // Média : image OU vidéo (catégorie `sponsor`, ≤ 500 Mo côté serveur ; garde locale 100 Mo).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val media = runCatching { readLocalMedia(context, uri) }.getOrElse {
                    viewModel.setMessage(it.message ?: "Fichier illisible."); return@launch
                }
                val kind = media.mediaKind
                if (kind == null) { viewModel.setMessage("Type de média non supporté."); return@launch }
                val duration = if (kind == "video") {
                    videoDurationSeconds(context, uri) ?: 30
                } else {
                    viewModel.imageDurationSlot()
                }
                viewModel.uploadMedia(media, kind, duration)
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        item { Text("1. Choisir l'événement", color = colors.mutedForeground) }
        if (ui.events.isEmpty()) item { Text("Aucun événement disponible.", color = colors.mutedForeground) }
        items(ui.events) { ev ->
            EventRow(ev, selected?.id == ev.id) { selected = ev }
        }

        item { Text("2. Média de la pub", color = colors.mutedForeground) }
        item {
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    Text(
                        when {
                            ui.uploadingMedia -> "Upload en cours…"
                            ui.mediaUrl != null -> "✅ Média prêt (${ui.mediaType}, ${ui.mediaDurationSeconds}s)."
                            else -> "Aucun média."
                        },
                        color = colors.mutedForeground,
                    )
                    DMButton(
                        if (ui.mediaUrl != null) "Changer le média" else "Choisir un média",
                        style = DMButtonStyle.SECONDARY,
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optionnel)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            DMButton(
                if (ui.submitting) "Envoi…" else "Envoyer la demande",
                onClick = {
                    val ev = selected
                    if (ev == null) viewModel.setMessage("Sélectionnez un événement.")
                    else viewModel.createRequest(ev, description, onSent)
                },
            )
        }
    }
}

/** Ligne sélectionnable d'un événement. */
@Composable
private fun EventRow(event: SponsorableEvent, selected: Boolean, onSelect: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onSelect),
    ) {
        Text(
            (if (selected) "● " else "○ ") + event.label,
            color = if (selected) colors.foreground else colors.mutedForeground,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Ligne d'une demande de sponsoring (paiement si approuvée). */
@Composable
private fun RequestRow(request: SponsorRequest, onPay: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(request.eventType ?: "Événement", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(statusLabel(request.status), color = colors.mutedForeground)
            }
            if (request.payable) {
                DMButton("Payer ${request.priceCredits.toInt()}", onClick = onPay)
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "En attente de validation"
    "approved" -> "Approuvé — à payer"
    "rejected" -> "Rejeté"
    else -> status
}

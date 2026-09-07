package com.dualmusic.feature.content

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.content.ContentEndpoints
import com.dualmusic.domain.content.CreateLifestyleRequest
import com.dualmusic.domain.content.LifestyleVideo
import com.dualmusic.domain.replay.ReplayEndpoints
import com.dualmusic.domain.replay.ReplayVideo
import com.dualmusic.domain.upload.UploadCategory
import com.dualmusic.domain.user.UserEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** État de l'écran « Contenu » (créateur). */
data class MyContentUiState(
    val artistName: String = "Artiste",
    val videos: List<LifestyleVideo> = emptyList(),
    val replays: List<ReplayVideo> = emptyList(),
    val pendingVideoUrl: String? = null,
    val pendingThumbnailUrl: String? = null,
    val pendingDuration: String = "0:00",
    val uploading: Boolean = false,
    val uploadingThumb: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel « Contenu » : publie une vidéo lifestyle (upload → `POST /lifestyle`) et liste
 * les vidéos + replays de l'artiste (`GET /lifestyle?artistId=me`, `GET /replays?artistId=me`).
 * Mêmes endpoints que le web.
 *
 * @param api client HTTP.
 * @param uploader upload média (presign → PUT → confirm).
 */
class MyContentViewModel(
    private val api: ApiClient,
    private val uploader: MediaUploader,
) : ViewModel() {

    private val json = Json { explicitNulls = false }
    private var myUserId: String? = null

    private val _uiState = MutableStateFlow(MyContentUiState())
    val uiState: StateFlow<MyContentUiState> = _uiState.asStateFlow()

    /** Charge l'artiste + ses vidéos + ses replays. */
    fun load() {
        viewModelScope.launch {
            val me = runCatching { api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer()) }.getOrNull()
            myUserId = me?.user?.id
            val name = me?.profile?.displayName ?: "Artiste"
            val videos = myUserId?.let { uid ->
                runCatching {
                    api.request(
                        Endpoint.get(ContentEndpoints.LIFESTYLE, query = mapOf("artistId" to uid)),
                        ListSerializer(LifestyleVideo.serializer()),
                    )
                }.getOrDefault(emptyList())
            } ?: emptyList()
            // `mine=true` (et non `artistId=uid`) : couvre à la fois les replays où l'appelant
            // est l'ARTISTE (`artist_id`) et ceux dont il est le CRÉATEUR (`created_by`, ex. un
            // manager de duel/compétition) — `artistId` seul ne renvoyait jamais les replays de
            // duels/compétitions gérés par un manager, qui n'est pas l'artiste. Parité web
            // (`MyReplays.tsx` utilise déjà `mine: "true"`).
            val replays = runCatching {
                api.request(
                    Endpoint.get(ReplayEndpoints.LIST, query = mapOf("mine" to "true")),
                    ListSerializer(ReplayVideo.serializer()),
                )
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(artistName = name, videos = videos, replays = replays) }
        }
    }

    /** Upload la vidéo choisie (catégorie lifestyle) et mémorise son URL + durée. */
    fun uploadVideo(media: com.dualmusic.core.upload.LocalMedia, duration: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.LIFESTYLE) }
                .onSuccess { url ->
                    _uiState.update { it.copy(uploading = false, pendingVideoUrl = url, pendingDuration = duration) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(uploading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) }
                }
        }
    }

    /** Upload la miniature choisie (catégorie image) et mémorise son URL. */
    fun uploadThumbnail(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingThumb = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.IMAGE) }
                .onSuccess { url -> _uiState.update { it.copy(uploadingThumb = false, pendingThumbnailUrl = url) } }
                .onFailure { e -> _uiState.update { it.copy(uploadingThumb = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) } }
        }
    }

    /** Signale une erreur (lecture/plafond de taille) à l'UI. */
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

    /** Réglages (prix, publication) d'un replay — hôte/propriétaire. */
    fun updateReplaySettings(id: String, replayPrice: Double, isPublic: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val body = """{"replay_price":$replayPrice,"is_public":$isPublic,"is_premium":${replayPrice > 0}}"""
            val ok = runCatching { api.request<Unit>(Endpoint.patch(ReplayEndpoints.detail(id), body)) }.isSuccess
            if (ok) load()
            onDone(ok)
        }
    }

    /** Remplace le fichier vidéo d'un replay (téléversement direct, catégorie `replay`). */
    fun replaceReplayVideo(id: String, media: com.dualmusic.core.upload.LocalMedia, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val url = uploader.upload(media, UploadCategory.REPLAY)
                api.request<Unit>(Endpoint.patch(ReplayEndpoints.detail(id), """{"video_url":${url.jsonQuoted()}}"""))
            }.isSuccess
            if (ok) load()
            onDone(ok)
        }
    }

    /** Publie la vidéo (URL déjà uploadée) via `POST /lifestyle`, puis recharge. */
    fun publish(title: String, description: String, onDone: () -> Unit) {
        val videoUrl = _uiState.value.pendingVideoUrl
        if (title.isBlank() || videoUrl.isNullOrBlank()) {
            _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errTitleDateRequired) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            val body = json.encodeToString(
                CreateLifestyleRequest.serializer(),
                CreateLifestyleRequest(
                    artistName = _uiState.value.artistName,
                    title = title.trim(),
                    videoUrl = videoUrl,
                    thumbnailUrl = _uiState.value.pendingThumbnailUrl,
                    description = description.ifBlank { null },
                    duration = _uiState.value.pendingDuration,
                ),
            )
            runCatching { api.request<Unit>(Endpoint.post(ContentEndpoints.LIFESTYLE, body)) }
                .onSuccess {
                    _uiState.update {
                        it.copy(submitting = false, pendingVideoUrl = null, pendingThumbnailUrl = null, message = com.dualmusic.core.ui.i18n.appStrings.videoPublished)
                    }
                    load()
                    onDone()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(submitting = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errCreateFailed) }
                }
        }
    }
}

/**
 * Écran « Contenu » — publier une vidéo lifestyle + voir ses vidéos et replays.
 * Équivalent mobile de l'onglet Contenu du web.
 *
 * @param viewModel source d'état.
 * @param isArtist masque la publication lifestyle + « Mes vidéos » pour un manager (pas
 *   d'artiste) qui accède à cet écran seulement pour gérer les replays de duels/compétitions
 *   qu'il gère — ces deux sections n'ont pas de sens pour lui.
 */
@Composable
fun MyContentScreen(viewModel: MyContentViewModel, isArtist: Boolean = true) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var manageReplay by remember { mutableStateOf<ReplayVideo?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Sélecteur vidéo : lit le média + extrait la durée, puis lance l'upload.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 500L * 1024 * 1024) }
                    .onSuccess { media -> viewModel.uploadVideo(media, extractDuration(context, uri)) }
                    .onFailure { viewModel.setMessage(it.message ?: s.fileUnreadable) }
            }
        }
    }
    // Sélecteur miniature (image, ≤ 5 Mo).
    val thumbPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 5L * 1024 * 1024) }
                    .onSuccess { media -> viewModel.uploadThumbnail(media) }
                    .onFailure { viewModel.setMessage(it.message ?: s.fileUnreadable) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        item { ui.message?.let { Text(it, color = colors.primary) } }

        // --- Publier une vidéo lifestyle (artiste seulement — sans objet pour un manager) ---
        if (isArtist) item {
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Text(s.publishLifestyle, color = colors.foreground, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(s.titleRequired) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(s.description) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DMButton(
                        if (ui.uploading) "…" else s.chooseVideo,
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.uploading,
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                    )
                    if (ui.pendingVideoUrl != null) Text("${s.videoReady} · Durée : ${ui.pendingDuration}", color = colors.accent)
                    // Miniature (optionnelle) — parité web.
                    DMButton(
                        if (ui.uploadingThumb) "…" else if (ui.pendingThumbnailUrl != null) "Miniature ✓" else "Miniature (optionnel)",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ui.uploadingThumb,
                        onClick = {
                            thumbPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DMButton(
                        s.publishVideo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.pendingVideoUrl != null && !ui.submitting,
                        onClick = { viewModel.publish(title, description) { title = ""; description = "" } },
                    )
                }
            }
        }

        // --- Mes vidéos (artiste seulement) ---
        if (isArtist) {
            item { Text(s.myVideos, color = colors.foreground, fontWeight = FontWeight.Bold) }
            if (ui.videos.isEmpty()) {
                item { Text(s.noMyVideos, color = colors.mutedForeground) }
            } else {
                items(ui.videos) { v -> ContentRow(v.title ?: "—", "❤ ${v.likesCount} · 👁 ${v.viewsCount}") }
            }
        }

        // --- Mes replays ---
        item { Text(s.myReplays, color = colors.foreground, fontWeight = FontWeight.Bold) }
        if (ui.replays.isEmpty()) {
            item { Text(s.noMyReplays, color = colors.mutedForeground) }
        } else {
            items(ui.replays) { r ->
                ContentRow(
                    title = r.title ?: "—",
                    meta = "👁 ${r.viewsCount}" + if (!r.isPublic) " · Brouillon" else "",
                    onClick = { manageReplay = r },
                )
            }
        }
    }

    // Gestion d'un replay (propriétaire) : prix, publication, téléchargement, remplacement vidéo.
    manageReplay?.let { r ->
        ReplayManageDialog(
            replay = r,
            onDismiss = { manageReplay = null },
            onSave = { price, isPublic -> viewModel.updateReplaySettings(r.id, price, isPublic) { manageReplay = null } },
            onReplaceVideo = { media -> viewModel.replaceReplayVideo(r.id, media) { manageReplay = null } },
        )
    }
}

/** Feuille de gestion d'un replay : prix, publication, téléchargement, remplacement vidéo. */
@Composable
private fun ReplayManageDialog(
    replay: ReplayVideo,
    onDismiss: () -> Unit,
    onSave: (price: Double, isPublic: Boolean) -> Unit,
    onReplaceVideo: (com.dualmusic.core.upload.LocalMedia) -> Unit,
) {
    val colors = DualMusicTheme.colors
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var price by remember(replay.id) { mutableStateOf(replay.replayPrice.toInt().toString()) }
    var isPublic by remember(replay.id) { mutableStateOf(replay.isPublic) }
    var uploadingVideo by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                uploadingVideo = true
                runCatching { readLocalMedia(context, uri, maxBytes = 2048L * 1024 * 1024) }
                    .onSuccess { media -> onReplaceVideo(media) }
                    .onFailure { uploadingVideo = false }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                Text("🎛️ Gestion du replay", color = colors.foreground, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rendre public", color = colors.foreground)
                        Text("Visible sur la page des replays une fois activé.", color = colors.mutedForeground, fontWeight = FontWeight.Normal)
                    }
                    androidx.compose.material3.Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = { v -> price = v.filter { it.isDigit() } },
                    label = { Text("Prix (crédits — 0 = gratuit)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                DMButton(
                    "Enregistrer les réglages",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSave(price.toDoubleOrNull() ?: 0.0, isPublic) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(
                        "Télécharger",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val url = replay.videoUrl
                            if (!url.isNullOrBlank()) {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        },
                    )
                    DMButton(
                        if (uploadingVideo) "…" else "Remplacer la vidéo",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        enabled = !uploadingVideo,
                        onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    )
                }
            }
        }
    }
}

/** Ligne simple de contenu (titre + méta) — cliquable pour ouvrir la gestion si [onClick] fourni. */
@Composable
private fun ContentRow(title: String, meta: String, onClick: (() -> Unit)? = null) {
    val colors = DualMusicTheme.colors
    DMCard(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(meta, color = colors.mutedForeground)
        }
    }
}

/** Échappe une chaîne pour l'insérer dans un corps JSON construit à la main. */
private fun String.jsonQuoted(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** Extrait la durée d'une vidéo locale au format `m:ss` (repli `0:00`). */
private fun extractDuration(context: android.content.Context, uri: Uri): String = runCatching {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    retriever.release()
    val totalSec = ms / 1000
    "%d:%02d".format(totalSec / 60, totalSec % 60)
}.getOrDefault("0:00")

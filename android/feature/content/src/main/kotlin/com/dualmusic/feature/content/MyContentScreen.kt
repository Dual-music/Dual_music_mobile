package com.dualmusic.feature.content

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
    val pendingDuration: String = "0:00",
    val uploading: Boolean = false,
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
            val replays = myUserId?.let { uid ->
                runCatching {
                    api.request(
                        Endpoint.get(ReplayEndpoints.LIST, query = mapOf("artistId" to uid)),
                        ListSerializer(ReplayVideo.serializer()),
                    )
                }.getOrDefault(emptyList())
            } ?: emptyList()
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

    /** Signale une erreur (lecture/plafond de taille) à l'UI. */
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

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
                    thumbnailUrl = null,
                    description = description.ifBlank { null },
                    duration = _uiState.value.pendingDuration,
                ),
            )
            runCatching { api.request<Unit>(Endpoint.post(ContentEndpoints.LIFESTYLE, body)) }
                .onSuccess {
                    _uiState.update {
                        it.copy(submitting = false, pendingVideoUrl = null, message = com.dualmusic.core.ui.i18n.appStrings.videoPublished)
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
 */
@Composable
fun MyContentScreen(viewModel: MyContentViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        item { ui.message?.let { Text(it, color = colors.primary) } }

        // --- Publier une vidéo lifestyle ---
        item {
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
                    if (ui.pendingVideoUrl != null) Text(s.videoReady, color = colors.accent)
                    DMButton(
                        s.publishVideo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.pendingVideoUrl != null && !ui.submitting,
                        onClick = { viewModel.publish(title, description) { title = ""; description = "" } },
                    )
                }
            }
        }

        // --- Mes vidéos ---
        item { Text(s.myVideos, color = colors.foreground, fontWeight = FontWeight.Bold) }
        if (ui.videos.isEmpty()) {
            item { Text(s.noMyVideos, color = colors.mutedForeground) }
        } else {
            items(ui.videos) { v -> ContentRow(v.title ?: "—", "❤ ${v.likesCount} · 👁 ${v.viewsCount}") }
        }

        // --- Mes replays ---
        item { Text(s.myReplays, color = colors.foreground, fontWeight = FontWeight.Bold) }
        if (ui.replays.isEmpty()) {
            item { Text(s.noMyReplays, color = colors.mutedForeground) }
        } else {
            items(ui.replays) { r -> ContentRow(r.title ?: "—", "👁 ${r.viewsCount}") }
        }
    }
}

/** Ligne simple de contenu (titre + méta). */
@Composable
private fun ContentRow(title: String, meta: String) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(meta, color = colors.mutedForeground)
        }
    }
}

/** Extrait la durée d'une vidéo locale au format `m:ss` (repli `0:00`). */
private fun extractDuration(context: android.content.Context, uri: Uri): String = runCatching {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    retriever.release()
    val totalSec = ms / 1000
    "%d:%02d".format(totalSec / 60, totalSec % 60)
}.getOrDefault("0:00")

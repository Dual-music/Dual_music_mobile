package com.dualmusic.feature.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.replay.ReplayVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du lecteur de replay : gère l'accès (gratuit / débloqué / à débloquer).
 *
 * @param replay le replay à lire.
 * @param repository accès + déblocage.
 */
class ReplayPlayerViewModel(
    val replay: ReplayVideo,
    private val repository: ReplayRepository,
) : ViewModel() {

    /** null = en vérification, true = accès accordé, false = déblocage requis. */
    private val _unlocked = MutableStateFlow<Boolean?>(null)
    val unlocked: StateFlow<Boolean?> = _unlocked.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Vérifie l'accès au chargement (gratuit → accordé d'office). */
    fun checkAccess() {
        if (!replay.requiresUnlock) { _unlocked.value = true; return }
        viewModelScope.launch {
            _unlocked.value = runCatching { repository.hasAccess(replay.id) }.getOrDefault(false)
        }
    }

    /** Débloque le replay (débit du solde) puis autorise la lecture. */
    fun unlock() {
        viewModelScope.launch {
            _busy.value = true
            val ok = repository.unlock(replay.id)
            _busy.value = false
            if (ok) _unlocked.value = true else _error.value = "Déblocage impossible (solde insuffisant ?)."
        }
    }

    /** Enregistre une vue (best-effort). */
    fun registerView() {
        viewModelScope.launch { runCatching { repository.registerView(replay.id) } }
    }
}

/**
 * Lecteur de replay. Affiche le paywall si un déblocage est requis, sinon lit la vidéo
 * via ExoPlayer (décodage matériel, HLS/MP4).
 *
 * @param viewModel état d'accès + le replay.
 */
@Composable
fun ReplayPlayerScreen(viewModel: ReplayPlayerViewModel) {
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val replay = viewModel.replay

    LaunchedEffect(Unit) { viewModel.checkAccess() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(replay.title ?: "Rediffusion", color = colors.foreground, fontWeight = FontWeight.Bold)

        when (unlocked) {
            null -> CircularProgressIndicator(color = colors.primary)
            false -> {
                // Paywall.
                DMCard {
                    Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        Text("Cette rediffusion est premium.", color = colors.foreground)
                        error?.let { Text(it, color = colors.destructive) }
                        DMButton(
                            if (busy) "Déblocage…" else "Débloquer pour ${replay.replayPrice.toInt()} crédits",
                            isLoading = busy,
                            enabled = !busy,
                        ) { viewModel.unlock() }
                    }
                }
            }
            true -> {
                val url = replay.videoUrl
                if (url.isNullOrBlank()) {
                    Text("Vidéo indisponible.", color = colors.mutedForeground)
                } else {
                    LaunchedEffect(Unit) { viewModel.registerView() }
                    VideoPlayer(url = url)
                }
            }
        }
    }
}

/**
 * Lecteur vidéo ExoPlayer intégré à Compose.
 * Le player est créé/relâché avec le cycle de vie du composable.
 * `@OptIn(UnstableApi)` : `PlayerView` de Media3 est encore marqué instable.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        )
    }
}

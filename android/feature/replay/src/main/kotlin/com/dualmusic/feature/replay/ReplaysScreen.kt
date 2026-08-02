package com.dualmusic.feature.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.replay.ReplayVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du catalogue de replays.
 *
 * @param repository lectures REST des replays.
 */
class ReplaysViewModel(private val repository: ReplayRepository) : ViewModel() {

    private val _replays = MutableStateFlow<List<ReplayVideo>>(emptyList())
    val replays: StateFlow<List<ReplayVideo>> = _replays.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge le catalogue. */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _replays.value = runCatching { repository.replays() }.getOrDefault(emptyList())
            _isLoading.value = false
        }
    }
}

/**
 * Catalogue des rediffusions.
 *
 * @param viewModel source du catalogue.
 * @param onOpen callback à l'ouverture d'un replay (lecteur).
 */
@Composable
fun ReplaysListScreen(
    viewModel: ReplaysViewModel,
    onOpen: (ReplayVideo) -> Unit,
) {
    val replays by viewModel.replays.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
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

        if (isLoading) DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
        if (!isLoading && replays.isEmpty()) {
            DMEmptyState(
                title = strings.noReplays,
                subtitle = strings.noReplaysHint,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(replays) { replay -> ReplayRow(replay) { onOpen(replay) } }
        }
    }
}

/** Carte d'un replay : vignette 16:9 (miniature + durée), titre, type d'événement, vues, prix. */
@Composable
private fun ReplayRow(replay: ReplayVideo, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Vignette : miniature (repli emoji) + overlay lecture + badge durée.
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                DMRemoteImage(
                    url = replay.thumbnailUrl,
                    contentDescription = replay.title,
                    modifier = Modifier.fillMaxSize(),
                    fallbackEmoji = "🎬",
                )
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
                replay.duration?.takeIf { it > 0 }?.let { d ->
                    Text(
                        formatDuration(d),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(replay.title ?: strings.replay, color = colors.foreground, fontWeight = FontWeight.Bold, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${sourceEmoji(replay.sourceType)} ", fontSize = 12.sp)
                    Text("${replay.viewsCount} ${strings.views}", color = colors.mutedForeground, fontSize = 12.sp)
                }
            }
            if (replay.requiresUnlock) {
                Text("🔒 ${replay.replayPrice.toInt()}", color = colors.accent, fontWeight = FontWeight.Bold)
            } else {
                Text(strings.free, color = colors.mutedForeground, fontSize = 12.sp)
            }
        }
    }
}

/** Durée en m:ss. */
private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:" + s.toString().padStart(2, '0')
}

/** Emoji du type d'événement source (duel/concert/compétition/live). */
private fun sourceEmoji(type: String?): String = when (type) {
    "duel" -> "⚔️"
    "concert" -> "🎤"
    "competition" -> "🏆"
    "live" -> "🔴"
    else -> "🎬"
}

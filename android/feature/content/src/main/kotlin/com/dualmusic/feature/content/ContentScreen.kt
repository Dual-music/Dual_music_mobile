package com.dualmusic.feature.content

import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.content.LifestyleVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel Lifestyle : vidéos + état de like (optimiste, pré-rempli via `/lifestyle/liked/mine`).
 */
class ContentViewModel(private val repository: ContentRepository) : ViewModel() {

    private val _videos = MutableStateFlow<List<LifestyleVideo>>(emptyList())
    val videos: StateFlow<List<LifestyleVideo>> = _videos.asStateFlow()

    /** Vidéos actuellement likées (bascule optimiste). */
    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    /** Snapshot des likes au chargement (pour ajuster le compteur affiché). */
    private val _initialLiked = MutableStateFlow<Set<String>>(emptySet())
    val initialLiked: StateFlow<Set<String>> = _initialLiked.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _videos.value = runCatching { repository.lifestyle() }.getOrDefault(emptyList())
            val mine = runCatching { repository.likedMine() }.getOrDefault(emptyList()).toSet()
            _initialLiked.value = mine
            _liked.value = mine
        }
    }

    /** Like/unlike (optimiste + serveur). */
    fun like(videoId: String) {
        val was = videoId in _liked.value
        _liked.update { if (was) it - videoId else it + videoId }
        viewModelScope.launch { runCatching { repository.toggleLike(videoId) } }
    }

    /** Enregistre une vue. */
    fun view(videoId: String) {
        viewModelScope.launch { runCatching { repository.registerVideoView(videoId) } }
    }
}

/**
 * Page publique « Lifestyle » — parité web : titre + sous-titre + recherche + grille de cartes
 * vidéo (miniature + durée, titre, artiste, vues, like/commentaire/partage).
 */
@Composable
fun ContentScreen(viewModel: ContentViewModel) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val initialLiked by viewModel.initialLiked.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var openVideo by remember { mutableStateOf<LifestyleVideo?>(null) }
    val open = openVideo
    if (open != null) {
        LifestylePlayer(open, onLike = { viewModel.like(open.id) }, onView = { viewModel.view(open.id) }, onBack = { openVideo = null })
        return
    }

    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()
    val filtered = if (q.isEmpty()) videos else videos.filter {
        (it.title?.lowercase()?.contains(q) == true) || (it.artistName?.lowercase()?.contains(q) == true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.lifestyle, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(s.lifestyleSubtitle, color = colors.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(s.searchPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (filtered.isEmpty()) {
            DMEmptyState(title = s.noLifestyleVideos, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                items(filtered, key = { it.id }) { video ->
                    val isLiked = video.id in liked
                    val delta = (if (isLiked) 1 else 0) - (if (video.id in initialLiked) 1 else 0)
                    VideoCard(
                        video = video,
                        isLiked = isLiked,
                        likeCount = video.likesCount + delta,
                        onOpen = { openVideo = video },
                        onLike = { viewModel.like(video.id) },
                    )
                }
            }
        }
    }
}

/** Carte d'une vidéo lifestyle (miniature verticale + infos + actions). */
@Composable
private fun VideoCard(video: LifestyleVideo, isLiked: Boolean, likeCount: Int, onOpen: () -> Unit, onLike: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current
    DMCard(modifier = Modifier.fillMaxWidth(), padded = false) {
        // Miniature 9:16 + play + durée.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clickable { onOpen() }, contentAlignment = Alignment.Center) {
            DMRemoteImage(url = video.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), fallbackEmoji = "🎬")
            Text("▶", color = Color.White, fontSize = 36.sp)
            video.duration?.let {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(it, color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(video.title ?: s.video, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            video.artistName?.let { Text(it, color = colors.mutedForeground, fontSize = 11.sp, maxLines = 1) }
            Text("👁 ${video.viewsCount} ${s.viewsWord}", color = colors.mutedForeground, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${if (isLiked) "❤" else "🤍"} $likeCount", color = if (isLiked) colors.accent else colors.mutedForeground, fontSize = 12.sp, modifier = Modifier.clickable { onLike() })
                Text("💬 ${video.commentsCount}", color = colors.mutedForeground, fontSize = 12.sp, modifier = Modifier.clickable { onOpen() })
                Text("↗", color = colors.mutedForeground, fontSize = 14.sp, modifier = Modifier.clickable { shareVideo(context, video) })
            }
        }
    }
}

/** Partage natif d'une vidéo lifestyle. */
private fun shareVideo(context: android.content.Context, video: LifestyleVideo) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, video.title ?: "Dual Music")
        putExtra(Intent.EXTRA_TEXT, "${video.title ?: ""} — Dual Music")
    }
    context.startActivity(Intent.createChooser(share, null))
}

/** Lecteur plein écran d'une vidéo lifestyle. */
@Composable
private fun LifestylePlayer(video: LifestyleVideo, onLike: () -> Unit, onView: () -> Unit, onBack: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val url = video.videoUrl

    Column(
        modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero).padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        DMButton("← ${s.back}", style = DMButtonStyle.OUTLINE, onClick = onBack)
        Text(video.title ?: s.video, color = colors.foreground, fontWeight = FontWeight.Bold)

        if (url.isNullOrBlank()) {
            Text(s.videoUnavailable, color = colors.mutedForeground)
        } else {
            LaunchedEffect(Unit) { onView() }
            ModernVideoPlayer(url = url)
            DMButton("❤ ${s.likeAction}", onClick = onLike)
        }
    }
}

package com.dualmusic.feature.content

import androidx.annotation.OptIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.content.BlogPost
import com.dualmusic.domain.content.LifestyleVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel du contenu : vidéos lifestyle + articles de blog.
 *
 * @param repository accès REST au contenu.
 */
class ContentViewModel(private val repository: ContentRepository) : ViewModel() {

    private val _videos = MutableStateFlow<List<LifestyleVideo>>(emptyList())
    val videos: StateFlow<List<LifestyleVideo>> = _videos.asStateFlow()

    private val _blogs = MutableStateFlow<List<BlogPost>>(emptyList())
    val blogs: StateFlow<List<BlogPost>> = _blogs.asStateFlow()

    /** Charge les deux flux de contenu. */
    fun load() {
        viewModelScope.launch {
            _videos.value = runCatching { repository.lifestyle() }.getOrDefault(emptyList())
            _blogs.value = runCatching { repository.blogs() }.getOrDefault(emptyList())
        }
    }

    /** Like d'une vidéo (best-effort). */
    fun like(videoId: String) {
        viewModelScope.launch { runCatching { repository.toggleLike(videoId) } }
    }

    /** Enregistre une vue de vidéo. */
    fun view(videoId: String) {
        viewModelScope.launch { runCatching { repository.registerVideoView(videoId) } }
    }
}

/**
 * Écran de contenu avec deux onglets : Lifestyle (vidéos) et Blog (articles).
 *
 * @param viewModel source d'état.
 */
@Composable
fun ContentScreen(viewModel: ContentViewModel) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val blogs by viewModel.blogs.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }
    var openVideo by remember { mutableStateOf<LifestyleVideo?>(null) }
    var openBlog by remember { mutableStateOf<BlogPost?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }

    // Superpositions plein écran (lecteur / article).
    openVideo?.let { video ->
        LifestylePlayer(video, onLike = { viewModel.like(video.id) }, onView = { viewModel.view(video.id) }, onBack = { openVideo = null })
        return
    }
    openBlog?.let { post ->
        BlogDetail(post, onBack = { openBlog = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(strings.lifestyle) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(strings.blog) })
        }

        if (tab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(videos) { v -> VideoRow(v) { openVideo = v } }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(blogs) { b -> BlogRow(b) { openBlog = b } }
            }
        }
    }
}

/** Carte d'une vidéo lifestyle. */
@Composable
private fun VideoRow(video: LifestyleVideo, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("▶  ${video.title ?: strings.video}", color = colors.foreground, fontWeight = FontWeight.Bold)
                video.artistName?.let { Text(it, color = colors.mutedForeground) }
            }
            Text("❤ ${video.likesCount}", color = colors.accent)
        }
    }
}

/** Carte d'un article de blog. */
@Composable
private fun BlogRow(post: BlogPost, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            Text(post.title ?: strings.article, color = colors.foreground, fontWeight = FontWeight.Bold)
            post.excerpt?.let { Text(it, color = colors.mutedForeground) }
        }
    }
}

/** Lecteur plein écran d'une vidéo lifestyle. */
@OptIn(UnstableApi::class)
@Composable
private fun LifestylePlayer(
    video: LifestyleVideo,
    onLike: () -> Unit,
    onView: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    val url = video.videoUrl

    Column(
        modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero).padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        DMButton("← ${strings.back}", style = DMButtonStyle.OUTLINE, onClick = onBack)
        Text(video.title ?: strings.video, color = colors.foreground, fontWeight = FontWeight.Bold)

        if (url.isNullOrBlank()) {
            Text(strings.videoUnavailable, color = colors.mutedForeground)
        } else {
            val player = remember {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
                }
            }
            DisposableEffect(Unit) {
                onView()
                onDispose { player.release() }
            }
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color.Black)) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> PlayerView(ctx).apply { this.player = player } })
            }
            DMButton("❤ ${strings.likeAction}", onClick = onLike)
        }
    }
}

/** Détail plein écran d'un article de blog. */
@Composable
private fun BlogDetail(post: BlogPost, onBack: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        DMButton("← ${strings.back}", style = DMButtonStyle.OUTLINE, onClick = onBack)
        Text(post.title ?: strings.article, color = colors.foreground, fontWeight = FontWeight.Bold)
        post.authorName?.let { Text("${strings.by} $it", color = colors.mutedForeground) }
        // Contenu brut (le rendu HTML riche sera ajouté ultérieurement).
        Text(post.content ?: post.excerpt ?: "", color = colors.foreground)
    }
}

package com.dualmusic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Live
import com.dualmusic.feature.live.LiveRoomScreen
import com.dualmusic.feature.live.LiveViewModel

/**
 * Feed vertical plein écran (style TikTok) : un live par page, `VerticalPager`.
 *
 * Seule la page **active** connecte la vidéo (via `LiveRoomScreen`) ; les autres montrent
 * une affiche légère. Le jeton de la prochaine room est pré-chauffé (`FeedViewModel`).
 *
 * @param makeLiveViewModel fabrique un `LiveViewModel` pour un live (injection app).
 */
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    makeLiveViewModel: (Live) -> LiveViewModel,
    initialPage: Int = 0,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })

    // Prewarm + pagination quand la page active change.
    LaunchedEffect(pagerState.currentPage, items.size) {
        if (items.isNotEmpty()) viewModel.onPageChanged(pagerState.currentPage)
    }

    // Aucun live en cours : état vide centré plutôt qu'un écran noir.
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero),
            contentAlignment = Alignment.Center,
        ) {
            DMEmptyState(
                title = "Aucun live en cours",
                subtitle = "Reviens bientôt : les lives des artistes apparaîtront ici dès qu'ils démarrent.",
                icon = Icons.Filled.PlayArrow,
            )
        }
        return
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1, // garde la page voisine prête (prewarm visuel)
    ) { page ->
        val item = items[page]
        if (page == pagerState.currentPage) {
            // Cellule active : vidéo live + overlays.
            LiveRoomScreen(
                viewModel = remember(item.id) { makeLiveViewModel(item) },
                hostUserId = item.artistId,
                quickGiftId = "", // TODO(feature-gifts): sélecteur de cadeau réel
                prewarmedToken = viewModel.prewarmedToken(item.id),
                liveTitle = item.title,
                artistName = item.artist?.displayName,
            )
        } else {
            Poster(item)
        }
    }
}

/** Affiche statique (avant activation) : évite de connecter toutes les vidéos. */
@Composable
private fun Poster(item: Live) {
    Box(
        modifier = Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.artist?.displayName ?: com.dualmusic.core.ui.i18n.LocalStrings.current.live, color = Color.White)
            item.title?.let { Text(it, color = Color.White.copy(alpha = 0.8f)) }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.dmGlow(),
            )
        }
    }
}

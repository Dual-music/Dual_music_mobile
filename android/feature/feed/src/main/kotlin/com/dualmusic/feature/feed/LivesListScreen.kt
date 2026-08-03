package com.dualmusic.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Live

/**
 * Liste (grille) de tous les lives en cours — parité web `/lives` : titre + sous-titre,
 * recherche (nom d'artiste ou titre), et cartes « Regarder ».
 *
 * @param viewModel réutilise [FeedViewModel] (charge `GET /lives?status=live`).
 * @param onOpen ouvre le live sélectionné (lecteur plein écran).
 */
@Composable
fun LivesListScreen(viewModel: FeedViewModel, onOpen: (Live) -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()
    val filtered = if (q.isEmpty()) items else items.filter {
        (it.artist?.displayName?.lowercase()?.contains(q) == true) || (it.title?.lowercase()?.contains(q) == true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.navLives, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Text(s.livesSubtitle, color = colors.mutedForeground)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(s.searchPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (filtered.isEmpty()) {
            DMEmptyState(
                title = s.noLivesActive,
                subtitle = s.noLivesHint,
                modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg),
            )
        } else {
            filtered.forEach { live -> LiveCard(live, onOpen) }
        }
    }
}

/** Carte d'un live : bandeau dégradé (badge LIVE + spectateurs + avatar) puis infos + « Regarder ». */
@Composable
private fun LiveCard(live: Live, onOpen: (Live) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(live) },
        padded = false,
    ) {
        // Bandeau dégradé avec avatar central.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.35f), colors.accent.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(DualMusicTheme.spacing.sm)
                    .background(Color(0xFFEF4444), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("🔴 LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DualMusicTheme.spacing.sm)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("👥 ${live.viewerCount} ${s.spectators}", color = Color.White, fontSize = 11.sp) }
            DMRemoteImage(
                url = live.artist?.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape),
                fallbackEmoji = "🎤",
            )
        }
        // Infos + bouton.
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(live.artist?.displayName ?: s.artistSingular, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(live.title ?: s.liveInProgress, color = colors.mutedForeground, fontSize = 13.sp)
            DMButton(s.watchLive, style = DMButtonStyle.DESTRUCTIVE, modifier = Modifier.fillMaxWidth()) { onOpen(live) }
        }
    }
}

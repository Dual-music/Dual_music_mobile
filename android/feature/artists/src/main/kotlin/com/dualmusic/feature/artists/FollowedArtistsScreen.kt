package com.dualmusic.feature.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.artist.ArtistSummary

/**
 * Écran « Suivis » — liste **uniquement** les artistes que le caller suit
 * (équivalent mobile de FollowedArtists du web). Réutilise [ArtistsViewModel]
 * (annuaire + set des suivis) et filtre sur les ids suivis, avec bouton « Ne plus suivre ».
 *
 * @param viewModel source d'état (annuaire + suivis).
 */
@Composable
fun FollowedArtistsScreen(viewModel: ArtistsViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    val followed = artists.filter { it.id in following }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(
            "${s.followedTitle} (${followed.size})",
            color = colors.foreground,
            fontWeight = FontWeight.Bold,
        )
        when {
            isLoading -> DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
            followed.isEmpty() -> DMEmptyState(
                title = s.notFollowingAny,
                subtitle = s.followedEmptyHint,
                icon = Icons.Filled.Favorite,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(followed) { artist ->
                    FollowedRow(artist = artist, onUnfollow = { viewModel.toggleFollow(artist.id) })
                }
            }
        }
    }
}

/** Ligne d'un artiste suivi : nom + followers + bouton « Ne plus suivre ». */
@Composable
private fun FollowedRow(artist: ArtistSummary, onUnfollow: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("🎤  ${artist.displayName}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text("${artist.followersCount} ${s.followers}", color = colors.mutedForeground)
            }
            DMButton(s.followed, style = DMButtonStyle.OUTLINE, onClick = onUnfollow)
        }
    }
}

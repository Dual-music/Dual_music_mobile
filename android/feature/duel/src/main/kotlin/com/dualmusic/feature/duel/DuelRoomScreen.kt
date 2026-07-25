package com.dualmusic.feature.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * Écran d'une room de duel (viewer).
 *
 * Vidéo plein écran + overlays : barre de votes (part de chaque artiste, en direct),
 * minuteur, panneau de vote payant, cadeaux animés (GPU) et chat.
 *
 * Le vote débite le portefeuille via une procédure atomique serveur ; le tally n'est
 * mis à jour qu'à réception de l'événement temps réel `vote` (pas d'optimisme sur l'argent).
 *
 * @param viewModel état + actions du duel.
 * @param voteAmount montant (crédits) d'un vote rapide.
 */
@Composable
fun DuelRoomScreen(
    viewModel: DuelViewModel,
    voteAmount: Double = 10.0,
) {
    val duel by viewModel.duel.collectAsStateWithLifecycle()
    val totals by viewModel.voteTotals.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val videoTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current

    // Démarre/arrête avec le cycle de vie du composable.
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Couche vidéo (décodage matériel, zero-copy) ---
        val track = videoTrack
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradé bas pour la lisibilité des overlays.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )

        // --- Cadeau animé (halo GPU) ---
        giftFeed.lastOrNull()?.let { gift ->
            key(gift.key) { GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
        }

        // --- Overlays ---
        Column(
            modifier = Modifier.fillMaxSize().padding(DualMusicTheme.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Haut : barre de votes + minuteur.
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                val a1 = duel?.artist1Id
                val a2 = duel?.artist2Id
                VoteBar(
                    leftName = duel?.artist1?.displayName ?: strings.artist1,
                    rightName = duel?.artist2?.displayName ?: strings.artist2,
                    leftTotal = a1?.let { totals[it] } ?: 0.0,
                    rightTotal = a2?.let { totals[it] } ?: 0.0,
                )
                if (timer.isRunning) {
                    Text(
                        strings.timerRunning,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Bas : panneau de vote.
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                error?.let { Text(it, color = colors.destructive) }
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    duel?.artist1Id?.let { id ->
                        DMButton(
                            "${strings.vote} ${duel?.artist1?.displayName ?: strings.artist1}",
                            modifier = Modifier.weight(1f),
                        ) { viewModel.vote(id, voteAmount) }
                    }
                    duel?.artist2Id?.let { id ->
                        DMButton(
                            "${strings.vote} ${duel?.artist2?.displayName ?: strings.artist2}",
                            style = DMButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.vote(id, voteAmount) }
                    }
                }
                Text("${strings.oneVote} = $voteAmount ${strings.credits}", color = colors.mutedForeground)
            }
        }
    }
}

/**
 * Barre de répartition des votes entre les deux artistes.
 * La largeur de chaque segment reflète la part de crédits reçue (mise à jour en direct).
 */
@Composable
private fun VoteBar(
    leftName: String,
    rightName: String,
    leftTotal: Double,
    rightTotal: Double,
) {
    val colors = DualMusicTheme.colors
    val sum = (leftTotal + rightTotal).takeIf { it > 0 } ?: 1.0
    val leftShare = (leftTotal / sum).toFloat().coerceIn(0.02f, 0.98f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$leftName · ${leftTotal.toInt()}", color = colors.foreground, fontWeight = FontWeight.Bold)
            Text("${rightTotal.toInt()} · $rightName", color = colors.foreground, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(DualMusicTheme.radii.sm)),
        ) {
            Box(
                Modifier
                    .weight(leftShare)
                    .fillMaxHeight()
                    .background(DualMusicTheme.gradients.primary),
            )
            Box(
                Modifier
                    .weight(1f - leftShare)
                    .fillMaxHeight()
                    .background(colors.electricBlue),
            )
        }
    }
}

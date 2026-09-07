package com.dualmusic.feature.replay

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Lecteur vidéo moderne (replays + lifestyle) — parité web `ConcertReplayPlayer` : lecture/pause,
 * retour/avance de 10s, barre de progression, volume/muet, temps écoulé/total, contrôles qui se
 * masquent automatiquement après quelques secondes (tap pour les ré-afficher), plein écran
 * optionnel. Remplace le `PlayerView` nu (contrôles ExoPlayer par défaut, non stylés) utilisé
 * jusqu'ici — même lecteur pour `ReplayPlayerScreen` et `LifestylePlayer`.
 *
 * @param url source vidéo (MP4/HLS).
 * @param aspectRatio 16:9 pour un replay, 9:16 pour une vidéo lifestyle verticale.
 * @param isFullscreen état plein écran courant (piloté par l'appelant — bascule l'orientation).
 * @param onToggleFullscreen callback bascule plein écran ; `null` masque le bouton (non pertinent
 *   dans certains contextes, ex. un flux vertical déjà plein écran par nature).
 */
@OptIn(UnstableApi::class)
@Composable
fun ModernVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Le seek est piloté par l'utilisateur pendant le drag : on n'écrase pas sa position avec
    // celle (encore ancienne) du polling tant qu'il n'a pas relâché le doigt.
    var seekDraft by remember { mutableStateOf<Float?>(null) }

    // Polling de position/durée/état — Media3 n'expose pas de Flow Compose natif pour ça.
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            durationMs = player.duration.coerceAtLeast(0L)
            if (seekDraft == null) positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(300)
        }
    }

    // Auto-masquage des contrôles après 3s de lecture sans interaction.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio).background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                controlsVisible = !controlsVisible
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // contrôles 100% Compose ci-dessous, pas ceux d'ExoPlayer.
                }
            },
        )

        if (controlsVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

            // Ligne centrale : -10s / lecture-pause / +10s.
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(Icons.Filled.Replay10, size = 32) {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                }
                PlayerIconButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    size = 48,
                    background = Color.White.copy(alpha = 0.2f),
                ) {
                    if (isPlaying) player.pause() else player.play()
                    controlsVisible = true
                }
                PlayerIconButton(Icons.Filled.Forward10, size = 32) {
                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0)))
                }
            }

            // Barre du bas : temps courant, curseur, temps total, muet, plein écran.
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Slider(
                    value = seekDraft ?: positionMs.toFloat(),
                    onValueChange = { seekDraft = it },
                    onValueChangeFinished = {
                        seekDraft?.let { player.seekTo(it.toLong()) }
                        seekDraft = null
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatTime((seekDraft ?: positionMs.toFloat()).toLong())} / ${formatTime(durationMs)}", color = Color.White, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerIconButton(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, size = 22) {
                            isMuted = !isMuted
                            player.volume = if (isMuted) 0f else 1f
                        }
                        if (onToggleFullscreen != null) {
                            PlayerIconButton(if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, size = 22) {
                                onToggleFullscreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerIconButton(icon: ImageVector, size: Int, background: Color = Color.Transparent, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size((size + 20).dp).background(background, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size.dp)) }
}

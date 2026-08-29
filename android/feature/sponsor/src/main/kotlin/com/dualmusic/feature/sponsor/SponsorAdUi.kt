package com.dualmusic.feature.sponsor

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.domain.realtime.SponsorAdVideo

/**
 * Couche complète de diffusion pub sponsor à déposer dans le [Box] d'une room :
 * le **contrôle hôte** (bas-gauche) si [canTrigger], et l'**overlay vidéo plein écran**
 * (au-dessus de tout) pour tous les spectateurs quand une pub est active.
 *
 * @param activeAd pub en cours (null = aucune).
 * @param canTrigger l'utilisateur peut lancer/arrêter (hôte/manager) — l'autorisation reste
 *   imposée côté serveur.
 * @param ads catalogue chargé (sélecteur).
 * @param busy verrou anti double-clic.
 * @param onLoadAds charge les pubs éligibles (à l'ouverture du sélecteur).
 * @param onPlay lance la pub choisie.
 * @param onStop arrête la diffusion.
 */
@Composable
fun BoxScope.SponsorAdLayer(
    activeAd: SponsorAdVideo?,
    canTrigger: Boolean,
    ads: List<SponsorAdVideo>,
    busy: Boolean,
    onLoadAds: () -> Unit,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    // Quand false, le bouton intégré « Démarrer pub » n'est PAS rendu (déclenché ailleurs, ex. rail).
    // L'overlay vidéo + l'arrêt (pour l'hôte) restent actifs.
    showTriggerButton: Boolean = true,
) {
    // Contrôle hôte : bouton discret en bas-gauche (au-dessus de la barre système).
    if (canTrigger && showTriggerButton) {
        SponsorAdControl(
            active = activeAd != null,
            ads = ads,
            busy = busy,
            onLoadAds = onLoadAds,
            onPlay = onPlay,
            onStop = onStop,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }

    // Overlay vidéo plein écran pour tous : au-dessus de la vidéo de l'événement.
    activeAd?.let { ad ->
        SponsorAdOverlay(
            ad = ad,
            canStop = canTrigger,
            busy = busy,
            onStop = onStop,
            // Fin de la vidéo : l'hôte clôt la diffusion pour tout le monde.
            onEnded = { if (canTrigger) onStop() },
        )
    }
}

/** Contrôle hôte : « Démarrer pub » (sélecteur) ou « Arrêter » selon l'état. */
@Composable
private fun SponsorAdControl(
    active: Boolean,
    ads: List<SponsorAdVideo>,
    busy: Boolean,
    onLoadAds: () -> Unit,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        if (!active) {
            DMButton(s.sponsorStartAd, style = DMButtonStyle.SECONDARY, enabled = !busy) {
                onLoadAds()
                expanded = true
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (ads.isEmpty()) {
                    DropdownMenuItem(text = { Text(s.sponsorNoAds) }, enabled = false, onClick = {})
                } else {
                    ads.forEach { ad ->
                        DropdownMenuItem(
                            text = { Text("${ad.title} · ${ad.durationSeconds}s") },
                            onClick = {
                                expanded = false
                                onPlay(ad.id)
                            },
                        )
                    }
                }
            }
        } else {
            DMButton(s.sponsorStopAd, style = DMButtonStyle.DESTRUCTIVE, enabled = !busy) { onStop() }
        }
    }
}

/** Overlay vidéo plein écran + bannière « PUBLICITÉ » (+ bouton stop pour l'hôte). */
@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.SponsorAdOverlay(
    ad: SponsorAdVideo,
    canStop: Boolean,
    busy: Boolean,
    onStop: () -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(ad.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(ad.videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(ad.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
        )
        // Bannière « PUBLICITÉ ».
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .background(Color(0xFFF59E0B), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("📢 ${LocalStrings.current.sponsorAdBadge}", color = Color(0xFF3B2E00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        // Bouton d'arrêt pour l'hôte.
        if (canStop) {
            DMButton(
                LocalStrings.current.sponsorStopAd,
                style = DMButtonStyle.DESTRUCTIVE,
                enabled = !busy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) { onStop() }
        }
    }
}

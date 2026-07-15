package com.dualmusic.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * Écran d'un live (viewer) : vidéo plein écran + overlays chat / cadeaux / présence.
 *
 * Style TikTok : la vidéo occupe tout l'écran, les overlays flottent par-dessus. Le rendu
 * vidéo utilise `SurfaceViewRenderer` (décodage matériel MediaCodec, zero-copy vers Vulkan/GLES).
 *
 * @param hostUserId destinataire des cadeaux.
 * @param quickGiftId cadeau rapide de démonstration (sélection réelle via feature-gifts).
 */
@Composable
fun LiveRoomScreen(
    viewModel: LiveViewModel,
    hostUserId: String,
    quickGiftId: String,
    prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val videoTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    // Démarre/arrête le live avec le cycle de vie du composable.
    DisposableEffect(Unit) {
        viewModel.start(prewarmedToken)
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Couche vidéo
        val track = videoTrack
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        viewModel.media.room.initVideoRenderer(this)
                    }
                },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradé bas pour la lisibilité
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
            ),
        )

        // Cadeaux animés (halo GPU AGSL) : dernier cadeau reçu, centré.
        giftFeed.lastOrNull()?.let { gift ->
            androidx.compose.runtime.key(gift.id) {
                GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center))
            }
        }

        // Overlays
        Column(
            modifier = Modifier.fillMaxSize().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Compteur de spectateurs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White)
                    Text(" $viewerCount", color = Color.White)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                // Chat (6 dernières lignes)
                Column {
                    messages.takeLast(6).forEach { msg ->
                        Row {
                            Text(msg.authorName, color = colors.accent)
                            Text("  ${msg.content}", color = Color.White)
                        }
                    }
                }

                // Barre d'action : message + cadeau
                var draft by remember { mutableStateOf("") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Dis quelque chose…", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.sendMessage(draft); draft = ""
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .dmGlow()
                            .background(DualMusicTheme.gradients.primary, CircleShape)
                            .padding(DualMusicTheme.spacing.md),
                    ) {
                        Icon(
                            Icons.Filled.CardGiftcard,
                            contentDescription = "Envoyer un cadeau",
                            tint = Color.White,
                            modifier = Modifier.clickable { viewModel.sendGift(quickGiftId, hostUserId) },
                        )
                    }
                }
            }
        }
    }
}

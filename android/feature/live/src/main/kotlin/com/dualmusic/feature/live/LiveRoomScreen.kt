package com.dualmusic.feature.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * Écran d'un live : vidéo plein écran + overlays chat / cadeaux / présence (style TikTok).
 *
 * Deux modes, même overlay riche :
 *  - **spectateur** ([isHost] = false) : reçoit la vidéo distante ; peut commenter + offrir.
 *  - **hôte** ([isHost] = true) : publie sa caméra (après permission), voit l'aperçu local +
 *    les commentaires/cadeaux/spectateurs, et dispose des contrôles micro + Terminer.
 *
 * @param hostUserId destinataire des cadeaux.
 * @param quickGiftId cadeau rapide (spectateur).
 * @param isHost vrai pour l'artiste qui diffuse.
 * @param onEndLive appelé quand l'hôte termine (ferme l'écran plein écran).
 */
@Composable
fun LiveRoomScreen(
    viewModel: LiveViewModel,
    hostUserId: String,
    quickGiftId: String,
    prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
    isHost: Boolean = false,
    onEndLive: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val localTrack by viewModel.media.localVideoTrack.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current

    // Permissions caméra + micro (hôte uniquement).
    fun hasPerms() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(!isHost || hasPerms()) }
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (isHost && !granted) launcher.launch(perms) }

    // Démarre la vidéo/temps réel une fois la permission OK (immédiat pour un spectateur).
    DisposableEffect(granted) {
        if (granted) viewModel.start(prewarmedToken)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val track = if (isHost) localTrack else remoteTrack

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Écran de permission (hôte).
        if (isHost && !granted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(DualMusicTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.cameraPermissionNeeded, color = Color.White, fontWeight = FontWeight.Bold)
                Pill(color = colors.primary, onClick = { launcher.launch(perms) }) { Text(s.liveRetry, color = Color.White) }
                Pill(color = Color.White.copy(alpha = 0.2f), onClick = onEndLive) { Text(s.back, color = Color.White) }
            }
            return@Box
        }

        // Couche vidéo (locale si hôte, distante sinon).
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradé bas pour la lisibilité.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
            ),
        )

        // Cadeaux animés : dernier cadeau reçu, centré.
        giftFeed.lastOrNull()?.let { gift ->
            androidx.compose.runtime.key(gift.id) {
                GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center))
            }
        }

        // Overlays.
        Column(
            modifier = Modifier.fillMaxSize().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Haut : badge LIVE (gauche) + compteur de spectateurs (droite).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(colors.destructive, RoundedCornerShape(6.dp)).padding(horizontal = DualMusicTheme.spacing.sm, vertical = DualMusicTheme.spacing.xs),
                ) {
                    Text("🔴 LIVE", color = Color.White, fontWeight = FontWeight.Black)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White)
                    Text(" $viewerCount", color = Color.White)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                // Chat (6 dernières lignes).
                Column {
                    messages.takeLast(6).forEach { msg ->
                        Row {
                            Text(msg.authorName, color = colors.accent)
                            Text("  ${msg.content}", color = Color.White)
                        }
                    }
                }

                // Contrôles hôte : micro + Terminer.
                if (isHost) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        Pill(color = if (micOn) colors.primary else colors.destructive, onClick = { viewModel.toggleMic() }) {
                            Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = Color.White)
                            Text("  ${if (micOn) s.micOn else s.micOff}", color = Color.White)
                        }
                        Pill(color = colors.destructive, onClick = { viewModel.endLive(onEndLive) }) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                            Text("  ${s.endLive}", color = Color.White)
                        }
                    }
                }

                // Barre d'action : message (+ cadeau pour les spectateurs).
                var draft by remember { mutableStateOf("") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(s.saySomething, color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    if (!isHost) {
                        Box(
                            modifier = Modifier
                                .dmGlow()
                                .background(DualMusicTheme.gradients.primary, CircleShape)
                                .padding(DualMusicTheme.spacing.md)
                                .clickable { viewModel.sendGift(quickGiftId, hostUserId) },
                        ) {
                            Icon(Icons.Filled.CardGiftcard, contentDescription = s.sendGift, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Petite pastille cliquable colorée (contrôles overlay). */
@Composable
private fun Pill(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
    ) { content() }
}

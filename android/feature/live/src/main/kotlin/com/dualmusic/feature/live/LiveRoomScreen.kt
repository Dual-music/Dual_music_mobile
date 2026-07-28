package com.dualmusic.feature.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * Écran d'un live plein écran, style TikTok — parité avec le format mobile du web
 * (`MobileStreamOverlay`). Vidéo en fond, overlays flottants respectant les **zones sûres**
 * (barre d'état en haut, touches système + clavier en bas).
 *
 *  - **spectateur** : reçoit la vidéo ; commente, aime, réagit (emojis), offre, suit.
 *  - **hôte** : rejoint la salle, voit un bouton central « Démarrer le Live » ; une fois
 *    lancé, publie sa caméra et dispose des contrôles (micro, terminer) sur le rail gauche.
 *
 * @param hostUserId artiste hôte (cadeaux + suivre).
 * @param quickGiftId cadeau rapide (spectateur).
 * @param isHost vrai pour l'artiste qui diffuse.
 * @param onEndLive ferme l'écran plein écran (fin du live / refus permission).
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
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val heartTick by viewModel.heartTick.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current

    // Permissions caméra + micro (hôte).
    fun hasPerms() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(!isHost || hasPerms()) }
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    LaunchedEffect(Unit) { if (isHost && !granted) launcher.launch(perms) }

    DisposableEffect(granted) {
        if (granted) viewModel.start(prewarmedToken)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    var hideOverlay by remember { mutableStateOf(false) }
    var showReactionBar by remember { mutableStateOf(false) }
    var showComment by remember { mutableStateOf(false) }
    var broadcasting by remember { mutableStateOf(false) }

    val track = if (isHost) localTrack else remoteTrack

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Écran de permission (hôte).
        if (isHost && !granted) {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPaddingCompat().padding(DualMusicTheme.spacing.xl),
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

        // Dégradés haut + bas pour la lisibilité.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.55f))),
            ),
        )

        // Animations flottantes.
        giftFeed.lastOrNull()?.let { gift ->
            androidx.compose.runtime.key(gift.id) { GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
        }
        if (heartTick > 0L) {
            androidx.compose.runtime.key(heartTick) {
                GiftBurst(symbol = "❤️", modifier = Modifier.align(Alignment.BottomEnd).padding(DualMusicTheme.spacing.xl))
            }
        }
        emojiFeed.lastOrNull()?.let { fe ->
            androidx.compose.runtime.key(fe.id) {
                GiftBurst(symbol = fe.emoji, modifier = Modifier.align(Alignment.CenterEnd).padding(DualMusicTheme.spacing.xl))
            }
        }

        // Interface masquée : seul un bouton de restauration.
        if (hideOverlay) {
            RailButton(Icons.Filled.Visibility, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f), modifier = Modifier.align(Alignment.TopEnd).statusBarsPaddingCompat().padding(DualMusicTheme.spacing.md)) { hideOverlay = false }
            return@Box
        }

        // Centre : état « avant démarrage » (hôte).
        if (isHost && !broadcasting) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(DualMusicTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                Text(s.readyToStart, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(s.clickToStart, color = Color.White.copy(alpha = 0.8f))
                Pill(color = colors.primary, onClick = { viewModel.startBroadcast(); broadcasting = true }) {
                    Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White)
                    Text("  ${s.startLive}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Barre du haut (sous la barre d'état).
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPaddingCompat().padding(DualMusicTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                Chip(colors.destructive) { Text("🔴 LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                Chip(Color.Black.copy(alpha = 0.4f)) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(" $viewerCount", color = Color.White, fontSize = 12.sp)
                }
                Chip(Color.Black.copy(alpha = 0.4f)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(14.dp))
                    Text(" $likes", color = Color.White, fontSize = 12.sp)
                }
            }
            if (!isHost) {
                Chip(colors.primary, onClick = { viewModel.follow(hostUserId) }) {
                    Icon(Icons.Filled.PersonAddAlt1, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("  ${s.followAction}", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Rail vertical gauche.
        Column(
            modifier = Modifier.align(Alignment.CenterStart).statusBarsPaddingCompat().padding(start = DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            RailButton(Icons.Filled.VisibilityOff, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { hideOverlay = true }
            if (isHost) {
                RailButton(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, tint = Color.White, bg = if (micOn) colors.primary else colors.destructive) { viewModel.toggleMic() }
                RailButton(Icons.Filled.Close, tint = Color.White, bg = colors.destructive) { viewModel.endLive(onEndLive) }
            }
        }

        // Bas : chat + barre emojis + barre d'action (au-dessus des touches système / clavier).
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            // Chat (largeur ~60%).
            Column(modifier = Modifier.fillMaxWidth(0.62f)) {
                messages.takeLast(6).forEach { msg ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(msg.authorName, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("  ${msg.content}", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Barre d'emojis réactions (togglée par le bouton emoji).
            if (showReactionBar) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    ReactionEmojis.forEach { e ->
                        Box(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(DualMusicTheme.spacing.sm),
                        ) { Text(e) }
                    }
                }
            }

            // Barre d'action.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { showComment = true }.padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
                ) {
                    Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                RailButton(Icons.Filled.Favorite, tint = colors.destructive, bg = Color.Black.copy(alpha = 0.3f)) { viewModel.sendLike() }
                RailButton(Icons.Filled.EmojiEmotions, tint = Color.White, bg = Color.Black.copy(alpha = 0.3f)) { showReactionBar = !showReactionBar }
                if (!isHost) {
                    Box(
                        modifier = Modifier.dmGlow().size(52.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { viewModel.sendGift(quickGiftId, hostUserId) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CardGiftcard, contentDescription = s.sendGift, tint = Color.White) }
                }
            }
        }

        // Popup de commentaire (feuille du bas).
        if (showComment) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showComment = false })
            var draft by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(s.saySomething) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = ""; showComment = false }),
                    modifier = Modifier.weight(1f),
                )
                RailButton(Icons.Filled.Send, tint = Color.White, bg = colors.primary) {
                    viewModel.sendMessage(draft); draft = ""; showComment = false
                }
            }
        }
    }
}

/** Emojis de réaction (identiques au web). */
private val ReactionEmojis = listOf("❤️", "🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

/** Bouton circulaire du rail / de la barre d'action. */
@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.size(44.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Petite pastille d'info (badge). */
@Composable
private fun Chip(color: Color, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DualMusicTheme.spacing.sm, vertical = DualMusicTheme.spacing.xs),
    ) { content() }
}

/** Pastille cliquable (bouton). */
@Composable
private fun Pill(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = DualMusicTheme.spacing.lg, vertical = DualMusicTheme.spacing.sm),
    ) { content() }
}

/** Padding zone-sûre haut+bas (compat : évite un import direct si l'API diffère). */
private fun Modifier.systemBarsPaddingCompat(): Modifier = this.statusBarsPadding().navigationBarsPadding()

/** Padding zone-sûre haut. */
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.statusBarsPadding()

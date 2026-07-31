package com.dualmusic.feature.concert

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.overlay.FloatingReactionsLayer
import com.dualmusic.core.ui.overlay.TopDonorBubble
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer

/**
 * Room de concert (viewer + hôte artiste) — overlay façon TikTok comme le live :
 * vidéo plein écran, réactions montantes, chat défilant, cadeaux, classement donateurs.
 * Un billet est requis pour regarder un concert payant (sauf l'hôte).
 *
 * @param viewModel état + actions.
 * @param concertTitle titre affiché.
 * @param artistName nom de l'artiste (hôte).
 */
@Composable
fun ConcertRoomScreen(
    viewModel: ConcertRoomViewModel,
    concertTitle: String? = null,
    artistName: String? = null,
    onLeave: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val needsTicket by viewModel.needsTicket.collectAsStateWithLifecycle()
    val broadcasting by viewModel.broadcasting.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val localTrack by viewModel.media.localVideoTrack.collectAsStateWithLifecycle()
    val camOn by viewModel.media.camEnabled.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()

    val isHost by viewModel.isHost.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showGiftPanel by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    // Permission caméra/micro (hôte) avant de démarrer la diffusion.
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasPerms() = perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) viewModel.startBroadcast()
    }

    val track = if (isHost) localTrack else remoteTrack

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Vidéo.
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradés haut + bas.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.55f))),
            ),
        )

        // Cadeau reçu (burst) + réactions montantes (vues par tous), gâtés par reduceAnimations.
        if (!uiPrefs.reduceAnimations) {
            giftFeed.lastOrNull()?.let { gift ->
                key(gift.id) { com.dualmusic.core.ui.gifts.GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
            }
        }
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

        // Bulle du meilleur donateur (parité web), pilotée par les préférences visuelles.
        TopDonorBubble(donor = topDonor, mode = uiPrefs.topDonorMode, animation = uiPrefs.topDonorAnimation)

        // Barre du haut : LIVE + spectateurs + likes + partage.
        Row(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth().padding(DualMusicTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            Box(Modifier.background(colors.destructive, RoundedCornerShape(6.dp)).padding(horizontal = DualMusicTheme.spacing.sm, vertical = DualMusicTheme.spacing.xs)) {
                Text("🔴 LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Box(Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs)) {
                Text("👁 $viewerCount", color = Color.White, fontSize = 12.sp)
            }
            Box(Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs)) {
                Text("❤️ $likes", color = Color.White, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f))
            Box(
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).clickable {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s.shareLiveText) }
                    context.startActivity(Intent.createChooser(send, null))
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }

        // Bas : chat + barre de réactions + barre d'action.
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            val chatState = rememberLazyListState()
            androidx.compose.runtime.LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) chatState.animateScrollToItem(messages.size - 1)
            }
            LazyColumn(
                state = chatState,
                modifier = Modifier.fillMaxWidth(0.68f).height(180.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(messages) { msg ->
                    Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(msg.authorName, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("  ${msg.content}", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Barre de réactions.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendLike() },
                    contentAlignment = Alignment.Center,
                ) { Text("❤️", fontSize = 18.sp) }
                ConcertReactionEmojis.forEach { e ->
                    Box(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(e) }
                }
            }

            // Barre d'action : message + cadeau + classement.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = "" }),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(48.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPanel = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CardGiftcard, contentDescription = s.sendGift, tint = Color.White) }
                Box(
                    modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { showLeaderboard = true; viewModel.loadGiftLeaderboard() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFFC107)) }
            }
        }

        // Contrôles hôte (caméra/micro/flip/terminer) une fois en diffusion.
        if (isHost && broadcasting) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(end = DualMusicTheme.spacing.md, top = 80.dp),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                CircleBtn(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, Color.Black.copy(alpha = 0.4f)) { viewModel.toggleCamera() }
                CircleBtn(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, Color.Black.copy(alpha = 0.4f)) { viewModel.toggleMic() }
                CircleBtn(Icons.Filled.Cameraswitch, Color.Black.copy(alpha = 0.4f)) { viewModel.flipCamera() }
                CircleBtn(Icons.Filled.Close, colors.destructive) { viewModel.endConcert(onLeave) }
            }
        }

        // Bouton « Démarrer le concert » (hôte, avant diffusion).
        if (isHost && !broadcasting) {
            Box(
                modifier = Modifier.align(Alignment.Center).background(DualMusicTheme.gradients.primary, RoundedCornerShape(999.dp))
                    .clickable { if (hasPerms()) viewModel.startBroadcast() else launcher.launch(perms) }
                    .padding(horizontal = DualMusicTheme.spacing.xl, vertical = DualMusicTheme.spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White)
                    Text("  ${s.startConcert}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Gate billetterie : concert payant, pas encore de billet.
        if (needsTicket) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                    Text(concertTitle ?: s.screenConcerts, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(s.ticketRequired, color = colors.mutedForeground)
                    Box(
                        modifier = Modifier.background(DualMusicTheme.gradients.primary, RoundedCornerShape(999.dp)).clickable { viewModel.buyTicket() }
                            .padding(horizontal = DualMusicTheme.spacing.xl, vertical = DualMusicTheme.spacing.md),
                    ) { Text("${s.buyTicket} · ${viewModel.ticketPriceValue.toInt()} ${s.credits}", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Panneau cadeaux (Mes cadeaux / Boutique).
        if (showGiftPanel) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showGiftPanel = false })
            var giftShop by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.55f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    TabPill(s.myGifts, !giftShop) { giftShop = false }
                    TabPill(s.giftShop, giftShop) { giftShop = true }
                }
                Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    if (!giftShop) {
                        if (inventory.isEmpty()) Text(s.noGiftsBuyInShop, color = colors.mutedForeground, fontSize = 13.sp)
                        inventory.forEach { g ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.sendGift(g.id, viewModel.hostUserId); showGiftPanel = false }.padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${g.imageUrl ?: "🎁"}  ${g.name}", color = colors.foreground)
                                Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        giftCatalog.forEach { gift ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.purchaseGift(gift.id) }.padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${gift.emoji ?: "🎁"}  ${gift.name}", color = colors.foreground)
                                Text("${gift.price.toInt()} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Classement des donateurs.
        if (showLeaderboard) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showLeaderboard = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.5f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🏆 ${s.donors}", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (leaderboard.isEmpty()) {
                    Text(s.emptyRanking, color = colors.mutedForeground)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        leaderboard.forEachIndexed { i, entry ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${concertMedal(i)} ${entry.displayName}", color = colors.foreground)
                                Text("${entry.value} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier.background(if (selected) colors.primary else Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = DualMusicTheme.spacing.lg, vertical = DualMusicTheme.spacing.sm),
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

/** Emojis de réaction (identiques au live/duel/web). */
private val ConcertReactionEmojis = listOf("🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

private fun concertMedal(index: Int): String = when (index) {
    0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}."
}

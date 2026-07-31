package com.dualmusic.feature.live

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.media.VideoFilterPresets
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    liveTitle: String? = null,
    artistName: String? = null,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val localTrack by viewModel.media.localVideoTrack.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val camOn by viewModel.media.camEnabled.collectAsStateWithLifecycle()
    val activeFilter by viewModel.media.activeFilter.collectAsStateWithLifecycle()
    val backgroundMode by viewModel.media.backgroundMode.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    // Préférences visuelles (parité web) : réduire les animations / carte top donateur.
    val uiPrefs by com.dualmusic.core.ui.prefs.UiPreferencesStore.state.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val myJoinRequestId by viewModel.myJoinRequestId.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val acceptedGuests by viewModel.acceptedGuests.collectAsStateWithLifecycle()
    val guestTimers by viewModel.guestTimers.collectAsStateWithLifecycle()
    val liveWaiting by viewModel.liveWaiting.collectAsStateWithLifecycle()
    val remoteVideos by viewModel.media.remoteVideos.collectAsStateWithLifecycle()
    val isGuestAccepted by viewModel.isGuestAccepted.collectAsStateWithLifecycle()
    val giftLeaderboard by viewModel.giftLeaderboard.collectAsStateWithLifecycle()
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
    var showGiftPanel by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showDedication by remember { mutableStateOf(false) }
    var showGuests by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var broadcasting by remember { mutableStateOf(false) }
    var pendingStage by remember { mutableStateOf(false) }

    // Invité accepté : monte sur scène une fois la permission caméra/micro accordée.
    LaunchedEffect(granted, pendingStage) {
        if (pendingStage && granted) {
            viewModel.goOnStage(); broadcasting = true; pendingStage = false
        }
    }

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

        // Spectateur : l'hôte a quitté sans terminer → bannière « live en attente ».
        if (liveWaiting && !isHost) {
            Box(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(DualMusicTheme.spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text(s.liveWaiting, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Cadeau reçu : burst central (halo GPU). Masqué si « Réduire les animations ».
        if (!uiPrefs.reduceAnimations) {
            giftFeed.lastOrNull()?.let { gift ->
                androidx.compose.runtime.key(gift.id) { GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
            }
        }
        // Réactions (cœurs + emojis) : montent bas→haut à droite, façon TikTok — vues par tous.
        // Désactivées si « Réduire les animations » (les compteurs continuent d'incrémenter).
        if (!uiPrefs.reduceAnimations) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 140.dp, end = DualMusicTheme.spacing.md)
                    .size(64.dp, 460.dp),
            ) {
                emojiFeed.forEach { fe ->
                    androidx.compose.runtime.key(fe.id) { FloatingReaction(fe.emoji) }
                }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                // Partage du live (tous).
                Chip(Color.Black.copy(alpha = 0.4f), onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, s.shareLiveText)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                if (!isHost) {
                    Chip(colors.primary, onClick = { viewModel.follow(hostUserId) }) {
                        Icon(Icons.Filled.PersonAddAlt1, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("  ${s.followAction}", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Vignettes multi-caméra (autres participants sur scène) — sous la barre du haut.
        // Cap à 3 visibles + tuile « +N » pour rester dans l'écran (pas de débordement).
        val thumbs = if (isHost) remoteVideos else (remoteVideos.drop(1) + listOfNotNull(if (broadcasting) localTrack else null))
        if (thumbs.isNotEmpty()) {
            val maxThumbs = 3
            val visible = thumbs.take(maxThumbs)
            val overflow = thumbs.size - visible.size
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPaddingCompat()
                    .padding(top = 56.dp, end = DualMusicTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                visible.forEach { t ->
                    androidx.compose.runtime.key(t) {
                        Box(modifier = Modifier.size(72.dp, 96.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                                update = { r -> t.addRenderer(r) },
                            )
                        }
                    }
                }
                if (overflow > 0) {
                    Box(
                        modifier = Modifier.size(72.dp, 96.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+$overflow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
        }

        // Rail vertical gauche — remonté sous la barre du haut pour libérer le bas (chat qui défile).
        Column(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPaddingCompat().padding(start = DualMusicTheme.spacing.md, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            RailButton(Icons.Filled.VisibilityOff, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { hideOverlay = true }
            if (isHost) {
                // Contrôles du live (caméra/micro/flip/terminer) — visibles une fois lancé.
                if (broadcasting) {
                    RailButton(Icons.Filled.Settings, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showSettings = true }
                }
                // Invités : badge ROUGE = demandes en attente (haut-droite), badge VERT = invités
                // actifs (bas-gauche), deux couleurs distinctes comme le web.
                Box {
                    RailButton(Icons.Filled.PersonAddAlt1, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showGuests = true; viewModel.loadJoinRequests() }
                    if (joinRequests.isNotEmpty()) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(colors.destructive, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${joinRequests.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (acceptedGuests.isNotEmpty()) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomStart).size(18.dp).background(Color(0xFF22C55E), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${acceptedGuests.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                // Infos artiste + description du live.
                RailButton(Icons.Filled.Description, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showDescription = true }
                // Effets : filtres couleur + fond (flou/image), en direct, publiés à tous.
                if (broadcasting) {
                    val effectsOn = activeFilter != "none" || backgroundMode != "none"
                    RailButton(Icons.Filled.AutoAwesome, tint = Color.White, bg = if (effectsOn) colors.primary else Color.Black.copy(alpha = 0.4f)) { showEffects = true }
                }
                // (« Terminer le live » est dans la feuille de contrôles ⚙️, pas ici.)
            } else {
                // Lever la main (demander à rejoindre / annuler).
                RailButton(
                    if (myJoinRequestId != null) Icons.Filled.Schedule else Icons.Filled.PanTool,
                    tint = Color.White,
                    bg = if (myJoinRequestId != null) colors.accent else Color.Black.copy(alpha = 0.4f),
                ) { if (myJoinRequestId == null) viewModel.requestJoin() else viewModel.cancelJoin() }
                RailButton(Icons.Filled.Campaign, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showDedication = true }
                RailButton(Icons.Filled.Flag, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showReport = true }
                // Invité sur scène : ses propres contrôles (micro/caméra/flip) via la feuille ⚙️.
                if (broadcasting) {
                    RailButton(Icons.Filled.Settings, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showSettings = true }
                }
            }
            // Quitter le live SANS y mettre fin — disponible pour TOUT LE MONDE. Si c'est l'hôte
            // qui quitte, le live passe « en attente » pour tous les spectateurs.
            RailButton(Icons.Filled.Logout, tint = Color.White, bg = colors.destructive) {
                if (isHost) viewModel.broadcastLiveWaiting()
                onEndLive()
            }
        }

        // Bas : chat + barre emojis + barre d'action (au-dessus des touches système / clavier).
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            // Invité accepté : monter sur scène (publier sa caméra).
            if (!isHost && isGuestAccepted && !broadcasting) {
                Pill(color = colors.primary, onClick = {
                    if (hasPerms()) { viewModel.goOnStage(); broadcasting = true }
                    else { pendingStage = true; launcher.launch(perms) }
                }) {
                    Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White)
                    Text("  ${s.goOnStage}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Chat défilant (largeur ~68%) — les messages montent du bas vers le haut façon TikTok,
            // le plus récent reste visible en bas, auto-défilement à chaque nouveau message.
            val chatState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) chatState.animateScrollToItem(messages.size - 1)
            }
            LazyColumn(
                state = chatState,
                modifier = Modifier.fillMaxWidth(0.68f).height(200.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
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
                    Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RailButton(Icons.Filled.Favorite, tint = colors.destructive, bg = Color.Black.copy(alpha = 0.3f)) { viewModel.sendLike() }
                RailButton(Icons.Filled.EmojiEmotions, tint = Color.White, bg = Color.Black.copy(alpha = 0.3f)) { showReactionBar = !showReactionBar }
                // Cadeau (tous) — l'hôte peut aussi offrir à un invité sur scène.
                Box(
                    modifier = Modifier.dmGlow().size(52.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPanel = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CardGiftcard, contentDescription = s.sendGift, tint = Color.White) }
                // Classement des donateurs.
                RailButton(Icons.Filled.EmojiEvents, tint = Color(0xFFFFC107), bg = Color.Black.copy(alpha = 0.3f)) { showLeaderboard = true; viewModel.loadGiftLeaderboard() }
            }
        }

        // Popup de commentaire (feuille du bas) — auto-focus + clavier au-dessus (adjustResize).
        if (showComment) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showComment = false })
            var draft by remember { mutableStateOf("") }
            val commentFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { commentFocus.requestFocus() }
            var showChatEmoji by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                // Sélecteur d'emojis : ajoute l'emoji au texte du commentaire (comme le web).
                if (showChatEmoji) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                    ) {
                        ChatComposeEmojis.forEach { e ->
                            Text(e, fontSize = 22.sp, modifier = Modifier.clickable { draft += e })
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    RailButton(Icons.Filled.EmojiEmotions, tint = Color.White, bg = Color.Black.copy(alpha = 0.3f)) { showChatEmoji = !showChatEmoji }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(s.saySomething) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = ""; showComment = false }),
                        modifier = Modifier.weight(1f).focusRequester(commentFocus),
                    )
                    RailButton(Icons.Filled.Send, tint = Color.White, bg = colors.primary) {
                        viewModel.sendMessage(draft); draft = ""; showComment = false
                    }
                }
            }
        }

        // Panneau cadeaux (feuille du bas) : onglet « Mes cadeaux » (envoi depuis l'inventaire,
        // parité web) + onglet « Boutique » (achat au catalogue → crédite l'inventaire).
        if (showGiftPanel) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showGiftPanel = false })
            var giftShop by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                // Sélecteur d'onglet segmenté.
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Pill(color = if (!giftShop) colors.primary else Color.Black.copy(alpha = 0.25f), onClick = { giftShop = false }) {
                        Text(s.myGifts, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Pill(color = if (giftShop) colors.primary else Color.Black.copy(alpha = 0.25f), onClick = { giftShop = true }) {
                        Text(s.giftShop, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    if (!giftShop) {
                        // Mes cadeaux : cliquer envoie au host (consomme 1 exemplaire).
                        if (inventory.isEmpty()) {
                            Text(s.noGiftsBuyInShop, color = colors.mutedForeground, fontSize = 13.sp)
                        }
                        inventory.forEach { g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.sendGift(g.id, hostUserId); showGiftPanel = false }
                                    .padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${g.imageUrl ?: "🎁"}  ${g.name}", color = colors.foreground)
                                Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Boutique : marketplace en grille 2 colonnes (tuile emoji + nom + prix +
                        // bouton acheter), comme la vraie page boutique web. Clic = achète 1 (débit wallet).
                        giftCatalog.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                                pair.forEach { gift ->
                                    val owned = inventory.firstOrNull { it.id == gift.id }?.quantity ?: 0
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                            .clickable { viewModel.purchaseGift(gift.id) }
                                            .padding(DualMusicTheme.spacing.sm),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(72.dp).background(DualMusicTheme.gradients.hero, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(gift.emoji ?: "🎁", fontSize = 40.sp)
                                            if (owned > 0) {
                                                Box(
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color(0xFF22C55E), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                                                ) { Text("×$owned", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                        Text(gift.name, color = colors.foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${gift.price.toInt()} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Pill(color = colors.primary, onClick = { viewModel.purchaseGift(gift.id) }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                            Text("  ${s.buyGift}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Feuille de signalement (motifs préréglés).
        if (showReport) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showReport = false })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(s.reportAction, color = colors.foreground, fontWeight = FontWeight.Bold)
                listOf(s.reportInappropriate, s.reportHarassment, s.reportSpam, s.reportViolence).forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.report(reason); showReport = false }
                            .padding(DualMusicTheme.spacing.md),
                    ) { Text(reason, color = colors.foreground) }
                }
            }
        }

        // Feuille de dédicace (message dédié).
        if (showDedication) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showDedication = false })
            var dedic by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(s.dedication, color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(s.dedicationHint, color = colors.mutedForeground)
                OutlinedTextField(
                    value = dedic,
                    onValueChange = { dedic = it },
                    placeholder = { Text(s.saySomething) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Pill(color = colors.primary, onClick = { viewModel.dedicate(dedic); showDedication = false }) {
                    Text(s.send, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Feuille des invités (hôte) : demandes en attente (accepter/refuser) + invités actifs
        // (couper le micro, accorder un temps de parole, retirer). Parité écran mobile web.
        if (showGuests) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showGuests = false })
            var mutedGuests by remember { mutableStateOf(setOf<String>()) }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(s.guests, color = colors.foreground, fontWeight = FontWeight.Bold)
                if (joinRequests.isEmpty() && acceptedGuests.isEmpty()) {
                    Text(s.noGuestRequests, color = colors.mutedForeground)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    // Demandes en attente.
                    if (joinRequests.isNotEmpty()) {
                        Text("${s.pendingRequests} (${joinRequests.size})", color = colors.destructive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    joinRequests.forEach { req ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(DualMusicTheme.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✋ ${req.displayName}", color = colors.foreground, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                                RailButton(Icons.Filled.Check, tint = Color.White, bg = colors.primary) { viewModel.respondJoin(req.id, true) }
                                RailButton(Icons.Filled.Close, tint = Color.White, bg = colors.destructive) { viewModel.respondJoin(req.id, false) }
                            }
                        }
                    }
                    // Invités actifs (sur scène).
                    if (acceptedGuests.isNotEmpty()) {
                        Text("${s.activeGuests} (${acceptedGuests.size})", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    acceptedGuests.forEach { g ->
                        val remaining = guestTimers[g.userId]
                        val muted = mutedGuests.contains(g.userId)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(DualMusicTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🎤 ${g.displayName}", color = colors.foreground, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                                    RailButton(
                                        if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                        tint = Color.White,
                                        bg = if (muted) colors.destructive else Color.Black.copy(alpha = 0.4f),
                                    ) {
                                        mutedGuests = if (muted) mutedGuests - g.userId else mutedGuests + g.userId
                                        viewModel.toggleGuestMic(g.userId, !muted)
                                    }
                                    RailButton(Icons.Filled.Schedule, tint = Color.White, bg = colors.accent) { viewModel.grantGuestTimer(g.userId, g.displayName, 120) }
                                    RailButton(Icons.Filled.PersonRemove, tint = Color.White, bg = colors.destructive) { viewModel.kickGuest(g.id, g.userId) }
                                }
                            }
                            if (remaining != null && remaining > 0) {
                                Text(
                                    "${s.timeRemaining} : ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}",
                                    color = colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Feuille du classement des donateurs.
        if (showLeaderboard) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showLeaderboard = false })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🏆 ${s.topDonors}", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (giftLeaderboard.isEmpty()) {
                    Text(s.noGuestRequests, color = colors.mutedForeground)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    giftLeaderboard.forEachIndexed { i, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(DualMusicTheme.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${medalFor(i)} ${entry.displayName}", color = colors.foreground, modifier = Modifier.weight(1f))
                            Text("${entry.value}", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Feuille « Contrôles du live » (hôte, en direct) : caméra / flip / micro / pause / stop.
        if (showSettings) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showSettings = false })
            val paused = !camOn && !micOn
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎛️ ${s.liveControls}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Pill(color = if (camOn) colors.primary else colors.destructive, modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleCamera() }) {
                    Icon(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = null, tint = Color.White)
                    Text("  ${if (camOn) s.cameraOn else s.cameraOff}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Pill(color = Color.Black.copy(alpha = 0.35f), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.switchCamera() }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = null, tint = Color.White)
                    Text("  ${s.flipCamera}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Pill(color = if (micOn) colors.primary else colors.destructive, modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleMic() }) {
                    Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = Color.White)
                    Text("  ${if (micOn) s.micOn else s.micOff}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Pill(color = Color(0xFFEAB308), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setPaused(!paused) }) {
                    Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White)
                    Text("  ${if (paused) s.resume else s.pause}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                // « Terminer le live » : réservé à l'hôte (met fin au direct pour tout le monde).
                if (isHost) {
                    Pill(color = colors.destructive, modifier = Modifier.fillMaxWidth(), onClick = { showSettings = false; viewModel.endLive(onEndLive) }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                        Text("  ${s.endLive}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Feuille « Effets » : filtres couleur + fond (flou/image), en direct, publiés à tous.
        if (showEffects) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showEffects = false })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎬 ${s.videoFilters}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(s.visibleToAll, color = colors.mutedForeground, fontSize = 11.sp)
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    // Grille des filtres couleur (4 par ligne).
                    VideoFilterPresets.all.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                            row.forEach { f ->
                                val selected = activeFilter == f.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.media.setColorFilter(f.id, f.matrix) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(f.emoji, fontSize = 24.sp) }
                                    Text(filterLabel(f.id), color = if (selected) colors.primary else colors.mutedForeground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(4 - row.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                    // Fond du direct.
                    Text(s.background, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        // Aucun.
                        EffectBgTile(label = s.none, brush = null, selected = backgroundMode == "none", modifier = Modifier.weight(1f)) {
                            viewModel.media.clearBackground()
                        }
                        // Flou.
                        EffectBgTile(label = s.blur, brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))), selected = backgroundMode == "blur", modifier = Modifier.weight(1f)) {
                            viewModel.media.setBackgroundBlur()
                        }
                        // Images de fond (dégradés).
                        BackgroundPresets.take(2).forEach { bg ->
                            EffectBgTile(label = bg.label, brush = Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom))), selected = false, modifier = Modifier.weight(1f)) {
                                viewModel.media.setBackgroundImage(makeGradientBitmap(bg.top, bg.bottom))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        BackgroundPresets.drop(2).forEach { bg ->
                            EffectBgTile(label = bg.label, brush = Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom))), selected = false, modifier = Modifier.weight(1f)) {
                                viewModel.media.setBackgroundImage(makeGradientBitmap(bg.top, bg.bottom))
                            }
                        }
                        repeat((4 - BackgroundPresets.drop(2).size).coerceAtLeast(0)) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Feuille « Description » : infos artiste + titre du live.
        if (showDescription) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showDescription = false })
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
                    .padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text(artistName ?: s.artists, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(liveTitle ?: s.liveActive, color = colors.mutedForeground)
            }
        }
    }
}

/** Médaille pour le podium, rang sinon. */
private fun medalFor(index: Int): String = when (index) {
    0 -> "🥇"
    1 -> "🥈"
    2 -> "🥉"
    else -> "${index + 1}."
}

/** Emojis de réaction (identiques au web). */
private val ReactionEmojis = listOf("❤️", "🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

/** Emojis à insérer dans le texte d'un commentaire (identiques au web). */
private val ChatComposeEmojis = listOf("😀", "😂", "❤️", "🔥", "👏", "🎵", "🎤", "💯", "😍", "🙌", "💪", "🎉", "😮", "👀", "✨", "🥳")

/** Libellé FR d'un filtre couleur (mêmes noms que la grille web). */
private fun filterLabel(id: String): String = when (id) {
    "beauty" -> "Beauté"
    "glow" -> "Lumineux"
    "warm" -> "Chaud"
    "cool" -> "Froid"
    "vivid" -> "Vif"
    "vintage" -> "Vintage"
    "noir" -> "N&B"
    "studio" -> "Studio"
    "neon" -> "Néon"
    else -> "Aucun"
}

/** Fond dégradé préréglé (couleurs ARGB). */
private data class BgPreset(val label: String, val top: Int, val bottom: Int)

private val BackgroundPresets = listOf(
    BgPreset("Sunset", 0xFFFF7E5F.toInt(), 0xFF7B2FF7.toInt()),
    BgPreset("Océan", 0xFF2193B0.toInt(), 0xFF0F2027.toInt()),
    BgPreset("Studio", 0xFF434343.toInt(), 0xFF000000.toInt()),
    BgPreset("Néon", 0xFFEC38BC.toInt(), 0xFF7303C0.toInt()),
)

/** Génère un bitmap dégradé vertical (fond virtuel). */
private fun makeGradientBitmap(top: Int, bottom: Int): android.graphics.Bitmap {
    val w = 720
    val h = 1280
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bmp
}

/** Tuile de sélection de fond (dégradé ou « aucun »). */
@Composable
private fun EffectBgTile(
    label: String,
    brush: androidx.compose.ui.graphics.Brush?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(brush ?: androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = 0.25f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White) }
        Text(label, color = if (selected) colors.primary else colors.mutedForeground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Réaction flottante façon TikTok : l'emoji monte du bas vers le haut avec une légère
 * dérive horizontale, grossit puis s'estompe. Vu par tous les spectateurs (les réactions
 * transitent par le relais broadcast). Chaque emoji du flux est rendu avec sa propre clé,
 * donc l'animation démarre à l'entrée en composition et s'arrête quand le VM le retire.
 */
@Composable
private fun BoxScope.FloatingReaction(symbol: String) {
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val drift = remember { (-28..28).random() }
    val startScale = remember { 0.7f + (0..30).random() / 100f }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { rise.animateTo(1f, tween(2400, easing = LinearEasing)) }
            launch {
                fade.animateTo(1f, tween(300))
                fade.animateTo(0f, tween(2100))
            }
        }
    }
    Text(
        text = symbol,
        fontSize = (26 + startScale * 8).sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset { IntOffset((drift * rise.value).toInt(), -(rise.value * 430).toInt()) }
            .alpha(fade.value.coerceIn(0f, 1f)),
    )
}

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
private fun Pill(color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(color, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = DualMusicTheme.spacing.lg, vertical = DualMusicTheme.spacing.md),
    ) { content() }
}

/** Padding zone-sûre haut+bas (compat : évite un import direct si l'API diffère). */
private fun Modifier.systemBarsPaddingCompat(): Modifier = this.statusBarsPadding().navigationBarsPadding()

/** Padding zone-sûre haut. */
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.statusBarsPadding()

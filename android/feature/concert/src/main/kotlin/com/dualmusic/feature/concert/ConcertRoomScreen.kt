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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.dualmusic.feature.sponsor.SponsorAdLayer
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Emojis rapides pour le commentaire (parité web/live, voir LiveRoomScreen.kt#ChatComposeEmojis). */
private val ChatComposeEmojis = listOf("😀", "😂", "❤️", "🔥", "👏", "🎵", "🎤", "💯", "😍", "🙌", "💪", "🎉", "😮", "👀", "✨", "🥳")

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
    onOpenArtist: (String) -> Unit = {},
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
    val bannedUserIds by viewModel.bannedUserIds.collectAsStateWithLifecycle()
    val iAmBanned by viewModel.iAmBanned.collectAsStateWithLifecycle()
    val chatEnabled by viewModel.chatEnabled.collectAsStateWithLifecycle()
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val viewers by viewModel.viewers.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val isModerator = myUserId != null && moderators.any { it.userId == myUserId }
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recPaused by viewModel.recordingCtl.paused.collectAsStateWithLifecycle()
    val recFinalizing by viewModel.recordingCtl.finalizing.collectAsStateWithLifecycle()
    val recFailed by viewModel.recordingCtl.failed.collectAsStateWithLifecycle()
    val recError by viewModel.recordingCtl.error.collectAsStateWithLifecycle()
    val recAccumulatedSeconds by viewModel.recordingCtl.accumulatedSeconds.collectAsStateWithLifecycle()
    val recRunStartedAt by viewModel.recordingCtl.runStartedAt.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val sponsorAd by viewModel.sponsor.activeAd.collectAsStateWithLifecycle()
    val sponsorAds by viewModel.sponsor.ads.collectAsStateWithLifecycle()
    val sponsorBusy by viewModel.sponsor.busy.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showChatEmoji by remember { mutableStateOf(false) }
    var showGiftPanel by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var banTarget by remember { mutableStateOf<ConcertChatMessage?>(null) }
    var replyingTo by remember { mutableStateOf<ConcertChatMessage?>(null) }
    var showCommentPopup by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showModerators by remember { mutableStateOf(false) }
    var showRecordingSheet by remember { mutableStateOf(false) }
    var showCancelRecordingConfirm by remember { mutableStateOf(false) }
    var hideOverlay by remember { mutableStateOf(false) }
    var showAdPicker by remember { mutableStateOf(false) }
    var showReactionBar by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    // Carte « cadeau reçu » glissante (fil) — état hissé AVANT le gate `hideOverlay` (plus bas) :
    // sinon masquer/démasquer l'œil détruisait puis recréait ce sous-arbre Compose, ce qui
    // relançait le LaunchedEffect(lastGift?.id) et REJOUAIT l'animation du DERNIER cadeau reçu
    // (même si aucun nouveau cadeau n'avait été envoyé entre-temps).
    val lastGift = giftFeed.lastOrNull()
    var shownGift by remember { mutableStateOf<ConcertGift?>(null) }
    var giftVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(lastGift?.id) {
        if (lastGift != null) { shownGift = lastGift; giftVisible = true; kotlinx.coroutines.delay(3200); giftVisible = false }
    }

    // Retour visible sur un échec d'action d'enregistrement — sans ça, un clic sur Pause/
    // Reprendre/Sauvegarder qui échoue ne montrait RIEN : le bouton semblait « ne pas prendre ».
    fun showRecordingError(message: String) {
        android.widget.Toast.makeText(context, "Enregistrement : $message", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Un enregistrement récent a échoué côté serveur (ex. Chrome headless de l'egress qui n'a
    // jamais démarré) : sans ça, la sauvegarde restait bloquée sur « Finalisation… » puis
    // revenait silencieusement à l'état inactif, sans aucune explication.
    androidx.compose.runtime.LaunchedEffect(recFailed) {
        if (recFailed) showRecordingError(recError ?: "échec, aucun segment récupérable")
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
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        viewModel.media.room.initVideoRenderer(this)
                        // Cadre ENTIER visible (jamais rogné) — parité avec ce que montre le PC :
                        // sans ça, l'écran (bien plus étroit/haut) rognait le haut/bas du corps.
                        setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    }
                },
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

        // (Le burst central de cadeau est retiré — parité live : la carte glissante à gauche,
        // au-dessus des messages, suffit déjà — la doublure au centre de l'écran était redondante.)
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

        // Interface masquée (œil du rail) : seul un bouton de restauration reste visible.
        if (hideOverlay) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(DualMusicTheme.spacing.md)
                    .size(44.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).clickable { hideOverlay = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Visibility, contentDescription = "Réafficher", tint = Color.White, modifier = Modifier.size(22.dp)) }
            return@Box
        }

        // Barre du haut unifiée : badge CONCERT (fond rouge, remplace LIVE) + spectateurs + likes +
        // partage + signaler + Quitter (rouge, icône différente du X « Terminer » du rail hôte —
        // ce dernier MET FIN au concert pour tous, celui-ci quitte juste l'écran sans y toucher).
        com.dualmusic.core.ui.live.LiveHeader(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(DualMusicTheme.spacing.md),
            eventLabel = "",
            badgeText = "CONCERT",
            viewerCount = viewerCount,
            likes = likes,
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s.shareLiveText) }
                context.startActivity(Intent.createChooser(send, null))
            },
            onReport = { showReport = true },
            onQuit = onLeave,
        )

        // Nom du meilleur donateur qui défile en continu (parité live) — remplace la bulle statique.
        topDonor?.let { d ->
            Row(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(top = 56.dp, start = DualMusicTheme.spacing.md, end = DualMusicTheme.spacing.xl)
                    .fillMaxWidth().clip(RoundedCornerShape(999.dp))
                    .background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConcertScrollingLabel("👑  ${d.name}  ·  ${d.amount} 🎁", Modifier.weight(1f))
            }
        }

        // --- Signalement du direct (parité web LiveReportButton) ---
        if (showReport) {
            com.dualmusic.core.ui.live.ReportDialog(
                onDismiss = { showReport = false },
                onSubmit = { reason ->
                    viewModel.report(reason)
                    android.widget.Toast.makeText(context, s.reportSent, android.widget.Toast.LENGTH_SHORT).show()
                },
            )
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
                items(messages.filter { it.userId !in bannedUserIds }) { msg ->
                    val parent = msg.parentId?.let { pid -> messages.find { it.id == pid } }
                    // Bannissable = je suis l'hôte artiste OU un modérateur désigné, et ce n'est pas moi.
                    val canBan = (isHost || isModerator) && msg.userId != viewModel.hostUserId
                    Row(
                        verticalAlignment = Alignment.Top,
                        // Tap sur le message → y répondre (parité duel).
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { replyingTo = msg; showCommentPopup = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        // Avatar rond — tap sur la PHOTO = bannir (hôte seulement).
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.55f))
                                .then(if (canBan) Modifier.clickable { banTarget = msg } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avatar = msg.user?.avatarUrl
                            if (!avatar.isNullOrBlank()) {
                                com.dualmusic.core.ui.components.DMRemoteImage(url = avatar, contentDescription = msg.authorName, modifier = Modifier.fillMaxSize(), fallbackEmoji = "👤")
                            } else {
                                Text(msg.authorName.take(1).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            if (parent != null) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.width(2.dp).height(13.dp).background(colors.mutedForeground))
                                    Text("  ↩ ${parent.authorName} : ${parent.content}", color = colors.mutedForeground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(msg.authorName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(msg.content, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Barre d'emojis réactions (togglée par le bouton emoji — parité live).
            if (showReactionBar) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    ConcertReactionEmojis.forEach { e ->
                        Box(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(DualMusicTheme.spacing.sm),
                        ) { Text(e) }
                    }
                }
            }

            // Barre d'action (parité EXACTE live) : pastille message (ouvre le popup) + j'aime +
            // bouton emoji (toggle la barre de réactions) + cadeau + classement.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .clickable(enabled = chatEnabled) { showCommentPopup = true }
                        .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
                ) {
                    if (!chatEnabled) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Text("  Chat désactivé", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape).clickable { viewModel.sendLike() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Favorite, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(20.dp)) }
                Box(
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape).clickable { showReactionBar = !showReactionBar },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.EmojiEmotions, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
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

        // Rail vertical GAUCHE (parité live/duel) : œil « masquer tout » pour TOUT LE MONDE, puis
        // Réglages (regroupe caméra/micro/flip/pause/enregistrement/terminer, parité live),
        // filtres et pub — hôte. Décalé SOUS le nom du meilleur donateur (qui défile juste sous
        // la barre du haut, même top que l'ancien rail) pour ne plus s'y mélanger.
        Column(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = DualMusicTheme.spacing.md, top = 104.dp),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            CircleBtn(Icons.Filled.VisibilityOff, Color.Black.copy(alpha = 0.4f)) { hideOverlay = true }
            // Modérateurs (hôte uniquement) : accessible même AVANT le démarrage de la diffusion
            // (le direct/chat existe déjà dès l'entrée dans la room) — contrairement aux autres
            // icônes du rail qui ne servent qu'une fois en direct.
            if (isHost) {
                CircleBtn(Icons.Filled.Shield, Color.Black.copy(alpha = 0.4f)) { showModerators = true }
            }
            if (isHost && broadcasting) {
                CircleBtn(Icons.Filled.Settings, Color.Black.copy(alpha = 0.4f)) { showSettings = true }
                CircleBtn(Icons.Filled.AutoAwesome, Color.Black.copy(alpha = 0.4f)) { showFilters = true }
                // Pub sponsor : icône du rail (au lieu du bouton texte qui recouvrait la ligne de
                // message) — lance/arrête, l'overlay pub + son arrêt restent gérés par SponsorAdLayer.
                CircleBtn(Icons.Filled.Campaign, Color.Black.copy(alpha = 0.4f)) {
                    if (sponsorAd != null) viewModel.sponsor.stop() else { viewModel.sponsor.loadAds(); showAdPicker = true }
                }
                // Enregistrement : icône DÉDIÉE (distincte du bouton « Pause » caméra/micro des
                // Réglages, avec lequel elle était confondue quand les deux étaient mélangés dans
                // la même feuille) — pastille rouge/orange dès qu'un segment tourne ou est en
                // pause, la feuille complète (chrono + Pause/Reprendre/Annuler/Sauvegarder)
                // s'ouvre au tap. Masquée si l'admin a coupé l'enregistrement pour les concerts.
                if (recMode == "manual") {
                    Box {
                        CircleBtn(Icons.Filled.FiberManualRecord, Color.Black.copy(alpha = 0.4f)) { showRecordingSheet = true }
                        com.dualmusic.feature.sponsor.RecordingRailBadge(
                            active = recActive, paused = recPaused,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                    }
                }
            }
        }

        // Réglages du concert (hôte) : caméra/micro/flip/terminer regroupés dans une feuille —
        // parité live, évite un rail à rallonge qui chevauchait le nom du meilleur donateur.
        if (showSettings) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showSettings = false })
            val paused = !camOn && !micOn
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎛️ Réglages du concert", color = colors.foreground, fontWeight = FontWeight.Bold)
                SettingsRow(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (camOn) "Caméra activée" else "Caméra coupée", if (camOn) colors.primary else colors.destructive) { viewModel.toggleCamera() }
                SettingsRow(Icons.Filled.Cameraswitch, "Retourner la caméra", Color.Black.copy(alpha = 0.35f)) { viewModel.flipCamera() }
                SettingsRow(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (micOn) "Micro activé" else "Micro coupé", if (micOn) colors.primary else colors.destructive) { viewModel.toggleMic() }
                SettingsRow(if (paused) Icons.Filled.Podcasts else Icons.Filled.Pause, if (paused) "Reprendre caméra/micro" else "Couper caméra & micro", Color(0xFFEAB308)) { viewModel.togglePause() }
                // Chat activé/désactivé pour TOUS — réservé à l'hôte (jamais aux modérateurs désignés).
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.15f)).padding(horizontal = DualMusicTheme.spacing.md, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (chatEnabled) "Chat activé" else "Chat désactivé", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Switch(checked = chatEnabled, onCheckedChange = { viewModel.toggleChat(it) })
                }
                // « Terminer le concert » : réservé à l'hôte, met fin au concert pour TOUS —
                // différent du Quitter (Logout) de la barre du haut qui ne fait que fermer l'écran.
                SettingsRow(Icons.Filled.Close, "Terminer le concert", colors.destructive) { showSettings = false; viewModel.endConcert(onLeave) }
            }
        }

        // Modérateurs désignés (hôte) : liste + révocation, et picker des spectateurs connectés.
        if (showModerators) {
            EventModeratorsDialog(
                isHost = isHost,
                moderators = moderators,
                viewers = viewers,
                onLoadViewers = { viewModel.loadViewers() },
                onAppoint = { viewModel.appointModerator(it) },
                onRevoke = { viewModel.revokeModerator(it) },
                onDismiss = { showModerators = false },
            )
        }

        // Feuille DÉDIÉE à l'enregistrement (hôte) : chrono + Pause/Reprendre/Annuler/Sauvegarder,
        // séparée des Réglages pour ne plus se mélanger avec le bouton « Pause » caméra/micro.
        if (showRecordingSheet) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showRecordingSheet = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🔴 Enregistrement du concert", color = colors.foreground, fontWeight = FontWeight.Bold)
                com.dualmusic.feature.sponsor.RecordingSessionControls(
                    mode = recMode,
                    active = recActive,
                    paused = recPaused,
                    finalizing = recFinalizing,
                    accumulatedSeconds = recAccumulatedSeconds,
                    runStartedAt = recRunStartedAt,
                    busy = recBusy,
                    onStart = { viewModel.recordingCtl.start(onError = ::showRecordingError) },
                    onPause = { viewModel.recordingCtl.pause(onError = ::showRecordingError) },
                    onResume = { viewModel.recordingCtl.resume(onError = ::showRecordingError) },
                    onCancel = { showCancelRecordingConfirm = true },
                    onSave = { viewModel.recordingCtl.save(onError = ::showRecordingError); showRecordingSheet = false },
                )
            }
        }

        // Confirmation d'annulation d'enregistrement (destructif : rien n'est recollé/publié).
        if (showCancelRecordingConfirm) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { showCancelRecordingConfirm = false })
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp))
                    .background(colors.background).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Text("Annuler l'enregistrement ?", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Tout ce qui a été enregistré jusqu'ici sera définitivement perdu — aucun replay ne sera créé.",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Box(modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp)).clickable { showCancelRecordingConfirm = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Retour", color = colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.weight(1f).background(colors.destructive, RoundedCornerShape(999.dp)).clickable { viewModel.recordingCtl.cancel(onError = ::showRecordingError); showCancelRecordingConfirm = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Annuler l'enregistrement", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sélecteur de pub sponsor (parité duel) — ouvert par l'icône 📢 du rail.
        if (showAdPicker) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showAdPicker = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("📢 Lancer une pub", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (sponsorAds.isEmpty()) Text("Aucune pub disponible.", color = colors.mutedForeground, fontSize = 13.sp)
                sponsorAds.forEach { ad ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.15f))
                            .clickable { viewModel.sponsor.play(ad.id); showAdPicker = false }.padding(DualMusicTheme.spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ad.title, color = colors.foreground)
                        Text("${ad.durationSeconds}s", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
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
                        // Mes cadeaux : icône ✉️ dédiée pour envoyer (le reste de la ligne n'envoie
                        // plus rien au tap — évite la confusion « je clique pour voir » vs « pour
                        // envoyer », parité live).
                        inventory.forEach { g ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${g.imageUrl ?: "🎁"}  ${g.name}", color = colors.foreground)
                                    Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier.size(38.dp).background(colors.primary, CircleShape)
                                        .clickable { viewModel.sendGift(g.id, viewModel.hostUserId); showGiftPanel = false },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Send, contentDescription = s.sendGift, tint = Color.White, modifier = Modifier.size(17.dp)) }
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

        // Diffusion pub sponsor : overlay vidéo pour tous ; le déclencheur est l'icône 📢 du rail
        // gauche (showTriggerButton = false) — évite le bouton texte qui recouvrait le chat.
        SponsorAdLayer(
            activeAd = sponsorAd,
            canTrigger = isHost,
            showTriggerButton = false,
            ads = sponsorAds,
            busy = sponsorBusy,
            onLoadAds = { viewModel.sponsor.loadAds() },
            onPlay = { viewModel.sponsor.play(it) },
            onStop = { viewModel.sponsor.stop() },
        )

        // Chip artiste (spectateur) → profil public de l'hôte (parité duel « tap nom »).
        if (!isHost && viewModel.hostUserId.isNotBlank()) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 56.dp, start = DualMusicTheme.spacing.md)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .clickable { onOpenArtist(viewModel.hostUserId) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text("🎤 ${artistName?.takeIf { it.isNotBlank() } ?: "Voir l'artiste"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }

        // Carte « cadeau reçu » glissante (fil) — parité duel/live. Seule animation de réception
        // (pas de bannière en plus, ni de doublon plein écran) ; état hissé plus haut (voir note).
        AnimatedVisibility(
            visible = giftVisible && !uiPrefs.reduceAnimations,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 320.dp),
        ) { shownGift?.let { ConcertGiftReceivedCard(it) } }

        // Confirmation de bannissement d'un spectateur (hôte).
        banTarget?.let { target ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { banTarget = null })
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp))
                    .background(colors.background).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Text("🚫 Bannir ${target.authorName} ?", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Ce spectateur ne pourra plus écrire dans ce direct ni le rejoindre, et ses messages seront masqués pour tous.",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    Box(modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp)).clickable { banTarget = null }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Annuler", color = colors.foreground, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.weight(1f).background(colors.destructive, RoundedCornerShape(999.dp)).clickable { viewModel.banUser(target.userId, target.content.take(200)); banTarget = null }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("Bannir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Popup commentaire / réponse : cite le message parent + envoie (parité duel).
        if (showCommentPopup) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCommentPopup = false; replyingTo = null })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background)
                    .navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (replyingTo != null) "Répondre" else "Commenter", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEmotions, contentDescription = "Emojis", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showChatEmoji = !showChatEmoji })
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showCommentPopup = false; replyingTo = null })
                    }
                }
                // Sélecteur d'emojis : ajoute l'emoji au texte du commentaire (parité web/live).
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
                replyingTo?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(30.dp).background(colors.accent))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("↩ ${r.authorName}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(r.content, color = colors.mutedForeground, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Filled.Close, contentDescription = "Annuler la réponse", tint = colors.mutedForeground, modifier = Modifier.size(18.dp).clickable { replyingTo = null })
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        if (!iAmBanned && chatEnabled && draft.isNotBlank()) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }
                        showCommentPopup = false; replyingTo = null
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().background(DualMusicTheme.gradients.primary, RoundedCornerShape(999.dp))
                        .clickable {
                            if (!iAmBanned && chatEnabled && draft.isNotBlank()) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }
                            showCommentPopup = false; replyingTo = null
                        }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (replyingTo != null) "Répondre" else "Envoyer", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }

        // Sélecteur de filtres vidéo (hôte) — parité duel.
        if (showFilters && broadcasting) {
            Box(Modifier.fillMaxSize().clickable { showFilters = false })
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(end = 70.dp)
                    .width(240.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Filtres", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dualmusic.core.media.VideoFilterPresets.all.forEach { f ->
                        val selected = activeFilter == f.id
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.45f))
                                .clickable { viewModel.setColorFilter(f.id, f.matrix); showFilters = false },
                            contentAlignment = Alignment.Center,
                        ) { Text(f.emoji, fontSize = 18.sp) }
                    }
                }
            }
        }

        // Écran de blocage : l'artiste m'a banni → je ne peux plus participer ni rejoindre.
        if (iAmBanned) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding().navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(64.dp))
                Text("Accès bloqué", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "L'artiste vous a banni de ce concert. Vous ne pouvez plus y participer ni le rejoindre.",
                    color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(0.7f).background(DualMusicTheme.gradients.primary, RoundedCornerShape(999.dp)).clickable(onClick = onLeave).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Quitter", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Nom du meilleur donateur qui défile en continu (parité live/duel `ScrollingLabel`). */
@Composable
private fun ConcertScrollingLabel(text: String, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "mq")
    val p by infinite.animateFloat(
        initialValue = 1f, targetValue = -1.3f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "mqx",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        Text(
            text, color = Color(0xFFFFD54A), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer { translationX = p * w },
        )
    }
}

/** Carte glissante « cadeau reçu » (image réelle + valeur) — parité duel. */
@Composable
private fun ConcertGiftReceivedCard(g: ConcertGift) {
    val infinite = rememberInfiniteTransition(label = "gift")
    val scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "giftScale",
    )
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xF2FF4FA3), Color(0xF27C3AED))))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.dualmusic.core.ui.components.DMRemoteImage(
            url = g.image?.takeIf { it.startsWith("http") },
            contentDescription = g.name,
            modifier = Modifier.size(34.dp).scale(scale),
            fallbackEmoji = g.image?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "🎁",
        )
        Column {
            Text(g.name ?: "Cadeau reçu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(
                "${g.fromUserName ?: "Quelqu'un"} · +${g.value.toInt()} crédits",
                color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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

/** Ligne pleine largeur de la feuille « Réglages du concert » (icône + libellé, fond coloré). */
@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text("  $label", color = Color.White, fontWeight = FontWeight.Bold)
    }
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

/**
 * Modérateurs désignés du concert (jusqu'à [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS])
 * — mêmes pouvoirs de bannissement/masquage que l'hôte, JAMAIS le toggle chat (réservé à l'hôte).
 * Parité web (panneau modération) : liste des modérateurs + révocation ; côté hôte uniquement,
 * un picker des spectateurs actuellement connectés pour en désigner de nouveaux.
 */
@Composable
private fun EventModeratorsDialog(
    isHost: Boolean,
    moderators: List<com.dualmusic.domain.moderation.EventModerator>,
    viewers: List<com.dualmusic.domain.model.DisplayProfile>,
    onLoadViewers: () -> Unit,
    onAppoint: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    androidx.compose.runtime.LaunchedEffect(Unit) { if (isHost) onLoadViewers() }
    val moderatorIds = remember(moderators) { moderators.map { it.userId }.toSet() }
    val atLimit = moderators.size >= com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛡️ Modérateurs") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("Modérateurs désignés (${moderators.size}/${com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS})", color = colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (moderators.isEmpty()) {
                    Text("Aucun modérateur désigné.", color = colors.mutedForeground, fontSize = 13.sp)
                } else {
                    moderators.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.displayName, color = colors.foreground, fontSize = 13.sp)
                            if (isHost) {
                                Icon(
                                    Icons.Filled.Close, contentDescription = "Révoquer", tint = colors.destructive,
                                    modifier = Modifier.size(18.dp).clickable { onRevoke(m.userId) },
                                )
                            }
                        }
                    }
                }
                if (isHost) {
                    Text("Spectateurs connectés", color = colors.mutedForeground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val eligible = viewers.filter { it.id !in moderatorIds }
                    if (eligible.isEmpty()) {
                        Text("Aucun spectateur éligible.", color = colors.mutedForeground, fontSize = 13.sp)
                    } else {
                        eligible.forEach { v ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v.displayName, color = colors.foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                DMButtonTextOnly(text = "Nommer", enabled = !atLimit) { onAppoint(v.id) }
                            }
                        }
                    }
                    if (atLimit) Text("Limite de ${com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS} modérateurs atteinte.", color = colors.mutedForeground, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Box(modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)) { Text("Fermer", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
    )
}

/** Petit bouton texte pilule (« Nommer ») — évite d'importer `DMButton` juste pour ce cas ponctuel. */
@Composable
private fun DMButtonTextOnly(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (enabled) colors.primary else colors.mutedForeground.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

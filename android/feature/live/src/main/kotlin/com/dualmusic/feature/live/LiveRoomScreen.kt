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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Switch
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
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.media.VideoFilterPresets
import com.dualmusic.core.ui.theme.DualMusicTheme
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign

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
 * @param isHost vrai pour l'artiste qui diffuse.
 * @param onEndLive ferme l'écran plein écran (fin du live / refus permission).
 */
@Composable
fun LiveRoomScreen(
    viewModel: LiveViewModel,
    hostUserId: String,
    prewarmedToken: com.dualmusic.domain.media.LiveKitToken? = null,
    isHost: Boolean = false,
    onEndLive: () -> Unit = {},
    liveTitle: String? = null,
    artistName: String? = null,
    onOpenArtist: (String) -> Unit = {},
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    // Ma diffusion : room principale si je suis l'hôte, ma room d'invité dédiée sinon (parité
    // web — voir LiveViewModel.selfMedia/guestMedia). Piloté depuis CE client, jamais `media`
    // pour un invité (qui reste connecté à `media` en simple spectateur pendant qu'il diffuse).
    val selfMedia = if (isHost) viewModel.media else viewModel.guestMedia
    val remoteTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
    val localTrack by selfMedia.localVideoTrack.collectAsStateWithLifecycle()
    val micOn by selfMedia.micEnabled.collectAsStateWithLifecycle()
    val camOn by selfMedia.camEnabled.collectAsStateWithLifecycle()
    val activeFilter by selfMedia.activeFilter.collectAsStateWithLifecycle()
    val backgroundMode by selfMedia.backgroundMode.collectAsStateWithLifecycle()
    // Pistes des AUTRES invités actifs (chacun dans sa propre room `live-guest-<liveId>-<id>`) —
    // valeur `null` = invité présent mais caméra coupée (case gardée en placeholder, jamais retirée).
    val guestVideos by viewModel.guestVideos.collectAsStateWithLifecycle()
    // Client LiveKit par invité (room de rendu dédiée + état micro distant pour le badge).
    val guestClients by viewModel.guestClients.collectAsStateWithLifecycle()
    // Mon id (invité) — pour afficher le chrono sur MA PROPRE case (self-preview) quand je suis
    // l'invité minuté, exactement comme tout le monde le voit sur la case des autres.
    val myUserId by viewModel.myUserIdFlow.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    // Préférences visuelles (parité web) : réduire les animations / carte top donateur.
    val uiPrefs by com.dualmusic.core.ui.prefs.UiPreferencesStore.state.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val myJoinRequestId by viewModel.myJoinRequestId.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val acceptedGuests by viewModel.acceptedGuests.collectAsStateWithLifecycle()
    val guestTimers by viewModel.guestTimers.collectAsStateWithLifecycle()
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recPaused by viewModel.recordingCtl.paused.collectAsStateWithLifecycle()
    val recFinalizing by viewModel.recordingCtl.finalizing.collectAsStateWithLifecycle()
    val recFailed by viewModel.recordingCtl.failed.collectAsStateWithLifecycle()
    val recError by viewModel.recordingCtl.error.collectAsStateWithLifecycle()
    val recAccumulatedSeconds by viewModel.recordingCtl.accumulatedSeconds.collectAsStateWithLifecycle()
    val recRunStartedAt by viewModel.recordingCtl.runStartedAt.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val liveWaiting by viewModel.liveWaiting.collectAsStateWithLifecycle()
    val remoteVideos by viewModel.media.remoteVideos.collectAsStateWithLifecycle()
    val isGuestAccepted by viewModel.isGuestAccepted.collectAsStateWithLifecycle()
    val giftLeaderboard by viewModel.giftLeaderboard.collectAsStateWithLifecycle()
    val bannedUserIds by viewModel.bannedUserIds.collectAsStateWithLifecycle()
    val iAmBanned by viewModel.iAmBanned.collectAsStateWithLifecycle()
    val giftReceived by viewModel.giftReceived.collectAsStateWithLifecycle()
    val dedications by viewModel.dedications.collectAsStateWithLifecycle()
    val dedicationHistory by viewModel.dedicationHistory.collectAsStateWithLifecycle()
    val dedicationMinPrice by viewModel.dedicationMinPrice.collectAsStateWithLifecycle()
    val dedicationFeedback by viewModel.dedicationFeedback.collectAsStateWithLifecycle()
    val liveAllowsDedications by viewModel.liveAllowsDedications.collectAsStateWithLifecycle()
    val liveAllowGuests by viewModel.liveAllowGuests.collectAsStateWithLifecycle()
    // Chat on/off (hôte) + modération déléguée : modérateurs désignés + suis-je l'un d'eux.
    val liveChatEnabled by viewModel.liveChatEnabled.collectAsStateWithLifecycle()
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val isModerator = myUserId != null && moderators.any { it.userId == myUserId }
    var showModeratorsDialog by remember { mutableStateOf(false) }
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
    var showDedicationRequests by remember { mutableStateOf(false) }
    var showGuests by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showRecordingSheet by remember { mutableStateOf(false) }
    var showCancelRecordingConfirm by remember { mutableStateOf(false) }

    // Retour visible sur un échec d'action d'enregistrement — sans ça, un clic sur Pause/
    // Reprendre/Sauvegarder qui échoue ne montrait RIEN : le bouton semblait « ne pas prendre ».
    fun showRecordingError(message: String) {
        android.widget.Toast.makeText(context, "Enregistrement : $message", android.widget.Toast.LENGTH_SHORT).show()
    }
    androidx.compose.runtime.LaunchedEffect(recFailed) {
        if (recFailed) showRecordingError(recError ?: "échec, aucun segment récupérable")
    }
    var broadcasting by remember { mutableStateOf(false) }
    var pendingStage by remember { mutableStateOf(false) }
    var banTarget by remember { mutableStateOf<LiveChatMessage?>(null) }
    var replyingTo by remember { mutableStateOf<LiveChatMessage?>(null) }
    // Case agrandie choisie par un tap sur une vignette (focus local, comme le duel). Un invité
    // caméra coupée n'a pas de VideoTrack → focus par id séparé (`focusedGuestId`), mutuellement
    // exclusif avec `focusedTrack`, pour pouvoir quand même l'agrandir (placeholder plein écran).
    var focusedTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var focusedGuestId by remember { mutableStateOf<String?>(null) }

    // Auto-effacement de la bannière « cadeau reçu ».
    LaunchedEffect(giftReceived) {
        if (giftReceived != null) { kotlinx.coroutines.delay(3500); viewModel.clearGiftReceived() }
    }
    // Confirmation (ou échec) de l'envoi d'une dédicace — le fan doit savoir si ça a marché.
    LaunchedEffect(dedicationFeedback) {
        dedicationFeedback?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearDedicationFeedback()
        }
    }

    // Invité accepté : monte sur scène AUTOMATIQUEMENT (plus de bouton « rejoindre le direct ») —
    // sa case apparaît immédiatement pour tout le monde, micro/caméra coupés par défaut (voir
    // `goOnStage`) ; c'est depuis SA PROPRE case qu'il décide ensuite d'activer l'un ou l'autre.
    LaunchedEffect(isGuestAccepted) {
        if (!isHost && isGuestAccepted && !broadcasting) {
            if (hasPerms()) { viewModel.goOnStage(); broadcasting = true }
            else { pendingStage = true; launcher.launch(perms) }
        }
    }
    // Suite du flux ci-dessus : une fois la permission caméra/micro accordée, monte sur scène.
    LaunchedEffect(granted, pendingStage) {
        if (pendingStage && granted) {
            viewModel.goOnStage(); broadcasting = true; pendingStage = false
        }
    }
    // Invité retiré (kick artiste) ou qui descend : il ne diffuse plus → réinitialise l'état publieur.
    LaunchedEffect(isGuestAccepted) {
        if (!isHost && !isGuestAccepted) broadcasting = false
    }

    // Pistes "brutes" (hôte + ma propre diffusion) — les invités sont gérés séparément ci-dessous
    // (case toujours présente, même caméra coupée, avec nom + badges + chrono).
    val allTracks = remoteVideos + listOfNotNull(if (broadcasting) localTrack else null)
    // Invité actuellement agrandi (s'il l'est toujours), pour résoudre la case principale.
    val focusedGuest = focusedGuestId?.let { gid -> acceptedGuests.find { it.userId == gid } }
    // Case principale : invité épinglé > focus choisi au tap (s'il est encore publié) > défaut
    // (ma caméra si hôte, sinon TOUJOURS l'artiste — jamais de repli sur `allTracks.firstOrNull()`,
    // qui pouvait tomber sur MA PROPRE caméra une fois sur scène : je ne dois jamais me retrouver
    // affiché à la place de l'artiste par défaut — `null` → écran d'attente, correct).
    val defaultMain = if (isHost) localTrack else remoteTrack
    val track = when {
        focusedGuest != null -> guestVideos[focusedGuest.userId]
        else -> focusedTrack?.takeIf { f -> allTracks.any { it === f } } ?: defaultMain
    }
    // Vignettes : pistes brutes restantes (ex. l'hôte, quand un invité est agrandi) + TOUS les
    // invités actifs sauf celui déjà agrandi — jamais retirés pour caméra coupée (placeholder).
    // Exclut TOUJOURS mon propre id : je n'ai pas de client de VUE pour ma propre room d'invité
    // (voir reconcileGuestSubscriptions, qui m'exclut du même calcul côté ViewModel) → sans ce
    // filtre, une case fantôme (jamais reliée à un vrai client, badge/vidéo cassés) s'affichait
    // en plus de mon vrai aperçu (self-preview, ci-dessous).
    // `localTrack` est EXCLU du pool générique : mon aperçu passe par `SelfPreview`, qui — comme
    // les vraies cases invité — reste affiché même caméra coupée (placeholder), au lieu de
    // disparaître purement et simplement quand `localTrack` est `null` (caméra fermée par défaut
    // à l'entrée en scène → sans ça, l'invité ne voyait littéralement jamais sa propre case).
    val plainThumbs = allTracks.filter { it !== track && it !== localTrack }
    val guestThumbs = acceptedGuests.filter { it.userId != focusedGuest?.userId && it.userId != myUserId }
    // Pour TOUT LE MONDE (hôte compris) : dès que je regarde autre chose que ma propre caméra
    // (ex. l'artiste épingle un invité), MA case doit rester visible en vignette — avant ce fix,
    // elle était exclue du pool générique (`plainThumbs` ci-dessus) mais son remplacement
    // (`SelfPreview`) ne s'affichait que pour un invité, jamais pour l'hôte → sa propre case
    // disparaissait purement et simplement dès qu'il épinglait quelqu'un d'autre.
    val showSelfPreview = broadcasting && track !== localTrack
    val thumbEntries: List<LiveThumbEntry> =
        plainThumbs.map { LiveThumbEntry.Plain(it) } +
            guestThumbs.map { g -> LiveThumbEntry.Guest(g.userId, g.displayName, g.user?.avatarUrl, guestVideos[g.userId], guestClients[g.userId]) } +
            (if (showSelfPreview) listOf(LiveThumbEntry.SelfPreview) else emptyList())
    val thumbs = thumbEntries

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

        // Couche vidéo (locale si hôte, distante sinon, ou invité épinglé en plein écran).
        if (focusedGuest != null) {
            GuestTileContent(
                track = track,
                client = guestClients[focusedGuest.userId],
                label = focusedGuest.displayName,
                avatarUrl = focusedGuest.user?.avatarUrl,
                showBadge = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (track != null) {
            LiveVideoView(track = track, room = viewModel.media.room, modifier = Modifier.fillMaxSize())
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

        // (Le burst central de cadeau a été retiré : la carte glissante à gauche, au-dessus des
        // messages, suffit déjà — la doublure au centre de l'écran était redondante.)
        // Réactions (cœurs + emojis) : montent bas→haut à droite, façon TikTok — vues par tous.
        // Désactivées si « Réduire les animations » (les compteurs continuent d'incrémenter).
        // Décalées à gauche quand des vignettes d'invités sont affichées (même coin bas-droite,
        // voir plus bas) pour ne jamais les chevaucher.
        if (!uiPrefs.reduceAnimations) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 140.dp, end = if (thumbs.isNotEmpty()) 88.dp else DualMusicTheme.spacing.md)
                    .size(64.dp, 460.dp),
            ) {
                emojiFeed.forEach { fe ->
                    androidx.compose.runtime.key(fe.id) { FloatingReaction(fe.emoji) }
                }
            }
        }

        // (La bulle statique « meilleur donateur » a été retirée : le nom défile déjà juste sous
        // la barre du haut — l'afficher UNE 2e fois, statique, juste en dessous était redondant.)

        // (Pub sponsor retirée pour les lives : un live spontané n'a pas besoin de pub — le
        // bouton « Démarrer pub » prenait la place de la ligne de commentaires côté hôte.)

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
                // Nom de l'artiste (spectateur) → profil public (parité duel « tap nom »).
                if (!isHost) {
                    Chip(Color.Black.copy(alpha = 0.4f), onClick = { onOpenArtist(hostUserId) }) {
                        Text("🎤 ${artistName?.takeIf { it.isNotBlank() } ?: s.artists}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                    }
                }
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
                // Quitter le live (déplacé du rail vertical, trop chargé côté hôte, jusqu'ici) —
                // juste après le partage. Ne met PAS fin au direct (voir le rail, comportement inchangé).
                Chip(colors.destructive, onClick = {
                    if (isHost) viewModel.broadcastLiveWaiting()
                    onEndLive()
                }) {
                    Icon(Icons.Filled.Logout, contentDescription = s.endLive, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                if (!isHost) {
                    // Suivre : icône seule (le libellé faisait déborder la barre → « Suivre » vertical).
                    Chip(colors.primary, onClick = { viewModel.follow(hostUserId) }) {
                        Icon(Icons.Filled.PersonAddAlt1, contentDescription = s.followAction, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Nom du meilleur donateur qui défile (parité duel) — sous la barre du haut, décalé pour
        // ne pas chevaucher le rail gauche (qui démarre au même niveau, top=56.dp).
        topDonor?.let { d ->
            Row(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPaddingCompat()
                    .padding(top = 56.dp, start = 64.dp, end = DualMusicTheme.spacing.xl)
                    .fillMaxWidth().clip(RoundedCornerShape(999.dp))
                    .background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrollingLabel("👑  ${d.name}  ·  ${d.amount} 🎁", Modifier.weight(1f))
            }
        }

        // Chrono du temps de parole, JUSTE SOUS le meilleur donateur, quand la case AGRANDIE est
        // celle d'un invité minuté — soi-même (self-preview en main) ou un autre invité épinglé.
        // Tout le monde le voit : artiste, invité concerné, spectateurs (parité duel).
        val mainGuestUserId = focusedGuest?.userId ?: (if (!isHost && broadcasting && track === localTrack) myUserId else null)
        mainGuestUserId?.let { uid ->
            guestTimers[uid]?.takeIf { it > 0 }?.let { remaining ->
                GuestChronoChip(
                    remaining,
                    Modifier.align(Alignment.TopStart).statusBarsPaddingCompat()
                        .padding(top = if (topDonor != null) 90.dp else 56.dp, start = 64.dp),
                )
            }
        }

        // Vignettes multi-caméra (autres participants sur scène) — parité duel : empilées à droite,
        // en bas, juste AU-DESSUS de la barre de saisie (jamais sous la barre du haut, où elles
        // chevauchaient le rail/la marquee). Le chat occupe la partie GAUCHE du bas de l'écran
        // (fillMaxWidth(0.68f)) → aucun mélange avec les messages. TAP = agrandir la vignette.
        // Cap à 3 visibles + tuile « +N » pour rester dans l'écran (pas de débordement).
        if (thumbs.isNotEmpty()) {
            val maxThumbs = 3
            val visible = thumbs.take(maxThumbs)
            val overflow = thumbs.size - visible.size
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                visible.forEach { entry ->
                    when (entry) {
                        is LiveThumbEntry.Plain -> {
                            androidx.compose.runtime.key(entry.track) {
                                Box(
                                    modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                                        .clickable { focusedTrack = entry.track; focusedGuestId = null },
                                ) {
                                    LiveVideoView(track = entry.track, room = viewModel.media.room, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        LiveThumbEntry.SelfPreview -> {
                            androidx.compose.runtime.key("self") {
                                Box(
                                    modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { focusedTrack = localTrack; focusedGuestId = null },
                                ) {
                                    // Toujours présente tant que je suis sur scène — même caméra coupée
                                    // (placeholder avec MON avatar/nom), là où avant elle disparaissait
                                    // purement et simplement (caméra fermée par défaut à l'entrée en scène).
                                    GuestTileContent(
                                        track = localTrack,
                                        client = selfMedia,
                                        // Affiché pour TOUT LE MONDE (hôte compris, voir `showSelfPreview`)
                                        // dès qu'on ne regarde pas sa propre caméra → pas besoin de mon
                                        // propre avatar, "Vous" suffit à s'identifier.
                                        label = "Vous",
                                        avatarUrl = null,
                                        micOnOverride = micOn,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    myUserId?.let { uid ->
                                        guestTimers[uid]?.takeIf { it > 0 }?.let { remaining ->
                                            GuestChronoChip(remaining, Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
                                        }
                                    }
                                }
                            }
                        }
                        is LiveThumbEntry.Guest -> {
                            androidx.compose.runtime.key(entry.userId) {
                                Box(
                                    modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { focusedGuestId = entry.userId; focusedTrack = null },
                                ) {
                                    GuestTileContent(
                                        track = entry.track,
                                        client = entry.client,
                                        label = entry.label,
                                        avatarUrl = entry.avatarUrl,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Chrono du temps de parole accordé par l'artiste (parité duel).
                                    guestTimers[entry.userId]?.takeIf { it > 0 }?.let { remaining ->
                                        GuestChronoChip(remaining, Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
                                    }
                                }
                            }
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
                // Enregistrement : icône DÉDIÉE (distincte du bouton « Pause » caméra/micro des
                // Réglages, avec lequel elle était confondue quand les deux étaient mélangés dans
                // la même feuille — même correctif que le concert) — pastille rouge/orange dès
                // qu'un segment tourne ou est en pause. Masquée si l'admin a coupé l'enregistrement
                // pour les lives.
                if (broadcasting && recMode == "manual") {
                    Box {
                        RailButton(Icons.Filled.FiberManualRecord, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showRecordingSheet = true }
                        com.dualmusic.feature.sponsor.RecordingRailBadge(
                            active = recActive, paused = recPaused,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                    }
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
                // Dédicaces reçues (en attente) — badge = nombre à traiter.
                Box {
                    RailButton(Icons.Filled.Campaign, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showDedicationRequests = true; viewModel.loadDedications() }
                    if (dedications.isNotEmpty()) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(colors.destructive, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${dedications.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
                // Lever la main (demander à rejoindre / annuler) — masqué une fois accepté comme
                // invité (il n'a plus de demande à annuler ; il descend du direct via ⚙️ à la place),
                // ou si l'artiste a désactivé les demandes d'invité pour ce live.
                if (!isGuestAccepted && liveAllowGuests) {
                    RailButton(
                        if (myJoinRequestId != null) Icons.Filled.Schedule else Icons.Filled.PanTool,
                        tint = Color.White,
                        bg = if (myJoinRequestId != null) colors.accent else Color.Black.copy(alpha = 0.4f),
                    ) { if (myJoinRequestId == null) viewModel.requestJoin() else viewModel.cancelJoin() }
                }
                // Dédicace : masquée si l'artiste a désactivé les dédicaces pour ce live.
                if (liveAllowsDedications) {
                    RailButton(Icons.Filled.Campaign, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showDedication = true }
                }
                RailButton(Icons.Filled.Flag, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showReport = true }
                // Invité sur scène : ses propres contrôles (micro/caméra/flip) via la feuille ⚙️.
                if (broadcasting) {
                    RailButton(Icons.Filled.Settings, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showSettings = true }
                }
            }
            // Modérateurs désignés (ban/masquer message) — visible de l'hôte ET des modérateurs
            // eux-mêmes (pour qu'ils voient qui d'autre a ce pouvoir).
            if (isHost || isModerator) {
                RailButton(Icons.Filled.Groups, tint = Color.White, bg = Color.Black.copy(alpha = 0.4f)) { showModeratorsDialog = true }
            }
            // (Quitter le live est désormais dans la barre du haut, juste après le partage —
            // trop d'icônes empilées verticalement côté hôte sinon.)
        }

        // Bas : chat + barre emojis + barre d'action (au-dessus des touches système / clavier).
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            // (Plus de bouton « rejoindre le direct » — l'invité monte sur scène automatiquement
            // dès l'acceptation, voir le LaunchedEffect(isGuestAccepted) plus haut.)

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
                items(messages.filter { it.userId !in bannedUserIds }) { msg ->
                    val parent = msg.parentId?.let { pid -> messages.find { it.id == pid } }
                    // Bannissable = je suis l'hôte (ou un modérateur désigné) et ce n'est pas l'hôte lui-même.
                    val canBan = (isHost || isModerator) && msg.userId != hostUserId
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { replyingTo = msg; showComment = true }
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
                                    Box(Modifier.size(2.dp, 13.dp).background(colors.mutedForeground))
                                    Text("  ↩ ${parent.authorName} : ${parent.content}", color = colors.mutedForeground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(msg.authorName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(msg.content, color = Color.White, fontSize = 12.sp)
                        }
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
                // Si l'hôte a désactivé le chat pour tous, la saisie est bloquée (parité web).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .then(if (liveChatEnabled) Modifier.clickable { showComment = true } else Modifier)
                        .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
                ) {
                    if (!liveChatEnabled) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Text("  Chat désactivé", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(s.saySomething, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
        // --- Feuilles/dialogues extraits en fonctions locales : une méthode Compose (Kotlin) ne
        // doit pas dépasser 64Ko de bytecode JVM — au-delà, `MethodTooLargeException` au build.
        // Chaque feuille devient sa PROPRE méthode (capture les variables du parent par closure,
        // aucun paramètre à redéclarer) au lieu d'être inlinée dans LiveRoomScreen lui-même.
        @Composable fun CommentSheet() {
        if (showComment) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showComment = false; replyingTo = null })
            var draft by remember { mutableStateOf("") }
            val commentFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { commentFocus.requestFocus() }
            var showChatEmoji by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                // Citation du message auquel on répond (parité duel).
                replyingTo?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(3.dp, 30.dp).background(colors.accent))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("↩ ${r.authorName}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(r.content, color = colors.mutedForeground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Filled.Close, contentDescription = "Annuler la réponse", tint = colors.mutedForeground, modifier = Modifier.size(18.dp).clickable { replyingTo = null })
                    }
                }
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
                        keyboardActions = KeyboardActions(onDone = { if (liveChatEnabled) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }; showComment = false; replyingTo = null }),
                        modifier = Modifier.weight(1f).focusRequester(commentFocus),
                    )
                    RailButton(Icons.Filled.Send, tint = Color.White, bg = colors.primary) {
                        if (liveChatEnabled) { viewModel.sendMessage(draft, replyingTo?.id); draft = "" }
                        showComment = false; replyingTo = null
                    }
                }
            }
        }
        }
        CommentSheet()

        // Panneau cadeaux (feuille du bas) : onglet « Mes cadeaux » (envoi depuis l'inventaire,
        // parité web) + onglet « Boutique » (achat au catalogue → crédite l'inventaire).
        @Composable fun GiftPanelSheet() {
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
                        // Mes cadeaux : icône ✉️ dédiée pour envoyer (le reste de la ligne n'envoie
                        // plus rien au tap — évite la confusion « je clique pour voir » vs « pour
                        // envoyer », signalée par les utilisateurs).
                        if (inventory.isEmpty()) {
                            Text(s.noGiftsBuyInShop, color = colors.mutedForeground, fontSize = 13.sp)
                        }
                        inventory.forEach { g ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${g.imageUrl ?: "🎁"}  ${g.name}", color = colors.foreground)
                                    Text("×${g.quantity}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier.size(38.dp).background(colors.primary, CircleShape)
                                        .clickable { viewModel.sendGift(g.id, hostUserId); showGiftPanel = false },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Send, contentDescription = s.sendGift, tint = Color.White, modifier = Modifier.size(17.dp)) }
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
        }
        GiftPanelSheet()

        // Feuille de signalement (motifs préréglés).
        @Composable fun ReportSheet() {
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
        }
        ReportSheet()

        // Feuille de dédicace (message dédié).
        @Composable fun DedicationSheet() {
        if (showDedication) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showDedication = false })
            var dedic by remember { mutableStateOf("") }
            // Prix modifiable par le fan (parité web) — pré-rempli au minimum, jamais en dessous.
            var priceText by remember(dedicationMinPrice) { mutableStateOf(dedicationMinPrice.toInt().toString()) }
            val priceValue = (priceText.toDoubleOrNull() ?: dedicationMinPrice).coerceAtLeast(dedicationMinPrice)
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
                // Prix librement augmentable (jamais en dessous du minimum fixé par l'artiste).
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v -> priceText = v.filter { it.isDigit() } },
                    label = { Text("${s.credits} (min ${dedicationMinPrice.toInt()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Pill(color = colors.primary, onClick = { viewModel.dedicate(dedic, priceValue); dedic = ""; showDedication = false }) {
                    Text("${s.send} (${priceValue.toInt()} ${s.credits})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
        DedicationSheet()

        // Feuille des invités (hôte) : demandes en attente (accepter/refuser) + invités actifs
        // (couper le micro, accorder un temps de parole, retirer). Parité écran mobile web.
        @Composable fun GuestsSheet() {
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
        }
        GuestsSheet()

        // Feuille du classement des donateurs.
        @Composable fun LeaderboardSheet() {
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
        }
        LeaderboardSheet()

        // Feuille « Contrôles du live » (hôte, en direct) : caméra / flip / micro / pause / stop.
        @Composable fun SettingsSheet() {
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
                // Invité sur scène : DESCENDRE du direct (arrête de publier, reste spectateur ; sa
                // tuile disparaît chez tous). Ne quitte PAS le live.
                if (!isHost) {
                    Pill(color = colors.destructive, modifier = Modifier.fillMaxWidth(), onClick = { showSettings = false; viewModel.leaveStage(); broadcasting = false }) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = Color.White)
                        Text("  Descendre du direct", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                // Enregistrement : en mode `manual`, une icône DÉDIÉE du rail ouvre sa propre feuille
                // (chrono + Pause/Reprendre/Annuler/Sauvegarder) — pas ici, pour ne plus la confondre
                // avec le bouton « Pause » caméra/micro juste au-dessus. En mode `auto`, un simple
                // indicateur REC suffit (rien à piloter), affiché ici faute d'icône dédiée.
                if (isHost && recMode == "auto") {
                    com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = {}, modifier = Modifier.fillMaxWidth())
                }
                // Réglages du live (hôte) : dédicaces on/off (+ prix minimum) et invités on/off —
                // appliqués en direct, visibles par tous immédiatement.
                if (isHost) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        Pill(
                            color = if (liveAllowsDedications) colors.primary else Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setDedicationsEnabled(!liveAllowsDedications) },
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("  Dédicaces ${if (liveAllowsDedications) "activées" else "coupées"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Pill(
                            color = if (liveAllowGuests) colors.primary else Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setGuestsEnabled(!liveAllowGuests) },
                        ) {
                            Icon(Icons.Filled.PersonAddAlt1, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("  Invités ${if (liveAllowGuests) "activés" else "coupés"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (liveAllowsDedications) {
                        var minPriceText by remember(dedicationMinPrice) { mutableStateOf(dedicationMinPrice.toInt().toString()) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                            OutlinedTextField(
                                value = minPriceText,
                                onValueChange = { v -> minPriceText = v.filter { it.isDigit() } },
                                label = { Text("Prix minimum dédicace") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            Pill(color = colors.primary, onClick = {
                                minPriceText.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.setDedicationMinPrice(it) }
                            }) { Text("OK", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    // Chat du live : l'hôte peut le couper entièrement pour tous — pouvoir EXCLUSIF,
                    // jamais délégué aux modérateurs désignés.
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("💬 Chat activé", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Switch(checked = liveChatEnabled, onCheckedChange = { viewModel.setChatEnabled(it) })
                    }
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
        }
        SettingsSheet()

        // Feuille DÉDIÉE à l'enregistrement (hôte) : chrono + Pause/Reprendre/Annuler/Sauvegarder,
        // séparée des Réglages pour ne plus se mélanger avec le bouton « Pause » caméra/micro.
        @Composable fun RecordingSheet() {
        if (showRecordingSheet) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showRecordingSheet = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🔴 Enregistrement du live", color = colors.foreground, fontWeight = FontWeight.Bold)
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
        }
        RecordingSheet()

        // Confirmation d'annulation d'enregistrement (destructif : rien n'est recollé/publié).
        @Composable fun CancelRecordingConfirmSheet() {
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
        }
        CancelRecordingConfirmSheet()

        // Feuille « Effets » : filtres couleur + fond (flou/image), en direct, publiés à tous.
        @Composable fun EffectsSheet() {
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
                                        .clickable { selfMedia.setColorFilter(f.id, f.matrix) },
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
                            selfMedia.clearBackground()
                        }
                        // Flou.
                        EffectBgTile(label = s.blur, brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))), selected = backgroundMode == "blur", modifier = Modifier.weight(1f)) {
                            selfMedia.setBackgroundBlur()
                        }
                        // Images de fond (dégradés).
                        BackgroundPresets.take(2).forEach { bg ->
                            EffectBgTile(label = bg.label, brush = Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom))), selected = false, modifier = Modifier.weight(1f)) {
                                selfMedia.setBackgroundImage(makeGradientBitmap(bg.top, bg.bottom))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        BackgroundPresets.drop(2).forEach { bg ->
                            EffectBgTile(label = bg.label, brush = Brush.verticalGradient(listOf(Color(bg.top), Color(bg.bottom))), selected = false, modifier = Modifier.weight(1f)) {
                                selfMedia.setBackgroundImage(makeGradientBitmap(bg.top, bg.bottom))
                            }
                        }
                        repeat((4 - BackgroundPresets.drop(2).size).coerceAtLeast(0)) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
        }
        EffectsSheet()

        // Feuille « Description » : infos artiste + titre du live.
        @Composable fun DescriptionSheet() {
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
                // Tap sur le nom → profil public de l'artiste (parité duel).
                Text(
                    artistName ?: s.artists, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.clickable { showDescription = false; onOpenArtist(hostUserId) },
                )
                Text(liveTitle ?: s.liveActive, color = colors.mutedForeground)
            }
        }
        }
        DescriptionSheet()

        // Feuille « Dédicaces » (hôte) : demandes payantes en attente + marquer comme livrée.
        @Composable fun DedicationRequestsSheet() {
        if (showDedicationRequests) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showDedicationRequests = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.55f).background(colors.background)
                    .navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("📣 Dédicaces", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (dedications.isEmpty() && dedicationHistory.isEmpty()) {
                    Text("Aucune dédicace pour l'instant.", color = colors.mutedForeground, fontSize = 13.sp)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    // En attente : accepter (débite le fan MAINTENANT) ou rejeter (aucun débit).
                    if (dedications.isNotEmpty()) {
                        Text("En attente (${dedications.size})", color = colors.destructive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    dedications.forEach { d ->
                        Column(
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(DualMusicTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
                        ) {
                            Text("${d.fanName}  ·  ${d.priceCredits.toInt()} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(d.message, color = colors.foreground, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                                Pill(color = colors.primary, modifier = Modifier.weight(1f), onClick = { viewModel.acceptDedication(d.id) }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text("  Accepter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Pill(color = colors.destructive, modifier = Modifier.weight(1f), onClick = { viewModel.rejectDedication(d.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text("  Rejeter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    // Acceptées / livrées — historique, sous les demandes en attente (même feuille).
                    if (dedicationHistory.isNotEmpty()) {
                        Text("Acceptées / livrées (${dedicationHistory.size})", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    dedicationHistory.forEach { d ->
                        Column(
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(DualMusicTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
                        ) {
                            Text("${d.fanName}  ·  ${d.priceCredits.toInt()} ${s.credits}", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(d.message, color = colors.foreground, fontSize = 14.sp)
                            if (d.status == "delivered") {
                                Text("✅ Livrée", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Pill(color = colors.primary, onClick = { viewModel.deliverDedication(d.id) }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Text("  Marquer comme livrée", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        DedicationRequestsSheet()

        // Carte « cadeau reçu » glissante (fil) — parité duel.
        val lastGift = giftFeed.lastOrNull()
        var shownGift by remember { mutableStateOf<LiveGift?>(null) }
        var giftVisible by remember { mutableStateOf(false) }
        LaunchedEffect(lastGift?.id) {
            if (lastGift != null) { shownGift = lastGift; giftVisible = true; kotlinx.coroutines.delay(3200); giftVisible = false }
        }
        AnimatedVisibility(
            visible = giftVisible && !uiPrefs.reduceAnimations,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 340.dp),
        ) { shownGift?.let { LiveGiftReceivedCard(it) } }

        // Bannière « vous avez reçu un cadeau » (destinataire).
        giftReceived?.let { msg ->
            Box(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPaddingCompat().padding(top = 96.dp)
                    .background(Color(0xF2FF4FA3), RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }

        // Confirmation de bannissement d'un spectateur (hôte).
        @Composable fun BanConfirmDialog() {
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
                    Pill(color = Color.Black.copy(alpha = 0.25f), modifier = Modifier.weight(1f), onClick = { banTarget = null }) {
                        Text("Annuler", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Pill(color = colors.destructive, modifier = Modifier.weight(1f), onClick = { viewModel.banUser(target.userId, target.content.take(200)); banTarget = null }) {
                        Text("Bannir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
        BanConfirmDialog()

        // Modérateurs désignés (ban/masquer message) — hôte : gère la liste ; modérateur : la consulte.
        if (showModeratorsDialog) {
            EventModeratorsDialog(viewModel = viewModel, isHost = isHost, onDismiss = { showModeratorsDialog = false })
        }

        // Écran de blocage : l'artiste m'a banni → je ne peux plus participer ni rejoindre.
        @Composable fun BannedScreen() {
        if (iAmBanned) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .systemBarsPaddingCompat().padding(DualMusicTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(64.dp))
                Text("Accès bloqué", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "L'artiste vous a banni de ce live. Vous ne pouvez plus y participer ni le rejoindre.",
                    color = colors.mutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Pill(color = colors.primary, onClick = onEndLive) { Text("Quitter", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        }
        BannedScreen()
    }
}

/** Nom du meilleur donateur qui défile en continu (parité duel `ScrollingLabel`). */
@Composable
private fun ScrollingLabel(text: String, modifier: Modifier = Modifier) {
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

/**
 * Chrono du temps de parole accordé par l'artiste à un invité — fond TRANSLUCIDE (comme les
 * autres badges de l'appli), affiché pour TOUT LE MONDE (artiste, invité lui-même, spectateurs)
 * sur la case de l'invité, petite comme agrandie (parité duel).
 */
@Composable
private fun GuestChronoChip(remainingSec: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "⏱ ${remainingSec / 60}:${(remainingSec % 60).toString().padStart(2, '0')}",
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Entrée de vignette bas-droite : piste brute (hôte visible en réduit) ou invité (nom + avatar +
 * badges micro/caméra, gardé même caméra coupée — parité duel `SlotTile`/`SlotContent`). */
private sealed class LiveThumbEntry {
    data class Plain(val track: VideoTrack) : LiveThumbEntry()
    data class Guest(val userId: String, val label: String, val avatarUrl: String?, val track: VideoTrack?, val client: LiveRoomClient?) : LiveThumbEntry()
    // Mon PROPRE aperçu (invité en train de diffuser) : toujours affiché tant que je suis sur
    // scène, même caméra coupée (placeholder) — état lu directement à l'usage (micOn/camOn/etc.
    // déjà collectés plus haut dans le composable), pas besoin de porter des champs ici.
    data object SelfPreview : LiveThumbEntry()
}

/**
 * Case d'un invité : vidéo si publiée, sinon placeholder (avatar + icône caméra coupée) — ne
 * disparaît jamais tant qu'il est présent. Bandeau bas optionnel : nom + icônes micro/caméra
 * (parité duel `SlotContent`, badge de la vignette).
 */
@Composable
private fun GuestTileContent(
    track: VideoTrack?,
    client: LiveRoomClient?,
    label: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true,
    // Pour MA PROPRE case (self-preview) : `client.remoteMicOn` lirait le micro d'un AUTRE
    // participant distant (faux pour ma propre room, où je suis le seul publieur) — on force
    // alors l'état déjà connu localement (`selfMedia.micEnabled`) au lieu de le déduire du client.
    micOnOverride: Boolean? = null,
) {
    val colors = DualMusicTheme.colors  
    val micOn = micOnOverride ?: if (client != null) client.remoteMicOn.collectAsStateWithLifecycle().value else false
    val room = client?.room
    Box(modifier = modifier.background(Color(0xFF241338))) {
        if (track != null && room != null) {
            androidx.compose.runtime.key(track) { LiveVideoView(track = track, room = room, modifier = Modifier.fillMaxSize()) }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.45f)),
                ) {
                    com.dualmusic.core.ui.components.DMRemoteImage(
                        url = avatarUrl, contentDescription = label, modifier = Modifier.fillMaxSize(),
                        fallbackEmoji = label.take(1).uppercase(),
                    )
                }
                Icon(
                    Icons.Filled.VideocamOff, contentDescription = null, tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp).padding(top = 4.dp),
                )
            }
        }
        if (showBadge) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null,
                    tint = if (micOn) Color(0xFF22C55E) else Color(0xFFEF4444), modifier = Modifier.size(11.dp),
                )
                Icon(
                    if (track != null) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = null,
                    tint = if (track != null) Color(0xFF22C55E) else Color(0xFFEF4444), modifier = Modifier.size(11.dp),
                )
                Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Rendu vidéo LiveKit robuste : détache la PISTE PRÉCÉDEMMENT attachée à ce renderer avant d'en
 * attacher une nouvelle (le même [SurfaceViewRenderer] peut être réutilisé par Compose quand
 * `track` change — ex. tap pour agrandir sa propre case — sans jamais recréer la vue), et libère
 * le renderer à la sortie de composition.
 *
 * Sans ce détachement, DEUX pistes vidéo se disputent le même renderer : c'est la cause du
 * clignotement infini observé en agrandissant une case (et qui pouvait empêcher l'invité de se
 * revoir lui-même), car l'ancienne piste continue de pousser des frames vers un renderer qui a
 * changé de propriétaire ou a été détruit.
 */
@Composable
private fun LiveVideoView(track: VideoTrack, room: io.livekit.android.room.Room, modifier: Modifier = Modifier) {
    val attached = remember { arrayOfNulls<VideoTrack>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                room.initVideoRenderer(this)
                // Cadre ENTIER visible (jamais rogné) — parité avec ce que montre le PC : sans ça,
                // l'écran (bien plus étroit/haut) rognait le haut/bas du corps.
                setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            }
        },
        update = { renderer ->
            val prev = attached[0]
            if (prev !== track) {
                prev?.let { runCatching { it.removeRenderer(renderer) } }
                runCatching { track.addRenderer(renderer) }
                attached[0] = track
            }
        },
        onRelease = { renderer ->
            attached[0]?.let { runCatching { it.removeRenderer(renderer) } }
            attached[0] = null
        },
    )
}

/** Carte glissante « cadeau reçu » (image réelle + valeur) — parité duel. */
@Composable
private fun LiveGiftReceivedCard(g: LiveGift) {
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
            url = g.giftImage?.takeIf { it.startsWith("http") },
            contentDescription = g.giftName,
            modifier = Modifier.size(34.dp).scale(scale),
            fallbackEmoji = g.giftImage?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "🎁",
        )
        Column {
            Text(g.giftName ?: "Cadeau reçu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(
                "${g.fromUserName ?: "Quelqu'un"} · +${g.value.toInt()} crédits",
                color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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

/**
 * Modérateurs désignés du live (max [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS]) :
 * l'hôte + les spectateurs qu'il nomme partagent le pouvoir de bannir/masquer un message —
 * JAMAIS le chat on/off, réservé à l'hôte. Parité web (panneau de modération). Copie propre à
 * cet écran (comme les autres feuilles ci-dessus), pas partagée avec le duel.
 */
@Composable
private fun EventModeratorsDialog(viewModel: LiveViewModel, isHost: Boolean, onDismiss: () -> Unit) {
    val colors = DualMusicTheme.colors
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val viewers by viewModel.viewers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (isHost) viewModel.loadViewers() }
    val appointedIds = moderators.map { it.userId }.toSet()
    val pickable = viewers.filter { it.id !in appointedIds }
    val atLimit = moderators.size >= com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modérateurs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text(
                    "Un modérateur peut bannir un spectateur ou masquer un message, comme vous.",
                    color = colors.mutedForeground, fontSize = 12.sp,
                )
                if (moderators.isEmpty()) {
                    Text("Aucun modérateur désigné.", color = colors.mutedForeground, fontSize = 13.sp)
                } else {
                    moderators.forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.displayName, color = colors.foreground, fontSize = 13.sp)
                            if (isHost) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Révoquer",
                                    tint = colors.destructive,
                                    modifier = Modifier.size(18.dp).clickable { viewModel.revokeModerator(m.userId) },
                                )
                            }
                        }
                    }
                }
                if (isHost) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.mutedForeground.copy(alpha = 0.2f)))
                    Text("Désigner un spectateur", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    when {
                        atLimit -> Text("Nombre maximum de modérateurs atteint (2).", color = colors.mutedForeground, fontSize = 12.sp)
                        pickable.isEmpty() -> Text("Aucun spectateur connecté pour le moment.", color = colors.mutedForeground, fontSize = 12.sp)
                        else -> Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            pickable.forEach { v ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(v.displayName, color = colors.foreground, fontSize = 13.sp)
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.primary)
                                            .clickable { viewModel.appointModerator(v.id) }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) { Text("Nommer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Pill(color = colors.primary, onClick = onDismiss) { Text("Fermer", color = Color.White, fontWeight = FontWeight.Bold) }
        },
    )
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

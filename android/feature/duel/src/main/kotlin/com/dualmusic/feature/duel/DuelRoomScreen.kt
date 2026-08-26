package com.dualmusic.feature.duel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.media.LiveRoomClient
import io.livekit.android.room.track.VideoTrack
import com.dualmusic.core.ui.celebration.WinnerCelebration
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.overlay.FloatingReactionsLayer
import com.dualmusic.core.ui.prefs.UiPreferencesStore
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.feature.sponsor.SponsorAdLayer
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
    onLeave: () -> Unit = {},
) {
    val duel by viewModel.duel.collectAsStateWithLifecycle()
    val totals by viewModel.voteTotals.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    // Vidéo par SLOT (parité web : 1 room LiveKit par acteur → 3 connexions).
    val a1Video by viewModel.artist1Video.collectAsStateWithLifecycle()
    val a2Video by viewModel.artist2Video.collectAsStateWithLifecycle()
    val mgrVideo by viewModel.managerVideo.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val isManager by viewModel.isManager.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val sponsorAd by viewModel.sponsor.activeAd.collectAsStateWithLifecycle()
    val sponsorAds by viewModel.sponsor.ads.collectAsStateWithLifecycle()
    val sponsorBusy by viewModel.sponsor.busy.collectAsStateWithLifecycle()
    // Diffusion caméra/micro (participant : artiste 1/2 ou manager) — parité web.
    val canPublish by viewModel.canPublish.collectAsStateWithLifecycle()
    val broadcasting by viewModel.broadcasting.collectAsStateWithLifecycle()
    val micOn by viewModel.micOn.collectAsStateWithLifecycle()
    val camOn by viewModel.camOn.collectAsStateWithLifecycle()
    // Focus : imposé par le manager (synchronisé) et/ou choix local du spectateur (tap sur une case).
    val forcedFocus by viewModel.forcedFocus.collectAsStateWithLifecycle()
    // Prix d'un vote configuré par l'admin (synchronisé avec le web) — remplace le 10 codé en dur.
    val votePrice by viewModel.votePrice.collectAsStateWithLifecycle()
    var localFocus by remember { mutableStateOf<String?>(null) }
    // Bannière « vous avez reçu un cadeau » (destinataire) — auto-effacée après quelques secondes.
    val giftReceived by viewModel.giftReceived.collectAsStateWithLifecycle()
    LaunchedEffect(giftReceived) {
        if (giftReceived != null) { kotlinx.coroutines.delay(3500); viewModel.clearGiftReceived() }
    }
    // Célébration du vainqueur TEMPORAIRE (~7 s) → ne reste pas en permanence sur le direct.
    var winnerCelebration by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(duel?.winnerId) {
        val wid = duel?.winnerId
        if (wid != null) { winnerCelebration = wid; kotlinx.coroutines.delay(7000); winnerCelebration = null }
    }
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showGiftPanel by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    // Rail gauche (parité web capture 4).
    // overlaysHidden (icône ✕) : masque TOUT (rail, barres, messages, vignettes) sauf la vidéo,
    //   + un œil flottant en haut pour tout réafficher.
    // thumbnailsHidden (icône œil) : masque seulement les petites cases (vignettes).
    // showSettings (icône ⚙️) : pop-up regroupant les contrôles de diffusion (participant).
    var overlaysHidden by remember { mutableStateOf(false) }
    var thumbnailsHidden by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // Barre du bas condensée (parité web) : emojis repliables + panneau de vote + pop-up saisie.
    var showEmojiBar by remember { mutableStateOf(false) }
    var showVotePanel by remember { mutableStateOf(false) }
    var showCommentPopup by remember { mutableStateOf(false) }
    // Message auquel on répond (tap sur un message du chat) — parité web.
    var replyingTo by remember { mutableStateOf<DuelChatMessage?>(null) }

    // Permission caméra/micro avant de diffuser (participant) — on lance la diffusion au retour.
    val camMicPerms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasCamMic() = camMicPerms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val camMicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) viewModel.startBroadcast()
    }

    // Démarre/arrête avec le cycle de vie du composable.
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Multi-cam (parité web) : chaque slot est rendu avec la room de SON client LiveKit ---
        // Les cases ATTENDUES (participants du duel) sont TOUJOURS présentes : vidéo si dispo, sinon
        // placeholder « caméra off » (la case ne disparaît pas et ne fige jamais la dernière image).
        val slotTiles: List<SlotTile> = buildList {
            duel?.artist1Id?.let { add(SlotTile("artist1", duel?.artist1?.displayName ?: strings.artist1, a1Video, viewModel.mediaA1)) }
            duel?.artist2Id?.let { add(SlotTile("artist2", duel?.artist2?.displayName ?: strings.artist2, a2Video, viewModel.mediaA2)) }
            duel?.managerId?.let { add(SlotTile("manager", "Manager", mgrVideo, viewModel.mediaMgr)) }
        }
        // Case en grand : focus imposé manager > choix local (tap) > 1re case EN DIRECT (souvent
        // l'adversaire) > 1re case. Sans ça, on s'affichait SOI-MÊME (placeholder) en grand et le
        // web se retrouvait en petite vignette → "je ne vois pas le web".
        val effectiveFocus = forcedFocus ?: localFocus
        val mainTile = slotTiles.find { it.slot == effectiveFocus }
            ?: slotTiles.firstOrNull { it.track != null }
            ?: slotTiles.firstOrNull()
        // --- Couche principale : vidéo (ou placeholder caméra off) du slot en grand ---
        if (mainTile != null) {
            SlotContent(mainTile, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradé bas pour la lisibilité des overlays.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )

        // --- Cadeau reçu : carte animée façon TikTok (glisse depuis la gauche, au-dessus du chat,
        // NON-intrusive) puis ressort après ~3,2s. Ne bloque pas le centre du direct. ---
        val lastGift = giftFeed.lastOrNull()
        var shownGift by remember { mutableStateOf<DuelGift?>(null) }
        var giftVisible by remember { mutableStateOf(false) }
        LaunchedEffect(lastGift?.key) {
            if (lastGift != null) {
                shownGift = lastGift; giftVisible = true
                kotlinx.coroutines.delay(3200); giftVisible = false
            }
        }
        AnimatedVisibility(
            visible = giftVisible && !uiPrefs.reduceAnimations,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 214.dp),
        ) {
            shownGift?.let { GiftReceivedCard(it) }
        }

        // --- Réactions flottantes (cœurs/emojis) montantes, vues par tous ---
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

        // --- Bannière « vous avez reçu un cadeau » (destinataire uniquement, transitoire) ---
        giftReceived?.let { msg ->
            Box(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 132.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color(0xF2FF4FA3)).padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }

        // (Le meilleur donateur est désormais une LIGNE dans la zone haute « navbar » ci-dessous,
        //  pour être masquée avec le reste via ✕ et ne pas flotter sur la vidéo.)

        // --- Overlays (zones sûres) — masquables via l'œil du rail gauche (vidéo plein cadre) ---
        if (!overlaysHidden) Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(DualMusicTheme.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Haut : zone « navbar » (fond opaque) → la vidéo ne bave pas dessus ; masquée par ✕.
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF21A0B2E)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                com.dualmusic.core.ui.live.LiveHeader(
                    eventLabel = "",
                    badgeText = "DUEL",
                    viewerCount = viewerCount,
                    likes = likes,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, strings.shareLiveText) }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onReport = { showReport = true },
                    onClose = onLeave,
                    onParticipants = { showDescription = true },
                )
                // 2ᵉ ligne (désengorge le badge) : mon état micro/caméra quand JE diffuse.
                if (broadcasting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vous", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = if (micOn) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                        Icon(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = null, tint = if (camOn) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                    }
                }
                val a1 = duel?.artist1Id
                val a2 = duel?.artist2Id
                VoteBar(
                    leftName = duel?.artist1?.displayName ?: strings.artist1,
                    rightName = duel?.artist2?.displayName ?: strings.artist2,
                    leftTotal = a1?.let { totals[it] } ?: 0.0,
                    rightTotal = a2?.let { totals[it] } ?: 0.0,
                )
                // Meilleur donateur : ligne défilante droite→gauche (pause entre les tours), dans la navbar.
                topDonor?.let { d ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                            .background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "👑  ${d.name}  ·  ${d.amount} 🎁",
                            color = Color(0xFFFFD54A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                    }
                }
                if (timer.isRunning) {
                    // Décompte visuel MM:SS (parité web) avec le nom de l'artiste qui a la parole.
                    val speaker = when (timer.targetId) {
                        a1 -> duel?.artist1?.displayName ?: strings.artist1
                        a2 -> duel?.artist2?.displayName ?: strings.artist2
                        else -> null
                    }
                    com.dualmusic.core.ui.live.LiveCountdown(endsAtIso = timer.endsAt, label = speaker)
                }
                // Contrôles de l'arbitre (manager) : minuteur de parole, vainqueur, fin.
                if (isManager) {
                    ManagerDuelControls(
                        artist1Name = duel?.artist1?.displayName ?: strings.artist1,
                        artist2Name = duel?.artist2?.displayName ?: strings.artist2,
                        artist1Id = duel?.artist1Id,
                        artist2Id = duel?.artist2Id,
                        timerRunning = timer.isRunning,
                        onGiveTurn = { id, sec -> viewModel.startTimer(id, sec) },
                        onStopTimer = { viewModel.stopTimer() },
                        onWinner = { id -> viewModel.announceWinner(id) },
                        onEnd = { viewModel.endDuel(onLeave) },
                    )
                    com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = { viewModel.recordingCtl.toggle() })
                }
            }

            // Bas : chat + panneau de vote + saisie message.
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                // Chat défilant (largeur ~68%) : plus récent en bas, auto-défilement (façon TikTok).
                val chatState = rememberLazyListState()
                // Défilement PRIORITAIRE au plus récent (en bas), MAIS sans forcer si l'utilisateur
                // a tiré vers le haut pour lire les anciens (on ne le ramène pas de force en bas).
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        val info = chatState.layoutInfo.visibleItemsInfo
                        val atBottom = info.isEmpty() || (info.lastOrNull()?.index ?: -1) >= messages.size - 2
                        if (atBottom) chatState.animateScrollToItem(messages.size - 1)
                    }
                }
                LazyColumn(
                    state = chatState,
                    // Aligné à GAUCHE (au niveau du rail), largeur limitée pour NE PAS toucher les vignettes.
                    // ~5 dernières lignes visibles (collées en bas) ; tirer vers le haut pour les précédentes.
                    modifier = Modifier.fillMaxWidth(0.62f).heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(messages) { msg ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            // Tap sur un message → y répondre (parité web).
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.28f)).clickable { replyingTo = msg; showCommentPopup = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            // Avatar rond (initiale) — parité web.
                            Box(
                                modifier = Modifier.size(22.dp).background(colors.primary.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(msg.authorName.take(1).uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            // Nom + contenu en COLONNE → le message long revient à la ligne.
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text(msg.authorName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(msg.content, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = colors.destructive) }
                // Barre d'emojis repliable (ouverte par le bouton 😊 de la rangée du bas).
                if (showEmojiBar) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DuelReactionEmojis.forEach { e ->
                            Box(
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) { Text(e) }
                        }
                    }
                }
                // Barre du bas UNIQUE (parité web capture 1) : fond « navbar » → la vidéo ne bave pas dessus.
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xF21A0B2E)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // « Message... » = pill déclencheur ; la vraie saisie s'ouvre en pop-up (capture 2).
                    Row(
                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.35f)).clickable { showCommentPopup = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                        Text("Message...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                    }
                    BottomBarIcon(Icons.Filled.Favorite, Color(0xFFFF4D6D)) { viewModel.sendLike() }
                    BottomBarIcon(Icons.Filled.Mood, Color.White) { showEmojiBar = !showEmojiBar }
                    // Cadeau : bouton principal (violet) — parité web.
                    Box(
                        modifier = Modifier.size(44.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPanel = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CardGiftcard, contentDescription = strings.sendGift, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    BottomBarIcon(Icons.Filled.EmojiEvents, Color(0xFFFFC107)) { showLeaderboard = true; viewModel.loadGiftLeaderboard() }
                    BottomBarIcon(Icons.Filled.HowToVote, colors.accent) { showVotePanel = true }
                }
            }
        }

        // --- Rail vertical GAUCHE (parité web capture 4) — remonté sous la navbar, masqué via ✕ ---
        if (!overlaysHidden) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 128.dp, start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ✕ : masque TOUT sauf la vidéo (œil flottant en haut pour restaurer).
                MediaRailButton(Icons.Filled.Close, "Masquer tout") { overlaysHidden = true }
                // Rouge : QUITTER le direct (ne termine pas le direct).
                MediaRailButton(Icons.AutoMirrored.Filled.Logout, "Quitter", danger = true) { onLeave() }
                MediaRailButton(Icons.Filled.Description, "Infos") { showDescription = true }
                // Œil : masque/affiche seulement les petites cases (vignettes).
                MediaRailButton(if (thumbnailsHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Petites cases") { thumbnailsHidden = !thumbnailsHidden }
                // Participant : Démarrer, puis ⚙️ regroupe les contrôles direct dans un pop-up.
                if (canPublish) {
                    if (!broadcasting) {
                        MediaRailButton(Icons.Filled.Podcasts, "Démarrer", accent = true) {
                            if (hasCamMic()) viewModel.startBroadcast() else camMicLauncher.launch(camMicPerms)
                        }
                    } else {
                        MediaRailButton(Icons.Filled.Settings, "Réglages", accent = true) { showSettings = true }
                    }
                }
            }
        }
        // Œil flottant : réafficher tout après un ✕ (parité web).
        if (overlaysHidden) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .size(44.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).clickable { overlaysHidden = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Visibility, contentDescription = "Réafficher", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        // État « Prêt à démarrer » au centre (participant pas encore en direct).
        if (canPublish && !broadcasting) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Text("Prêt à démarrer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Pop-up RÉGLAGES du direct (icône ⚙️) — regroupe les contrôles pour gagner de la place.
        if (showSettings && broadcasting && !overlaysHidden) {
            Box(Modifier.fillMaxSize().clickable { showSettings = false })
            val paused = !camOn && !micOn
            Column(
                modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 62.dp)
                    .width(190.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SettingsRow(if (paused) Icons.Filled.Podcasts else Icons.Filled.Pause, if (paused) "Reprendre" else "Pause") { viewModel.togglePause() }
                SettingsRow(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (camOn) "Caméra ON" else "Caméra OFF") { viewModel.toggleCamera() }
                SettingsRow(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (micOn) "Micro ON" else "Micro OFF") { viewModel.toggleMic() }
                SettingsRow(Icons.Filled.Cameraswitch, "Retourner caméra") { viewModel.flipCamera() }
                // « Arrêter » = METTRE FIN au direct → réservé au MANAGER (parité web). Un artiste
                // quitte via le bouton rouge (il ne termine pas l'événement).
                if (isManager) {
                    SettingsRow(Icons.Filled.Stop, "Arrêter le direct", danger = true) { showSettings = false; viewModel.endDuel(onLeave) }
                }
            }
        }

        // Vignettes multi-cam = les slots autres que celui affiché en grand (parité web).
        val thumbTiles = slotTiles.filter { it !== mainTile }
        if (thumbTiles.isNotEmpty() && !overlaysHidden && !thumbnailsHidden) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                thumbTiles.forEach { tile ->
                    key(tile.slot) {
                        Box(
                            Modifier.width(84.dp).height(112.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)
                                // Tap = agrandir cette case (focus LOCAL) — sauf si le manager a imposé un focus.
                                .clickable(enabled = forcedFocus == null) { localFocus = tile.slot },
                        ) {
                            SlotContent(tile, Modifier.fillMaxSize())
                            // Libellé : pastille signal verte + nom (parité web « Papou Koné »).
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).background(if (tile.track != null) Color(0xFF22C55E) else Color(0xFF888888), CircleShape))
                                Text("  ${tile.label}", color = Color.White, fontSize = 9.sp, maxLines = 1)
                            }
                            // Manager : épingle cette case en plein écran pour TOUS.
                            if (isManager) {
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                        .clickable { viewModel.setFocus(tile.slot) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.PushPin, contentDescription = "Épingler pour tous", tint = Color.White, modifier = Modifier.size(13.dp)) }
                            }
                        }
                    }
                }
            }
        }
        // Manager : bouton « libérer le focus » quand une case est épinglée (rend le focus libre à tous).
        if (isManager && forcedFocus != null && !overlaysHidden) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                    .clip(RoundedCornerShape(999.dp)).background(colors.primary).clickable { viewModel.setFocus(null) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text("  Libérer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Panneau « Infos » (icône doc du rail) : rappel de l'affiche du duel.
        if (showDescription) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showDescription = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("⚔️ Duel", color = colors.accent, fontWeight = FontWeight.Bold)
                Text(
                    "${duel?.artist1?.displayName ?: strings.artist1}  vs  ${duel?.artist2?.displayName ?: strings.artist2}",
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Panneau de VOTE (icône vote de la barre du bas) : voter pour l'un des deux artistes.
        if (showVotePanel) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showVotePanel = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("⚔️ ${strings.vote}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    duel?.artist1Id?.let { id ->
                        DMButton(duel?.artist1?.displayName ?: strings.artist1, modifier = Modifier.weight(1f)) { viewModel.vote(id, votePrice); showVotePanel = false }
                    }
                    duel?.artist2Id?.let { id ->
                        DMButton(duel?.artist2?.displayName ?: strings.artist2, style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f)) { viewModel.vote(id, votePrice); showVotePanel = false }
                    }
                }
                Text("${strings.oneVote} = ${votePrice.toInt()} ${strings.credits}", color = colors.mutedForeground, fontSize = 13.sp)
            }
        }

        // Pop-up de saisie « Commenter » (capture 2) — ouvert depuis le pill « Message... ».
        if (showCommentPopup) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCommentPopup = false; replyingTo = null })
            fun send() {
                if (draft.isNotBlank()) {
                    // Réponse : on préfixe par @auteur (le web thread ; ici on garde la mention).
                    val text = replyingTo?.let { "@${it.authorName} $draft" } ?: draft
                    viewModel.sendMessage(text); draft = ""
                }
                showCommentPopup = false; replyingTo = null
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background)
                    .navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (replyingTo != null) "Répondre à ${replyingTo?.authorName}" else "Commenter", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showCommentPopup = false; replyingTo = null })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Votre message...", color = colors.mutedForeground) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { send() }),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.size(44.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { send() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }

        // Célébration du vainqueur : dès que l'arbitre l'annonce (event `status` → winnerId), tous
        // les spectateurs voient les confettis + la carte, puis ça s'estompe (~7 s) → vue dégagée.
        winnerCelebration?.let { wid ->
            val name = when (wid) {
                duel?.artist1Id -> duel?.artist1?.displayName
                duel?.artist2Id -> duel?.artist2?.displayName
                else -> null
            }
            WinnerCelebration(
                winnerName = name ?: strings.winnerGeneric,
                title = strings.winnerTitle,
                subtitle = strings.winnerCongrats,
                reduceAnimations = uiPrefs.reduceAnimations,
            )
        }

        // Diffusion pub sponsor : overlay vidéo pour tous + contrôle pour le manager
        // (masqué si l'organisateur a désactivé les pubs sur ce duel — parité web).
        SponsorAdLayer(
            activeAd = sponsorAd,
            canTrigger = isManager && duel?.allowsSponsorAds != false,
            ads = sponsorAds,
            busy = sponsorBusy,
            onLoadAds = { viewModel.sponsor.loadAds() },
            onPlay = { viewModel.sponsor.play(it) },
            onStop = { viewModel.sponsor.stop() },
        )

        // --- Panneau cadeaux : destinataire (Artiste 1 / Artiste 2 / Manager) + Mes cadeaux / Boutique ---
        if (showGiftPanel) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showGiftPanel = false })
            var giftShop by remember { mutableStateOf(false) }
            val targets = buildList {
                duel?.artist1Id?.let { add(it to (duel?.artist1?.displayName ?: strings.artist1)) }
                duel?.artist2Id?.let { add(it to (duel?.artist2?.displayName ?: strings.artist2)) }
                duel?.managerId?.let { add(it to "Manager") }
            }
            var target by remember(targets.size) { mutableStateOf(targets.firstOrNull()?.first) }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.6f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🎁 ${strings.sendGift}", color = colors.foreground, fontWeight = FontWeight.Bold)
                // Destinataire
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    targets.forEach { (id, name) -> GiftPill("🎤 $name", target == id) { target = id } }
                }
                // Onglets Mes cadeaux / Boutique
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    GiftPill(strings.myGifts, !giftShop) { giftShop = false }
                    GiftPill(strings.giftShop, giftShop) { giftShop = true }
                }
                Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    if (!giftShop) {
                        if (inventory.isEmpty()) Text(strings.noGiftsBuyInShop, color = colors.mutedForeground, fontSize = 13.sp)
                        inventory.forEach { g ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { target?.let { viewModel.sendGift(g.giftId, it) }; showGiftPanel = false }.padding(DualMusicTheme.spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${g.imageUrl ?: "🎁"}  ${g.name ?: ""}", color = colors.foreground)
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
                                Text("${gift.price.toInt()} ${strings.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Classement des donateurs ---
        if (showLeaderboard) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showLeaderboard = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.5f).background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🏆 ${strings.donors}", color = colors.foreground, fontWeight = FontWeight.Bold)
                if (leaderboard.isEmpty()) {
                    Text(strings.emptyRanking, color = colors.mutedForeground)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        leaderboard.forEachIndexed { i, entry ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${duelMedal(i)} ${entry.displayName}", color = colors.foreground)
                                Text("${entry.value} ${strings.credits}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Signalement du direct (parité web LiveReportButton) ---
        if (showReport) {
            com.dualmusic.core.ui.live.ReportDialog(
                onDismiss = { showReport = false },
                onSubmit = { reason ->
                    viewModel.report(reason)
                    android.widget.Toast.makeText(context, strings.reportSent, android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** Pastille sélectionnable (destinataire du cadeau / onglet). */
@Composable
private fun GiftPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

/** Médaille de rang (or/argent/bronze puis numéro). */
private fun duelMedal(i: Int): String = when (i) {
    0 -> "🥇"
    1 -> "🥈"
    2 -> "🥉"
    else -> "#${i + 1}"
}

/** Emojis de réaction (identiques au live/web). */
private val DuelReactionEmojis = listOf("🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

/**
 * Bouton circulaire du rail de contrôle média (participant) : démarrer/micro/caméra/flip.
 * `accent` = action principale (démarrer, violet) ; `active=false` = état coupé (rouge).
 */
@Composable
private fun MediaRailButton(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    active: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val bg = when {
        accent -> colors.primary
        danger || !active -> Color(0xFFDC2626)
        else -> Color.Black.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier.size(48.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** Ligne du pop-up réglages du direct (Pause / Caméra / Micro / Retourner / Arrêter). */
@Composable
private fun SettingsRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (danger) Color(0xFFDC2626) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Case d'un slot (artiste 1/2 ou manager) : clé de focus + libellé + piste (null = caméra off) + client. */
private data class SlotTile(
    val slot: String,
    val label: String,
    val track: VideoTrack?,
    val client: LiveRoomClient,
)

/** Rend une case de slot : la vidéo si la piste existe, sinon un placeholder « caméra off ». */
@Composable
private fun SlotContent(tile: SlotTile, modifier: Modifier) {
    val colors = DualMusicTheme.colors
    val track = tile.track
    if (track != null) {
        // key(piste) : au flip la piste est recréée → renderer neuf (sinon écran noir).
        key(track) {
            AndroidView(
                modifier = modifier,
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { tile.client.room.initVideoRenderer(this) } },
                update = { renderer -> track.addRenderer(renderer) },
            )
        }
    } else {
        // Placeholder « comme si la caméra n'avait jamais été activée » : avatar initiale + caméra barrée.
        Box(modifier = modifier.background(Color(0xFF241338)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).background(colors.primary.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(tile.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Carte « cadeau reçu » animée (façon TikTok) : emoji qui rebondit + nom + valeur. Non-intrusive. */
@Composable
private fun GiftReceivedCard(g: DuelGift) {
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
        // Image RÉELLE du cadeau (URL) — sinon l'emoji du cadeau — sinon 🎁 (parité web).
        com.dualmusic.core.ui.components.DMRemoteImage(
            url = g.image?.takeIf { it.startsWith("http") },
            contentDescription = g.name,
            modifier = Modifier.size(34.dp).scale(scale),
            fallbackEmoji = g.image?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "🎁",
        )
        Column {
            Text(g.name ?: "Cadeau reçu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text("+${g.value.toInt()} crédits", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
    }
}

/** Bouton rond translucide de la barre du bas (like / emoji / classement / vote). */
@Composable
private fun BottomBarIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Panneau de contrôle de l'arbitre (manager) : minuteur de parole, vainqueur, fin du duel. */
@Composable
private fun ManagerDuelControls(
    artist1Name: String,
    artist2Name: String,
    artist1Id: String?,
    artist2Id: String?,
    timerRunning: Boolean,
    onGiveTurn: (String, Int) -> Unit,
    onStopTimer: () -> Unit,
    onWinner: (String) -> Unit,
    onEnd: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var seconds by remember { mutableStateOf(120) }
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(DualMusicTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
    ) {
        Text("🎛 ${s.speakingTime}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            listOf(60, 120, 180).forEach { sec ->
                Box(
                    modifier = Modifier.background(if (seconds == sec) colors.primary else Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp)).clickable { seconds = sec }.padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("${sec}s", color = Color.White, fontSize = 12.sp) }
            }
            if (timerRunning) {
                Box(
                    modifier = Modifier.background(colors.destructive, RoundedCornerShape(999.dp)).clickable { onStopTimer() }.padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text(s.stopAction, color = Color.White, fontSize = 12.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            artist1Id?.let { DMButton("🎤 $artist1Name", modifier = Modifier.weight(1f), onClick = { onGiveTurn(it, seconds) }) }
            artist2Id?.let { DMButton("🎤 $artist2Name", style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f), onClick = { onGiveTurn(it, seconds) }) }
        }
        Text("🏆 ${s.announceWinner}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            artist1Id?.let { DMButton(artist1Name, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { onWinner(it) }) }
            artist2Id?.let { DMButton(artist2Name, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { onWinner(it) }) }
        }
        DMButton(s.endDuelBtn, modifier = Modifier.fillMaxWidth(), onClick = onEnd)
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

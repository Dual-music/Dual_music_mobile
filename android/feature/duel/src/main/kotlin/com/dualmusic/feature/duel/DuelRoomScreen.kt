package com.dualmusic.feature.duel

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material.icons.filled.Podcasts
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
import com.dualmusic.core.ui.celebration.WinnerCelebration
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.gifts.GiftBurst
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.overlay.FloatingReactionsLayer
import com.dualmusic.core.ui.overlay.TopDonorBubble
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
    val videoTrack by viewModel.media.primaryVideoTrack.collectAsStateWithLifecycle()
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
    val localTrack by viewModel.media.localVideoTrack.collectAsStateWithLifecycle()
    val remoteTiles by viewModel.media.remoteTiles.collectAsStateWithLifecycle()
    val micOn by viewModel.media.micEnabled.collectAsStateWithLifecycle()
    val camOn by viewModel.media.camEnabled.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showGiftPanel by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    // Rail gauche (parité web) : masquer les overlays (voir la vidéo plein cadre) + panneau infos.
    var overlaysHidden by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // Barre du bas condensée (parité web) : emojis repliables + panneau de vote.
    var showEmojiBar by remember { mutableStateOf(false) }
    var showVotePanel by remember { mutableStateOf(false) }

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
        // --- Couche vidéo (décodage matériel, zero-copy) ---
        val track = videoTrack
        if (track != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                update = { renderer -> track.addRenderer(renderer) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
        }

        // Dégradé bas pour la lisibilité des overlays.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            ),
        )

        // --- Cadeau animé (halo GPU) — masqué si « Réduire les animations » ---
        if (!uiPrefs.reduceAnimations) {
            giftFeed.lastOrNull()?.let { gift ->
                key(gift.key) { GiftBurst(symbol = "🎁", modifier = Modifier.align(Alignment.Center)) }
            }
        }

        // --- Réactions flottantes (cœurs/emojis) montantes, vues par tous ---
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

        // Bulle du meilleur donateur (parité web), pilotée par les préférences visuelles.
        TopDonorBubble(donor = topDonor, mode = uiPrefs.topDonorMode, animation = uiPrefs.topDonorAnimation)

        // --- Overlays (zones sûres) — masquables via l'œil du rail gauche (vidéo plein cadre) ---
        if (!overlaysHidden) Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 56.dp, top = DualMusicTheme.spacing.lg, end = DualMusicTheme.spacing.lg, bottom = DualMusicTheme.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Haut : header live unifié (mêmes icônes que le web), barre de votes + minuteur.
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                com.dualmusic.core.ui.live.LiveHeader(
                    eventLabel = "DUEL",
                    viewerCount = viewerCount,
                    likes = likes,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, strings.shareLiveText) }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onReport = { showReport = true },
                    onClose = onLeave,
                )
                val a1 = duel?.artist1Id
                val a2 = duel?.artist2Id
                VoteBar(
                    leftName = duel?.artist1?.displayName ?: strings.artist1,
                    rightName = duel?.artist2?.displayName ?: strings.artist2,
                    leftTotal = a1?.let { totals[it] } ?: 0.0,
                    rightTotal = a2?.let { totals[it] } ?: 0.0,
                )
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
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) chatState.animateScrollToItem(messages.size - 1)
                }
                LazyColumn(
                    state = chatState,
                    modifier = Modifier.fillMaxWidth(0.68f).height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(messages) { msg ->
                        Row(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(msg.authorName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("  ${msg.content}", color = Color.White, fontSize = 12.sp)
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
                // Barre du bas UNIQUE (parité web) : message · ❤️ · 😊 · 🎁 · 🏆 · vote.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(strings.saySomething, color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    BottomBarIcon(Icons.Filled.Favorite, Color(0xFFFF4D6D)) { viewModel.sendLike() }
                    BottomBarIcon(Icons.Filled.Mood, Color.White) { showEmojiBar = !showEmojiBar }
                    // Cadeau : bouton principal (violet, plus gros) — parité web.
                    Box(
                        modifier = Modifier.size(48.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPanel = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CardGiftcard, contentDescription = strings.sendGift, tint = Color.White) }
                    BottomBarIcon(Icons.Filled.EmojiEvents, Color(0xFFFFC107)) { showLeaderboard = true; viewModel.loadGiftLeaderboard() }
                    BottomBarIcon(Icons.Filled.HowToVote, colors.accent) { showVotePanel = true }
                }
            }
        }

        // --- Rail vertical GAUCHE (parité web mobile) : fermer, quitter, infos, masquer + média ---
        // Toujours visible (même overlays masqués) pour pouvoir revenir. Aligné milieu-gauche.
        Column(
            modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MediaRailButton(Icons.Filled.Close, "Fermer") { onLeave() }
            MediaRailButton(Icons.AutoMirrored.Filled.Logout, "Quitter", danger = true) { onLeave() }
            MediaRailButton(Icons.Filled.Description, "Infos") { showDescription = true }
            MediaRailButton(if (overlaysHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Masquer") { overlaysHidden = !overlaysHidden }
            // Contrôles de diffusion réservés aux participants (artiste 1/2 ou manager).
            if (canPublish) {
                if (!broadcasting) {
                    MediaRailButton(Icons.Filled.Podcasts, "Démarrer", accent = true) {
                        if (hasCamMic()) viewModel.startBroadcast() else camMicLauncher.launch(camMicPerms)
                    }
                } else {
                    MediaRailButton(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, "Micro", active = micOn) { viewModel.toggleMic() }
                    MediaRailButton(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Caméra", active = camOn) { viewModel.toggleCamera() }
                    MediaRailButton(Icons.Filled.Cameraswitch, "Retourner") { viewModel.flipCamera() }
                }
            }
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

        // Vignettes multi-cam (ma caméra + autres participants) — bas-droite, au-dessus de la barre.
        val duelTiles = remoteTiles.map { it.second } + listOfNotNull(localTrack)
        if (duelTiles.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                duelTiles.takeLast(3).forEach { t ->
                    key(t) {
                        AndroidView(
                            modifier = Modifier.width(84.dp).height(112.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black),
                            factory = { ctx -> SurfaceViewRenderer(ctx).apply { viewModel.media.room.initVideoRenderer(this) } },
                            update = { renderer -> t.addRenderer(renderer) },
                        )
                    }
                }
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
                        DMButton(duel?.artist1?.displayName ?: strings.artist1, modifier = Modifier.weight(1f)) { viewModel.vote(id, voteAmount); showVotePanel = false }
                    }
                    duel?.artist2Id?.let { id ->
                        DMButton(duel?.artist2?.displayName ?: strings.artist2, style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f)) { viewModel.vote(id, voteAmount); showVotePanel = false }
                    }
                }
                Text("${strings.oneVote} = $voteAmount ${strings.credits}", color = colors.mutedForeground, fontSize = 13.sp)
            }
        }

        // Célébration du vainqueur : dès que l'arbitre l'annonce (event `status` → winnerId),
        // tous les spectateurs voient les confettis + la carte. Non bloquant pour « Terminer ».
        duel?.winnerId?.let { wid ->
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

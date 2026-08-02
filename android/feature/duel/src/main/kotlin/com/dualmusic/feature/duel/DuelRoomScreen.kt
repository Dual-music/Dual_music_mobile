package com.dualmusic.feature.duel

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
    val recMode by viewModel.recordingCtl.mode.collectAsStateWithLifecycle()
    val recActive by viewModel.recordingCtl.active.collectAsStateWithLifecycle()
    val recBusy by viewModel.recordingCtl.busy.collectAsStateWithLifecycle()
    val sponsorAd by viewModel.sponsor.activeAd.collectAsStateWithLifecycle()
    val sponsorAds by viewModel.sponsor.ads.collectAsStateWithLifecycle()
    val sponsorBusy by viewModel.sponsor.busy.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    var draft by remember { mutableStateOf("") }

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

        // --- Overlays (zones sûres : barre d'état en haut, touches système + clavier en bas) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(DualMusicTheme.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Haut : LIVE + spectateurs, barre de votes + minuteur.
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    Box(Modifier.background(colors.destructive, RoundedCornerShape(6.dp)).padding(horizontal = DualMusicTheme.spacing.sm, vertical = DualMusicTheme.spacing.xs)) {
                        Text("🔴 LIVE", color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs)) {
                        Text("👁 $viewerCount", color = Color.White)
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
                if (timer.isRunning) {
                    Text(strings.timerRunning, color = colors.accent, fontWeight = FontWeight.Bold)
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
                // Barre de réactions : J'aime + emojis (flottent pour tous).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendLike() },
                        contentAlignment = Alignment.Center,
                    ) { Text("❤️", fontSize = 18.sp) }
                    if (likes > 0) Text("$likes", color = Color.White, fontSize = 12.sp)
                    DuelReactionEmojis.forEach { e ->
                        Box(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable { viewModel.sendReaction(e) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text(e) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    duel?.artist1Id?.let { id ->
                        DMButton(
                            "${strings.vote} ${duel?.artist1?.displayName ?: strings.artist1}",
                            modifier = Modifier.weight(1f),
                        ) { viewModel.vote(id, voteAmount) }
                    }
                    duel?.artist2Id?.let { id ->
                        DMButton(
                            "${strings.vote} ${duel?.artist2?.displayName ?: strings.artist2}",
                            style = DMButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.vote(id, voteAmount) }
                    }
                }
                Text("${strings.oneVote} = $voteAmount ${strings.credits}", color = colors.mutedForeground)
                // Saisie de message.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(strings.saySomething, color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { viewModel.sendMessage(draft); draft = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    DMButton(strings.send, style = DMButtonStyle.SECONDARY) { viewModel.sendMessage(draft); draft = "" }
                }
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
    }
}

/** Emojis de réaction (identiques au live/web). */
private val DuelReactionEmojis = listOf("🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂")

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

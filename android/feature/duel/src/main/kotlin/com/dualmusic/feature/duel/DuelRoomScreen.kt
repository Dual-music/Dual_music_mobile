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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DuelRoomScreen(
    viewModel: DuelViewModel,
    voteAmount: Double = 10.0,
    onLeave: () -> Unit = {},
    // Ouvre le profil public d'un artiste (clic sur son nom) — userId de l'artiste.
    onOpenArtist: (String) -> Unit = {},
) {
    val duel by viewModel.duel.collectAsStateWithLifecycle()
    val totals by viewModel.voteTotals.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    // Vidéo par SLOT (parité web : 1 room LiveKit par acteur → 3 connexions).
    val a1Video by viewModel.artist1Video.collectAsStateWithLifecycle()
    val a2Video by viewModel.artist2Video.collectAsStateWithLifecycle()
    val mgrVideo by viewModel.managerVideo.collectAsStateWithLifecycle()
    // État micro distant par slot (pour l'icône micro des petites cases).
    val a1Mic by viewModel.mediaA1.remoteMicOn.collectAsStateWithLifecycle()
    val a2Mic by viewModel.mediaA2.remoteMicOn.collectAsStateWithLifecycle()
    val mgrMic by viewModel.mediaMgr.remoteMicOn.collectAsStateWithLifecycle()
    val mySlot by viewModel.mySlot.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    // Modération manager : artistes coupés d'autorité + spectateurs bannis (synchronisés temps réel).
    val mutedArtists by viewModel.mutedArtists.collectAsStateWithLifecycle()
    val bannedUserIds by viewModel.bannedUserIds.collectAsStateWithLifecycle()
    val iAmBanned by viewModel.iAmBanned.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val emojiFeed by viewModel.emojiFeed.collectAsStateWithLifecycle()
    val likes by viewModel.likes.collectAsStateWithLifecycle()
    val uiPrefs by UiPreferencesStore.state.collectAsStateWithLifecycle()
    val topDonor by viewModel.topDonor.collectAsStateWithLifecycle()
    val isManager by viewModel.isManager.collectAsStateWithLifecycle()
    // Modération déléguée : modérateurs désignés + mon id (pour savoir si JE suis l'un d'eux).
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserIdFlow.collectAsStateWithLifecycle()
    val isModerator = myUserId != null && moderators.any { it.userId == myUserId }
    var showModeratorsDialog by remember { mutableStateOf(false) }
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
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
    // Diffusion caméra/micro (participant : artiste 1/2 ou manager) — parité web.
    val canPublish by viewModel.canPublish.collectAsStateWithLifecycle()
    val broadcasting by viewModel.broadcasting.collectAsStateWithLifecycle()
    val micOn by viewModel.micOn.collectAsStateWithLifecycle()
    val camOn by viewModel.camOn.collectAsStateWithLifecycle()
    // Focus : imposé par le manager (synchronisé) et/ou choix local du spectateur (tap sur une case).
    val forcedFocus by viewModel.forcedFocus.collectAsStateWithLifecycle()
    // Prix d'un vote configuré par l'admin (synchronisé avec le web) — remplace le 10 codé en dur.
    val votePrice by viewModel.votePrice.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    var localFocus by remember { mutableStateOf<String?>(null) }
    // Bannière « vous avez reçu un cadeau » (destinataire) — auto-effacée après quelques secondes.
    val giftReceived by viewModel.giftReceived.collectAsStateWithLifecycle()
    LaunchedEffect(giftReceived) {
        if (giftReceived != null) { kotlinx.coroutines.delay(3500); viewModel.clearGiftReceived() }
    }
    // Célébration du vainqueur : PLEIN ÉCRAN persistante pour TOUS, pilotée par le broadcast du
    // manager (winner_announced / winner_stopped) — reste jusqu'à ce que le manager l'arrête.
    val winnerInfo by viewModel.winnerInfo.collectAsStateWithLifecycle()
    // Pluie d'emojis d'acclamation tant que la célébration est active.
    var winnerEmojis by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    LaunchedEffect(winnerInfo) {
        winnerEmojis = emptyList()
        if (winnerInfo != null) {
            val acc = listOf("👏", "🎉", "🔥", "🎊", "⭐", "💜", "🏆", "😍", "🙌")
            var i = 0L
            while (true) {
                winnerEmojis = (winnerEmojis + (i to acc[(i % acc.size).toInt()])).takeLast(24); i++
                kotlinx.coroutines.delay(220)
            }
        }
    }
    // Applaudissements pendant la célébration du vainqueur (démarre à l'annonce, s'arrête à l'arrêt).
    val winnerActive = winnerInfo != null
    DisposableEffect(winnerActive) {
        val player = if (winnerActive) {
            runCatching {
                android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource("https://assets.mixkit.co/active_storage/sfx/1011/1011-preview.mp3")
                    isLooping = true
                    setOnPreparedListener { runCatching { start() } }
                    prepareAsync()
                }
            }.getOrNull()
        } else {
            null
        }
        onDispose { runCatching { player?.stop() }; runCatching { player?.release() } }
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
    var showFilters by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // Panneau MANAGER (arbitre) : temps de parole (slider min), vainqueur, fin.
    var showManagerPanel by remember { mutableStateOf(false) }
    // Feuille DÉDIÉE à l'enregistrement (manager) — séparée du panneau ci-dessus, parité
    // concert/live/compétition (évite de mélanger « enregistrer » avec le reste de la gestion).
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
    // Spectateur ciblé pour un bannissement (le manager a tapé sa photo dans le chat) → confirmation.
    var banTarget by remember { mutableStateOf<DuelChatMessage?>(null) }
    // Sélecteur de pub sponsor (déclenché par l'icône 📢 du rail, plus par un bouton bas).
    var showAdPicker by remember { mutableStateOf(false) }
    // Barre du bas condensée (parité web) : emojis repliables + panneau de vote + pop-up saisie.
    var showEmojiBar by remember { mutableStateOf(false) }
    var showVotePanel by remember { mutableStateOf(false) }
    var showCommentPopup by remember { mutableStateOf(false) }
    // Message auquel on répond (tap sur un message du chat) — parité web.
    var replyingTo by remember { mutableStateOf<DuelChatMessage?>(null) }
    // Emoji picker dans le pop-up de saisie.
    var showPopupEmojis by remember { mutableStateOf(false) }

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
            duel?.artist1Id?.let { add(SlotTile("artist1", duel?.artist1?.displayName ?: strings.artist1, a1Video, viewModel.mediaA1, duel?.artist1?.avatarUrl)) }
            duel?.artist2Id?.let { add(SlotTile("artist2", duel?.artist2?.displayName ?: strings.artist2, a2Video, viewModel.mediaA2, duel?.artist2?.avatarUrl)) }
            duel?.managerId?.let { add(SlotTile("manager", duel?.manager?.displayName ?: "Manager", mgrVideo, viewModel.mediaMgr, duel?.manager?.avatarUrl)) }
        }
        // Case en grand : focus imposé manager > choix local (tap) > 1re case EN DIRECT (souvent
        // l'adversaire) > 1re case. Sans ça, on s'affichait SOI-MÊME (placeholder) en grand et le
        // web se retrouvait en petite vignette → "je ne vois pas le web".
        val effectiveFocus = forcedFocus ?: localFocus
        val mainTile = slotTiles.find { it.slot == effectiveFocus }
            ?: slotTiles.firstOrNull { it.track != null }
            ?: slotTiles.firstOrNull()
        // Le performeur est-il la GRANDE case ? → son chrono s'affiche sous le top-donateur (navbar),
        // pas en overlay. (Sur les petites cases, le chrono reste sur la vignette, cf. plus bas.)
        val mainArtistId = when (mainTile?.slot) { "artist1" -> duel?.artist1Id; "artist2" -> duel?.artist2Id; "manager" -> duel?.managerId; else -> null }
        val mainPerformerActive = timer.isRunning && mainArtistId != null && mainArtistId == timer.targetId
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
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 300.dp),
        ) {
            shownGift?.let { GiftReceivedCard(it) }
        }
        // (Réactions flottantes déplacées PLUS BAS, après les vignettes, pour passer AU-DESSUS d'elles.)

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
            // Haut : infos TRANSPARENTES (les pastilles badge/spectateurs/likes ont leur propre fond
            // translucide) ; masquée avec le reste via ✕.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Ligne 1 (allégée) : badge DUEL + likes + signaler + QUITTER rouge (tout le monde).
                com.dualmusic.core.ui.live.LiveHeader(
                    eventLabel = "",
                    badgeText = "DUEL",
                    viewerCount = viewerCount,
                    likes = likes,
                    showViewers = false,
                    onReport = { showReport = true },
                    onParticipants = { showDescription = true }, // infos (remplace l'ancienne icône doc du rail)
                    onQuit = onLeave,
                )
                // Ligne 2 (désengorge le badge) : spectateurs + partage + mon état micro/caméra,
                // et — à DROITE — le nom + chrono du performeur dont la case est en grand (jamais de débordement).
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("$viewerCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable {
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, strings.shareLiveText) }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Share, contentDescription = "Partager", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    if (broadcasting) {
                        Text("Vous", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = if (micOn) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                        Icon(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = null, tint = if (camOn) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                    }
                    // Performeur en grand : nom (tronqué) + chrono poussés à DROITE, juste après le
                    // contenu existant de cette 2ᵉ ligne — jamais de débordement ni de chevauchement.
                    if (mainPerformerActive) {
                        Spacer(Modifier.weight(1f))
                        val perfName = when (timer.targetId) {
                            duel?.artist1Id -> duel?.artist1?.displayName ?: strings.artist1
                            duel?.artist2Id -> duel?.artist2?.displayName ?: strings.artist2
                            else -> "Manager"
                        }.let { if (it.length > 12) it.take(11) + "…" else it }
                        com.dualmusic.core.ui.live.LiveCountdown(endsAtIso = timer.endsAt, label = perfName)
                    }
                }
                val a1 = duel?.artist1Id
                val a2 = duel?.artist2Id
                VoteBar(
                    leftName = duel?.artist1?.displayName ?: strings.artist1,
                    rightName = duel?.artist2?.displayName ?: strings.artist2,
                    leftTotal = a1?.let { totals[it] } ?: 0.0,
                    rightTotal = a2?.let { totals[it] } ?: 0.0,
                    // Tap sur le nom d'un artiste → son profil public (spectateurs).
                    onLeftClick = { a1?.let(onOpenArtist) },
                    onRightClick = { a2?.let(onOpenArtist) },
                )
                // Meilleur donateur : ticker qui défile TOUJOURS droite→gauche (basicMarquee ne
                // bougeait pas car le texte tenait dans la largeur → défilement manuel garanti).
                topDonor?.let { d ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                            .background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScrollingLabel("👑  ${d.name}  ·  ${d.amount} 🎁", Modifier.weight(1f))
                    }
                }
                // (Le chrono du performeur en grand est désormais sur la 2ᵉ ligne d'en-tête, à droite.)
                // (Le chrono de temps de parole n'est plus affiché ici : il apparaît directement SUR
                //  la case de l'artiste concerné — plus de doublon surchargé en haut de l'écran.)
                // (Contrôles arbitre/manager déplacés dans un PANNEAU dédié — bouton 🎛 du rail —
                //  pour ne plus bloquer l'écran en permanence.)
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
                    // Les messages des spectateurs bannis sont masqués partout (parité web).
                    items(messages.filter { it.userId !in bannedUserIds }) { msg ->
                        // Résolution du message parent (réponse) → citation grisée façon TikTok.
                        val parent = msg.parentId?.let { pid -> messages.find { it.id == pid } }
                        // Le manager peut bannir un spectateur (pas un participant) en tapant sa photo.
                        val isParticipant = msg.userId == duel?.artist1Id || msg.userId == duel?.artist2Id || msg.userId == duel?.managerId
                        val canBan = (isManager || isModerator) && !isParticipant
                        Row(
                            verticalAlignment = Alignment.Top,
                            // Tap = répondre ; APPUI LONG (manager, sur un spectateur) = bannir.
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .combinedClickable(
                                    onClick = { replyingTo = msg; showCommentPopup = true },
                                    onLongClick = { if (canBan) banTarget = msg },
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            // Avatar rond (photo de profil ou initiale) — le manager tape ICI pour bannir.
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
                            // Nom + contenu en COLONNE → le message long revient à la ligne.
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                // Citation GRISÉE du message auquel on répond (le différencie de la réponse).
                                if (parent != null) {
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.width(2.dp).height(13.dp).background(colors.mutedForeground))
                                        Text(
                                            "  ↩ ${parent.authorName} : ${parent.content}",
                                            color = colors.mutedForeground, fontSize = 10.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // « Message... » = pill déclencheur ; la vraie saisie s'ouvre en pop-up (capture 2).
                    // Si JE suis banni par le manager, OU que le manager a désactivé le chat pour
                    // tous, la saisie est bloquée (parité web).
                    val chatDisabled = duel?.chatEnabled == false
                    Row(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .then(if (iAmBanned || chatDisabled) Modifier else Modifier.clickable { showCommentPopup = true })
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            when { iAmBanned -> Icons.Filled.Block; chatDisabled -> Icons.Filled.Lock; else -> Icons.AutoMirrored.Filled.Chat },
                            contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp),
                        )
                        Text(
                            when { iAmBanned -> "Vous avez été banni"; chatDisabled -> "Chat désactivé"; else -> "Message..." },
                            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1,
                        )
                    }
                    BottomBarIcon(Icons.Filled.Favorite, Color(0xFFFF4D6D)) { viewModel.sendLike() }
                    BottomBarIcon(Icons.Filled.Mood, Color.White) { showEmojiBar = !showEmojiBar }
                    // Cadeau : bouton principal (violet) — parité web.
                    Box(
                        modifier = Modifier.size(40.dp).background(DualMusicTheme.gradients.primary, CircleShape).clickable { showGiftPanel = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CardGiftcard, contentDescription = strings.sendGift, tint = Color.White, modifier = Modifier.size(19.dp)) }
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
                // (Le bouton QUITTER rouge et les Infos sont désormais dans le header, en haut.)
                MediaRailButton(Icons.Filled.Close, "Masquer tout") { overlaysHidden = true }
                // Œil : masque/affiche seulement les petites cases (vignettes).
                MediaRailButton(if (thumbnailsHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Petites cases") { thumbnailsHidden = !thumbnailsHidden }
                // Manager (arbitre) : panneau de gestion (temps de parole, vainqueur, fin).
                if (isManager) {
                    MediaRailButton(Icons.Filled.Tune, "Gérer le duel", accent = true) { showManagerPanel = true }
                }
                // Modérateurs désignés (ban/masquer message) — visible du manager ET des
                // modérateurs eux-mêmes (pour qu'ils voient qui d'autre a ce pouvoir).
                if (isManager || isModerator) {
                    MediaRailButton(Icons.Filled.Groups, "Modérateurs", accent = true) { showModeratorsDialog = true }
                }
                // Enregistrement : icône DÉDIÉE (parité concert/live/compétition — évite de la
                // mélanger avec le reste de la gestion du duel) — pastille rouge/orange dès qu'un
                // segment tourne ou est en pause. Masquée si l'admin a coupé l'enregistrement
                // pour les duels.
                if (isManager && recMode == "manual") {
                    Box {
                        MediaRailButton(Icons.Filled.FiberManualRecord, "Enregistrement", accent = true) { showRecordingSheet = true }
                        com.dualmusic.feature.sponsor.RecordingRailBadge(
                            active = recActive, paused = recPaused,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                        )
                    }
                } else if (isManager && recMode == "auto") {
                    com.dualmusic.feature.sponsor.RecordingHostButton(mode = recMode, active = recActive, busy = recBusy, onToggle = {})
                }
                // 📢 Pub sponsor (manager) — EN BAS du rail : lance/arrête la pub (ne masque plus la
                //    ligne de message). Masqué pendant l'annonce du vainqueur.
                if (isManager && duel?.allowsSponsorAds != false && winnerInfo == null) {
                    MediaRailButton(
                        Icons.Filled.Campaign,
                        if (sponsorAd != null) "Arrêter la pub" else "Lancer une pub",
                        accent = true,
                    ) {
                        if (sponsorAd != null) viewModel.sponsor.stop()
                        else { viewModel.sponsor.loadAds(); showAdPicker = true }
                    }
                }
                // Participant : bouton « Démarrer » TOUT EN BAS du rail (sous la pub, comme demandé) ;
                // une fois en direct il laisse place à Filtres + Réglages au même endroit.
                if (canPublish) {
                    if (!broadcasting) {
                        MediaRailButton(Icons.Filled.Podcasts, "Démarrer", accent = true) {
                            if (hasCamMic()) viewModel.startBroadcast() else camMicLauncher.launch(camMicPerms)
                        }
                    } else {
                        // Filtres vidéo (parité web) — juste avant Réglages, pour le publieur.
                        MediaRailButton(Icons.Filled.AutoAwesome, "Filtres", accent = true) { showFilters = true }
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

        // Pop-up FILTRES vidéo (icône Filtres du rail) — presets partagés avec le web.
        if (showFilters && broadcasting && !overlaysHidden) {
            Box(Modifier.fillMaxSize().clickable { showFilters = false })
            Column(
                modifier = Modifier.align(Alignment.CenterStart).statusBarsPadding().padding(start = 62.dp)
                    .width(250.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(12.dp)).padding(8.dp),
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

        // Vignettes multi-cam = les slots autres que celui affiché en grand (parité web).
        val thumbTiles = slotTiles.filter { it !== mainTile }
        if (thumbTiles.isNotEmpty() && !overlaysHidden && !thumbnailsHidden) {
            Column(
                // Descendues au niveau des commentaires (à droite), sans toucher la ligne de saisie.
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 84.dp),
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
                            // Chrono du performeur AFFICHÉ SUR SA CASE (parité web) — même en petit.
                            val thumbArtistId = when (tile.slot) { "artist1" -> duel?.artist1Id; "artist2" -> duel?.artist2Id; else -> duel?.managerId }
                            if (timer.isRunning && thumbArtistId != null && thumbArtistId == timer.targetId) {
                                Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp)) {
                                    com.dualmusic.core.ui.live.LiveCountdown(endsAtIso = timer.endsAt)
                                }
                            }
                            // Libellé : icônes MICRO + CAMÉRA (ON vert / OFF rouge) + nom TRONQUÉ (…).
                            val tileMicOn = when {
                                tile.slot == mySlot -> broadcasting && micOn   // ma case : micro ON seulement si je diffuse
                                tile.slot == "artist1" -> a1Mic
                                tile.slot == "artist2" -> a2Mic
                                else -> mgrMic
                            }
                            val tileCamOn = tile.track != null
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    if (tileMicOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                                    contentDescription = null,
                                    tint = if (tileMicOn) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    modifier = Modifier.size(11.dp),
                                )
                                Icon(
                                    if (tileCamOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    contentDescription = null,
                                    tint = if (tileCamOn) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(tile.label, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
        // --- Réactions flottantes (cœurs/emojis) — rendues APRÈS les vignettes → passent AU-DESSUS d'elles.
        FloatingReactionsLayer(reactions = emojiFeed, reduceAnimations = uiPrefs.reduceAnimations)

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

        // Panneau MANAGER (arbitre) : temps de parole (slider min) + vainqueur + fin + enregistrement.
        if (showManagerPanel && isManager) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showManagerPanel = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.72f)
                    .background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎛 Gestion du duel", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showManagerPanel = false })
                }
                // Chat du duel : le manager (hôte) peut le couper entièrement pour tous — pouvoir
                // exclusif, jamais délégué aux modérateurs désignés.
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💬 Chat activé", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = duel?.chatEnabled ?: true, onCheckedChange = { viewModel.toggleChat(it) })
                }
                ManagerDuelControls(
                    artist1Name = duel?.artist1?.displayName ?: strings.artist1,
                    artist2Name = duel?.artist2?.displayName ?: strings.artist2,
                    artist1Id = duel?.artist1Id,
                    artist2Id = duel?.artist2Id,
                    timerRunning = timer.isRunning,
                    mutedArtists = mutedArtists,
                    onToggleMute = { viewModel.toggleMuteArtist(it) },
                    onGiveTurn = { id, sec -> viewModel.startTimer(id, sec) },
                    onStopTimer = { viewModel.stopTimer() },
                    onAnnounceWinner = { viewModel.announceWinnerAuto(); showManagerPanel = false },
                    onEnd = { viewModel.endDuel(onLeave) },
                )
            }
        }

        // Feuille DÉDIÉE à l'enregistrement (manager) : chrono + Pause/Reprendre/Annuler/Sauvegarder.
        // Extraite en fonction locale (comme d'autres feuilles de cet écran) pour rester sous la
        // limite de taille de méthode JVM (64 Ko) une fois toutes les fonctionnalités cumulées.
        @Composable fun RecordingSheet() {
        if (showRecordingSheet) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showRecordingSheet = false })
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background).navigationBarsPadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Text("🔴 Enregistrement du duel", color = colors.foreground, fontWeight = FontWeight.Bold)
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

        // Confirmation de bannissement d'un spectateur (le manager a tapé sa photo dans le chat).
        banTarget?.let { target ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { banTarget = null })
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp))
                    .background(colors.background).padding(DualMusicTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            ) {
                Text("🚫 Bannir ${target.authorName} ?", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Ce spectateur ne pourra plus écrire dans ce direct et ses messages seront masqués pour tous.",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton("Annuler", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f)) { banTarget = null }
                    DMButton("Bannir", modifier = Modifier.weight(1f)) {
                        viewModel.banUser(target.userId, target.content.take(200))
                        banTarget = null
                    }
                }
            }
        }

        // Modérateurs désignés (ban/masquer message) — hôte : gère la liste ; modérateur : la consulte.
        if (showModeratorsDialog) {
            EventModeratorsDialog(viewModel = viewModel, isManager = isManager, onDismiss = { showModeratorsDialog = false })
        }

        // Pop-up de saisie « Commenter » (capture 2) — ouvert depuis le pill « Message... ».
        if (showCommentPopup) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showCommentPopup = false; replyingTo = null })
            fun send() {
                // Banni par le manager OU chat désactivé pour tous → aucun envoi possible (parité web).
                if (!iAmBanned && duel?.chatEnabled != false && draft.isNotBlank()) {
                    // Vraie RÉPONSE liée (parent_id) — la citation grisée est rendue côté affichage.
                    viewModel.sendMessage(draft, replyingTo?.id); draft = ""
                }
                showCommentPopup = false; replyingTo = null; showPopupEmojis = false
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.background)
                    .navigationBarsPadding().imePadding().padding(DualMusicTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (replyingTo != null) "Répondre" else "Commenter", color = colors.foreground, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = colors.mutedForeground, modifier = Modifier.size(20.dp).clickable { showCommentPopup = false; replyingTo = null; showPopupEmojis = false })
                }
                // Carte de CITATION du message auquel on répond (indexation façon TikTok).
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
                // Emoji picker (repliable) : insère l'emoji dans le message.
                if (showPopupEmojis) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DuelReactionEmojis.forEach { e ->
                            Box(
                                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.12f)).clickable { draft += e }.padding(horizontal = 8.dp, vertical = 6.dp),
                            ) { Text(e, fontSize = 18.sp) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.12f)).clickable { showPopupEmojis = !showPopupEmojis },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Mood, contentDescription = "Emojis", tint = colors.foreground, modifier = Modifier.size(20.dp)) }
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

        // Célébration du vainqueur : PLEIN ÉCRAN pour TOUS (comme une pub), acclamations d'emojis +
        // confettis, PERSISTANTE jusqu'à ce que le MANAGER l'arrête (n'arrête pas le direct).
        winnerInfo?.let { w ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                WinnerCelebration(
                    winnerName = w.name,
                    title = strings.winnerTitle,
                    subtitle = "${w.votes} voix · ${w.percent}% des votes",
                    reduceAnimations = uiPrefs.reduceAnimations,
                )
                // Emojis d'acclamation ÉPARPILLÉS sur tout l'écran, en mouvement, jusqu'à l'arrêt.
                if (!uiPrefs.reduceAnimations) ScatteredEmojiLayer(winnerEmojis)
                // Manager : bouton ARRÊTER remonté (au-dessus de la zone pub) — n'arrête pas le direct.
                if (isManager) {
                    DMButton(
                        "Arrêter l'annonce",
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 150.dp),
                    ) { viewModel.stopWinnerAnnouncement() }
                }
            }
        }

        // Sélecteur de pub (ouvert par l'icône 📢 du rail) : choisir la pub à diffuser.
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

        // Diffusion pub sponsor : overlay vidéo pour tous + contrôle pour le manager
        // (masqué si l'organisateur a désactivé les pubs sur ce duel — parité web).
        SponsorAdLayer(
            activeAd = sponsorAd,
            // Masqué pendant l'annonce du vainqueur (pour ne pas cacher « Arrêter l'annonce »).
            canTrigger = isManager && duel?.allowsSponsorAds != false && winnerInfo == null,
            ads = sponsorAds,
            busy = sponsorBusy,
            onLoadAds = { viewModel.sponsor.loadAds() },
            onPlay = { viewModel.sponsor.play(it) },
            onStop = { viewModel.sponsor.stop() },
            // Le déclencheur est désormais une ICÔNE du rail (voir 📢), pas un bouton bas qui
            // masquait la ligne de message. L'overlay + l'arrêt restent gérés ici.
            showTriggerButton = false,
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

        // --- Barrière de BANNISSEMENT (parité web BannedAccessGate) : plein écran OPAQUE qui bloque
        //     tout et empêche de rejoindre/rester dans ce duel dès que je suis banni par le manager. ---
        if (iAmBanned) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background)
                    // Capte tous les taps (sans ondulation) → rien derrière n'est cliquable/joignable.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding().navigationBarsPadding().padding(DualMusicTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(64.dp))
                Text("Accès bloqué", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "Le manager vous a banni de ce duel. Vous ne pouvez plus y participer ni le rejoindre.",
                    color = colors.mutedForeground, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                DMButton("Quitter", modifier = Modifier.fillMaxWidth(0.7f), onClick = onLeave)
            }
        }
    }
}

/**
 * Modérateurs désignés du duel (max [com.dualmusic.domain.moderation.MAX_EVENT_MODERATORS]) :
 * l'hôte + les spectateurs qu'il nomme partagent le pouvoir de bannir/masquer un message —
 * JAMAIS le chat on/off, réservé au manager (hôte). Parité web (panneau de modération). Suit le
 * même patron que `AddCandidateDialog` (`ManagerCompetitionsScreen.kt`) : `AlertDialog` privé,
 * non partagé entre écrans.
 */
@Composable
private fun EventModeratorsDialog(viewModel: DuelViewModel, isManager: Boolean, onDismiss: () -> Unit) {
    val colors = DualMusicTheme.colors
    val moderators by viewModel.moderators.collectAsStateWithLifecycle()
    val viewers by viewModel.viewers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (isManager) viewModel.loadViewers() }
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
                            if (isManager) {
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
                if (isManager) {
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
        confirmButton = { DMButton("Fermer", onClick = onDismiss) },
    )
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
    val avatarUrl: String? = null,
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
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        tile.client.room.initVideoRenderer(this)
                        // Cadre ENTIER visible (jamais rogné) — parité avec ce que montre le PC.
                        setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    }
                },
                update = { renderer -> track.addRenderer(renderer) },
            )
        }
    } else {
        // Placeholder « caméra off » : PHOTO DE PROFIL au centre (sinon initiale) + caméra barrée.
        Box(modifier = modifier.background(Color(0xFF241338)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.45f))) {
                    com.dualmusic.core.ui.components.DMRemoteImage(
                        url = tile.avatarUrl,
                        contentDescription = tile.label,
                        modifier = Modifier.fillMaxSize(),
                        fallbackEmoji = tile.label.take(1).uppercase(),
                    )
                }
                Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Emojis d'acclamation ÉPARPILLÉS sur tout l'écran, chacun oscillant (célébration du vainqueur). */
@Composable
private fun ScatteredEmojiLayer(emojis: List<Pair<Long, String>>) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        emojis.forEach { (id, e) ->
            key(id) {
                val transition = rememberInfiniteTransition(label = "sc")
                val drift by transition.animateFloat(
                    initialValue = 0f, targetValue = -36f,
                    animationSpec = infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Reverse),
                    label = "scy",
                )
                val bx = ((id * 73) % 100).toFloat() / 100f * (w - 40f)
                val by = ((id * 137) % 100).toFloat() / 100f * (h - 60f)
                Text(e, fontSize = 30.sp, modifier = Modifier.graphicsLayer { translationX = bx; translationY = by + drift })
            }
        }
    }
}

/** Libellé qui défile en continu droite→gauche (ticker), quelle que soit sa longueur. */
@Composable
private fun ScrollingLabel(text: String, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "mq")
    val p by infinite.animateFloat(
        initialValue = 1f, targetValue = -1.3f,
        animationSpec = infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)),
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

/** Bouton rond translucide de la barre du bas (like / emoji / classement / vote). Compact. */
@Composable
private fun BottomBarIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}

/** Panneau de contrôle de l'arbitre (manager) : minuteur de parole, vainqueur, fin du duel. */
@Composable
private fun ManagerDuelControls(
    artist1Name: String,
    artist2Name: String,
    artist1Id: String?,
    artist2Id: String?,
    timerRunning: Boolean,
    mutedArtists: Set<String>,
    onToggleMute: (String) -> Unit,
    onGiveTurn: (String, Int) -> Unit,
    onStopTimer: () -> Unit,
    onAnnounceWinner: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    // Durée définie en MINUTES via un slider (1→10 min), au lieu de secondes prédéfinies.
    var minutes by remember { mutableStateOf(2f) }
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(DualMusicTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
    ) {
        val mm = minutes.toInt()
        val durLabel = if (mm >= 60) "${mm / 60}h${(mm % 60).toString().padStart(2, '0')}" else "$mm min"
        Text("🎛 ${s.speakingTime} : $durLabel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = 1f..120f, // 1 min → 2 h
            modifier = Modifier.fillMaxWidth(),
        )
        if (timerRunning) {
            Box(
                modifier = Modifier.background(colors.destructive, RoundedCornerShape(999.dp)).clickable { onStopTimer() }.padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Text(s.stopAction, color = Color.White, fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            artist1Id?.let { DMButton("🎤 $artist1Name", modifier = Modifier.weight(1f), onClick = { onGiveTurn(it, (minutes * 60).toInt()) }) }
            artist2Id?.let { DMButton("🎤 $artist2Name", style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f), onClick = { onGiveTurn(it, (minutes * 60).toInt()) }) }
        }
        // Couper/réactiver d'AUTORITÉ le micro de chaque artiste (synchronisé partout, parité web).
        Text("🎙 Micro des artistes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            artist1Id?.let { id ->
                val muted = mutedArtists.contains(id)
                DMButton(
                    if (muted) "🔊 Réactiver $artist1Name" else "🔇 Couper $artist1Name",
                    style = if (muted) DMButtonStyle.SECONDARY else DMButtonStyle.OUTLINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onToggleMute(id) },
                )
            }
            artist2Id?.let { id ->
                val muted = mutedArtists.contains(id)
                DMButton(
                    if (muted) "🔊 Réactiver $artist2Name" else "🔇 Couper $artist2Name",
                    style = if (muted) DMButtonStyle.SECONDARY else DMButtonStyle.OUTLINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onToggleMute(id) },
                )
            }
        }
        // UN SEUL bouton : le vainqueur est calculé AUTOMATIQUEMENT selon les votes (parité web),
        // puis la célébration s'affiche en plein écran chez tous les spectateurs.
        DMButton("🏆 ${s.announceWinner}", modifier = Modifier.fillMaxWidth(), onClick = onAnnounceWinner)
        DMButton(s.endDuelBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth(), onClick = onEnd)
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
    onLeftClick: () -> Unit = {},
    onRightClick: () -> Unit = {},
) {
    val colors = DualMusicTheme.colors
    val sum = (leftTotal + rightTotal).takeIf { it > 0 } ?: 1.0
    val leftShare = (leftTotal / sum).toFloat().coerceIn(0.02f, 0.98f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Noms cliquables (souligné) → profil public de l'artiste.
            Text(
                "$leftName · ${leftTotal.toInt()}",
                color = colors.foreground, fontWeight = FontWeight.Bold,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onLeftClick),
            )
            Text(
                "${rightTotal.toInt()} · $rightName",
                color = colors.foreground, fontWeight = FontWeight.Bold,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onRightClick),
            )
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

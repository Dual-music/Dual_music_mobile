package com.dualmusic.feature.concert

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.components.formatCredits
import com.dualmusic.core.ui.currency.LocalCurrency
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.model.EventStatus
import com.dualmusic.domain.realtime.PresencePayload
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.replay.ReplayVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la page « Concerts » (3 onglets) — parité web :
 *  - En direct / À venir : fusion `GET /concerts` (admin) + `GET /artist-concerts`, filtrée par statut.
 *  - Replays : `GET /replays?sourceType=concert&isPublic=true`.
 *  - Spectateurs temps réel par carte : présence Socket.IO `/live` (rooms `concert:<id>`).
 *  - File d'approbation admin conservée (en haut, si admin).
 */
class ConcertsViewModel(
    private val repository: ConcertRepository,
    private val realtime: RealtimeClient,
) : ViewModel() {

    data class UiState(
        val live: List<Concert> = emptyList(),
        val upcoming: List<Concert> = emptyList(),
        val replays: List<ReplayVideo> = emptyList(),
        val presence: Map<String, Int> = emptyMap(),
        val perCreditEur: Double = 0.0,
        val loading: Boolean = false,
        val pending: List<Concert> = emptyList(),
        val isAdmin: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var liveSession: NamespaceSession? = null

    fun load() {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val admin = runCatching { repository.adminConcerts() }.getOrDefault(emptyList())
            val artist = runCatching { repository.concerts(limit = 100) }.getOrDefault(emptyList())
            val all = (admin + artist).distinctBy { it.id }
            val live = all.filter { it.status == EventStatus.LIVE }
            val upcoming = all.filter { it.status == EventStatus.UPCOMING || it.status == EventStatus.SCHEDULED }
            val replays = runCatching { repository.concertReplays() }.getOrDefault(emptyList())
            val rate = runCatching { repository.perCreditEur() }.getOrDefault(0.0)
            _ui.update { it.copy(live = live, upcoming = upcoming, replays = replays, perCreditEur = rate, loading = false) }
            connectPresence(live)
            // File d'approbation (admin).
            if (runCatching { repository.amIAdmin() }.getOrDefault(false)) {
                _ui.update { it.copy(isAdmin = true) }
                loadPending()
            }
        }
    }

    private fun loadPending() {
        viewModelScope.launch {
            _ui.update { it.copy(pending = runCatching { repository.pendingConcerts() }.getOrDefault(emptyList())) }
        }
    }

    /** Approuve/rejette un concert (admin) puis recharge. */
    fun review(id: String, approve: Boolean) {
        viewModelScope.launch {
            runCatching { repository.reviewConcert(id, approve) }.onSuccess {
                loadPending()
                load()
            }
        }
    }

    /** Prix minimum d'une dédicace de concert (config économique, section `dedication`). */
    suspend fun dedicationMinPrice(): Double = repository.dedicationMinPrice()

    /**
     * Envoie une demande de dédicace pour un concert AVANT son direct (le serveur rejette sinon).
     * @param onDone appelé avec `null` en cas de succès, sinon un message d'erreur affichable.
     */
    fun sendDedication(concertId: String, message: String, priceCredits: Double, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.purchaseDedication(concertId, message, priceCredits) }
                .onSuccess { onDone(null) }
                .onFailure { onDone(it.message ?: com.dualmusic.core.ui.i18n.appStrings.sendFailed) }
        }
    }

    private fun connectPresence(concerts: List<Concert>) {
        if (concerts.isEmpty()) return
        val session = realtime.session(Realtime.Namespace.LIVE).also { liveSession = it }
        viewModelScope.launch {
            session.onConnect { concerts.forEach { session.join(Realtime.RoomType.CONCERT, it.id) } }
            session.on(Realtime.RealtimeEvent.PRESENCE, PresencePayload.serializer()) { p ->
                val id = p.room?.substringAfter("concert:", "")?.takeIf { it.isNotBlank() }
                if (id != null) _ui.update { it.copy(presence = it.presence + (id to p.count)) }
            }
            session.connect()
        }
    }

    override fun onCleared() {
        liveSession?.disconnect()
        super.onCleared()
    }
}

/**
 * Page « Concerts » : titre + recherche + 3 onglets (En direct / À venir / Replays).
 *
 * @param onOpen ouvre la room d'un concert (live) ou son détail/billetterie (à venir).
 * @param onOpenReplay ouvre le lecteur d'un replay de concert.
 */
@Composable
fun ConcertsListScreen(
    viewModel: ConcertsViewModel,
    onOpen: (Concert) -> Unit = {},
    onOpenReplay: (ReplayVideo) -> Unit = {},
    /** Ouvre l'écran Sponsoring (Profil) avec cet événement présélectionné — voir affiche « Sponsoriser ».
     *  (Pas de bouton "Demander une dédicace" ici : contrairement au web, aucun flux d'envoi de
     *  dédicace n'existe encore sur mobile — même le DTO `DedicationRequest` n'a pas `priceCredits`,
     *  requis par le backend. À construire séparément avant d'exposer un bouton fonctionnel.) */
    onRequestSponsor: (eventType: String, eventId: String) -> Unit = { _, _ -> },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()

    fun matchConcert(c: Concert) = q.isEmpty() ||
        c.title.lowercase().contains(q) ||
        (c.artist?.displayName?.lowercase()?.contains(q) == true) ||
        (c.artistName?.lowercase()?.contains(q) == true)
    fun matchReplay(r: ReplayVideo) = q.isEmpty() || (r.title?.lowercase()?.contains(q) == true)

    val live = ui.live.filter(::matchConcert)
    val upcoming = ui.upcoming.filter(::matchConcert)
    val replays = ui.replays.filter(::matchReplay)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.screenConcerts, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(s.concertsSubtitle, color = colors.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(s.searchPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // File d'approbation (admin).
        if (ui.isAdmin && ui.pending.isNotEmpty()) {
            Text("⏳ ${s.pendingApproval}", color = colors.accent, fontWeight = FontWeight.Bold)
            ui.pending.forEach { c -> PendingConcertRow(c, onApprove = { viewModel.review(c.id, true) }, onReject = { viewModel.review(c.id, false) }) }
        }

        // Onglets.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            TabPill("${s.duelTabLive} (${ui.live.size})", tab == 0) { tab = 0 }
            TabPill("${s.duelTabUpcoming} (${ui.upcoming.size})", tab == 1) { tab = 1 }
            TabPill("${s.duelTabReplays} (${ui.replays.size})", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> if (live.isEmpty()) EmptyConcerts(s.noConcertsLive) else live.forEach { ConcertCard(it, ui.presence[it.id], ui.perCreditEur, isLive = true, onOpen = onOpen, onRequestSponsor = onRequestSponsor, viewModel = viewModel) }
            1 -> if (upcoming.isEmpty()) EmptyConcerts(s.noConcertsUpcoming) else upcoming.forEach { ConcertCard(it, null, ui.perCreditEur, isLive = false, onOpen = onOpen, onRequestSponsor = onRequestSponsor, viewModel = viewModel) }
            else -> if (replays.isEmpty()) EmptyConcerts(s.noConcertReplays) else replays.forEach { ConcertReplayCard(it, onOpenReplay) }
        }
    }
}

@Composable
private fun EmptyConcerts(title: String) {
    DMEmptyState(title = title, icon = Icons.Filled.DateRange, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = Modifier
            .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) Color.White else colors.mutedForeground, fontSize = 13.sp) }
}

/** Carte d'un concert (En direct / À venir) : couverture + badge + infos + bouton. */
@Composable
private fun ConcertCard(
    concert: Concert,
    viewers: Int?,
    perCreditEur: Double,
    isLive: Boolean,
    onOpen: (Concert) -> Unit,
    onRequestSponsor: (String, String) -> Unit = { _, _ -> },
    viewModel: ConcertsViewModel? = null,
) {
    var showDedicationDialog by remember { mutableStateOf(false) }
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val currency = LocalCurrency.current
    val artistName = concert.artist?.displayName ?: concert.artistName ?: s.artistSingular
    val location = concert.location?.takeIf { it.isNotBlank() } ?: s.onlineLoc

    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(concert) }, padded = false) {
        // Couverture.
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
            DMRemoteImage(url = concert.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp), fallbackEmoji = "🎵")
            if (isLive) {
                Text("▶", color = Color.White, fontSize = 40.sp)
                Box(modifier = Modifier.align(Alignment.TopStart).padding(DualMusicTheme.spacing.sm).background(Color(0xFFEF4444), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("🔴 ${s.liveBadge}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            // Badge prix (à venir) : crédits + équivalent fiat, ou Gratuit.
            if (!isLive) {
                if (concert.ticketPrice > 0) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("🪙 ${formatCredits(concert.ticketPrice)} ≈ ${currency.format(concert.ticketPrice * perCreditEur)}", color = colors.foreground, fontSize = 11.sp)
                    }
                } else {
                    Box(modifier = Modifier.background(Color(0x3310B981), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("🎁 ${s.duelFree}", color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }
            }
            Text(artistName, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(concert.title, color = colors.mutedForeground, fontSize = 13.sp)
            concert.scheduledDate?.let {
                Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy '•' HH:mm")}", color = colors.mutedForeground, fontSize = 12.sp)
            }
            Text("📍 $location", color = colors.mutedForeground, fontSize = 12.sp)
            if (isLive && viewers != null) {
                Text("👥 $viewers ${s.spectators}", color = colors.mutedForeground, fontSize = 12.sp)
            }
            DMButton(
                if (isLive) s.watchLiveConcert else s.buyTicket,
                style = if (isLive) DMButtonStyle.DESTRUCTIVE else DMButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            ) { onOpen(concert) }
            // Dédicace UNIQUEMENT avant le direct (contrairement au live, où c'est possible pendant
            // la diffusion) : toutes les demandes doivent être traitées avant que le concert ne
            // démarre — le serveur rejette de toute façon (`resolveDedicationArtist`) une fois
            // passé en `live`/`ended`, mais on masque déjà le bouton côté client.
            if (!isLive && concert.isArtistConcert && concert.allowsDedications && !isDeadlinePassed(concert.dedicationSubmissionDeadline)) {
                DMButton(s.requestDedicationBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth()) {
                    showDedicationDialog = true
                }
            }
            if (concert.isArtistConcert && concert.allowsSponsorAds && !isDeadlinePassed(concert.sponsorSubmissionDeadline)) {
                DMButton(s.requestSponsorBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth()) {
                    onRequestSponsor("artist_concert", concert.id)
                }
            }
        }
    }

    if (showDedicationDialog && viewModel != null) {
        DedicationRequestDialog(
            concertId = concert.id,
            artistName = artistName,
            viewModel = viewModel,
            onDismiss = { showDedicationDialog = false },
        )
    }
}

/**
 * Popup « Demander une dédicace » (message + prix libre) — parité web `DedicationDialog`, en plus
 * compact puisque limité à un concert AVANT son direct (déclenché depuis l'affiche de la liste,
 * pas depuis une salle live).
 */
@Composable
private fun DedicationRequestDialog(concertId: String, artistName: String, viewModel: ConcertsViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var message by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf(10.0) }
    var priceText by remember { mutableStateOf("10") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val min = viewModel.dedicationMinPrice()
        minPrice = min
        priceText = min.toInt().toString()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("${s.requestDedicationBtn} — $artistName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(s.dedicationMessageLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("${s.dedicationPriceLabel} (min ${minPrice.toInt()})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = DualMusicTheme.colors.destructive, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            DMButton(if (sending) s.sending else s.send, enabled = !sending && message.isNotBlank()) {
                val price = (priceText.toDoubleOrNull() ?: minPrice).coerceAtLeast(minPrice)
                sending = true
                error = null
                viewModel.sendDedication(concertId, message.trim(), price) { err ->
                    sending = false
                    if (err == null) onDismiss() else error = err
                }
            }
        },
        dismissButton = { DMButton(s.cancel, style = DMButtonStyle.OUTLINE, enabled = !sending) { onDismiss() } },
    )
}

/** `true` seulement si une date limite est fixée ET déjà dépassée (pas de date = jamais fermé). */
private fun isDeadlinePassed(deadline: String?): Boolean {
    if (deadline == null) return false
    val clean = deadline.trim().replace(' ', 'T').substringBefore('.').substringBefore('+').removeSuffix("Z")
        .let { if (it.length > 19) it.take(19) else it }
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val parsed = runCatching { fmt.parse(clean) }.getOrNull() ?: return false
    return parsed.time < System.currentTimeMillis()
}

/** Carte d'un replay de concert : couverture + badges Gratuit/Replay disponible + Regarder. */
@Composable
private fun ConcertReplayCard(replay: ReplayVideo, onOpen: (ReplayVideo) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(replay) }, padded = false) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
            DMRemoteImage(url = replay.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp), fallbackEmoji = "🎬")
            Text("▶", color = Color.White, fontSize = 40.sp)
            if (!replay.requiresUnlock) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(DualMusicTheme.spacing.sm).background(Color(0xCC10B981), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("🎁 ${s.duelFree}", color = Color.White, fontSize = 11.sp)
                }
            }
            if (replay.videoUrl != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(DualMusicTheme.spacing.sm).background(Color(0xCC10B981), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("▶ ${s.replayAvailable}", color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(replay.title ?: s.screenConcerts, color = colors.foreground, fontWeight = FontWeight.Bold)
            replay.recordedDate?.let { Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy")}", color = colors.mutedForeground, fontSize = 12.sp) }
            DMButton(s.watchLive, modifier = Modifier.fillMaxWidth()) { onOpen(replay) }
        }
    }
}

/** Carte d'un concert en attente d'approbation (admin) : titre, date + Valider/Rejeter. */
@Composable
private fun PendingConcertRow(concert: Concert, onApprove: () -> Unit, onReject: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(concert.title, color = colors.foreground, fontWeight = FontWeight.Bold)
            concert.scheduledDate?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it), color = colors.mutedForeground) }
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                DMButton(s.approveAction, modifier = Modifier.weight(1f), onClick = onApprove)
                DMButton(s.rejectAction, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onReject)
            }
        }
    }
}

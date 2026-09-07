package com.dualmusic.feature.competition

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
import androidx.compose.material.icons.filled.Star
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
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.components.formatCredits
import com.dualmusic.core.ui.currency.LocalCurrency
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Competition
import com.dualmusic.domain.replay.ReplayVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la page « Compétitions » (3 onglets) — parité web :
 *  - En direct : `status == "live"` OU (`published` ET start ≤ maintenant < end).
 *  - À venir : le reste des `published`.
 *  - Replays : `GET /replays?sourceType=competition&isPublic=true`.
 * Données : `GET /competitions?status=published` + `?status=live` (fusion).
 */
class CompetitionsViewModel(private val repository: CompetitionRepository) : ViewModel() {

    data class UiState(
        val live: List<Competition> = emptyList(),
        val upcoming: List<Competition> = emptyList(),
        val replays: List<ReplayVideo> = emptyList(),
        val perCreditEur: Double = 0.0,
        val loading: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load() {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val published = runCatching { repository.competitions(100, "published") }.getOrDefault(emptyList())
            val liveStatus = runCatching { repository.competitions(100, "live") }.getOrDefault(emptyList())
            val all = (published + liveStatus).distinctBy { it.id }
            val now = java.time.Instant.now()
            val live = all.filter { isLiveNow(it, now) }
            val upcoming = all.filterNot { isLiveNow(it, now) }
            val replays = runCatching { repository.competitionReplays() }.getOrDefault(emptyList())
            val rate = runCatching { repository.perCreditEur() }.getOrDefault(0.0)
            _ui.update { it.copy(live = live, upcoming = upcoming, replays = replays, perCreditEur = rate, loading = false) }
        }
    }

    /** En direct = statut `live`, ou `published` dont la fenêtre [start, end) contient maintenant. */
    private fun isLiveNow(c: Competition, now: java.time.Instant): Boolean {
        if (c.status == "live") return true
        if (c.status != "published") return false
        val start = c.startAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return false
        val end = c.endAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        return !start.isAfter(now) && (end == null || end.isAfter(now))
    }
}

/**
 * Page « Compétitions » : titre + 3 onglets (En direct / À venir / Replays). Pas de recherche
 * ni de sous-titre (parité web).
 *
 * @param onOpen ouvre la room d'une compétition.
 * @param onOpenReplay ouvre le lecteur d'un replay de compétition.
 */
@Composable
fun CompetitionsListScreen(
    viewModel: CompetitionsViewModel,
    onOpen: (Competition) -> Unit,
    onOpenReplay: (ReplayVideo) -> Unit = {},
    /** Ouvre l'écran Sponsoring (Profil) avec cette compétition présélectionnée. */
    onRequestSponsor: (eventType: String, eventId: String) -> Unit = { _, _ -> },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("🏆 ${s.screenCompetitions}", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            TabPill("${s.duelTabLive} (${ui.live.size})", tab == 0) { tab = 0 }
            TabPill("${s.duelTabUpcoming} (${ui.upcoming.size})", tab == 1) { tab = 1 }
            TabPill("${s.duelTabReplays} (${ui.replays.size})", tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> if (ui.live.isEmpty()) EmptyComp(s.noCompetitionsLive) else ui.live.forEach { CompetitionCard(it, ui.perCreditEur, isLive = true, onOpen = onOpen, onRequestSponsor = onRequestSponsor) }
            1 -> if (ui.upcoming.isEmpty()) EmptyComp(s.noCompetitionsUpcoming) else ui.upcoming.forEach { CompetitionCard(it, ui.perCreditEur, isLive = false, onOpen = onOpen, onRequestSponsor = onRequestSponsor) }
            else -> if (ui.replays.isEmpty()) EmptyComp(s.noConcertReplays) else ui.replays.forEach { CompetitionReplayCard(it, onOpenReplay) }
        }
    }
}

@Composable
private fun EmptyComp(title: String) {
    DMEmptyState(title = title, icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
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

/** Carte d'une compétition (En direct / À venir) : couverture + badges + infos + bouton. */
@Composable
private fun CompetitionCard(
    competition: Competition,
    perCreditEur: Double,
    isLive: Boolean,
    onOpen: (Competition) -> Unit,
    onRequestSponsor: (String, String) -> Unit = { _, _ -> },
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val currency = LocalCurrency.current
    val canWatch = isLive || competition.viewerTicketPrice <= 0

    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(competition) }, padded = false) {
        // Couverture.
        Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
            DMRemoteImage(url = competition.coverUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(170.dp), fallbackEmoji = "🏆")
            if (isLive) {
                Text("▶", color = Color.White, fontSize = 40.sp)
                Box(modifier = Modifier.align(Alignment.TopStart).padding(DualMusicTheme.spacing.sm).background(Color(0xFFEF4444), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("🔴 ${s.liveBadge}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            // Badge mode (haut-droite).
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(DualMusicTheme.spacing.sm).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (competition.mode == "online") "🌐 ${s.compOnline}" else "📍 ${s.compOnsite}", color = Color.White, fontSize = 11.sp)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            // Badge prix (à venir) : crédits + fiat, ou Gratuit.
            if (!isLive) {
                if (competition.viewerTicketPrice > 0) {
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("🪙 ${formatCredits(competition.viewerTicketPrice)} ≈ ${currency.format(competition.viewerTicketPrice * perCreditEur)}", color = colors.foreground, fontSize = 11.sp)
                    }
                } else {
                    Box(modifier = Modifier.background(Color(0x3310B981), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("🎁 ${s.duelFree}", color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }
            }
            Text(competition.title, color = colors.foreground, fontWeight = FontWeight.Bold)
            competition.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mutedForeground, fontSize = 13.sp, maxLines = 2) }
            competition.startAt?.let { Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy '•' HH:mm")}", color = colors.mutedForeground, fontSize = 12.sp) }
            DMButton(
                if (canWatch) s.watchLive else s.buyTicket,
                style = if (isLive) DMButtonStyle.DESTRUCTIVE else DMButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            ) { onOpen(competition) }
            if (competition.status != "ended" && competition.status != "cancelled" &&
                competition.acceptsSponsors && !isDeadlinePassed(competition.sponsorSubmissionDeadline)
            ) {
                DMButton(s.requestSponsorBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth()) {
                    onRequestSponsor("competition", competition.id)
                }
            }
        }
    }
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

/** Carte d'un replay de compétition : couverture + Replay disponible + Regarder. */
@Composable
private fun CompetitionReplayCard(replay: ReplayVideo, onOpen: (ReplayVideo) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(replay) }, padded = false) {
        Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(DualMusicTheme.gradients.hero), contentAlignment = Alignment.Center) {
            DMRemoteImage(url = replay.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(170.dp), fallbackEmoji = "🎬")
            Text("▶", color = Color.White, fontSize = 40.sp)
            if (replay.videoUrl != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(DualMusicTheme.spacing.sm).background(Color(0xCC10B981), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("▶ ${s.replayAvailable}", color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(replay.title ?: s.screenCompetitions, color = colors.foreground, fontWeight = FontWeight.Bold)
            replay.recordedDate?.let { Text("📅 ${com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy")}", color = colors.mutedForeground, fontSize = 12.sp) }
            DMButton(s.watchLive, modifier = Modifier.fillMaxWidth()) { onOpen(replay) }
        }
    }
}

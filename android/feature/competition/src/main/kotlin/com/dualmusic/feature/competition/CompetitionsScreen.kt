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
        /** Le caller a-t-il le rôle artiste ? (éligibilité au bouton Candidater). */
        val isArtist: Boolean = false,
        /** Ids des compétitions où le caller (artiste) a déjà une candidature. */
        val appliedCompetitionIds: Set<String> = emptySet(),
        /** Résultat transitoire de la dernière candidature (dialogue de détails). */
        val applying: Boolean = false,
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
            // Éligibilité à candidater : rôle artiste + candidatures déjà déposées (badge "déjà candidaté").
            val isArtist = runCatching { repository.amIArtist() }.getOrDefault(false)
            val appliedIds = if (isArtist) {
                runCatching { repository.myCandidacies() }.getOrDefault(emptyList()).map { it.competitionId }.toSet()
            } else {
                emptySet()
            }
            _ui.update {
                it.copy(
                    live = live, upcoming = upcoming, replays = replays, perCreditEur = rate, loading = false,
                    isArtist = isArtist, appliedCompetitionIds = appliedIds,
                )
            }
        }
    }

    /**
     * Auto-candidature de l'artiste à une compétition à venir (parité web
     * `CompetitionApplyDialog`). Met à jour [UiState.appliedCompetitionIds] en cas de succès.
     */
    fun apply(competitionId: String, pitch: String?, videoDemoUrl: String?, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(applying = true) }
            runCatching { repository.apply(competitionId, pitch, videoDemoUrl) }
                .onSuccess {
                    _ui.update { it.copy(applying = false, appliedCompetitionIds = it.appliedCompetitionIds + competitionId) }
                    onResult(null)
                }
                .onFailure { e ->
                    _ui.update { it.copy(applying = false) }
                    onResult(e)
                }
        }
    }

    /** En direct = statut `live`, ou `published` dont la fenêtre (start inclus, end exclu) contient maintenant. */
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
    // Compétition « à venir » dont on affiche le détail (parité web /competition/:id) — ouvert
    // depuis la carte AU LIEU d'aller directement dans la room (le gate bloquerait de toute façon
    // trop tôt, mais l'utilisateur doit pouvoir voir les infos et candidater avant l'heure).
    var detailsFor by remember { mutableStateOf<Competition?>(null) }

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
            // À venir : la carte ouvre le détail (infos + candidature) plutôt que la room directement.
            1 -> if (ui.upcoming.isEmpty()) EmptyComp(s.noCompetitionsUpcoming) else ui.upcoming.forEach { CompetitionCard(it, ui.perCreditEur, isLive = false, onOpen = { detailsFor = it }, onRequestSponsor = onRequestSponsor) }
            else -> if (ui.replays.isEmpty()) EmptyComp(s.noConcertReplays) else ui.replays.forEach { CompetitionReplayCard(it, onOpenReplay) }
        }
    }

    detailsFor?.let { comp ->
        CompetitionDetailsDialog(
            competition = comp,
            perCreditEur = ui.perCreditEur,
            isArtist = ui.isArtist,
            hasApplied = comp.id in ui.appliedCompetitionIds,
            applying = ui.applying,
            onApply = { pitch, videoDemoUrl, onResult -> viewModel.apply(comp.id, pitch, videoDemoUrl, onResult) },
            onJoin = { detailsFor = null; onOpen(comp) },
            onDismiss = { detailsFor = null },
        )
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
            if (isLive) {
                // En direct : un seul bouton pleine largeur (parité web).
                DMButton(
                    if (canWatch) s.watchLive else s.buyTicket,
                    style = DMButtonStyle.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth(),
                ) { onOpen(competition) }
            } else {
                // Avant le direct : "Voir les détails" (candidature, règlement, achat de ticket) et
                // "Sponsoriser" côte à côte — parité web `Competitions.tsx` (bouton "Acheter un
                // ticket" seul était trompeur pour un artiste qui veut d'abord candidater).
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(
                        s.compViewDetails,
                        style = DMButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f),
                    ) { onOpen(competition) }
                    if (competition.status != "ended" && competition.status != "cancelled" &&
                        competition.acceptsSponsors && !isDeadlinePassed(competition.sponsorSubmissionDeadline)
                    ) {
                        DMButton(s.requestSponsorBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f)) {
                            onRequestSponsor("competition", competition.id)
                        }
                    }
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

/**
 * Détail d'une compétition « à venir » (parité web `/competition/:id`) : dates de candidature et
 * de direct, description, frais éventuels, puis actions selon l'éligibilité :
 *  - « Candidater » (ouvre [CompetitionApplyDialog]) si artiste, pas déjà candidat, candidatures
 *    ouvertes et pas encore fermées, compétition publiée.
 *  - « Voir / Rejoindre » (ouvre la room — le [com.dualmusic.core.ui.components.ScheduledAccessGate]
 *    s'y charge de bloquer l'accès si l'heure programmée n'est pas encore atteinte).
 *
 * Choix d'architecture : un dialogue (AlertDialog) plutôt qu'un écran dédié — la carte de la
 * liste "À venir" l'ouvre à la place d'appeler `onOpen` directement, ce qui s'intègre sans
 * toucher la navigation (`MainActivity`) ni casser le chemin direct des compétitions en direct.
 */
@Composable
private fun CompetitionDetailsDialog(
    competition: Competition,
    perCreditEur: Double,
    isArtist: Boolean,
    hasApplied: Boolean,
    applying: Boolean,
    onApply: (pitch: String?, videoDemoUrl: String?, onResult: (Throwable?) -> Unit) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = DualMusicTheme.colors
    val currency = LocalCurrency.current
    var applyOpen by remember { mutableStateOf(false) }

    val deadlinePassed = isDeadlinePassed(competition.applicationDeadline)
    val applicationsNotOpenYet = competition.applicationOpensAt != null && !isDeadlinePassed(competition.applicationOpensAt)
    val canApply = isArtist && !hasApplied && !deadlinePassed && competition.status == "published"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(competition.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    DetailBadgeChip(if (competition.mode == "onsite") "📍 ${s.compOnsite}" else "🌐 ${s.compOnline}")
                    if (competition.viewerTicketPrice > 0) {
                        DetailBadgeChip("🪙 ${formatCredits(competition.viewerTicketPrice)} ≈ ${currency.format(competition.viewerTicketPrice * perCreditEur)}")
                    } else {
                        DetailBadgeChip("🎁 ${s.duelFree}")
                    }
                }
                competition.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mutedForeground, fontSize = 13.sp) }

                competition.applicationOpensAt?.let { InfoLine("📅 ${s.compApplicationOpensAt}", com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm")) }
                InfoLine("📅 ${s.compApplicationDeadline}", competition.applicationDeadline?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoLine("📅 ${s.compStartAtLabel}", competition.startAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoLine("📅 ${s.compEndAtLabel}", competition.endAt?.let { com.dualmusic.core.ui.datetime.formatTz(it, "dd MMMM yyyy HH:mm") })
                InfoLine("👥 ${s.maxCandidatesLabel}", competition.maxCandidates?.toString())
                InfoLine("🏆 ${s.compRewardDesc}", competition.rewardDescription)
                if (competition.entryFeeRequired) {
                    InfoLine("💰 ${s.compEntryFeeAmount}", formatCredits(competition.entryFeeAmount))
                }

                when {
                    hasApplied -> Text("✅ ${s.compApplied}", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    isArtist && deadlinePassed -> Text(s.compDeadlinePassed, color = colors.destructive, fontSize = 12.sp)
                }

                if (canApply) {
                    DMButton(
                        if (applicationsNotOpenYet) s.compApplicationsNotOpenYet else s.compApply,
                        style = DMButtonStyle.SECONDARY,
                        enabled = !applicationsNotOpenYet,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { applyOpen = true },
                    )
                }
            }
        },
        confirmButton = {
            DMButton(s.compViewJoin, onClick = onJoin)
        },
        dismissButton = {
            DMButton(s.compCancel, style = DMButtonStyle.OUTLINE, onClick = onDismiss)
        },
    )

    if (applyOpen) {
        CompetitionApplyDialog(
            applying = applying,
            onSubmit = { pitch, videoDemoUrl, onResult -> onApply(pitch, videoDemoUrl, onResult) },
            onDismiss = { applyOpen = false },
            onApplied = { applyOpen = false; onDismiss() },
        )
    }
}

/**
 * Popup « Déposer ma candidature » — auto-candidature de l'artiste (`POST /competitions/:id/apply`).
 * Distincte de `AddCandidateDialog` (`ManagerCompetitionsScreen.kt`) qui est l'ajout MANUEL par le
 * manager (`/candidates/manual`) : ici l'artiste candidate lui-même. Parité web
 * `CompetitionApplyDialog` : pitch + lien vidéo démo, tous deux requis avant envoi.
 */
@Composable
private fun CompetitionApplyDialog(
    applying: Boolean,
    onSubmit: (pitch: String?, videoDemoUrl: String?, onResult: (Throwable?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = DualMusicTheme.colors
    var pitch by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val canSubmit = pitch.isNotBlank() && videoUrl.isNotBlank() && !applying

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(s.compApplyTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                androidx.compose.material3.OutlinedTextField(
                    value = pitch,
                    onValueChange = { pitch = it; error = null },
                    label = { Text(s.compApplyPitch) },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it; error = null },
                    label = { Text(s.compApplyVideo) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = colors.destructive, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            DMButton(if (applying) s.sending else s.compApply, enabled = canSubmit) {
                if (pitch.isBlank() || videoUrl.isBlank()) {
                    error = s.compApplyRequiredFields
                    return@DMButton
                }
                onSubmit(pitch.trim(), videoUrl.trim()) { err ->
                    if (err == null) {
                        onApplied()
                    } else {
                        val de = err as? com.dualmusic.domain.api.DomainError
                        error = when (de?.details?.get("reason")) {
                            "deadline_passed" -> s.compDeadlinePassed
                            "already_applied" -> s.compAlreadyApplied
                            else -> de?.message?.takeIf { it.isNotBlank() } ?: s.networkSlow
                        }
                    }
                }
            }
        },
        dismissButton = {
            DMButton(s.compCancel, style = DMButtonStyle.OUTLINE, enabled = !applying, onClick = onDismiss)
        },
    )
}

/** Pilule de badge compacte du dialogue de détail. */
@Composable
private fun DetailBadgeChip(label: String) {
    val colors = DualMusicTheme.colors
    Box(modifier = Modifier.background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, color = colors.foreground, fontSize = 11.sp)
    }
}

/** Ligne d'information « label : valeur » du dialogue de détail (masquée si valeur nulle). */
@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val colors = DualMusicTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
        Text(label, color = colors.mutedForeground, fontSize = 12.sp)
        Text(value, color = colors.foreground, fontSize = 12.sp)
    }
}

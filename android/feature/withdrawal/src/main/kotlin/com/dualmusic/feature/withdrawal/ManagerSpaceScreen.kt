package com.dualmusic.feature.withdrawal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.formatCredits
import com.dualmusic.core.ui.currency.LocalCurrency
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.i18n.Strings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.wallet.EventTransaction
import com.dualmusic.domain.wallet.RevenueBreakdown
import com.dualmusic.domain.wallet.RevenueEvent

/**
 * « Espace Manager / Artiste » — 3 onglets en parité stricte avec le web :
 *  1. Mes revenus (revenus par événement, période, total, export CSV/PDF, détail).
 *  2. Retrait (PIN + formulaire).
 *  3. Historique (demandes de retrait).
 *
 * Mêmes endpoints que le web → mêmes données pour un même compte.
 */
@Composable
fun ManagerSpaceScreen(revenueVm: RevenueViewModel, withdrawalVm: WithdrawalViewModel) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        revenueVm.load()
        withdrawalVm.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("💼 ${s.managerSpace}", color = colors.foreground, fontWeight = FontWeight.Bold)

        // Onglets.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
        ) {
            TabPill(s.revTabRevenues, tab == 0) { tab = 0 }
            TabPill(s.revTabWithdraw, tab == 1) { tab = 1 }
            TabPill(s.revTabHistory, tab == 2) { tab = 2 }
        }

        when (tab) {
            0 -> RevenuesTab(revenueVm)
            1 -> WithdrawTab(withdrawalVm)
            else -> HistoryTab(withdrawalVm)
        }
    }
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

// ─────────────────────────── Onglet 1 : Mes revenus ───────────────────────────

@Composable
private fun RevenuesTab(vm: RevenueViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val currency = LocalCurrency.current
    val context = LocalContext.current
    val label: (String) -> String = { srcLabel(it, s) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.revByEventTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(s.revByEventSubtitle, color = colors.mutedForeground, fontSize = 12.sp)
        }
        PeriodSelector(ui.period) { vm.setPeriod(it) }
    }

    // Total de la période.
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.revTotalPeriod, color = colors.mutedForeground, fontSize = 12.sp)
            Text(formatCredits(ui.totalCredits), color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("≈ ${currency.format(ui.totalCredits * ui.perCreditEur)}", color = colors.mutedForeground, fontSize = 12.sp)
        }
    }

    // Export.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        DMButton(s.revExportCsv, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = {
            if (ui.events.isNotEmpty()) exportRevenuesCsv(context, ui.events, ui.period, label)
        })
        DMButton(s.revExportPdf, style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = {
            if (ui.events.isNotEmpty()) exportRevenuesPdf(context, ui.events, ui.totalCredits, label)
        })
    }

    if (ui.loading) {
        CircularProgressIndicator(color = colors.primary)
    } else if (ui.events.isEmpty()) {
        DMEmptyState(title = s.revNoRevenue, subtitle = s.revNoRevenueHint, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
    } else {
        ui.events.forEach { e ->
            RevenueEventRow(
                event = e,
                expanded = ui.expandedId == e.sourceId,
                breakdown = if (ui.expandedId == e.sourceId) ui.breakdown else emptyList(),
                transactions = if (ui.expandedId == e.sourceId) ui.transactions else emptyList(),
                detailLoading = ui.expandedId == e.sourceId && ui.detailLoading,
                txHasMore = ui.expandedId == e.sourceId && ui.txHasMore,
                perCreditEur = ui.perCreditEur,
                onToggle = { vm.toggleEvent(e.sourceId) },
                onLoadMore = { vm.loadMoreTx() },
            )
        }
    }
}

@Composable
private fun PeriodSelector(period: String, onSelect: (String) -> Unit) {
    val s = LocalStrings.current
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val label = when (period) {
        "day" -> s.revPeriodToday
        "week" -> s.revPeriod7d
        "month" -> s.revPeriod30d
        else -> s.revPeriodAll
    }
    Box {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(DualMusicTheme.radii.md))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label ▾", color = colors.foreground, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("all" to s.revPeriodAll, "day" to s.revPeriodToday, "week" to s.revPeriod7d, "month" to s.revPeriod30d).forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); expanded = false })
            }
        }
    }
}

@Composable
private fun RevenueEventRow(
    event: RevenueEvent,
    expanded: Boolean,
    breakdown: List<RevenueBreakdown>,
    transactions: List<EventTransaction>,
    detailLoading: Boolean,
    txHasMore: Boolean,
    perCreditEur: Double,
    onToggle: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    val currency = LocalCurrency.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().clickable { onToggle() }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(srcLabel(event.sourceType, s), color = colors.foreground, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        event.lastAt?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy"), color = colors.mutedForeground, fontSize = 12.sp) }
                        Text("${event.txCount} ${s.revVersements}", color = colors.mutedForeground, fontSize = 12.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCredits(event.totalReceived), color = colors.accent, fontWeight = FontWeight.Bold)
                    Text("≈ ${currency.format(event.totalReceived * perCreditEur)}", color = colors.mutedForeground, fontSize = 11.sp)
                }
            }

            if (expanded) {
                if (detailLoading) {
                    CircularProgressIndicator(color = colors.primary, modifier = Modifier.padding(top = DualMusicTheme.spacing.sm))
                } else {
                    // Répartition par type de source.
                    breakdown.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(srcLabel(b.sourceType, s), color = colors.mutedForeground, fontSize = 12.sp)
                            Text(formatCredits(b.total), color = colors.foreground, fontSize = 12.sp)
                        }
                    }
                    // Transactions détaillées.
                    transactions.forEach { tx ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm)) {
                            tx.createdAt?.let { Text(com.dualmusic.core.ui.datetime.formatTz(it, "dd/MM/yyyy HH:mm"), color = colors.mutedForeground, fontSize = 11.sp) }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${s.revTotalPaid}: ${formatCredits(tx.totalCredits)}", color = colors.mutedForeground, fontSize = 11.sp)
                                Text("${s.revReceivedYou}: ${formatCredits(tx.myCredits)}", color = colors.accent, fontSize = 11.sp)
                            }
                        }
                    }
                    if (txHasMore) {
                        DMButton(s.revShowMore, style = DMButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm), onClick = onLoadMore)
                    }
                }
            }
        }
    }
}

/** Libellé FR d'un type de source de revenu (parité web). */
private fun srcLabel(sourceType: String, s: Strings): String = when (sourceType) {
    "duel_ticket" -> s.srcDuelTicket
    "duel_replay" -> s.srcDuelReplay
    "concert_ticket" -> s.srcConcertTicket
    "concert_replay" -> s.srcConcertReplay
    "gift_concert" -> s.srcGiftConcert
    "gift_duel" -> s.srcGiftDuel
    "gift_live" -> s.srcGiftLive
    "vote" -> s.srcVote
    else -> sourceType
}

// ─────────────────────────── Onglet 2 : Retrait ───────────────────────────

@Composable
private fun WithdrawTab(vm: WithdrawalViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    if (ui.isLoading) CircularProgressIndicator(color = colors.primary)
    ui.error?.let { Text(it, color = colors.destructive) }
    if (ui.submitted) Text(s.withdrawSubmitted, color = colors.primary)

    when (ui.hasPin) {
        false -> CreatePinCard(onCreate = vm::createPin)
        true -> {
            PayoutMethodsSection(
                methods = ui.methods,
                onAdd = vm::addMethod,
                onDelete = vm::removeMethod,
                onSetDefault = vm::setDefaultMethod,
            )
            WithdrawForm(
                methods = ui.methods,
                selectedMethodId = ui.selectedMethodId,
                onSelectMethod = vm::selectMethod,
                amount = ui.amount,
                onAmountChange = vm::onAmountChange,
                feePct = ui.net?.feePct,
                net = ui.net?.net,
                submitting = ui.submitting,
                onSubmit = vm::submit,
            )
        }
        null -> Unit
    }
}

// ─────────────────────────── Onglet 3 : Historique ───────────────────────────

@Composable
private fun HistoryTab(vm: WithdrawalViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    Text(s.history, color = DualMusicTheme.colors.foreground, fontWeight = FontWeight.Bold)
    if (ui.requests.isEmpty()) {
        DMEmptyState(title = s.wdNoWithdrawals, modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.lg))
    } else {
        ui.requests.forEach { RequestRow(it) }
    }
}

package com.dualmusic.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.CreditPill
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.wallet.RevenueEvent
import com.dualmusic.domain.payment.CreditPurchase
import com.dualmusic.domain.wallet.SpendItem

/**
 * Écran portefeuille : solde + historiques (dépenses / revenus).
 *
 * Tous les montants proviennent du backend (procédures atomiques) — aucun calcul d'argent
 * n'est refait côté mobile. Reprend le design system pour la parité visuelle avec le web.
 *
 * @param viewModel source d'état (solde + historiques).
 */
@Composable
fun WalletScreen(viewModel: WalletViewModel, onOpenRecharge: () -> Unit = {}, canEarn: Boolean = false) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = com.dualmusic.core.ui.i18n.LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // --- Solde ---
        DMCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CreditPill(credits = ui.balance.balance)
                Text(
                    "≈ %.2f €".format(ui.balance.eurValue),
                    color = colors.mutedForeground,
                )
            }
        }

        DMButton(com.dualmusic.core.ui.i18n.LocalStrings.current.rechargeCredits, modifier = Modifier.fillMaxWidth(), onClick = onOpenRecharge)

        ui.error?.let { Text(it, color = colors.destructive) }
        if (ui.isLoading) CircularProgressIndicator(color = colors.primary)

        // --- Historiques (calqués sur le web : Achats de crédits + Dépenses ; Revenus si earner) ---
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(s.creditPurchases) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(s.walletExpenses) })
            if (canEarn) {
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(s.walletIncome) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
        ) {
            when (tab) {
                0 -> {
                    if (ui.purchases.isEmpty()) item { Text(s.noPurchases, color = colors.mutedForeground) }
                    items(ui.purchases) { PurchaseRow(it) }
                }
                1 -> items(ui.spending) { SpendRow(it) }
                else -> items(ui.revenues) { RevenueRow(it) }
            }
        }
    }
}

/** Ligne d'historique d'un achat de crédits (recharge). */
@Composable
private fun PurchaseRow(item: CreditPurchase) {
    val colors = DualMusicTheme.colors
    DMCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(item.paymentMethod ?: "Mobile Money", color = colors.foreground)
                val date = item.createdAt?.take(10)
                val status = item.status
                Text(listOfNotNull(date, status).joinToString(" · "), color = colors.mutedForeground)
            }
            Text("+${item.creditsAmount.toInt()}", color = colors.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ligne d'historique de dépense (crédits sortants). */
@Composable
private fun SpendRow(item: SpendItem) {
    val colors = DualMusicTheme.colors
    DMCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(labelForSource(item.sourceType), color = colors.foreground)
                item.createdAt?.let { Text(it.take(10), color = colors.mutedForeground) }
            }
            Text("-${item.totalCredits}", color = colors.destructive, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ligne d'historique de revenu (crédits entrants, agrégés par événement). */
@Composable
private fun RevenueRow(item: RevenueEvent) {
    val colors = DualMusicTheme.colors
    DMCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(labelForSource(item.sourceType), color = colors.foreground)
                Text("${item.txCount} transaction(s)", color = colors.mutedForeground)
            }
            Text("+${item.totalReceived}", color = colors.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** Traduit le `source_type` backend en libellé lisible. */
private fun labelForSource(sourceType: String): String = when (sourceType) {
    "vote" -> "Vote"
    "gift_duel", "gift_live", "gift_concert", "gift_competition" -> "Cadeau"
    "duel_ticket" -> "Ticket duel"
    "concert_ticket" -> "Ticket concert"
    "duel_replay", "concert_replay" -> "Replay"
    else -> sourceType.replaceFirstChar { it.uppercase() }
}

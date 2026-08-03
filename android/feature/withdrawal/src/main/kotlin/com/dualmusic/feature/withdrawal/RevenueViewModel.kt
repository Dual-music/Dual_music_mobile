package com.dualmusic.feature.withdrawal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.wallet.EventTransaction
import com.dualmusic.domain.wallet.RevenueBreakdown
import com.dualmusic.domain.wallet.RevenueEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Taille de page des transactions détaillées (parité web). */
private const val TX_PAGE = 10

/** État de l'onglet « Mes revenus ». */
data class RevenueUiState(
    val period: String = "all",
    val events: List<RevenueEvent> = emptyList(),
    val totalCredits: Double = 0.0,
    /** Taux €/crédit dérivé du solde (pour l'aperçu fiat). */
    val perCreditEur: Double = 0.0,
    val expandedId: String? = null,
    val breakdown: List<RevenueBreakdown> = emptyList(),
    val transactions: List<EventTransaction> = emptyList(),
    val txOffset: Int = 0,
    val txHasMore: Boolean = false,
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
)

/**
 * ViewModel de l'onglet « Mes revenus » : revenus groupés par événement, filtre de période,
 * total, et détail dépliable (répartition + transactions paginées). Mêmes endpoints que le web.
 */
class RevenueViewModel(private val repository: RevenueRepository) : ViewModel() {

    private val _ui = MutableStateFlow(RevenueUiState())
    val ui: StateFlow<RevenueUiState> = _ui.asStateFlow()

    /** Change la période (day/week/month/all) et recharge. */
    fun setPeriod(period: String) {
        _ui.update { it.copy(period = period) }
        load()
    }

    /** Borne ISO `since` correspondant à la période (null = tout). */
    private fun sinceFor(period: String): String? = when (period) {
        "day" -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        "week" -> java.time.Instant.now().minusSeconds(7L * 24 * 3600).toString()
        "month" -> java.time.Instant.now().minusSeconds(30L * 24 * 3600).toString()
        else -> null
    }

    /** Charge les revenus de la période + le taux €/crédit. */
    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, expandedId = null) }
            val since = sinceFor(_ui.value.period)
            val events = runCatching { repository.revenues(since) }.getOrDefault(emptyList())
            val bal = runCatching { repository.balance() }.getOrNull()
            val perCredit = if (bal != null && bal.balance > 0) bal.eurValue / bal.balance else 0.0
            _ui.update {
                it.copy(
                    events = events,
                    totalCredits = events.sumOf { e -> e.totalReceived },
                    perCreditEur = perCredit,
                    loading = false,
                )
            }
        }
    }

    /** Déplie/replie un événement ; au dépliage, charge répartition + 1re page de transactions. */
    fun toggleEvent(sourceId: String) {
        if (_ui.value.expandedId == sourceId) {
            _ui.update { it.copy(expandedId = null, breakdown = emptyList(), transactions = emptyList(), txOffset = 0, txHasMore = false) }
            return
        }
        _ui.update { it.copy(expandedId = sourceId, breakdown = emptyList(), transactions = emptyList(), txOffset = 0, detailLoading = true) }
        viewModelScope.launch {
            val bd = runCatching { repository.breakdown(sourceId) }.getOrDefault(emptyList())
            val tx = runCatching { repository.transactions(sourceId, TX_PAGE, 0) }.getOrDefault(emptyList())
            _ui.update {
                it.copy(
                    breakdown = bd,
                    transactions = tx,
                    txOffset = tx.size,
                    txHasMore = tx.size >= TX_PAGE,
                    detailLoading = false,
                )
            }
        }
    }

    /** Charge la page de transactions suivante (bouton « Voir plus »). */
    fun loadMoreTx() {
        val sourceId = _ui.value.expandedId ?: return
        val offset = _ui.value.txOffset
        viewModelScope.launch {
            val next = runCatching { repository.transactions(sourceId, TX_PAGE, offset) }.getOrDefault(emptyList())
            _ui.update {
                it.copy(
                    transactions = it.transactions + next,
                    txOffset = it.txOffset + next.size,
                    txHasMore = next.size >= TX_PAGE,
                )
            }
        }
    }
}

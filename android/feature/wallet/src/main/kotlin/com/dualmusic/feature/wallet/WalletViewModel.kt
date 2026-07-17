package com.dualmusic.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.api.DomainError
import com.dualmusic.domain.wallet.RevenueEvent
import com.dualmusic.domain.wallet.SpendItem
import com.dualmusic.domain.wallet.WalletBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran portefeuille.
 *
 * @property balance solde + contre-valeur € (source : backend).
 * @property spending dépenses du caller (cadeaux, votes, tickets…).
 * @property revenues revenus agrégés par événement (artiste/manager).
 * @property isLoading chargement initial ou rafraîchissement en cours.
 * @property error message d'erreur affichable, `null` si aucune.
 */
data class WalletUiState(
    val balance: WalletBalance = WalletBalance(),
    val spending: List<SpendItem> = emptyList(),
    val revenues: List<RevenueEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel du portefeuille.
 *
 * Charge le solde et les deux historiques (dépenses / revenus) en une passe. Aucun calcul
 * d'argent n'est fait ici : les montants affichés proviennent tous du backend.
 *
 * @param repository accès aux endpoints `/wallet/…`.
 */
class WalletViewModel(private val repository: WalletRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    /** Charge (ou recharge) solde + historiques. Idempotent : ignoré si déjà en cours. */
    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Les 3 lectures sont indépendantes : un échec d'historique ne doit pas
                // masquer le solde, d'où les repli sur liste vide.
                val balance = repository.balance()
                val spending = runCatching { repository.spending() }.getOrDefault(emptyList())
                val revenues = runCatching { repository.revenues() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(balance = balance, spending = spending, revenues = revenues, isLoading = false)
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = friendlyMessage(t)) }
            }
        }
    }

    /** Traduit une erreur technique en message affichable. */
    private fun friendlyMessage(t: Throwable): String = when {
        t is DomainError && t.isAuthExpired -> "Session expirée — reconnecte-toi."
        t is DomainError && t.isRetriable -> "Connexion instable. Réessaie."
        t is DomainError -> t.message
        else -> "Impossible de charger le portefeuille."
    }
}

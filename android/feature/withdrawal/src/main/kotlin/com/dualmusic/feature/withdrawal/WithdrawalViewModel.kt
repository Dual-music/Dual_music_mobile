package com.dualmusic.feature.withdrawal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.api.DomainError
import com.dualmusic.domain.model.WithdrawalNet
import com.dualmusic.domain.model.WithdrawalRequest
import com.dualmusic.domain.withdrawal.PayoutMethodData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran de retrait.
 *
 * @property hasPin `null` tant qu'on ne sait pas, puis true/false.
 * @property net aperçu du net après frais pour le montant courant.
 * @property submitted true après une demande envoyée avec succès.
 */
data class WithdrawalUiState(
    val hasPin: Boolean? = null,
    val methods: List<PayoutMethodData> = emptyList(),
    val selectedMethodId: String? = null,
    val amount: String = "",
    val net: WithdrawalNet? = null,
    val requests: List<WithdrawalRequest> = emptyList(),
    val isLoading: Boolean = false,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel du retrait.
 *
 * Aucun calcul d'argent local : le net affiché vient de `/withdrawals/net`, et la demande
 * est validée + réservée côté serveur. Le PIN est re-vérifié par le backend.
 *
 * @param repository accès au flux de retrait.
 */
class WithdrawalViewModel(private val repository: WithdrawalRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WithdrawalUiState())
    val uiState: StateFlow<WithdrawalUiState> = _uiState.asStateFlow()

    /** Charge PIN, méthodes et historique. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val hasPin = runCatching { repository.hasPin() }.getOrDefault(false)
            val methods = runCatching { repository.methods() }.getOrDefault(emptyList())
            val requests = runCatching { repository.myRequests() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    hasPin = hasPin,
                    methods = methods,
                    selectedMethodId = methods.firstOrNull { m -> m.isDefault }?.id ?: methods.firstOrNull()?.id,
                    requests = requests,
                    isLoading = false,
                )
            }
        }
    }

    /** Met à jour le montant et rafraîchit l'aperçu du net. */
    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amount = value, error = null) }
        val amount = value.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(net = null) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.net(amount) }.getOrNull()?.let { net ->
                _uiState.update { it.copy(net = net) }
            }
        }
    }

    /** Sélectionne une méthode de retrait. */
    fun selectMethod(id: String) = _uiState.update { it.copy(selectedMethodId = id) }

    /** Crée le PIN (première configuration). */
    fun createPin(pin: String) {
        viewModelScope.launch {
            runCatching { repository.setPin(pin) }
                .onSuccess { _uiState.update { it.copy(hasPin = true) } }
                .onFailure { t -> _uiState.update { it.copy(error = friendly(t)) } }
        }
    }

    /** Envoie la demande de retrait avec le PIN saisi. */
    fun submit(pin: String) {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(error = "Montant invalide") }
            return
        }
        if (state.selectedMethodId == null) {
            _uiState.update { it.copy(error = "Choisis une méthode de retrait") }
            return
        }
        if (!pin.matches(Regex("^\\d{6}$"))) {
            _uiState.update { it.copy(error = "Le code PIN doit contenir 6 chiffres") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, error = null) }
            runCatching { repository.createRequest(amount, pin, state.selectedMethodId) }
                .onSuccess {
                    val requests = runCatching { repository.myRequests() }.getOrDefault(state.requests)
                    _uiState.update {
                        it.copy(submitting = false, submitted = true, amount = "", net = null, requests = requests)
                    }
                }
                .onFailure { t -> _uiState.update { it.copy(submitting = false, error = friendly(t)) } }
        }
    }

    /** Réinitialise le drapeau de succès (après affichage du message). */
    fun consumeSubmitted() = _uiState.update { it.copy(submitted = false) }

    private fun friendly(t: Throwable): String = when {
        t is DomainError && t.code == "PIN_WRONG" -> "Code PIN incorrect."
        t is DomainError && t.code == "PIN_LOCKED" -> "PIN bloqué après trop de tentatives. Réessaie plus tard."
        t is DomainError && t.isInsufficientBalance -> "Solde insuffisant pour ce retrait."
        t is DomainError -> t.message
        else -> "Opération impossible."
    }
}

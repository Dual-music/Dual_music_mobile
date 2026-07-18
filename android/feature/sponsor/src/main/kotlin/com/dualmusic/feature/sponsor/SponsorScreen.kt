package com.dualmusic.feature.sponsor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.sponsor.SponsorEndpoints
import com.dualmusic.domain.sponsor.SponsorRequest
import com.dualmusic.domain.sponsor.SponsorTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** État de l'écran sponsoring. */
data class SponsorUiState(
    val tiers: List<SponsorTier> = emptyList(),
    val requests: List<SponsorRequest> = emptyList(),
    val message: String? = null,
)

/**
 * ViewModel du sponsoring : paliers tarifaires + mes demandes + paiement.
 *
 * @param api client HTTP.
 */
class SponsorViewModel(private val api: ApiClient) : ViewModel() {

    private val _uiState = MutableStateFlow(SponsorUiState())
    val uiState: StateFlow<SponsorUiState> = _uiState.asStateFlow()

    /** Charge paliers + demandes. */
    fun load() {
        viewModelScope.launch {
            val tiers = runCatching {
                api.request(Endpoint.get(SponsorEndpoints.TIERS), ListSerializer(SponsorTier.serializer()))
            }.getOrDefault(emptyList())
            val requests = runCatching {
                api.request(Endpoint.get(SponsorEndpoints.MY_REQUESTS), ListSerializer(SponsorRequest.serializer()))
            }.getOrDefault(emptyList())
            _uiState.value = SponsorUiState(tiers = tiers, requests = requests)
        }
    }

    /** Paie une demande approuvée (débit + idempotent), puis recharge. */
    fun pay(id: String) {
        viewModelScope.launch {
            val ok = runCatching { api.request<Unit>(Endpoint.post(SponsorEndpoints.pay(id))) }.isSuccess
            _uiState.update { it.copy(message = if (ok) "✅ Sponsoring payé." else "Paiement impossible.") }
            if (ok) load()
        }
    }

    private fun MutableStateFlow<SponsorUiState>.update(block: (SponsorUiState) -> SponsorUiState) {
        value = block(value)
    }
}

/**
 * Écran de sponsoring : tarifs + mes demandes (paiement des demandes approuvées).
 *
 * @param viewModel source d'état.
 */
@Composable
fun SponsorScreen(viewModel: SponsorViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        item {
            Text("Sponsoring", color = colors.foreground, fontWeight = FontWeight.Bold)
            ui.message?.let { Text(it, color = colors.primary) }
        }

        // Tarifs.
        item { Text("Tarifs (par durée)", color = colors.mutedForeground) }
        items(ui.tiers) { tier ->
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${tier.label ?: "Palier"} (${tier.minSeconds}-${tier.maxSeconds}s)", color = colors.foreground)
                    Text("${tier.priceCredits.toInt()} cr.", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Mes demandes.
        item { Text("Mes demandes", color = colors.mutedForeground) }
        if (ui.requests.isEmpty()) {
            item { Text("Aucune demande de sponsoring.", color = colors.mutedForeground) }
        }
        items(ui.requests) { req -> RequestRow(req) { viewModel.pay(req.id) } }
    }
}

/** Ligne d'une demande de sponsoring (paiement si approuvée). */
@Composable
private fun RequestRow(request: SponsorRequest, onPay: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(request.eventType ?: "Événement", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(statusLabel(request.status), color = colors.mutedForeground)
            }
            if (request.payable) {
                DMButton("Payer ${request.priceCredits.toInt()}", onClick = onPay)
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "En attente de validation"
    "approved" -> "Approuvé — à payer"
    "rejected" -> "Rejeté"
    else -> status
}

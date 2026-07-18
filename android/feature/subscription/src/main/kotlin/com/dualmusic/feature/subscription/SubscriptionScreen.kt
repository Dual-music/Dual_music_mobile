package com.dualmusic.feature.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.subscription.MySubscription
import com.dualmusic.domain.subscription.SubscriptionEndpoints
import com.dualmusic.domain.subscription.SubscriptionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** État de l'écran d'abonnement. */
data class SubscriptionUiState(
    val plans: List<SubscriptionPlan> = emptyList(),
    val current: MySubscription = MySubscription(),
)

/**
 * ViewModel des abonnements : offres + statut courant.
 *
 * @param api client HTTP.
 */
class SubscriptionViewModel(private val api: ApiClient) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    /** Charge offres + abonnement courant. */
    fun load() {
        viewModelScope.launch {
            val plans = runCatching {
                api.request(Endpoint.get(SubscriptionEndpoints.PLANS), ListSerializer(SubscriptionPlan.serializer()))
            }.getOrDefault(emptyList())
            val current = runCatching {
                api.request(Endpoint.get(SubscriptionEndpoints.ME), MySubscription.serializer())
            }.getOrDefault(MySubscription())
            _uiState.value = SubscriptionUiState(plans = plans, current = current)
        }
    }
}

/**
 * Écran des abonnements Pro/Premium.
 *
 * L'achat réel passe par Google Play Billing (bouton informatif pour l'instant).
 *
 * @param viewModel source d'état.
 */
@Composable
fun SubscriptionScreen(viewModel: SubscriptionViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Abonnements", color = colors.foreground, fontWeight = FontWeight.Bold)

        // Statut courant.
        if (ui.current.isActive) {
            DMCard {
                Text(
                    "✅ Abonnement actif : ${ui.current.subscriptionType ?: ""}",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        ui.plans.forEach { plan -> PlanCard(plan) }

        Text(
            "L'achat d'un abonnement se fera via Google Play (bientôt).",
            color = colors.mutedForeground,
        )
    }
}

/** Carte d'une offre : nom, prix, description + bouton (achat via store à venir). */
@Composable
private fun PlanCard(plan: SubscriptionPlan) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(plan.name ?: (plan.tier ?: "Offre"), color = colors.foreground, fontWeight = FontWeight.Bold)
                Text("${plan.price.toInt()} cr.", color = colors.accent, fontWeight = FontWeight.Bold)
            }
            plan.description?.let { Text(it, color = colors.mutedForeground) }
            // Achat désactivé tant que Play Billing n'est pas branché (conformité stores).
            DMButton("Bientôt via Google Play", style = DMButtonStyle.OUTLINE, enabled = false) { }
        }
    }
}

package com.dualmusic.feature.subscription

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.payment.PaymentEndpoints
import com.dualmusic.domain.payment.StripeCheckoutResponse
import com.dualmusic.domain.payment.StripeSubscriptionRequest
import com.dualmusic.domain.subscription.MySubscription
import com.dualmusic.domain.subscription.SubscriptionEndpoints
import com.dualmusic.domain.subscription.SubscriptionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** État de l'écran d'abonnement. */
data class SubscriptionUiState(
    val plans: List<SubscriptionPlan> = emptyList(),
    val current: MySubscription = MySubscription(),
    val loading: Boolean = false,
    val message: String? = null,
    /** URL de paiement Stripe à ouvrir (consommée par l'UI puis remise à null). */
    val checkoutUrl: String? = null,
)

/**
 * ViewModel des abonnements : offres + statut courant.
 *
 * @param api client HTTP.
 */
class SubscriptionViewModel(private val api: ApiClient) : ViewModel() {

    private val json = Json { explicitNulls = false }

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

    /**
     * Achète un abonnement (`pro`/`premium`) via Stripe : ouvre l'URL de paiement hébergée.
     * Le compte est mis à jour côté serveur après le paiement (webhook Stripe).
     */
    fun subscribe(plan: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val body = json.encodeToString(StripeSubscriptionRequest.serializer(), StripeSubscriptionRequest(plan))
            runCatching {
                api.request(Endpoint.post(PaymentEndpoints.STRIPE_SUBSCRIPTION, body), StripeCheckoutResponse.serializer())
            }.onSuccess { res -> _uiState.update { it.copy(loading = false, checkoutUrl = res.url, message = com.dualmusic.core.ui.i18n.appStrings.openingPayment) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errSubscriptionUnavailable) } }
        }
    }

    /** À appeler après avoir ouvert l'URL de paiement. */
    fun consumeUrl() = _uiState.update { it.copy(checkoutUrl = null) }
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
    val strings = LocalStrings.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    // Ouvre l'URL de paiement Stripe dès qu'elle est disponible, puis la consomme.
    LaunchedEffect(ui.checkoutUrl) {
        val url = ui.checkoutUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        viewModel.consumeUrl()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {

        // Statut courant.
        if (ui.current.isActive) {
            DMCard {
                Text(
                    "${strings.subscriptionActive} ${ui.current.subscriptionType ?: ""}",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        ui.message?.let { Text(it, color = colors.primaryGlow) }
        ui.plans.forEach { plan ->
            PlanCard(plan, enabled = !ui.loading) { viewModel.subscribe((plan.tier ?: plan.name ?: "pro").lowercase()) }
        }

        Text(strings.stripeSubscriptionHint, color = colors.mutedForeground)
    }
}

/** Carte d'une offre : nom, prix, description + bouton d'abonnement (paiement Stripe). */
@Composable
private fun PlanCard(plan: SubscriptionPlan, enabled: Boolean, onSubscribe: () -> Unit) {
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(plan.name ?: (plan.tier ?: strings.offer), color = colors.foreground, fontWeight = FontWeight.Bold)
                Text("${plan.price.toInt()} cr.", color = colors.accent, fontWeight = FontWeight.Bold)
            }
            plan.description?.let { Text(it, color = colors.mutedForeground) }
            DMButton(strings.subscribeByCard, modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = onSubscribe)
        }
    }
}

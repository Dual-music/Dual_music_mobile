package com.dualmusic.feature.wallet

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.payment.CinetpayCountry
import com.dualmusic.domain.payment.CinetpayInitRequest
import com.dualmusic.domain.payment.CinetpayInitResponse
import com.dualmusic.domain.payment.PaymentEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** État de l'écran de recharge (carte Stripe + Mobile Money CinetPay). */
data class RechargeUiState(
    /** Méthode de paiement : `card` (Stripe) | `cinetpay` (Mobile Money). */
    val method: String = "card",
    val balance: Double = 0.0,
    val eurValue: Double = 0.0,
    val countries: List<CinetpayCountry> = emptyList(),
    val selected: CinetpayCountry? = null,
    /** Code de l'opérateur Mobile Money choisi (ex. `OM`, `MOMO`). */
    val operator: String? = null,
    val amount: String = "",
    val phone: String = "",
    val loading: Boolean = false,
    val message: String? = null,
    /** URL de paiement à ouvrir (consommée par l'UI puis remise à null). */
    val paymentUrl: String? = null,
)

/**
 * ViewModel de la recharge de crédits par **Mobile Money** (CinetPay).
 *
 * Récupère le catalogue de pays, initie le paiement (avec `Idempotency-Key`) et expose l'URL
 * hébergée à ouvrir. Le crédit du compte se fait ensuite côté serveur (webhook).
 *
 * @param api client HTTP.
 */
class RechargeViewModel(private val api: ApiClient) : ViewModel() {

    private val json = Json { explicitNulls = false }

    private val _uiState = MutableStateFlow(RechargeUiState())
    val uiState: StateFlow<RechargeUiState> = _uiState.asStateFlow()

    /** Charge le solde + la liste des pays Mobile Money disponibles. */
    fun load() {
        viewModelScope.launch {
            runCatching {
                api.request(Endpoint.get(com.dualmusic.domain.wallet.WalletEndpoints.BALANCE), com.dualmusic.domain.wallet.WalletBalance.serializer())
            }.getOrNull()?.let { b -> _uiState.update { it.copy(balance = b.balance, eurValue = b.eurValue) } }
            val countries = runCatching {
                api.request(
                    Endpoint.get(PaymentEndpoints.CINETPAY_COUNTRIES, anonymous = true),
                    ListSerializer(CinetpayCountry.serializer()),
                )
            }.getOrDefault(emptyList())
            _uiState.update { s ->
                val sel = s.selected ?: countries.firstOrNull()
                s.copy(countries = countries, selected = sel, operator = s.operator ?: sel?.operators?.firstOrNull()?.code)
            }
        }
    }

    fun onMethodChange(m: String) = _uiState.update { it.copy(method = m, message = null) }

    /** Recharge par carte (Stripe Checkout) : ouvre l'URL hébergée. */
    fun payWithStripe() {
        val amount = _uiState.value.amount.toIntOrNull()
        if (amount == null || amount < 1) { _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errEnterValidAmount) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val body = json.encodeToString(
                com.dualmusic.domain.payment.StripeCreditsRequest.serializer(),
                com.dualmusic.domain.payment.StripeCreditsRequest(amount = amount, currency = "usd"),
            )
            runCatching {
                api.request(
                    Endpoint.post(PaymentEndpoints.STRIPE_CREDITS, body, idempotencyKey = java.util.UUID.randomUUID().toString()),
                    com.dualmusic.domain.payment.StripeCreditsResponse.serializer(),
                )
            }.onSuccess { res ->
                _uiState.update { it.copy(loading = false, paymentUrl = res.url, message = com.dualmusic.core.ui.i18n.appStrings.openingPayment) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errRechargeFailed) }
            }
        }
    }

    fun onAmountChange(v: String) = _uiState.update { it.copy(amount = v.filter { c -> c.isDigit() }, message = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phone = v, message = null) }
    fun onCountrySelected(c: CinetpayCountry) =
        _uiState.update { it.copy(selected = c, operator = c.operators.firstOrNull()?.code, message = null) }
    fun onOperatorSelected(code: String) = _uiState.update { it.copy(operator = code, message = null) }

    /** Initie le paiement ; en cas de succès, expose l'URL à ouvrir. */
    fun pay() {
        val s = _uiState.value
        val amount = s.amount.toIntOrNull()
        val country = s.selected
        if (amount == null || amount < 1) { _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errEnterValidAmount) }; return }
        if (country == null) { _uiState.update { it.copy(message = com.dualmusic.core.ui.i18n.appStrings.errChooseCountry) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = null) }
            val body = json.encodeToString(
                CinetpayInitRequest.serializer(),
                CinetpayInitRequest(
                    amount = amount,
                    countryCode = country.countryCode,
                    phone = s.phone.trim().ifBlank { null },
                    paymentMethod = s.operator ?: country.operators.firstOrNull()?.code,
                ),
            )
            val key = java.util.UUID.randomUUID().toString()
            runCatching {
                api.request(
                    Endpoint.post(PaymentEndpoints.CINETPAY_INIT, body, idempotencyKey = key),
                    CinetpayInitResponse.serializer(),
                )
            }.onSuccess { res ->
                _uiState.update { it.copy(loading = false, paymentUrl = res.paymentUrl, message = com.dualmusic.core.ui.i18n.appStrings.openingPayment) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errRechargeFailed) }
            }
        }
    }

    /** À appeler après avoir ouvert l'URL, pour éviter de la rouvrir. */
    fun consumeUrl() = _uiState.update { it.copy(paymentUrl = null) }
}

/**
 * Écran de recharge : montant en crédits + pays + numéro Mobile Money → ouvre le paiement
 * hébergé. Le compte est crédité automatiquement après le paiement (webhook serveur).
 *
 * @param viewModel source d'état.
 */
@Composable
fun RechargeScreen(viewModel: RechargeViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    // Ouvre l'URL de paiement dès qu'elle est disponible, puis la consomme.
    LaunchedEffect(ui.paymentUrl) {
        val url = ui.paymentUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        viewModel.consumeUrl()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        val s = com.dualmusic.core.ui.i18n.LocalStrings.current
        Text(s.rechargeCredits, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Ajoutez des crédits pour voter, acheter des cadeaux et des tickets.", color = colors.mutedForeground)

        // --- Solde actuel ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                Text("💳 ${s.myBalance}", color = colors.mutedForeground)
                Text("${ui.balance.toInt()} ${s.credits}", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("≈ ${"%.2f".format(ui.eurValue)} € · ${s.availableForTx}", color = colors.mutedForeground, fontSize = 12.sp)
            }
        }

        // --- Ajouter des crédits ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                Text("💲 ${s.addCredits}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(s.choosePaymentMethod, color = colors.mutedForeground, fontSize = 12.sp)

                // Onglets méthode : Carte (Stripe) | CinetPay.
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(DualMusicTheme.radii.md)).background(colors.card),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MethodTab("💳 ${s.cardMethod}", ui.method == "card", Modifier.weight(1f)) { viewModel.onMethodChange("card") }
                    MethodTab("📱 CinetPay", ui.method == "cinetpay", Modifier.weight(1f)) { viewModel.onMethodChange("cinetpay") }
                }

                // Montant + préréglages.
                OutlinedTextField(
                    value = ui.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(s.amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    listOf(5, 10, 20).forEach { amt -> PresetChip(amt, Modifier.weight(1f)) { viewModel.onAmountChange(amt.toString()) } }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    listOf(50, 100).forEach { amt -> PresetChip(amt, Modifier.weight(1f)) { viewModel.onAmountChange(amt.toString()) } }
                    Box(Modifier.weight(1f))
                }

                // Formulaire spécifique à la méthode.
                if (ui.method == "cinetpay") {
                    CountrySelector(ui, viewModel)
                    if (!ui.selected?.operators.isNullOrEmpty()) OperatorSelector(ui, viewModel)
                    OutlinedTextField(
                        value = ui.phone,
                        onValueChange = viewModel::onPhoneChange,
                        label = { Text(s.mobileMoneyNumber) },
                        placeholder = { Text("${ui.selected?.phonePrefix ?: ""}...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ui.message?.let { Text(it, color = colors.primaryGlow) }

                DMButton(
                    when {
                        ui.loading -> "…"
                        ui.method == "card" -> s.rechargeWithStripe
                        else -> s.payMobileMoney
                    },
                    enabled = !ui.loading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (ui.method == "card") viewModel.payWithStripe() else viewModel.pay() },
                )
            }
        }
    }
}

/** Onglet de méthode de paiement (sélectionné = fond primaire). */
@Composable
private fun MethodTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DualMusicTheme.radii.md))
            .background(if (selected) DualMusicTheme.gradients.primary else androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent))
            .clickable(onClick = onClick)
            .padding(vertical = DualMusicTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) androidx.compose.ui.graphics.Color.White else colors.foreground, fontWeight = FontWeight.Bold) }
}

/** Bouton de montant préréglé ($). */
@Composable
private fun PresetChip(amount: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DualMusicTheme.radii.md))
            .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
            .clickable(onClick = onClick)
            .padding(vertical = DualMusicTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) { Text("$$amount", color = colors.foreground, fontWeight = FontWeight.Bold) }
}

/** Sélecteur de pays Mobile Money. */
@Composable
private fun CountrySelector(ui: RechargeUiState, vm: RechargeViewModel) {
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.country, color = colors.mutedForeground)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                    .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
                    .clickable { expanded = true }
                    .padding(DualMusicTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ui.selected?.countryName ?: ui.selected?.countryCode ?: com.dualmusic.core.ui.i18n.LocalStrings.current.chooseDots, color = colors.foreground)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.mutedForeground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ui.countries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.countryName ?: c.countryCode} ${c.phonePrefix ?: ""}") },
                        onClick = { vm.onCountrySelected(c); expanded = false },
                    )
                }
            }
        }
    }
}

/** Sélecteur d'opérateur Mobile Money (selon le pays choisi). */
@Composable
private fun OperatorSelector(ui: RechargeUiState, vm: RechargeViewModel) {
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val operators = ui.selected?.operators ?: emptyList()
    val currentLabel = operators.firstOrNull { it.code == ui.operator }?.let { it.label ?: it.code }
        ?: ui.operator ?: com.dualmusic.core.ui.i18n.LocalStrings.current.chooseDots
    Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.operator, color = colors.mutedForeground)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                    .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
                    .clickable { expanded = true }
                    .padding(DualMusicTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentLabel, color = colors.foreground)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.mutedForeground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                operators.forEach { op ->
                    DropdownMenuItem(
                        text = { Text(op.label ?: op.code) },
                        onClick = { vm.onOperatorSelected(op.code); expanded = false },
                    )
                }
            }
        }
    }
}

package com.dualmusic.feature.withdrawal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.WithdrawalRequest
import com.dualmusic.domain.withdrawal.PayoutMethodData

/**
 * Écran de retrait des crédits.
 *
 * Étapes : (1) définir un PIN si absent, (2) choisir une méthode de retrait, (3) saisir un
 * montant (net après frais affiché), (4) confirmer avec le PIN. Historique des demandes en bas.
 *
 * @param viewModel état + actions.
 */
@Composable
fun WithdrawalScreen(viewModel: WithdrawalViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {

        if (ui.isLoading) CircularProgressIndicator(color = colors.primary)
        ui.error?.let { Text(it, color = colors.destructive) }
        if (ui.submitted) Text("✅ Demande de retrait envoyée.", color = colors.primary)

        when (ui.hasPin) {
            false -> CreatePinCard(onCreate = viewModel::createPin)
            true -> WithdrawForm(
                methods = ui.methods,
                selectedMethodId = ui.selectedMethodId,
                onSelectMethod = viewModel::selectMethod,
                amount = ui.amount,
                onAmountChange = viewModel::onAmountChange,
                feePct = ui.net?.feePct,
                net = ui.net?.net,
                submitting = ui.submitting,
                onSubmit = viewModel::submit,
            )
            null -> Unit // en chargement
        }

        if (ui.requests.isNotEmpty()) {
            Text("Historique", color = colors.foreground, fontWeight = FontWeight.Bold)
            ui.requests.forEach { RequestRow(it) }
        }
    }
}

/** Carte de création du PIN de retrait (première configuration). */
@Composable
private fun CreatePinCard(onCreate: (String) -> Unit) {
    val colors = DualMusicTheme.colors
    var pin by remember { mutableStateOf("") }
    DMCard {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text("Crée ton code PIN de retrait (6 chiffres)", color = colors.foreground)
            PinField(value = pin, onChange = { pin = it })
            DMButton("Créer le code", enabled = pin.length == 6) { onCreate(pin) }
        }
    }
}

/** Formulaire de retrait : méthode + montant + net + PIN. */
@Composable
private fun WithdrawForm(
    methods: List<PayoutMethodData>,
    selectedMethodId: String?,
    onSelectMethod: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    feePct: Double?,
    net: Double?,
    submitting: Boolean,
    onSubmit: (String) -> Unit,
) {
    val colors = DualMusicTheme.colors
    var pin by remember { mutableStateOf("") }

    if (methods.isEmpty()) {
        DMCard {
            Text(
                "Aucune méthode de retrait. Ajoute-en une depuis le site pour l'instant.",
                color = colors.mutedForeground,
            )
        }
        return
    }

    // Méthodes.
    Text("Méthode", color = colors.mutedForeground)
    methods.forEach { m ->
        val selected = m.id == selectedMethodId
        DMCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) colors.primary else colors.border,
                    shape = RoundedCornerShape(DualMusicTheme.radii.md),
                )
                .clickable { onSelectMethod(m.id) },
        ) {
            Column {
                Text(m.label ?: m.method, color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(m.subtitle, color = colors.mutedForeground)
            }
        }
    }

    // Montant.
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = { Text("Montant (crédits)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    // Aperçu du net.
    if (feePct != null && net != null) {
        DMCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Frais : ${feePct.toInt()} %", color = colors.mutedForeground)
                Text("Net : ${net.toInt()} crédits", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        }
    }

    // PIN + confirmation.
    Text("Code PIN de retrait", color = colors.mutedForeground)
    PinField(value = pin, onChange = { pin = it })
    DMButton(
        if (submitting) "Envoi…" else "Demander le retrait",
        enabled = !submitting && pin.length == 6,
        isLoading = submitting,
    ) { onSubmit(pin) }
}

/** Champ PIN 6 chiffres (clavier numérique, masqué). */
@Composable
private fun PinField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text("••••••") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Ligne d'historique d'une demande de retrait. */
@Composable
private fun RequestRow(request: WithdrawalRequest) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${request.amount.toInt()} crédits", color = colors.foreground)
                request.createdAt?.let { Text(it.take(10), color = colors.mutedForeground) }
            }
            Text(statusLabel(request.status.name), color = statusColor(request.status.name))
        }
    }
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "En attente"
    "approved" -> "Approuvé"
    "completed" -> "Payé"
    "rejected" -> "Rejeté"
    else -> status
}

@Composable
private fun statusColor(status: String) = when (status.lowercase()) {
    "completed" -> DualMusicTheme.colors.primary
    "rejected" -> DualMusicTheme.colors.destructive
    else -> DualMusicTheme.colors.mutedForeground
}

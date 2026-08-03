package com.dualmusic.feature.withdrawal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.i18n.Strings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.withdrawal.PayoutMethodData
import com.dualmusic.domain.withdrawal.PayoutMethodInput

/** Libellé FR d'un type de méthode. */
private fun methodLabel(method: String, s: Strings): String = when (method) {
    "mobile_money" -> s.payoutMobileMoney
    "paypal" -> s.payoutPaypal
    else -> s.payoutBankTransfer
}

/**
 * Gestion des méthodes de paiement (parité web `PayoutMethodsManager`) : liste (défaut,
 * suppression), et ajout (Mobile Money / Virement / PayPal) avec champs conditionnels.
 */
@Composable
fun PayoutMethodsSection(
    methods: List<PayoutMethodData>,
    onAdd: (PayoutMethodInput) -> Unit,
    onDelete: (String) -> Unit,
    onSetDefault: (String) -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var showForm by remember { mutableStateOf(false) }

    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.payoutTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
                DMButton(s.payoutAdd, style = DMButtonStyle.SECONDARY) { showForm = !showForm }
            }
            Text(s.payoutDesc, color = colors.mutedForeground, fontSize = 12.sp)

            if (showForm) {
                AddMethodForm(onSave = { onAdd(it); showForm = false })
            }

            if (methods.isEmpty()) {
                Text(s.payoutEmpty, color = colors.mutedForeground)
            } else {
                methods.forEach { m -> MethodRow(m, onDelete, onSetDefault) }
            }
        }
    }
}

@Composable
private fun MethodRow(m: PayoutMethodData, onDelete: (String) -> Unit, onSetDefault: (String) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(DualMusicTheme.radii.md))
            .padding(DualMusicTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(m.label ?: methodLabel(m.method, s), color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(m.subtitle, color = colors.mutedForeground, fontSize = 12.sp)
        }
        if (m.isDefault) {
            Box(modifier = Modifier.background(colors.primary.copy(alpha = 0.2f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("★ ${s.payoutDefault}", color = colors.primary, fontSize = 11.sp)
            }
        } else {
            Text("★", color = colors.mutedForeground, modifier = Modifier.clickable { onSetDefault(m.id) }.padding(6.dp))
        }
        Text("🗑", modifier = Modifier.clickable { onDelete(m.id) }.padding(6.dp))
    }
}

@Composable
private fun AddMethodForm(onSave: (PayoutMethodInput) -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var method by remember { mutableStateOf("mobile_money") }
    var label by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var paypalEmail by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
        // Sélecteur de type de méthode (pilules).
        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            listOf("mobile_money", "bank", "paypal").forEach { m ->
                val selected = method == m
                Box(
                    modifier = Modifier
                        .background(if (selected) colors.primary else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                        .clickable { method = m }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(methodLabel(m, s), color = if (selected) Color.White else colors.mutedForeground, fontSize = 12.sp) }
            }
        }
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(s.payoutLabelOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        when (method) {
            "mobile_money" -> {
                OutlinedTextField(value = operator, onValueChange = { operator = it }, label = { Text(s.payoutOperator) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(s.payoutPhone) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            "bank" -> {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(s.payoutBank) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = iban, onValueChange = { iban = it }, label = { Text(s.payoutIban) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = holder, onValueChange = { holder = it }, label = { Text(s.payoutHolder) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                OutlinedTextField(value = paypalEmail, onValueChange = { paypalEmail = it }, label = { Text(s.payoutPaypalEmail) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.payoutSetDefault, color = colors.foreground)
            Switch(checked = isDefault, onCheckedChange = { isDefault = it })
        }
        DMButton(s.payoutSave, modifier = Modifier.fillMaxWidth()) {
            onSave(
                PayoutMethodInput(
                    method = method,
                    label = label.trim().ifBlank { null },
                    phoneNumber = if (method == "mobile_money") phone.trim().ifBlank { null } else null,
                    mobileOperator = if (method == "mobile_money") operator.trim().ifBlank { null } else null,
                    iban = if (method == "bank") iban.trim().ifBlank { null } else null,
                    bankName = if (method == "bank") bankName.trim().ifBlank { null } else null,
                    accountHolder = if (method == "bank") holder.trim().ifBlank { null } else null,
                    paypalEmail = if (method == "paypal") paypalEmail.trim().ifBlank { null } else null,
                    isDefault = isDefault,
                ),
            )
        }
    }
}

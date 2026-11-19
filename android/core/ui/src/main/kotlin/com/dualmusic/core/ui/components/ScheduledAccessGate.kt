package com.dualmusic.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dualmusic.core.ui.datetime.formatTz
import com.dualmusic.core.ui.datetime.isoToEpochMillis
import com.dualmusic.core.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gate d'accès à un événement programmé (duel/concert/compétition) — équivalent Android EXACT du
 * composant web `ScheduledAccessGate.tsx` : bloque l'entrée dans un direct **avant** l'heure
 * programmée (`scheduledAtIso`), et exige un billet si l'événement est déjà `live` mais payant et
 * que le caller n'en a pas encore un. AUCUNE exception pour les acteurs (artiste/manager/admin)
 * tant que l'heure programmée n'est pas atteinte — seul le temps qui passe (ou l'achat du billet
 * pour le cas "déjà en direct, payant") permet de franchir la porte.
 *
 * Ne rend RIEN si l'accès n'est pas bloqué (`blocked == false`).
 *
 * @param type "duel" | "concert" | "competition" — sélectionne le message `{date}` par type.
 * @param scheduledAtIso date/heure programmée (ISO, UTC) de l'événement, ou `null`.
 * @param status statut courant de l'événement (`"live"`, `"ended"`, …) — voir modèle de domaine.
 * @param isActor vrai si le caller est un acteur de l'événement (artiste/manager/admin) — SANS
 *   effet sur le blocage "pas encore commencé", seulement sur l'exigence de billet.
 * @param hasTicket vrai si le caller possède déjà son billet pour cet événement.
 * @param ticketPrice prix du billet (crédits) ; `0` = accès gratuit pour les spectateurs.
 * @param isAuthenticated vrai si le caller est connecté (achat impossible sinon).
 * @param onPurchase déclenche le VRAI achat de billet (branché par l'appelant sur le repository
 *   concerné) ; retourne un [Result] pour afficher l'erreur dans la boîte de dialogue.
 * @param onDismiss retour à la liste catalogue (duels/concerts/compétitions) — fourni par
 *   l'appelant, qui gère la navigation réelle. Ce composant ne connaît PAS la navigation.
 * @param onPurchased callback de succès (en plus de la mise à jour interne de l'état de
 *   chargement) — permet à l'appelant de recharger son propre `hasTicket`.
 */
@Composable
fun ScheduledAccessGate(
    type: String,
    scheduledAtIso: String?,
    status: String?,
    isActor: Boolean,
    hasTicket: Boolean,
    ticketPrice: Double,
    isAuthenticated: Boolean,
    onPurchase: suspend () -> Result<Unit>,
    onDismiss: () -> Unit,
    onPurchased: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    // Ticker "maintenant" : se rafraîchit toutes les 5s (parité web `setInterval`) tant que ce
    // composant reste monté → la porte s'auto-referme dès que l'heure programmée est atteinte,
    // sans qu'un événement externe ne soit nécessaire pour redéclencher la recomposition.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            now = System.currentTimeMillis()
        }
    }

    val startTs = remember(scheduledAtIso) { isoToEpochMillis(scheduledAtIso) }
    val notStartedYet = startTs != null && now < startTs && status != "live" && status != "ended"
    // Événement déjà en direct, payant, et le spectateur (jamais un acteur) n'a pas encore payé.
    val liveRequiresPayment = status == "live" && !isActor && !hasTicket && ticketPrice > 0.0
    val blocked = notStartedYet || liveRequiresPayment
    if (!blocked) return

    var paying by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }

    val formatted = if (!scheduledAtIso.isNullOrBlank()) formatTz(scheduledAtIso, "dd MMMM yyyy HH:mm") else ""
    val msgTemplate = when (type) {
        "duel" -> strings.scheduledNotStartedMsgDuel
        "competition" -> strings.scheduledNotStartedMsgCompetition
        else -> strings.scheduledNotStartedMsgConcert
    }
    val message = if (liveRequiresPayment) strings.scheduledPaidAccessMsg else msgTemplate.replace("{date}", formatted)
    val title = if (liveRequiresPayment) strings.scheduledPaidAccessTitle else strings.scheduledNotStartedTitle

    // Le paiement reste proposé même AVANT l'heure programmée (achat anticipé) — parité web
    // `canPay`, calculé indépendamment de `liveRequiresPayment`.
    val canPay = !isActor && !hasTicket && ticketPrice > 0.0

    fun purchase() {
        if (!isAuthenticated) { onDismiss(); return }
        if (paying) return
        scope.launch {
            paying = true
            purchaseError = null
            onPurchase()
                .onSuccess { paying = false; onPurchased() }
                .onFailure { paying = false; purchaseError = it.message ?: "—" }
        }
    }

    AlertDialog(
        onDismissRequest = {}, // Non-fermable tant que bloqué (parité web `<AlertDialog open>` sans onOpenChange).
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = {
            Icon(
                if (liveRequiresPayment) Icons.Filled.CreditCard else Icons.Filled.DateRange,
                contentDescription = null,
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                if (hasTicket) {
                    Text(strings.scheduledAccessAlready, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                }
                purchaseError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (canPay) {
                DMButton(
                    title = "${strings.scheduledPayAccess} · ${ticketPrice.toInt()} ${strings.creditUnit}",
                    isLoading = paying,
                    modifier = Modifier.width(240.dp),
                    onClick = ::purchase,
                )
            } else {
                DMButton(title = strings.scheduledBackHome, modifier = Modifier.width(160.dp), onClick = onDismiss)
            }
        },
        dismissButton = if (canPay) {
            { TextButton(onClick = onDismiss) { Text(strings.scheduledBackHome) } }
        } else {
            null
        },
    )
}

package com.dualmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Popup d'erreur **moderne et centralisée** pour les actions en direct (démarrer la diffusion,
 * rejoindre une salle...) qui peuvent échouer de façon transitoire (réseau, salle pas encore
 * prête) — remplace le simple bandeau de texte rouge en bas d'écran, peu visible et sans action
 * possible. Propose « Réessayer » (relance l'action fournie par l'appelant) et « Fermer ».
 *
 * Usage dans un écran de direct (duel/concert/compétition/live) :
 * ```
 * error?.let {
 *     DMErrorDialog(message = it, onRetry = { viewModel.startBroadcast() }, onDismiss = { viewModel.clearError() })
 * }
 * ```
 *
 * @param message message déjà humanisé (jamais de texte technique brut — voir convention de
 *   l'appli : les erreurs affichées à l'utilisateur passent toujours par un message clair).
 * @param onRetry callback de relance ; `null` masque le bouton « Réessayer » (erreur non
 *   récupérable par un simple nouvel essai, ex. banni de l'événement).
 * @param onDismiss ferme la popup sans relancer.
 * @param retryLabel libellé du bouton de relance (par défaut « Réessayer »).
 */
@Composable
fun DMErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Réessayer",
) {
    val colors = DualMusicTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(colors.background, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(colors.destructive.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(30.dp))
            }
            Text(
                message,
                color = colors.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DMButton(
                    "Fermer",
                    style = DMButtonStyle.OUTLINE,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                if (onRetry != null) {
                    DMButton(
                        retryLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { onDismiss(); onRetry() },
                    )
                }
            }
        }
    }
}

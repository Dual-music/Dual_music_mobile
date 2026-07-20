package com.dualmusic.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMLogo
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Écran de vérification de l'email après inscription.
 *
 * Le code a été envoyé automatiquement à l'inscription ; l'utilisateur le saisit ici.
 * La vérification est **non bloquante** : le bouton « Passer » permet d'entrer dans l'app
 * et de valider plus tard.
 *
 * @param viewModel source d'état + actions (verifyEmail / resendEmailCode / skipVerification).
 * @param email adresse destinataire (affichage).
 */
@Composable
fun EmailVerifyScreen(viewModel: AuthViewModel, email: String) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        DMLogo(height = 64.dp, modifier = Modifier.padding(top = DualMusicTheme.spacing.xxl))
        Text("Vérifie ton email", color = colors.foreground, fontWeight = FontWeight.Bold)
        Text(
            "Un code de vérification a été envoyé à $email.",
            color = colors.mutedForeground,
            textAlign = TextAlign.Center,
        )

        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.verifyCode,
                    onValueChange = viewModel::onVerifyCodeChange,
                    label = { Text("Code reçu par email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                ui.info?.let { Text(it, color = colors.primaryGlow) }
                ui.error?.let { Text(it, color = colors.destructive) }

                DMButton(
                    title = if (ui.isSubmitting) "Vérification…" else "Valider",
                    isLoading = ui.isSubmitting,
                    enabled = ui.verifyCode.length in 4..8 && !ui.isSubmitting,
                    onClick = viewModel::verifyEmail,
                )
                DMButton("Renvoyer le code", style = DMButtonStyle.SECONDARY, onClick = viewModel::resendEmailCode)
                DMButton("Passer pour l'instant", style = DMButtonStyle.OUTLINE, onClick = viewModel::skipVerification)
            }
        }
    }
}

package com.dualmusic.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
 * Étape 3 de l'inscription (après validation du code email) : saisie des informations de
 * profil — **nom** (requis), **pays** et **numéro** (optionnel). Enregistre via
 * `PATCH /users/me` puis entre dans l'app. « Plus tard » permet de compléter ultérieurement.
 *
 * @param viewModel source d'état (partagé avec l'écran d'auth).
 */
@Composable
fun ProfileCompletionScreen(viewModel: AuthViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        DMLogo(height = 64.dp, modifier = Modifier.padding(top = DualMusicTheme.spacing.xxl))
        Text("Complète ton profil", color = colors.foreground, fontWeight = FontWeight.Bold)
        Text(
            "Ton email est vérifié ✅. Dis-nous en un peu plus pour finaliser ton compte.",
            color = colors.mutedForeground,
            textAlign = TextAlign.Center,
        )

        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = { Text("Nom complet *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CountryField(ui, viewModel)
                OutlinedTextField(
                    value = ui.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("Téléphone (optionnel)") },
                    placeholder = { Text("${ui.country.dial}612345678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                ui.info?.let { Text(it, color = colors.primaryGlow) }
                ui.error?.let { Text(it, color = colors.destructive) }

                DMButton(
                    title = if (ui.isSubmitting) "Enregistrement…" else "Terminer",
                    isLoading = ui.isSubmitting,
                    enabled = !ui.isSubmitting && ui.fullName.isNotBlank(),
                    onClick = viewModel::completeProfile,
                )
                DMButton(
                    title = "Plus tard",
                    style = DMButtonStyle.OUTLINE,
                    enabled = !ui.isSubmitting,
                    onClick = viewModel::skipProfile,
                )
            }
        }
    }
}

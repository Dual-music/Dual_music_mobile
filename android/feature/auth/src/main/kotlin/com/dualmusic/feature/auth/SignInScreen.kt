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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Écran de connexion — email + mot de passe (primaire) + entrée Google.
 *
 * Reprend le design system `core:ui` (fond héro, CTA dégradé, thème sombre) pour la
 * parité visuelle avec le web.
 *
 * @param onGoogle callback pour lancer le flux Google (ouverture Custom Tab).
 */
@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onGoogle: () -> Unit = {},
) {
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
        Text(
            "Dual Music",
            color = colors.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = DualMusicTheme.spacing.xxl),
        )
        Text("Connecte-toi pour rejoindre les lives", color = colors.mutedForeground)

        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                ui.error?.let { Text(it, color = colors.destructive) }

                DMButton(
                    title = "Se connecter",
                    isLoading = ui.isSubmitting,
                    enabled = ui.canSubmit,
                    onClick = viewModel::signIn,
                )

                DMButton(
                    title = "Continuer avec Google",
                    style = DMButtonStyle.OUTLINE,
                    onClick = onGoogle,
                )
            }
        }
    }
}

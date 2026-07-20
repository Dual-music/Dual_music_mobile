package com.dualmusic.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import com.dualmusic.core.ui.components.DMLogo
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Écran d'authentification — connexion, inscription, mot de passe oublié, réinitialisation.
 *
 * Reprend les champs du web (inscription : nom complet, email, mot de passe + confirmation,
 * téléphone optionnel, code de parrainage optionnel, acceptation des CGU).
 *
 * @param viewModel source d'état.
 * @param onGoogle callback pour lancer le flux Google (différé).
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
        DMLogo(height = 72.dp, modifier = Modifier.padding(top = DualMusicTheme.spacing.xxl))
        Text(subtitleFor(ui.mode), color = colors.mutedForeground, textAlign = TextAlign.Center)

        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                when (ui.mode) {
                    AuthMode.LOGIN -> LoginFields(ui, viewModel)
                    AuthMode.REGISTER -> RegisterFields(ui, viewModel)
                    AuthMode.FORGOT -> ForgotFields(ui, viewModel)
                    AuthMode.RESET -> ResetFields(ui, viewModel)
                }

                ui.info?.let { Text(it, color = colors.primaryGlow) }
                ui.error?.let { Text(it, color = colors.destructive) }

                DMButton(
                    title = submitLabel(ui.mode, ui.isSubmitting),
                    isLoading = ui.isSubmitting,
                    enabled = ui.canSubmit,
                    onClick = viewModel::submit,
                )

                if (ui.mode == AuthMode.LOGIN) {
                    DMButton("Continuer avec Google", style = DMButtonStyle.OUTLINE, onClick = onGoogle)
                }

                ModeLinks(ui, viewModel)
            }
        }
    }
}

/** Champs de connexion. */
@Composable
private fun LoginFields(ui: SignInUiState, vm: AuthViewModel) {
    EmailField(ui.email, vm::onEmailChange)
    PasswordField(ui.password, vm::onPasswordChange, label = "Mot de passe")
}

/** Champs d'inscription (parité web). */
@Composable
private fun RegisterFields(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    OutlinedTextField(
        value = ui.fullName,
        onValueChange = vm::onFullNameChange,
        label = { Text("Nom complet *") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    EmailField(ui.email, vm::onEmailChange)
    PasswordField(ui.password, vm::onPasswordChange, label = "Mot de passe *", supporting = "Au moins 8 caractères")
    PasswordField(
        ui.confirmPassword,
        vm::onConfirmPasswordChange,
        label = "Confirmer le mot de passe *",
        supporting = if (ui.confirmPassword.isNotEmpty() && ui.confirmPassword != ui.password) {
            "Les mots de passe ne correspondent pas"
        } else {
            null
        },
    )
    OutlinedTextField(
        value = ui.phone,
        onValueChange = vm::onPhoneChange,
        label = { Text("Téléphone (optionnel)") },
        placeholder = { Text("+33612345678") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = ui.referralCode,
        onValueChange = vm::onReferralChange,
        label = { Text("Code de parrainage (optionnel)") },
        placeholder = { Text("Ex : REF-ABC12345") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ui.acceptTerms, onCheckedChange = vm::onAcceptTermsChange)
        Text(
            "J'accepte la Politique de confidentialité et les Conditions d'utilisation.",
            color = colors.mutedForeground,
        )
    }
}

/** Champ email de la demande de réinitialisation. */
@Composable
private fun ForgotFields(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    Text("Reçois un code par email pour réinitialiser ton mot de passe.", color = colors.mutedForeground)
    EmailField(ui.email, vm::onEmailChange)
}

/** Champs de réinitialisation : email + code + nouveau mot de passe. */
@Composable
private fun ResetFields(ui: SignInUiState, vm: AuthViewModel) {
    EmailField(ui.email, vm::onEmailChange)
    OutlinedTextField(
        value = ui.resetCode,
        onValueChange = vm::onResetCodeChange,
        label = { Text("Code reçu par email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(ui.newPassword, vm::onNewPasswordChange, label = "Nouveau mot de passe", supporting = "Au moins 8 caractères")
}

/** Liens de navigation entre modes (oublié / bascule connexion-inscription / retour). */
@Composable
private fun ModeLinks(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    when (ui.mode) {
        AuthMode.LOGIN -> {
            LinkText("Mot de passe oublié ?") { vm.setMode(AuthMode.FORGOT) }
            LinkText("Pas de compte ? S'inscrire") { vm.setMode(AuthMode.REGISTER) }
        }
        AuthMode.REGISTER -> LinkText("Déjà un compte ? Se connecter") { vm.setMode(AuthMode.LOGIN) }
        AuthMode.FORGOT -> LinkText("Retour à la connexion") { vm.setMode(AuthMode.LOGIN) }
        AuthMode.RESET -> LinkText("Retour à la connexion") { vm.setMode(AuthMode.LOGIN) }
    }
}

/** Champ email standard. */
@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Email") },
        placeholder = { Text("votremail@exemple.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Champ mot de passe standard (masqué). */
@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = supporting?.let { msg -> { Text(msg) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lien cliquable centré. */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = DualMusicTheme.colors.primaryGlow,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DualMusicTheme.spacing.xs),
    )
}

private fun subtitleFor(mode: AuthMode): String = when (mode) {
    AuthMode.LOGIN -> "Connecte-toi pour rejoindre les lives"
    AuthMode.REGISTER -> "Crée ton compte pour rejoindre les lives"
    AuthMode.FORGOT -> "Réinitialiser le mot de passe"
    AuthMode.RESET -> "Saisis le code reçu et ton nouveau mot de passe"
}

private fun submitLabel(mode: AuthMode, submitting: Boolean): String = when {
    submitting -> "Veuillez patienter…"
    mode == AuthMode.LOGIN -> "Se connecter"
    mode == AuthMode.REGISTER -> "Créer mon compte"
    mode == AuthMode.FORGOT -> "Envoyer le code"
    else -> "Réinitialiser"
}

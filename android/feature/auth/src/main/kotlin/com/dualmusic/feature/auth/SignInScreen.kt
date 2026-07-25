package com.dualmusic.feature.auth

import com.dualmusic.domain.geo.Country
import com.dualmusic.domain.geo.Countries

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Capturé au niveau @Composable : réutilisable dans les lambdas (coroutines) ci-dessous.
    val strings = com.dualmusic.core.ui.i18n.LocalStrings.current

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
        Text(subtitleFor(ui.mode, com.dualmusic.core.ui.i18n.LocalStrings.current), color = colors.mutedForeground, textAlign = TextAlign.Center)

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
                    title = submitLabel(ui.mode, ui.isSubmitting, com.dualmusic.core.ui.i18n.LocalStrings.current),
                    isLoading = ui.isSubmitting,
                    enabled = ui.canSubmit,
                    onClick = viewModel::submit,
                )

                if (ui.mode == AuthMode.LOGIN) {
                    DMButton(
                        title = strings.continueWithGoogle,
                        style = DMButtonStyle.OUTLINE,
                        enabled = !ui.isSubmitting,
                        onClick = {
                            scope.launch {
                                runCatching { requestGoogleIdToken(context) }
                                    .onSuccess { viewModel.signInWithGoogle(it) }
                                    .onFailure { viewModel.onGoogleError(it.message ?: strings.googleCancelled) }
                            }
                        },
                    )
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
    PasswordField(ui.password, vm::onPasswordChange, label = com.dualmusic.core.ui.i18n.LocalStrings.current.password)
}

/**
 * Étape 1 de l'inscription : email + mot de passe + confirmation UNIQUEMENT.
 * Un code de vérification est ensuite envoyé par email ; les autres informations (nom,
 * pays, numéro) sont saisies à l'étape suivante (voir [ProfileCompletionScreen]).
 */
@Composable
private fun RegisterFields(ui: SignInUiState, vm: AuthViewModel) {
    EmailField(ui.email, vm::onEmailChange)
    PasswordField(ui.password, vm::onPasswordChange, label = com.dualmusic.core.ui.i18n.LocalStrings.current.passwordStar, supporting = com.dualmusic.core.ui.i18n.LocalStrings.current.atLeast8)
    PasswordField(
        ui.confirmPassword,
        vm::onConfirmPasswordChange,
        label = com.dualmusic.core.ui.i18n.LocalStrings.current.confirmPasswordStar,
        supporting = if (ui.confirmPassword.isNotEmpty() && ui.confirmPassword != ui.password) {
            com.dualmusic.core.ui.i18n.LocalStrings.current.passwordsDontMatch
        } else {
            null
        },
    )
}

/** Champ email de la demande de réinitialisation. */
@Composable
private fun ForgotFields(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    Text(com.dualmusic.core.ui.i18n.LocalStrings.current.forgotHint, color = colors.mutedForeground)
    EmailField(ui.email, vm::onEmailChange)
}

/** Champs de réinitialisation : email + code + nouveau mot de passe. */
@Composable
private fun ResetFields(ui: SignInUiState, vm: AuthViewModel) {
    EmailField(ui.email, vm::onEmailChange)
    OutlinedTextField(
        value = ui.resetCode,
        onValueChange = vm::onResetCodeChange,
        label = { Text(com.dualmusic.core.ui.i18n.LocalStrings.current.codeFromEmail) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(ui.newPassword, vm::onNewPasswordChange, label = com.dualmusic.core.ui.i18n.LocalStrings.current.newPassword, supporting = com.dualmusic.core.ui.i18n.LocalStrings.current.atLeast8)
}

/** Liens de navigation entre modes (oublié / bascule connexion-inscription / retour). */
@Composable
private fun ModeLinks(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    when (ui.mode) {
        AuthMode.LOGIN -> {
            LinkText(com.dualmusic.core.ui.i18n.LocalStrings.current.forgotPassword) { vm.setMode(AuthMode.FORGOT) }
            LinkText(com.dualmusic.core.ui.i18n.LocalStrings.current.noAccountSignUp) { vm.setMode(AuthMode.REGISTER) }
        }
        AuthMode.REGISTER -> LinkText(com.dualmusic.core.ui.i18n.LocalStrings.current.alreadyAccountSignIn) { vm.setMode(AuthMode.LOGIN) }
        AuthMode.FORGOT -> LinkText(com.dualmusic.core.ui.i18n.LocalStrings.current.backToLogin) { vm.setMode(AuthMode.LOGIN) }
        AuthMode.RESET -> LinkText(com.dualmusic.core.ui.i18n.LocalStrings.current.backToLogin) { vm.setMode(AuthMode.LOGIN) }
    }
}

/** Sélecteur de pays (menu déroulant) → alimente countryCode + phoneCountryCode. */
@Composable
internal fun CountryField(ui: SignInUiState, vm: AuthViewModel) {
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
        Text(com.dualmusic.core.ui.i18n.LocalStrings.current.countryStar, color = colors.mutedForeground)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                    .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
                    .clickable { expanded = true }
                    .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${ui.country.name} (${ui.country.dial})", color = colors.foreground)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = com.dualmusic.core.ui.i18n.LocalStrings.current.chooseCountry, tint = colors.mutedForeground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Countries.ALL.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.name} (${c.dial})") },
                        onClick = {
                            vm.onCountrySelected(c)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Champ email standard. */
@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(com.dualmusic.core.ui.i18n.LocalStrings.current.email) },
        placeholder = { Text(com.dualmusic.core.ui.i18n.LocalStrings.current.emailPlaceholder) },
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

private fun subtitleFor(mode: AuthMode, s: com.dualmusic.core.ui.i18n.Strings): String = when (mode) {
    AuthMode.LOGIN -> s.authLoginSubtitle
    AuthMode.REGISTER -> s.authRegisterSubtitle
    AuthMode.FORGOT -> s.resetPasswordTitle
    AuthMode.RESET -> s.authResetSubtitle
}

private fun submitLabel(mode: AuthMode, submitting: Boolean, s: com.dualmusic.core.ui.i18n.Strings): String = when {
    submitting -> s.pleaseWait
    mode == AuthMode.LOGIN -> s.signIn
    mode == AuthMode.REGISTER -> s.createAccount
    mode == AuthMode.FORGOT -> s.sendCode
    else -> s.reset
}

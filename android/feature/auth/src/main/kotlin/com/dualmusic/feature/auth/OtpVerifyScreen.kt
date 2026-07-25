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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.theme.DualMusicTheme
import kotlinx.coroutines.launch

/**
 * Écran de **vérification du numéro** par OTP (post-login).
 *
 * Rappel : ce n'est PAS une connexion passwordless — l'utilisateur est déjà authentifié ;
 * on vérifie la propriété de son numéro via un code SMS.
 *
 * @param onVerified callback appelé une fois le code validé.
 */
@Composable
fun OtpVerifyScreen(
    repository: AuthRepository,
    onVerified: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = DualMusicTheme.colors

    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Envoie un premier code à l'ouverture de l'écran.
    LaunchedEffect(Unit) {
        sending = true
        runCatching { repository.sendPhoneOtp() }
        sending = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        Text("Vérifie ton numéro", color = colors.foreground)
        Text("Saisis le code à 6 chiffres reçu par SMS", color = colors.mutedForeground)

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(it, color = colors.destructive) }

        DMButton(
            title = "Vérifier",
            isLoading = submitting,
            enabled = code.length == 6,
        ) {
            scope.launch {
                submitting = true; error = null
                runCatching { repository.verifyPhoneOtp(code) }
                    .onSuccess { onVerified() }
                    .onFailure { error = com.dualmusic.core.ui.i18n.appStrings.errCodeInvalid }
                submitting = false
            }
        }

        TextButton(
            enabled = !sending,
            onClick = {
                scope.launch {
                    sending = true; error = null
                    runCatching { repository.sendPhoneOtp() }
                    sending = false
                }
            },
        ) { Text(if (sending) "Envoi…" else "Renvoyer le code", color = colors.primary) }
    }
}

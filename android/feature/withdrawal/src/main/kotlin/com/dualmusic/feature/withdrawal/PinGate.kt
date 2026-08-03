package com.dualmusic.feature.withdrawal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Carte de déverrouillage (zone retrait verrouillée) : saisie du PIN + « PIN oublié ? » →
 * réinitialisation par OTP email. Si aucun PIN n'existe, on délègue à la création.
 */
@Composable
internal fun PinLockCard(
    hasPin: Boolean,
    resetSent: Boolean,
    onVerify: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRequestReset: () -> Unit,
    onConfirmReset: (String, String) -> Unit,
) {
    if (!hasPin) {
        CreatePinCard(onCreate = onCreate)
        return
    }
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var pin by remember { mutableStateOf("") }
    var showReset by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text("🔒 ${s.pinEnterTitle}", color = colors.foreground, fontWeight = FontWeight.Bold)
            Text(s.pinEnterDesc, color = colors.mutedForeground)

            if (!showReset) {
                PinField(value = pin, onChange = { pin = it })
                DMButton(s.pinUnlock, enabled = pin.length == 6, modifier = Modifier.fillMaxWidth()) { onVerify(pin) }
                Text(s.pinForgot, color = colors.accent, modifier = Modifier.fillMaxWidth().clickable { showReset = true; onRequestReset() })
            } else {
                if (resetSent) Text(s.pinResetSent, color = colors.primary)
                PinField(value = otp, onChange = { otp = it }) // OTP 6 chiffres
                Text(s.pinResetOtp, color = colors.mutedForeground)
                PinField(value = newPin, onChange = { newPin = it })
                Text(s.pinResetNewPin, color = colors.mutedForeground)
                DMButton(s.pinResetConfirmBtn, enabled = otp.length == 6 && newPin.length == 6, modifier = Modifier.fillMaxWidth()) {
                    onConfirmReset(otp, newPin)
                }
                DMButton(s.pinResetRequestBtn, style = DMButtonStyle.OUTLINE, modifier = Modifier.fillMaxWidth()) { onRequestReset() }
            }
        }
    }
}

/** Contrôles quand la zone est déverrouillée : changer le PIN + verrouiller. */
@Composable
internal fun UnlockedControls(onChangePin: (String, String) -> Unit, onLock: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var showChange by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🔓 ${s.pinUnlocked}", color = colors.primary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                    DMButton(s.pinChange, style = DMButtonStyle.SECONDARY) { showChange = !showChange }
                    DMButton(s.pinLock, style = DMButtonStyle.OUTLINE) { onLock() }
                }
            }
            if (showChange) {
                Text(s.pinCurrent, color = colors.mutedForeground)
                PinField(value = current, onChange = { current = it })
                Text(s.pinNew, color = colors.mutedForeground)
                PinField(value = newPin, onChange = { newPin = it })
                DMButton(s.pinChangeBtn, enabled = current.length == 6 && newPin.length == 6, modifier = Modifier.fillMaxWidth()) {
                    onChangePin(newPin, current)
                    showChange = false
                }
            }
        }
    }
}

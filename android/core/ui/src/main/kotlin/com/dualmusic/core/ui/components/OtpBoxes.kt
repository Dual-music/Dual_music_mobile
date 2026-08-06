package com.dualmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Saisie d'un code à **une case par chiffre** (parité web `InputOTP`).
 *
 * Un unique champ (invisible) capture la saisie ; l'affichage est une rangée de cases, la case
 * active surlignée. Accepte uniquement des chiffres, longueur bornée à [length].
 *
 * @param value valeur courante (chiffres).
 * @param onValueChange rappelé à chaque changement (déjà filtré/tronqué).
 * @param length nombre de cases (6 par défaut).
 */
@Composable
fun OtpBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int = 6,
    modifier: Modifier = Modifier,
) {
    val colors = DualMusicTheme.colors
    BasicTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() }.take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
        textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val active = i == value.length.coerceAtMost(length - 1) && value.length < length
                    val ch = value.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .size(44.dp, 52.dp)
                            .background(colors.muted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) colors.primary else colors.border,
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ch, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        },
    )
}

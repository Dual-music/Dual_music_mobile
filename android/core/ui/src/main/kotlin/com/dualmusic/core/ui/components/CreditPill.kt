package com.dualmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.theme.DualMusicTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Pastille affichant un solde/prix en crédits, avec l'accent rose néon.
 *
 * ```
 * CreditPill(credits = 1250.0)              // « 💎 1 250 »
 * CreditPill(credits = 50.0, label = "Vote")
 * ```
 */
@Composable
fun CreditPill(
    credits: Double,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DualMusicTheme.radii.pill))
            .background(colors.accent)
            .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
    ) {
        Text("💎 ", color = colors.accentForeground)
        Text(
            text = formatCredits(credits),
            color = colors.accentForeground,
            fontWeight = FontWeight.SemiBold,
        )
        if (label != null) {
            Text("  $label", color = colors.accentForeground)
        }
    }
}

/** Formatage avec séparateur de milliers, sans décimales inutiles. */
private fun formatCredits(credits: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = if (credits % 1.0 == 0.0) 0 else 2
    return nf.format(credits)
}

package com.dualmusic.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Conteneur « carte » de Dual Music : surface `card`, rayon 12dp, bordure fine. Base
 * visuelle des listes (duels, concerts, replays…).
 *
 * ```
 * DMCard {
 *     Text("Duel du soir", style = MaterialTheme.typography.titleMedium)
 * }
 * ```
 *
 * @param padded ajoute un padding interne standard (défaut : oui).
 */
@Composable
fun DMCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DualMusicTheme.colors
    val shape = RoundedCornerShape(DualMusicTheme.radii.md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card, shape)
            .border(BorderStroke(1.dp, colors.border), shape)
            .padding(if (padded) DualMusicTheme.spacing.lg else 0.dp),
        content = content,
    )
}

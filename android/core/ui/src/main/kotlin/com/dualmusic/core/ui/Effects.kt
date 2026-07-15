package com.dualmusic.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Halos lumineux (miroir des `--shadow-glow*` du web) : la signature « néon » de Dual Music.
 *
 * Compose n'a pas de flou de type box-shadow ; on approche l'effet avec une ombre colorée
 * (`spotColor`/`ambientColor` = violet) et une élévation élevée. Rendu proche du glow web.
 */

/** Halo violet standard (éléments interactifs). */
fun Modifier.dmGlow(radius: Int = 12): Modifier = this.shadow(
    elevation = radius.dp,
    shape = RoundedCornerShape(12.dp),
    ambientColor = GlowViolet,
    spotColor = GlowViolet,
)

/** Halo violet intense (éléments actifs/live). */
fun Modifier.dmGlowStrong(): Modifier = dmGlow(radius = 20)

// Violet de marque (280 70% 55%) — constant hors composition pour usage dans les modifiers.
private val GlowViolet = Color.hsl(280f, 0.70f, 0.55f)

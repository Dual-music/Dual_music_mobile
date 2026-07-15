package com.dualmusic.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dégradés de marque (miroir des `--gradient-*` du web).
 * Construits à partir des mêmes couleurs HSL que le thème.
 */
@Immutable
class DMGradients(colors: DMColors) {
    /** Violet → rose (CTA principaux). `linear-gradient(135°, primary, neon-pink)`. */
    val primary: Brush = Brush.linearGradient(
        listOf(colors.primary, colors.neonPink),
        start = Offset.Zero, end = Offset.Infinite, // ≈ 135° (coin haut-gauche → bas-droit)
    )

    /** Bleu → cyan. `linear-gradient(135°, electric-blue, neon-cyan)`. */
    val electric: Brush = Brush.linearGradient(
        listOf(colors.electricBlue, colors.neonCyan),
        start = Offset.Zero, end = Offset.Infinite,
    )

    /** Fond héro sombre → violet. `linear-gradient(180°, bg, hsl(280 50% 15%))`. */
    val hero: Brush = Brush.verticalGradient(
        listOf(colors.background, Color.hsl(280f, 0.50f, 0.15f)),
    )
}

/** Rayons de coin (web `--radius: 0.75rem` = 12dp + dérivés). */
@Immutable
data class DMRadii(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp, // = --radius
    val lg: Dp = 16.dp,
    val pill: Dp = 999.dp,
)

/** Échelle d'espacement (base 4dp). */
@Immutable
data class DMSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

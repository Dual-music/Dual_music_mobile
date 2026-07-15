package com.dualmusic.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Thème Dual Music pour Jetpack Compose.
 *
 * Expose les jetons de marque (couleurs, dégradés, rayons, espacement) via des
 * CompositionLocal, ET mappe la palette sur `MaterialTheme` pour que les composants
 * Material 3 standards héritent des bonnes couleurs. **Thème sombre par défaut**, comme
 * le web.
 *
 * Usage (au sommet de l'app) :
 * ```
 * DualMusicTheme { AppNavHost() }
 * ```
 * Accès aux jetons dans un composable :
 * ```
 * Text("Solde", color = DualMusicTheme.colors.mutedForeground)
 * Box(Modifier.background(DualMusicTheme.gradients.primary))
 * ```
 */
object DualMusicTheme {
    val colors: DMColors
        @Composable @ReadOnlyComposable get() = LocalDMColors.current
    val gradients: DMGradients
        @Composable @ReadOnlyComposable get() = LocalDMGradients.current
    val radii: DMRadii
        @Composable @ReadOnlyComposable get() = LocalDMRadii.current
    val spacing: DMSpacing
        @Composable @ReadOnlyComposable get() = LocalDMSpacing.current
}

@Composable
fun DualMusicTheme(
    darkTheme: Boolean = true, // défaut : sombre (parité web)
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val gradients = DMGradients(colors)

    CompositionLocalProvider(
        LocalDMColors provides colors,
        LocalDMGradients provides gradients,
        LocalDMRadii provides DMRadii(),
        LocalDMSpacing provides DMSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(darkTheme),
            typography = DMTypography,
            content = content,
        )
    }
}

/** Mappe les jetons de marque sur un `ColorScheme` Material 3. */
private fun DMColors.toMaterialScheme(dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = primaryForeground,
        secondary = secondary,
        onSecondary = secondaryForeground,
        tertiary = accent,
        onTertiary = accentForeground,
        background = background,
        onBackground = foreground,
        surface = card,
        onSurface = cardForeground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        error = destructive,
        outline = border,
    )

// CompositionLocal — `staticCompositionLocalOf` car les jetons changent rarement (perf).
internal val LocalDMColors = staticCompositionLocalOf { DarkColors }
internal val LocalDMGradients = staticCompositionLocalOf { DMGradients(DarkColors) }
internal val LocalDMRadii = staticCompositionLocalOf { DMRadii() }
internal val LocalDMSpacing = staticCompositionLocalOf { DMSpacing() }

/**
 * Typographie — pile système (Roboto), en cohérence avec le web (pile système).
 * On garde les defaults Material 3 ; centraliser ici toute police de marque future.
 */
internal val DMTypography = Typography()

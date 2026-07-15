package com.dualmusic.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Jetons de couleur sémantiques (miroir exact de `duel_music_frontend/src/index.css`).
 *
 * Compose fournit `Color.hsl(hue, saturation, lightness)` : parité DIRECTE avec les
 * valeurs HSL du web — ne jamais introduire de teinte hors de cette liste.
 *
 * Deux instances : [DarkColors] (défaut de l'app) et [LightColors].
 */
@Immutable
data class DMColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val primaryGlow: Color,
    val electricBlue: Color,
    val neonPink: Color,
    val neonCyan: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
)

/** Thème CLAIR (`:root` du web). */
val LightColors = DMColors(
    background = Color.hsl(0f, 0f, 1f),
    foreground = Color.hsl(240f, 0.10f, 0.10f),
    card = Color.hsl(0f, 0f, 1f),
    cardForeground = Color.hsl(240f, 0.10f, 0.10f),
    primary = Color.hsl(280f, 0.70f, 0.55f),
    primaryForeground = Color.hsl(0f, 0f, 1f),
    primaryGlow = Color.hsl(280f, 0.80f, 0.65f),
    electricBlue = Color.hsl(210f, 1f, 0.60f),
    neonPink = Color.hsl(330f, 1f, 0.65f),
    neonCyan = Color.hsl(180f, 1f, 0.60f),
    secondary = Color.hsl(240f, 0.15f, 0.20f),
    secondaryForeground = Color.hsl(0f, 0f, 1f),
    muted = Color.hsl(240f, 0.10f, 0.90f),
    mutedForeground = Color.hsl(240f, 0.05f, 0.40f),
    accent = Color.hsl(330f, 1f, 0.65f),
    accentForeground = Color.hsl(0f, 0f, 1f),
    destructive = Color.hsl(0f, 0.842f, 0.602f),
    border = Color.hsl(240f, 0.10f, 0.85f),
    input = Color.hsl(240f, 0.10f, 0.85f),
    ring = Color.hsl(280f, 0.70f, 0.55f),
)

/** Thème SOMBRE (`.dark` du web) — **thème par défaut de l'application**. */
val DarkColors = DMColors(
    background = Color.hsl(240f, 0.15f, 0.08f),
    foreground = Color.hsl(0f, 0f, 0.98f),
    card = Color.hsl(240f, 0.15f, 0.12f),
    cardForeground = Color.hsl(0f, 0f, 0.98f),
    primary = Color.hsl(280f, 0.70f, 0.55f),
    primaryForeground = Color.hsl(0f, 0f, 1f),
    primaryGlow = Color.hsl(280f, 0.80f, 0.65f),
    electricBlue = Color.hsl(210f, 1f, 0.60f),
    neonPink = Color.hsl(330f, 1f, 0.65f),
    neonCyan = Color.hsl(180f, 1f, 0.60f),
    secondary = Color.hsl(240f, 0.15f, 0.18f),
    secondaryForeground = Color.hsl(0f, 0f, 0.98f),
    muted = Color.hsl(240f, 0.15f, 0.18f),
    mutedForeground = Color.hsl(240f, 0.05f, 0.65f),
    accent = Color.hsl(330f, 1f, 0.65f),
    accentForeground = Color.hsl(0f, 0f, 1f),
    destructive = Color.hsl(0f, 0.628f, 0.50f),
    border = Color.hsl(240f, 0.15f, 0.20f),
    input = Color.hsl(240f, 0.15f, 0.20f),
    ring = Color.hsl(280f, 0.70f, 0.55f),
)

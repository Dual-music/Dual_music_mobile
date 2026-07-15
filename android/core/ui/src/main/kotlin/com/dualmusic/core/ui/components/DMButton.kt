package com.dualmusic.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import com.dualmusic.core.ui.dmGlow
import com.dualmusic.core.ui.theme.DualMusicTheme

/** Variantes visuelles du bouton, alignées sur le web. */
enum class DMButtonStyle { PRIMARY, SECONDARY, OUTLINE, DESTRUCTIVE }

/**
 * Bouton d'action principal de Dual Music — CTA à **dégradé violet → rose** avec halo,
 * repris du bouton signature du web.
 *
 * ```
 * DMButton("Envoyer le cadeau 🎁") { send() }
 * DMButton("Confirmer", style = DMButtonStyle.SECONDARY, isLoading = submitting) { confirm() }
 * ```
 *
 * @param title libellé du bouton.
 * @param style variante visuelle (défaut : dégradé primaire).
 * @param isLoading affiche un indicateur et bloque le tap.
 * @param enabled désactive et atténue le bouton.
 */
@Composable
fun DMButton(
    title: String,
    modifier: Modifier = Modifier,
    style: DMButtonStyle = DMButtonStyle.PRIMARY,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val shape = RoundedCornerShape(DualMusicTheme.radii.md)

    // Fond : dégradé pour le CTA primaire, couleur pleine sinon.
    val background: Brush = when (style) {
        DMButtonStyle.PRIMARY -> DualMusicTheme.gradients.primary
        DMButtonStyle.SECONDARY -> SolidColor(colors.secondary)
        DMButtonStyle.DESTRUCTIVE -> SolidColor(colors.destructive)
        DMButtonStyle.OUTLINE -> SolidColor(Color.Transparent)
    }
    val foreground: Color = when (style) {
        DMButtonStyle.PRIMARY, DMButtonStyle.DESTRUCTIVE -> colors.primaryForeground
        DMButtonStyle.SECONDARY -> colors.secondaryForeground
        DMButtonStyle.OUTLINE -> colors.foreground
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (style == DMButtonStyle.PRIMARY && enabled) Modifier.dmGlow() else Modifier)
            .clip(shape)
            .background(background, shape)
            .then(if (style == DMButtonStyle.OUTLINE) Modifier.border(BorderStroke(1.dp, colors.border), shape) else Modifier)
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(horizontal = 16.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = foreground, strokeWidth = 2.dp)
        } else {
            Text(
                text = title,
                color = foreground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

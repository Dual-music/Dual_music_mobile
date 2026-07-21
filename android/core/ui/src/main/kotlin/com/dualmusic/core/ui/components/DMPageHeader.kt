package com.dualmusic.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * En-tête de page standardisé : **titre centré** + **flèche de retour en haut à gauche**.
 *
 * Remplace l'ancien bouton « ← Retour ». À utiliser en tête de chaque sous-page (portefeuille,
 * boutique, classements…) et de chaque écran de catalogue (Lives, Duels, Concerts…) pour une
 * navigation cohérente et professionnelle.
 *
 * @param title titre de la page, centré horizontalement.
 * @param modifier modificateur du conteneur.
 * @param onBack si non nul, affiche la flèche de retour (sinon l'espace reste réservé pour
 *   garder le titre parfaitement centré).
 */
@Composable
fun DMPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = DualMusicTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = DualMusicTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = colors.foreground,
                )
            }
        }
        Text(
            text = title,
            color = colors.foreground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
    }
}

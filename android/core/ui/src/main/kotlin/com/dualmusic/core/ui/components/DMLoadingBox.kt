package com.dualmusic.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Indicateur de chargement **centré** dans l'espace disponible.
 *
 * Remplace les `CircularProgressIndicator` bruts qui s'affichaient en haut à gauche : passer
 * `Modifier.weight(1f)` depuis une `Column` pour occuper la hauteur restante et centrer le
 * spinner au milieu de l'écran.
 *
 * @param modifier par défaut `fillMaxSize` ; dans une Column, préférer `Modifier.weight(1f)`.
 */
@Composable
fun DMLoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DualMusicTheme.colors.primary)
    }
}

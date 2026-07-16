package com.dualmusic.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.R

/**
 * Logo Dual Music.
 *
 * Affiche EXACTEMENT le même logo que le site web (asset `logo-tr.png` réutilisé), pour
 * garantir la reconnaissance de marque entre plateformes. Le ratio d'origine est
 * préservé ; on contrôle uniquement la hauteur.
 *
 * @param modifier modificateur Compose (positionnement/alignement par l'appelant).
 * @param height hauteur cible du logo (la largeur s'ajuste au ratio). Défaut : 64 dp.
 */
@Composable
fun DMLogo(
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    Image(
        painter = painterResource(id = R.drawable.dm_logo),
        contentDescription = "Dual Music",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

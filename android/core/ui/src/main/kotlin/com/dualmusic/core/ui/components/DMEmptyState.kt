package com.dualmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * État vide standardisé : icône ronde + titre + sous-texte, le tout **centré**.
 *
 * À utiliser partout où une liste/section n'a pas (encore) de données, à la place d'une
 * simple phrase. Chaque écran passe l'[icon] la plus parlante (les modules n'ont que le pack
 * d'icônes de base ; l'app peut fournir des icônes étendues).
 *
 * @param title message principal (ex. « Aucune notification »).
 * @param modifier modificateur du conteneur (souvent `Modifier.fillMaxSize()`).
 * @param subtitle explication optionnelle (ex. « Tes alertes apparaîtront ici »).
 * @param icon icône illustrative (défaut : information).
 */
@Composable
fun DMEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.Info,
) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DualMusicTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(colors.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primaryGlow,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            color = colors.foreground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(
                text = it,
                color = colors.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

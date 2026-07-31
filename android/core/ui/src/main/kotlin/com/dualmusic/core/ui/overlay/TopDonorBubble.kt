package com.dualmusic.core.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.theme.DualMusicTheme

/** Meilleur donateur courant d'un événement (live/concert/duel). */
data class TopDonor(val name: String, val amount: Int)

/**
 * Bulle « meilleur donateur » — parité web (`TopDonorBubble`). Affichée en haut, sous la barre
 * du haut. Pilotée par les préférences visuelles de l'utilisateur :
 *
 * @param donor donateur courant, ou `null` pour ne rien afficher.
 * @param mode `full` (carte complète) | `reduced` (compacte) | `off` (masquée).
 * @param animation `traversing` (bandeau large) | `default` (carte discrète). L'intention web ;
 *   on garde une présentation statique (pas d'animation superflue) pour la lisibilité.
 */
@Composable
fun BoxScope.TopDonorBubble(donor: TopDonor?, mode: String, animation: String) {
    if (donor == null || mode == "off") return
    val colors = DualMusicTheme.colors
    val reduced = mode == "reduced"
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 92.dp, start = 12.dp, end = 12.dp)
            .background(DualMusicTheme.gradients.primary, RoundedCornerShape(999.dp))
            .padding(horizontal = if (reduced) 10.dp else 14.dp, vertical = if (reduced) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("👑", fontSize = if (reduced) 12.sp else 15.sp)
        Text(donor.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (reduced) 11.sp else 13.sp)
        Text("· ${donor.amount} 💎", color = Color.White, fontSize = if (reduced) 11.sp else 13.sp)
    }
}

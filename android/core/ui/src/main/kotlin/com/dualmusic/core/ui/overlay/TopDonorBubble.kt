package com.dualmusic.core.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.theme.DualMusicTheme

/** Meilleur donateur courant d'un événement (live/concert/duel). */
data class TopDonor(val name: String, val amount: Int)

/**
 * Bulle « meilleur donateur » — parité web (`TopDonorBubble`). Affichée en haut, juste sous la
 * barre du haut (fond semi-transparent, pas de carte opaque — parité concert, voir
 * `ConcertScrollingLabel`). Le nom défile en continu — sans ça, un nom long se faisait tronquer et
 * n'était jamais lisible en entier.
 *
 * @param donor donateur courant, ou `null` pour ne rien afficher.
 * @param mode `full` (carte complète) | `reduced` (compacte) | `off` (masquée).
 * @param animation `traversing` (bandeau large) | `default` (carte discrète). L'intention web ;
 *   les deux modes défilent désormais, seule la taille change.
 * @param topOffset décalage sous la barre du haut — à augmenter (compétition en ligne) quand une
 *   bande de vignettes multi-cam occupe déjà cette zone, pour ne pas s'y superposer.
 */
@Composable
fun BoxScope.TopDonorBubble(donor: TopDonor?, mode: String, animation: String, topOffset: androidx.compose.ui.unit.Dp = 56.dp) {
    if (donor == null || mode == "off") return
    val reduced = mode == "reduced"
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = topOffset, start = 12.dp, end = 12.dp)
            .fillMaxWidth()
            .background(Color(0x33FFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = if (reduced) 10.dp else 14.dp, vertical = if (reduced) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("👑", fontSize = if (reduced) 12.sp else 15.sp)
        DonorScrollingLabel(
            "${donor.name}  ·  ${donor.amount} 💎",
            fontSize = if (reduced) 11.sp else 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Texte défilant en boucle (parité `ConcertScrollingLabel`) — évite de tronquer un nom long. */
@Composable
private fun DonorScrollingLabel(text: String, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "donorMq")
    val p by infinite.animateFloat(
        initialValue = 1f, targetValue = -1.3f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "donorMqx",
    )
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        Text(
            text, color = Color(0xFFFFD54A), fontWeight = FontWeight.Bold, fontSize = fontSize,
            maxLines = 1, softWrap = false,
            modifier = Modifier.graphicsLayer { translationX = p * w },
        )
    }
}

package com.dualmusic.core.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Couche de réactions flottantes façon TikTok — partagée par tous les overlays (live, duel,
 * concert, compétition). Chaque emoji du flux monte du bas vers le haut à droite avec une légère
 * dérive et un fondu ; il est rendu avec sa propre clé, donc l'animation démarre à l'entrée en
 * composition et s'arrête quand le VM le retire du flux.
 *
 * @param reactions flux `(id, emoji)` — le VM garde les N derniers ; l'id doit être stable/unique.
 * @param reduceAnimations si vrai, ne rend rien (préférence « Réduire les animations »).
 */
@Composable
fun BoxScope.FloatingReactionsLayer(
    reactions: List<Pair<Long, String>>,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
) {
    if (reduceAnimations) return
    Box(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(bottom = 140.dp, end = 12.dp)
            .size(64.dp, 460.dp),
    ) {
        reactions.forEach { (id, emoji) ->
            key(id) { FloatingReaction(emoji) }
        }
    }
}

@Composable
private fun BoxScope.FloatingReaction(symbol: String) {
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val drift = remember { (-28..28).random() }
    val startScale = remember { 0.7f + (0..30).random() / 100f }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        coroutineScope {
            launch { rise.animateTo(1f, tween(2400, easing = LinearEasing)) }
            launch {
                fade.animateTo(1f, tween(300))
                fade.animateTo(0f, tween(2100))
            }
        }
    }
    Text(
        text = symbol,
        fontSize = (26 + startScale * 8).sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset { IntOffset((drift * rise.value).toInt(), -(rise.value * 430).toInt()) }
            .alpha(fade.value.coerceIn(0f, 1f)),
    )
}

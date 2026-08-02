package com.dualmusic.core.ui.celebration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Overlay de **célébration du vainqueur** (parité web) : confettis qui tombent + carte
 * « 🏆 Vainqueur » avec le nom, apparaissant avec un léger rebond.
 *
 * Non bloquant pour les gestes : les couches (confettis, carte) n'interceptent aucun pointeur,
 * les contrôles sous-jacents (ex. « Terminer ») restent cliquables.
 *
 * @param winnerName nom affiché du vainqueur.
 * @param title libellé (ex. « 🏆 Vainqueur »).
 * @param subtitle sous-titre optionnel (ex. « Félicitations ! »).
 * @param reduceAnimations respecte la préférence : masque les confettis + le rebond.
 */
@Composable
fun WinnerCelebration(
    winnerName: String,
    title: String,
    subtitle: String? = null,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduceAnimations) ConfettiLayer()
        WinnerCard(winnerName = winnerName, title = title, subtitle = subtitle, animate = !reduceAnimations)
    }
}

/** Palette festive des confettis. */
private val ConfettiColors = listOf(
    Color(0xFFA63DDB), // violet marque
    Color(0xFF3DA5FF), // bleu électrique
    Color(0xFFFFD23D), // or
    Color(0xFFFF4D8D), // rose
    Color(0xFF3DE0A0), // vert menthe
)

@Immutable
private data class Confetto(
    val x: Float,     // position horizontale de base (0..1)
    val color: Color,
    val size: Float,  // côté (px)
    val phase: Float, // décalage de chute (0..1)
    val drift: Float, // amplitude d'oscillation horizontale
    val rot: Float,   // rotation de base (deg)
)

/** Couche de confettis animés (chute continue + oscillation + rotation), qui s'estompe. */
@Composable
private fun ConfettiLayer() {
    val confetti = remember {
        List(90) { i ->
            val r = Random(i * 2654435761u.toInt())
            Confetto(
                x = r.nextFloat(),
                color = ConfettiColors[i % ConfettiColors.size],
                size = 8f + r.nextFloat() * 10f,
                phase = r.nextFloat(),
                drift = r.nextFloat(),
                rot = r.nextFloat() * 360f,
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "fall",
    )
    // Durée de vie : les confettis tombent ~5 s puis s'estompent ; la carte, elle, reste.
    var life by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4800)
        life = 0f
    }
    val alpha by animateFloatAsState(targetValue = life, animationSpec = tween(1200), label = "confettiFade")

    if (alpha <= 0.01f) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        confetti.forEach { c ->
            val p = (t + c.phase) % 1f
            val y = p * (size.height + 40f) - 20f
            val x = c.x * size.width + sin((p + c.phase) * 6.2832f * 2f) * c.drift * 36f
            rotate(degrees = c.rot + p * 540f, pivot = Offset(x, y)) {
                drawRect(
                    color = c.color.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(c.size, c.size * 1.6f),
                )
            }
        }
    }
}

/** Carte centrale annonçant le vainqueur (apparition avec léger rebond). */
@Composable
private fun WinnerCard(
    winnerName: String,
    title: String,
    subtitle: String?,
    animate: Boolean,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown || !animate) 1f else 0.6f,
        animationSpec = if (animate) spring(dampingRatio = Spring.DampingRatioMediumBouncy) else tween(0),
        label = "winnerScale",
    )
    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🏆", fontSize = 54.sp)
        Text(
            title,
            color = Color(0xFFFFD23D),
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            winnerName,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

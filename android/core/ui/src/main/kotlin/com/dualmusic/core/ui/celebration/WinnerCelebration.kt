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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.rotate
import com.dualmusic.core.ui.components.DMRemoteImage
import kotlin.math.sin
import kotlin.random.Random

/**
 * Overlay de **célébration du vainqueur** (parité web `WinnerAnnouncement.tsx`) : lueur
 * pulsante en fond, confettis qui tombent, et carte centrale avec couronne, avatar du
 * vainqueur (bague dorée + pastille trophée), « 🎉 FÉLICITATIONS 🎉 », nom en doré et votes.
 *
 * Non bloquant pour les gestes : les couches (lueur, confettis, carte) n'interceptent aucun
 * pointeur, les contrôles sous-jacents (ex. « Terminer ») restent cliquables.
 *
 * @param winnerName nom affiché du vainqueur.
 * @param title libellé (ex. « 🏆 Vainqueur »).
 * @param avatarUrl URL de l'avatar du vainqueur (repli sur son initiale si absente).
 * @param subtitle sous-titre optionnel (ex. « Félicitations ! »).
 * @param soundUrl son personnalisé téléversé par l'admin (réglage `winner_sound_url`) ; `null` =
 *   son par défaut. Voir `CompetitionRepository`/`DuelRepository#winnerSoundUrl`.
 * @param reduceAnimations respecte la préférence : masque lueur + confettis + le rebond.
 */
@Composable
fun WinnerCelebration(
    winnerName: String,
    title: String,
    avatarUrl: String? = null,
    subtitle: String? = null,
    soundUrl: String? = null,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Son (par défaut ou personnalisé admin), en boucle tant que la célébration reste affichée
    // (parité web `WinnerAnnouncement.tsx`) — ce composable n'existe dans l'arbre QUE pendant la
    // célébration, donc l'arrêt du son suit automatiquement le démontage (manager qui arrête
    // l'annonce, ou fin de vie de l'écran). Clé sur `soundUrl` : si le réglage admin arrive après
    // le premier montage (chargement async), on relance proprement avec la bonne source.
    DisposableEffect(soundUrl) {
        val player = runCatching {
            android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(soundUrl?.takeIf { it.isNotBlank() } ?: "https://assets.mixkit.co/active_storage/sfx/2010/2010-preview.mp3")
                isLooping = true
                setOnPreparedListener { runCatching { start() } }
                prepareAsync()
            }
        }.getOrNull()
        onDispose { runCatching { player?.stop() }; runCatching { player?.release() } }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduceAnimations) PulsingGlow()
        if (!reduceAnimations) ConfettiLayer()
        WinnerCard(winnerName = winnerName, title = title, avatarUrl = avatarUrl, subtitle = subtitle, animate = !reduceAnimations)
    }
}

/** Lueur radiale douce qui pulse en fond (parité web : `radial-gradient` + scale/opacity infinis). */
@Composable
private fun PulsingGlow() {
    val transition = rememberInfiniteTransition(label = "glow")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1500), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "glowScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1500), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "glowAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFA63DDB).copy(alpha = alpha), Color.Transparent),
                ),
            ),
    )
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

/**
 * Carte centrale annonçant le vainqueur (parité web : couronne qui rebondit au-dessus de
 * l'avatar en bague dorée avec pastille trophée, « 🎉 FÉLICITATIONS 🎉 », nom en doré, votes
 * dans une pastille) — apparition avec léger rebond (spring, une seule valeur cible : voir
 * `animateFloatAsState` ci-dessous, pas de limite de keyframes contrairement à Framer Motion
 * côté web, dont l'équivalent JS a justement provoqué le bug d'affichage web signalé).
 */
@Composable
private fun WinnerCard(
    winnerName: String,
    title: String,
    avatarUrl: String?,
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
    val crownTransition = rememberInfiniteTransition(label = "crown")
    val crownY by crownTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(1000), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "crownY",
    )
    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("👑", fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = crownY })

        // Avatar en bague dorée + pastille trophée (parité web : ring-4 ring-yellow-500 + 🏆
        // en overlay coin haut-droit), repli sur l'emoji trophée si aucun avatar disponible.
        Box(contentAlignment = Alignment.Center) {
            if (avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFFD23D), Color(0xFFFFA53D))))
                        .border(4.dp, Color(0xFFFFD23D), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(winnerName.take(1).uppercase(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
            } else {
                DMRemoteImage(
                    url = avatarUrl,
                    contentDescription = winnerName,
                    modifier = Modifier.size(92.dp).clip(CircleShape).border(4.dp, Color(0xFFFFD23D), CircleShape),
                    fallbackEmoji = "🏆",
                )
            }
            Text("🏆", fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { translationX = 8f; translationY = (-8f) })
        }

        Text(
            "🎉 $title 🎉",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            winnerName,
            color = Color(0xFFFFD23D),
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                    .border(1.dp, Color(0xFFFFD23D).copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(it, color = Color(0xFFFFD23D), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

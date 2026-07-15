package com.dualmusic.core.ui.gifts

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shader **AGSL** (Android 13+) : halo violet pulsant appliqué au rendu d'un cadeau.
 *
 * Équivalent du shader Metal iOS `giftGlow`. Exécuté sur le GPU (RenderThread) → 60 fps
 * même pendant le décodage vidéo (MediaCodec, GPU distinct).
 *
 * `content` = la couche source ; `size`/`time` = uniformes animés.
 */
private const val GIFT_GLOW_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float time;

    half4 main(float2 coord) {
        half4 color = content.eval(coord);
        float2 center = size * 0.5;
        float radius = max(size.x, size.y) * 0.5;
        float dist = distance(coord, center) / radius;      // 0 centre → 1 bord
        float pulse = 0.5 + 0.5 * sin(time * 6.0);
        half3 brand = half3(0.65, 0.24, 0.86);              // violet 280 70% 55%
        half3 glow = brand * half(clamp(1.0 - dist, 0.0, 1.0)) * half(pulse);
        return half4(color.rgb + glow * color.a, color.a);
    }
"""

/**
 * Animation « wow » d'un cadeau reçu, avec halo GPU (AGSL sur Android 13+, repli simple sinon).
 *
 * @param symbol emoji/glyphe du cadeau (rendu image possible via une surcouche).
 */
@Composable
fun GiftBurst(
    symbol: String,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GiftBurstShader(symbol, modifier)
    } else {
        // Repli < API 33 : simple glyphe sans shader (l'animation d'apparition suffit).
        Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Text(symbol, style = TextStyle(fontSize = 72.sp))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GiftBurstShader(symbol: String, modifier: Modifier) {
    val shader = remember { RuntimeShader(GIFT_GLOW_AGSL) }

    // Temps animé (boucle) pour piloter la pulsation du halo.
    val transition = rememberInfiniteTransition(label = "giftGlow")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f, // 2π
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "time",
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .graphicsLayer {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", time)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = TextStyle(fontSize = 72.sp))
    }
}

package com.dualmusic.core.media

import kotlin.math.cos
import kotlin.math.sin

/**
 * Presets de filtres couleur — équivalents des filtres CSS du web (`videoFilters.ts`).
 *
 * Chaque preset est une matrice couleur 4×4 appliquée par [ColorFilterVideoProcessor] dans le
 * shader (rgb' = M·[r,g,b,1]). Les matrices sont fournies en **colonne-major** (attendu par
 * `glUniformMatrix4fv` en GLES2, transpose=false). L'identité est représentée par `null`
 * (passthrough, filtre « Aucun »).
 */
data class VideoFilter(val id: String, val emoji: String, val matrix: FloatArray?)

object VideoFilterPresets {

    /** Liste ordonnée identique à la grille web (Aucun, Beauté, Lumineux, Chaud, Froid, Vif, Vintage, N&B, Studio, Néon). */
    val all: List<VideoFilter> by lazy {
        listOf(
            VideoFilter("none", "🚫", null),
            VideoFilter("beauty", "✨", colMajor(compose(saturate(1.15f), brightness(1.08f), contrast(1.05f)))),
            VideoFilter("glow", "💡", colMajor(compose(brightness(1.18f), contrast(1.05f), saturate(1.1f)))),
            VideoFilter("warm", "🌅", colMajor(compose(sepia(0.25f), saturate(1.3f), hueRotate(-10f), brightness(1.05f)))),
            VideoFilter("cool", "❄️", colMajor(compose(saturate(1.1f), hueRotate(15f), brightness(1.02f), contrast(1.05f)))),
            VideoFilter("vivid", "🎨", colMajor(compose(saturate(1.6f), contrast(1.15f)))),
            VideoFilter("vintage", "📷", colMajor(compose(sepia(0.55f), contrast(1.1f), brightness(1.05f), saturate(0.9f)))),
            VideoFilter("noir", "🎞️", colMajor(compose(grayscale(1f), contrast(1.15f), brightness(1.05f)))),
            VideoFilter("studio", "🎬", colMajor(compose(contrast(1.2f), brightness(1.05f), saturate(1.1f)))),
            VideoFilter("neon", "🌈", colMajor(compose(saturate(1.8f), hueRotate(20f), contrast(1.2f), brightness(1.1f)))),
        )
    }

    // --- Primitives (matrices 4×4 ligne-major, rgb' = M·[r,g,b,1]) ---

    private fun brightness(b: Float) = floatArrayOf(
        b, 0f, 0f, 0f,
        0f, b, 0f, 0f,
        0f, 0f, b, 0f,
        0f, 0f, 0f, 1f,
    )

    private fun contrast(c: Float): FloatArray {
        val t = 0.5f - 0.5f * c
        return floatArrayOf(
            c, 0f, 0f, t,
            0f, c, 0f, t,
            0f, 0f, c, t,
            0f, 0f, 0f, 1f,
        )
    }

    // Coefficients de luminance (spec CSS/SVG feColorMatrix).
    private const val LR = 0.213f
    private const val LG = 0.715f
    private const val LB = 0.072f

    private fun saturate(s: Float) = floatArrayOf(
        LR + 0.787f * s, LG - LG * s, LB - LB * s, 0f,
        LR - LR * s, LG + 0.285f * s, LB - LB * s, 0f,
        LR - LR * s, LG - LG * s, LB + 0.928f * s, 0f,
        0f, 0f, 0f, 1f,
    )

    /** grayscale(amount) = saturate(1 - amount) (spec CSS). */
    private fun grayscale(amount: Float) = saturate(1f - amount)

    private fun sepia(p: Float): FloatArray {
        // lerp(identité, sepia complet, p).
        val sr0 = 0.393f; val sr1 = 0.769f; val sr2 = 0.189f
        val sg0 = 0.349f; val sg1 = 0.686f; val sg2 = 0.168f
        val sb0 = 0.272f; val sb1 = 0.534f; val sb2 = 0.131f
        fun l(id: Float, se: Float) = id * (1f - p) + se * p
        return floatArrayOf(
            l(1f, sr0), l(0f, sr1), l(0f, sr2), 0f,
            l(0f, sg0), l(1f, sg1), l(0f, sg2), 0f,
            l(0f, sb0), l(0f, sb1), l(1f, sb2), 0f,
            0f, 0f, 0f, 1f,
        )
    }

    private fun hueRotate(deg: Float): FloatArray {
        val rad = deg * (Math.PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        return floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    // --- Composition + conversion ---

    /** Compose des filtres appliqués dans l'ordre (f1 puis f2 …) → matrice ligne-major résultante. */
    private fun compose(vararg ms: FloatArray): FloatArray {
        var acc = IDENTITY_ROW.copyOf()
        for (m in ms) acc = mul(m, acc) // acc = m · acc → f1 appliqué en premier
        return acc
    }

    /** Produit de deux matrices 4×4 ligne-major. */
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[row * 4 + k] * b[k * 4 + col]
                r[row * 4 + col] = sum
            }
        }
        return r
    }

    /** Ligne-major → colonne-major (pour glUniformMatrix4fv, transpose=false). */
    private fun colMajor(rowMajor: FloatArray): FloatArray {
        val c = FloatArray(16)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                c[col * 4 + row] = rowMajor[row * 4 + col]
            }
        }
        return c
    }

    private val IDENTITY_ROW = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )
}

package livekit.org.webrtc

import android.opengl.GLES20

/**
 * [RendererCommon.GlDrawer] qui applique une matrice de couleur 4×4 dans le fragment shader.
 *
 * Déclaré dans le package `livekit.org.webrtc` car [GlGenericDrawer] (et son [GlGenericDrawer.ShaderCallbacks])
 * y sont *package-private* — c'est le seul moyen d'y accéder. Délègue à un [GlGenericDrawer] qui gère
 * l'échantillonnage OES/RGB/YUV et la fonction `sample()`.
 *
 * @param matrixProvider fournit la matrice couleur active (colonne-major, rgb' = M·[r,g,b,1]) ;
 *   `null` = identité (passthrough).
 */
class ColorFilterGlDrawer(private val matrixProvider: () -> FloatArray?) : RendererCommon.GlDrawer {

    private val inner = GlGenericDrawer(FRAGMENT_SHADER, Callbacks())

    private inner class Callbacks : GlGenericDrawer.ShaderCallbacks {
        override fun onNewShader(shader: GlShader) = Unit

        override fun onPrepareShader(
            shader: GlShader,
            texMatrix: FloatArray,
            frameWidth: Int,
            frameHeight: Int,
            viewportWidth: Int,
            viewportHeight: Int,
        ) {
            val m = matrixProvider() ?: IDENTITY
            val loc = shader.getUniformLocation("uColorMatrix")
            // GLES2 exige transpose = false → la matrice est déjà en colonne-major.
            GLES20.glUniformMatrix4fv(loc, 1, false, m, 0)
        }
    }

    override fun drawOes(
        oesTextureId: Int,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) = inner.drawOes(oesTextureId, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)

    override fun drawRgb(
        textureId: Int,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) = inner.drawRgb(textureId, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)

    override fun drawYuv(
        yuvTextures: IntArray,
        texMatrix: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) = inner.drawYuv(yuvTextures, texMatrix, frameWidth, frameHeight, viewportX, viewportY, viewportWidth, viewportHeight)

    override fun release() = inner.release()

    companion object {
        private val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        // GlGenericDrawer injecte `sample(tc)` + le varying `tc` selon le type de texture.
        private const val FRAGMENT_SHADER =
            "uniform mat4 uColorMatrix;\n" +
                "void main() {\n" +
                "  vec4 c = sample(tc);\n" +
                "  vec3 rgb = (uColorMatrix * vec4(c.rgb, 1.0)).rgb;\n" +
                "  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
                "}\n"
    }
}

package com.dualmusic.core.media

import android.opengl.GLES20
import android.view.Surface
import io.livekit.android.room.track.video.ChainVideoProcessor
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.EglRenderer
import livekit.org.webrtc.GlGenericDrawer
import livekit.org.webrtc.GlShader
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor

/**
 * Filtre couleur GL publié (visible par TOUS les spectateurs) — équivalent Android des filtres
 * CSS du web (Beauté, Chaud, Froid, N&B, Néon…).
 *
 * Applique une matrice de couleur 4×4 à chaque frame via un shader GL, puis transmet la frame
 * traitée au maillon suivant de la chaîne (ex. le flou/fond virtuel). Passthrough (aucun coût GL)
 * quand [matrix] est `null` (filtre « Aucun »).
 *
 * Implémenté comme [ChainVideoProcessor] pour pouvoir être combiné avec le fond virtuel :
 * `colorProcessor.childVideoProcessor = virtualBackgroundProcessor`.
 */
class ColorFilterVideoProcessor(eglBase: EglBase) : ChainVideoProcessor() {

    /** Matrice couleur active (colonne-major, rgb' = M·[r,g,b,1]). `null` = passthrough. */
    @Volatile
    var matrix: FloatArray? = null

    private val drawer = ColorFilterGlDrawer { matrix }
    private val surfaceTextureHelper = SurfaceTextureHelper.create("ColorFilter", eglBase.eglBaseContext)
    private val surface = Surface(surfaceTextureHelper.surfaceTexture)
    private val eglRenderer = EglRenderer("ColorFilter").apply {
        init(eglBase.eglBaseContext, EglBase.CONFIG_PLAIN, drawer)
        createEglSurface(surface)
    }
    private var lastWidth = 0
    private var lastHeight = 0

    override fun onCapturerStarted(started: Boolean) {
        super.onCapturerStarted(started) // propage au maillon enfant
        if (started) {
            surfaceTextureHelper.startListening { frame -> continueChain(frame) }
        }
    }

    override fun onCapturerStopped() {
        super.onCapturerStopped()
        surfaceTextureHelper.stopListening()
    }

    // Force le traitement de toutes les frames (pas de drop selon l'état de publication),
    // comme NoDropVideoProcessor — on est en tête de chaîne, à la place de l'ancien processor.
    override fun onFrameCaptured(frame: VideoFrame, parameters: VideoProcessor.FrameAdaptationParameters) {
        val adapted = VideoProcessor.applyFrameAdaptationParameters(frame, parameters)
        if (adapted != null) {
            onFrameCaptured(adapted)
            adapted.release()
        } else {
            onFrameCaptured(frame)
        }
    }

    override fun onFrameCaptured(frame: VideoFrame) {
        // Aucun filtre : passthrough sans traitement GL.
        if (matrix == null) {
            continueChain(frame)
            return
        }
        if (lastWidth != frame.rotatedWidth || lastHeight != frame.rotatedHeight) {
            surfaceTextureHelper.setTextureSize(frame.rotatedWidth, frame.rotatedHeight)
            lastWidth = frame.rotatedWidth
            lastHeight = frame.rotatedHeight
        }
        frame.retain()
        surfaceTextureHelper.handler.post {
            eglRenderer.onFrame(frame)
            frame.release()
        }
    }

    fun dispose() {
        surfaceTextureHelper.stopListening()
        surfaceTextureHelper.dispose()
        surface.release()
        eglRenderer.release()
        drawer.release()
    }
}

/**
 * [RendererCommon.GlDrawer] qui applique une matrice de couleur 4×4 dans le fragment shader.
 * Délègue à un [GlGenericDrawer] (gère OES/RGB/YUV + la fonction `sample()`).
 */
private class ColorFilterGlDrawer(private val matrixProvider: () -> FloatArray?) : RendererCommon.GlDrawer {

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

        // Fragment shader générique de GlGenericDrawer : `sample(tc)` et le varying `tc` sont
        // injectés par GlGenericDrawer selon le type de texture (OES/RGB/YUV).
        private const val FRAGMENT_SHADER =
            "uniform mat4 uColorMatrix;\n" +
                "void main() {\n" +
                "  vec4 c = sample(tc);\n" +
                "  vec3 rgb = (uColorMatrix * vec4(c.rgb, 1.0)).rgb;\n" +
                "  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
                "}\n"
    }
}

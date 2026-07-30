package com.dualmusic.core.media

import android.view.Surface
import io.livekit.android.room.track.video.ChainVideoProcessor
import livekit.org.webrtc.ColorFilterGlDrawer
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.EglRenderer
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

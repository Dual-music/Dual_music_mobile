import CoreImage
import CoreVideo
import Foundation
import LiveKit

/// Applique un filtre couleur (matrice 4×4, voir ``VideoFilterPresets``) à chaque frame vidéo
/// avant publication, via `CIColorMatrix` (Core Image).
///
/// Miroir de `ColorFilterVideoProcessor.kt` (Android) — mais plus simple à écrire côté iOS :
/// `VideoProcessor` est un protocole **public** du SDK LiveKit Swift (`weak var processor` sur
/// `LocalVideoTrack`, exécuté sur la file de capture dédiée du SDK), alors qu'Android a dû
/// contourner une visibilité package-private (`livekit.org.webrtc`) pour écrire un shader GL
/// custom. `BackgroundBlurVideoProcessor` (livré officiellement par le SDK) suit exactement le
/// même patron : jamais de rendu dans le buffer SOURCE, toujours dans un buffer de sortie
/// séparé et réutilisé (pool), pour éviter les artefacts de lecture/écriture simultanée.
final class ColorFilterVideoProcessor: NSObject, VideoProcessor, @unchecked Sendable {

    /// Matrice active (ligne-major, `rgb' = M·[r,g,b,1]`) — `nil` = passthrough. Écrite depuis
    /// le `MainActor` (``LiveRoomClient``), lue depuis la file de capture du SDK : protégée par
    /// un verrou plutôt que de compter sur une simple visibilité mémoire de `Array`.
    var matrix: [Float]? {
        get { lock.withLock { _matrix } }
        set { lock.withLock { _matrix = newValue } }
    }
    private var _matrix: [Float]?
    private let lock = NSLock()

    private let context = CIContext()
    private var pixelBufferPool: CVPixelBufferPool?
    private var poolSize: (width: Int, height: Int) = (0, 0)

    func process(frame: VideoFrame) -> VideoFrame? {
        guard let matrix else { return frame }
        guard let inputBuffer = frame.toCVPixelBuffer() else { return frame }

        let inputImage = CIImage(cvPixelBuffer: inputBuffer)
        guard let filter = CIFilter(name: "CIColorMatrix") else { return frame }
        filter.setValue(inputImage, forKey: kCIInputImageKey)
        // Ligne 0 (R'), ligne 1 (G'), ligne 2 (B') de la matrice + leur terme constant (biais,
        // utilisé par `contrast`) — l'alpha n'est jamais touché.
        filter.setValue(CIVector(x: CGFloat(matrix[0]), y: CGFloat(matrix[1]), z: CGFloat(matrix[2]), w: 0), forKey: "inputRVector")
        filter.setValue(CIVector(x: CGFloat(matrix[4]), y: CGFloat(matrix[5]), z: CGFloat(matrix[6]), w: 0), forKey: "inputGVector")
        filter.setValue(CIVector(x: CGFloat(matrix[8]), y: CGFloat(matrix[9]), z: CGFloat(matrix[10]), w: 0), forKey: "inputBVector")
        filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        filter.setValue(
            CIVector(x: CGFloat(matrix[3]), y: CGFloat(matrix[7]), z: CGFloat(matrix[11]), w: 0),
            forKey: "inputBiasVector"
        )
        guard let outputImage = filter.outputImage else { return frame }

        let width = CVPixelBufferGetWidth(inputBuffer)
        let height = CVPixelBufferGetHeight(inputBuffer)
        guard let outputBuffer = makeOutputBuffer(width: width, height: height) else { return frame }
        context.render(outputImage, to: outputBuffer)

        return VideoFrame(
            dimensions: frame.dimensions,
            rotation: frame.rotation,
            timeStampNs: frame.timeStampNs,
            buffer: CVPixelVideoBuffer(pixelBuffer: outputBuffer)
        )
    }

    /// Pool de pixel buffers réutilisable — recréé seulement si la résolution change (évite une
    /// allocation par frame, ~30 fois/seconde).
    private func makeOutputBuffer(width: Int, height: Int) -> CVPixelBuffer? {
        if pixelBufferPool == nil || poolSize.width != width || poolSize.height != height {
            let attrs: [CFString: Any] = [
                kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey: width,
                kCVPixelBufferHeightKey: height,
                kCVPixelBufferIOSurfacePropertiesKey: [:],
            ]
            var pool: CVPixelBufferPool?
            CVPixelBufferPoolCreate(kCFAllocatorDefault, nil, attrs as CFDictionary, &pool)
            pixelBufferPool = pool
            poolSize = (width, height)
        }
        guard let pool = pixelBufferPool else { return nil }
        var buffer: CVPixelBuffer?
        CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &buffer)
        return buffer
    }
}

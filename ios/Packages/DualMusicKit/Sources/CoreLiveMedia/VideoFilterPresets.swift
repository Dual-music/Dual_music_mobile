import Foundation

/// Un filtre couleur : matrice 4×4 **ligne-major** (`rgb' = M·[r,g,b,1]`), `nil` = passthrough
/// (filtre « Aucun »). Miroir exact de `VideoFilterPresets.kt` (Android) — mêmes ids, mêmes
/// valeurs numériques, même ordre de composition. Contrairement à Android (qui convertit en
/// colonne-major pour `glUniformMatrix4fv`), la forme ligne-major est gardée telle quelle ici :
/// `CIColorMatrix` (Core Image) attend directement les coefficients par canal de sortie, pas
/// une matrice au sens OpenGL — voir ``ColorFilterVideoProcessor``.
public struct VideoFilterPreset: Identifiable, Sendable, Equatable {
    public let id: String
    public let emoji: String
    public let matrix: [Float]?
}

public enum VideoFilterPresets {

    /// Liste ordonnée IDENTIQUE à la grille Android/web : Aucun, Beauté, Lissé, Lumineux,
    /// Chaud, Froid, Vif, Vintage, N&B, Studio, Néon, Rêve. Les filtres `smooth`/`dream`/`beauty`
    /// incluent côté web un flou spatial impossible à reproduire en matrice couleur seule → on
    /// reproduit uniquement leur composante couleur (même écart assumé qu'Android).
    public static let all: [VideoFilterPreset] = [
        VideoFilterPreset(id: "none", emoji: "🚫", matrix: nil),
        VideoFilterPreset(id: "beauty", emoji: "✨", matrix: compose(saturate(1.15), brightness(1.08), contrast(1.05))),
        VideoFilterPreset(id: "smooth", emoji: "💆", matrix: compose(saturate(1.05), brightness(1.05), contrast(0.95))),
        VideoFilterPreset(id: "glow", emoji: "💡", matrix: compose(brightness(1.18), contrast(1.05), saturate(1.1))),
        VideoFilterPreset(id: "warm", emoji: "🌅", matrix: compose(sepia(0.25), saturate(1.3), hueRotate(-10), brightness(1.05))),
        VideoFilterPreset(id: "cool", emoji: "❄️", matrix: compose(saturate(1.1), hueRotate(15), brightness(1.02), contrast(1.05))),
        VideoFilterPreset(id: "vivid", emoji: "🎨", matrix: compose(saturate(1.6), contrast(1.15))),
        VideoFilterPreset(id: "vintage", emoji: "📷", matrix: compose(sepia(0.55), contrast(1.1), brightness(1.05), saturate(0.9))),
        VideoFilterPreset(id: "noir", emoji: "🎞️", matrix: compose(grayscale(1), contrast(1.15), brightness(1.05))),
        VideoFilterPreset(id: "studio", emoji: "🎬", matrix: compose(contrast(1.2), brightness(1.05), saturate(1.1))),
        VideoFilterPreset(id: "neon", emoji: "🌈", matrix: compose(saturate(1.8), hueRotate(20), contrast(1.2), brightness(1.1))),
        VideoFilterPreset(id: "dream", emoji: "🌙", matrix: compose(saturate(1.1), brightness(1.1), contrast(0.95))),
    ]

    // MARK: - Primitives (matrices 4×4 ligne-major, rgb' = M·[r,g,b,1])

    private static func brightness(_ b: Float) -> [Float] {
        [
            b, 0, 0, 0,
            0, b, 0, 0,
            0, 0, b, 0,
            0, 0, 0, 1,
        ]
    }

    private static func contrast(_ c: Float) -> [Float] {
        let t = 0.5 - 0.5 * c
        return [
            c, 0, 0, t,
            0, c, 0, t,
            0, 0, c, t,
            0, 0, 0, 1,
        ]
    }

    // Coefficients de luminance (spec CSS/SVG feColorMatrix).
    private static let lr: Float = 0.213
    private static let lg: Float = 0.715
    private static let lb: Float = 0.072

    private static func saturate(_ s: Float) -> [Float] {
        [
            lr + 0.787 * s, lg - lg * s, lb - lb * s, 0,
            lr - lr * s, lg + 0.285 * s, lb - lb * s, 0,
            lr - lr * s, lg - lg * s, lb + 0.928 * s, 0,
            0, 0, 0, 1,
        ]
    }

    /// `grayscale(amount) = saturate(1 - amount)` (spec CSS).
    private static func grayscale(_ amount: Float) -> [Float] { saturate(1 - amount) }

    private static func sepia(_ p: Float) -> [Float] {
        let sr0: Float = 0.393, sr1: Float = 0.769, sr2: Float = 0.189
        let sg0: Float = 0.349, sg1: Float = 0.686, sg2: Float = 0.168
        let sb0: Float = 0.272, sb1: Float = 0.534, sb2: Float = 0.131
        func l(_ identity: Float, _ sepiaValue: Float) -> Float { identity * (1 - p) + sepiaValue * p }
        return [
            l(1, sr0), l(0, sr1), l(0, sr2), 0,
            l(0, sg0), l(1, sg1), l(0, sg2), 0,
            l(0, sb0), l(0, sb1), l(1, sb2), 0,
            0, 0, 0, 1,
        ]
    }

    private static func hueRotate(_ deg: Float) -> [Float] {
        let rad = deg * (Float.pi / 180)
        let c = cos(rad)
        let s = sin(rad)
        return [
            0.213 + c * 0.787 - s * 0.213, 0.715 - c * 0.715 - s * 0.715, 0.072 - c * 0.072 + s * 0.928, 0,
            0.213 - c * 0.213 + s * 0.143, 0.715 + c * 0.285 + s * 0.140, 0.072 - c * 0.072 - s * 0.283, 0,
            0.213 - c * 0.213 - s * 0.787, 0.715 - c * 0.715 + s * 0.715, 0.072 + c * 0.928 + s * 0.072, 0,
            0, 0, 0, 1,
        ]
    }

    // MARK: - Composition

    private static let identityMatrix: [Float] = [
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1,
    ]

    /// Compose des filtres appliqués dans l'ordre (`f1` puis `f2`…) → matrice résultante
    /// (`f1` est le plus « interne », appliqué en premier au pixel).
    private static func compose(_ ms: [Float]...) -> [Float] {
        var acc = identityMatrix
        for m in ms { acc = multiply(m, acc) }
        return acc
    }

    /// Produit de deux matrices 4×4 ligne-major.
    private static func multiply(_ a: [Float], _ b: [Float]) -> [Float] {
        var r = [Float](repeating: 0, count: 16)
        for row in 0..<4 {
            for col in 0..<4 {
                var sum: Float = 0
                for k in 0..<4 { sum += a[row * 4 + k] * b[k * 4 + col] }
                r[row * 4 + col] = sum
            }
        }
        return r
    }
}

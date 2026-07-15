import SwiftUI

public extension Color {
    /// Construit une `Color` depuis les **mêmes valeurs HSL que le design web**.
    ///
    /// SwiftUI n'a pas d'init HSL natif (seulement HSB, différent). Ce helper reproduit
    /// exactement la conversion HSL→sRGB du CSS, garantissant la parité pixel avec le web.
    ///
    /// - Parameters:
    ///   - h: teinte en degrés (0–360).
    ///   - s: saturation (0–1).
    ///   - l: luminosité (0–1).
    ///   - alpha: opacité (0–1).
    init(webHSL h: Double, _ s: Double, _ l: Double, alpha: Double = 1) {
        let c = (1 - abs(2 * l - 1)) * s
        let x = c * (1 - abs((h / 60).truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let (r, g, b): (Double, Double, Double)
        switch h {
        case ..<60:   (r, g, b) = (c, x, 0)
        case ..<120:  (r, g, b) = (x, c, 0)
        case ..<180:  (r, g, b) = (0, c, x)
        case ..<240:  (r, g, b) = (0, x, c)
        case ..<300:  (r, g, b) = (x, 0, c)
        default:      (r, g, b) = (c, 0, x)
        }
        self.init(.sRGB, red: r + m, green: g + m, blue: b + m, opacity: alpha)
    }
}

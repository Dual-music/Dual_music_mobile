import SwiftUI

/// Halos lumineux (miroir des `--shadow-glow*` du web) : la signature « néon » de Dual Music.
public extension View {
    /// Halo violet standard. Web : `0 0 40px hsl(280 70% 55% / 0.40)`.
    func dmGlow() -> some View {
        shadow(color: Color(webHSL: 280, 0.70, 0.55, alpha: 0.40), radius: 20)
    }

    /// Halo violet intense (éléments actifs/live). Web : `0 0 60px hsl(280 70% 55% / 0.60)`.
    func dmGlowStrong() -> some View {
        shadow(color: Color(webHSL: 280, 0.70, 0.55, alpha: 0.60), radius: 30)
    }

    /// Ombre portée élégante des cartes. Web : `0 10px 40px -10px hsl(280 70% 55% / 0.30)`.
    func dmElegantShadow() -> some View {
        shadow(color: Color(webHSL: 280, 0.70, 0.55, alpha: 0.30), radius: 20, x: 0, y: 8)
    }
}

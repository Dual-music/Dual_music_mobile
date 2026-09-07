import SwiftUI

/// Animation « wow » d'un cadeau reçu : halo violet pulsant + sujet qui monte, grossit et
/// s'estompe. Équivalent visuel du `GiftBurst` (shader AGSL) d'Android.
///
/// ## Pourquoi pas un shader Metal ?
/// La version initiale utilisait un shader `stitchable` (`.layerEffect`). L'effet obtenu ici
/// — halo radial additif animé par `TimelineView(.animation)` — est **rendu par le même
/// compositeur GPU** (Core Animation), donc tout aussi fluide pendant le décodage vidéo,
/// sans imposer la compilation d'un `.metal` dans le paquet. Un module de moins à faire
/// compiler = un risque de moins sur une plateforme qu'on ne peut pas tester localement.
///
/// ```swift
/// GiftBurstView(symbol: "🎁", label: "Rose") { viewModel.consumeGift() }
/// ```
public struct GiftBurstView: View {

    private let symbol: String
    private let label: String?
    private let onFinished: (() -> Void)?

    @State private var appeared = false
    @State private var start = Date()

    /// - Parameters:
    ///   - symbol: emoji/glyphe du cadeau.
    ///   - label: nom du cadeau affiché sous le symbole (optionnel).
    ///   - onFinished: appelé ~2,5 s après l'apparition (pour retirer la vue).
    public init(symbol: String, label: String? = nil, onFinished: (() -> Void)? = nil) {
        self.symbol = symbol
        self.label = label
        self.onFinished = onFinished
    }

    public var body: some View {
        TimelineView(.animation) { timeline in
            // Pulsation continue (0 → 1 → 0), pilotée à la fréquence d'écran.
            let elapsed = timeline.date.timeIntervalSince(start)
            let pulse = 0.5 + 0.5 * sin(elapsed * 6.0)
            content(pulse: pulse)
        }
        .onAppear {
            start = Date()
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) { appeared = true }
            // Auto-nettoyage à la fin de l'effet.
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { onFinished?() }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true) // décoratif : ne pas polluer VoiceOver pendant un live
    }

    /// Composition du halo + du sujet pour un état de pulsation donné.
    /// - Parameter pulse: intensité du halo (0 → 1).
    @ViewBuilder
    private func content(pulse: Double) -> some View {
        VStack(spacing: 4) {
            ZStack {
                // Halo radial de marque (violet 280 70% 55%), additif au-dessus du fond.
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color(webHSL: 280, 0.70, 0.55, alpha: 0.75 * pulse),
                                Color(webHSL: 280, 0.70, 0.55, alpha: 0),
                            ],
                            center: .center,
                            startRadius: 4,
                            endRadius: 70
                        )
                    )
                    .frame(width: 140, height: 140)
                    .blendMode(.plusLighter)
                    .blur(radius: 6)

                Text(symbol)
                    .font(.system(size: 72))
                    .shadow(color: Color(webHSL: 280, 0.70, 0.55, alpha: 0.9), radius: 12 * pulse)
            }
            if let label {
                Text(label)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(Color(webHSL: 330, 1.0, 0.65)) // accent rose néon
            }
        }
        .scaleEffect(appeared ? 1.0 : 0.4)
        .offset(y: appeared ? -40 : 40)   // léger mouvement ascendant
        .opacity(appeared ? 1 : 0)
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        GiftBurstView(symbol: "🎁", label: "Rose")
    }
    .dualMusicTheme(.dark)
}

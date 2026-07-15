import SwiftUI

/// Animation « wow » d'un cadeau reçu, avec halo GPU (shader Metal `giftGlow`).
///
/// - Le halo pulsant est rendu par le GPU via `.layerEffect` (thread de rendu distinct du
///   décodage vidéo → 60 fps même en plein live).
/// - Le sujet monte, grossit puis s'estompe (animation SwiftUI légère).
///
/// ```swift
/// GiftBurstView(symbol: "🎁", label: "Rose") { onFinished() }
/// ```
public struct GiftBurstView: View {
    private let symbol: String
    private let label: String?
    private let onFinished: (() -> Void)?

    @State private var appeared = false
    private let start = Date()

    public init(symbol: String, label: String? = nil, onFinished: (() -> Void)? = nil) {
        self.symbol = symbol
        self.label = label
        self.onFinished = onFinished
    }

    public var body: some View {
        // TimelineView(.animation) pilote le temps du shader à la fréquence d'écran.
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSince(start)
            content(time: t)
        }
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) { appeared = true }
            // Auto-nettoyage après ~2,5 s (durée de l'effet).
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { onFinished?() }
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder private func content(time: TimeInterval) -> some View {
        VStack(spacing: 4) {
            Text(symbol)
                .font(.system(size: 72))
                .layerEffect(
                    ShaderLibrary.bundle(.module).giftGlow(
                        .float(Float(time)),
                        .float2(120, 120)
                    ),
                    maxSampleOffset: .zero
                )
            if let label {
                Text(label)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(Color(webHSL: 330, 1.0, 0.65)) // accent rose néon
            }
        }
        .scaleEffect(appeared ? 1.0 : 0.4)
        .offset(y: appeared ? -40 : 40)      // léger mouvement ascendant
        .opacity(appeared ? 1 : 0)
    }
}

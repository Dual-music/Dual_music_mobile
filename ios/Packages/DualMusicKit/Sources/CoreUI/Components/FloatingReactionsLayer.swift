import SwiftUI

/// Un élément du flux de réactions flottantes — voir ``FloatingReactionsLayer``.
public struct FloatingReactionItem: Identifiable, Sendable {
    public let id: String
    public let emoji: String

    public init(id: String, emoji: String) {
        self.id = id
        self.emoji = emoji
    }
}

/// Couche de réactions flottantes façon TikTok — partagée par tous les overlays (live, duel,
/// concert, compétition). Chaque emoji du flux monte du bas vers le haut à droite avec une
/// légère dérive et un fondu ; identifié par sa propre clé (id stable), l'animation démarre à
/// l'apparition et s'arrête quand le ViewModel le retire du flux.
///
/// Miroir de `FloatingReactionsLayer`/`FloatingReaction` (Compose,
/// `core/ui/overlay/FloatingReactions.kt`) — se positionne lui-même en bas-à-droite de l'écran
/// (comme la version Android, `Alignment.BottomEnd`), à déposer directement dans un `ZStack`.
///
/// ```swift
/// FloatingReactionsLayer(reactions: viewModel.emojiFeed.map { FloatingReactionItem(id: $0.id, emoji: $0.emoji) })
/// ```
public struct FloatingReactionsLayer: View {
    private let reactions: [FloatingReactionItem]
    private let onFinished: (FloatingReactionItem) -> Void

    /// - Parameters:
    ///   - reactions: flux courant (le ViewModel garde les N derniers ; id stable/unique).
    ///   - onFinished: appelé ~2,4s après l'apparition de CHAQUE élément (retirer du flux —
    ///     l'ordre de fin respecte l'ordre d'ajout, donc `consumeOldestEmoji()` reste correct
    ///     même avec plusieurs réactions simultanées).
    public init(reactions: [FloatingReactionItem], onFinished: @escaping (FloatingReactionItem) -> Void = { _ in }) {
        self.reactions = reactions
        self.onFinished = onFinished
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            ForEach(reactions) { item in
                FloatingReactionParticle(emoji: item.emoji) { onFinished(item) }
            }
        }
        .frame(width: 64, height: 460)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        .padding(.bottom, 140)
        .padding(.trailing, 12)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Un emoji isolé : monte de 430pt en 2,4s (linéaire), dérive latéralement aléatoirement
/// (±28pt), apparaît en fondu (300ms) puis s'estompe (2,1s) — mêmes constantes qu'Android.
private struct FloatingReactionParticle: View {
    let emoji: String
    let onFinished: () -> Void

    @State private var rise: CGFloat = 0
    @State private var visible = false
    @State private var faded = false

    private let drift = CGFloat.random(in: -28...28)
    private let startScale = 0.7 + CGFloat.random(in: 0...0.3)

    var body: some View {
        Text(emoji)
            .font(.system(size: 26 + startScale * 8))
            .offset(x: drift * rise, y: -(rise * 430))
            .opacity(faded ? 0 : (visible ? 1 : 0))
            .onAppear {
                withAnimation(.linear(duration: 2.4)) { rise = 1 }
                withAnimation(.easeInOut(duration: 0.3)) { visible = true }
                withAnimation(.easeInOut(duration: 2.1).delay(0.3)) { faded = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { onFinished() }
            }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        FloatingReactionsLayer(reactions: [
            FloatingReactionItem(id: "1", emoji: "🔥"),
            FloatingReactionItem(id: "2", emoji: "😍"),
        ])
    }
}

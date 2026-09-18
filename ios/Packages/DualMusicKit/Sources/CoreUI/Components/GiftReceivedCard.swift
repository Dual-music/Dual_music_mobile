import SwiftUI

/// Carte « cadeau reçu » — pilule en dégradé rose→violet, image réelle du cadeau (ou emoji de
/// repli) qui pulse légèrement, nom + valeur en crédits. Glisse depuis le bord gauche de l'écran,
/// positionnée en bas-gauche, tant que le cadeau est affiché par le ViewModel.
///
/// Remplace, pour Live/Duel/Concert/Compétition, l'ancien burst centré (``GiftBurstView``,
/// gardé pour les RÉACTIONS emoji uniquement) : Android a explicitement retiré le burst centré
/// au profit de cette carte glissante pour les cadeaux (voir commentaires `LiveRoomScreen.kt`
/// et `ConcertRoomScreen.kt` : « Le burst central de cadeau a été retiré »).
///
/// Miroir de `GiftReceivedCard`/`ConcertGiftReceivedCard`/`LiveGiftReceivedCard` (Compose,
/// répétés à l'identique dans chaque écran Android).
///
/// ```swift
/// if let gift = viewModel.giftFeed.last {
///     GiftReceivedCard(imageURL: gift.image, name: gift.giftName, valueCredits: gift.value)
///         .id(gift.id)
/// }
/// ```
public struct GiftReceivedCard: View {
    private let imageURL: String?
    private let fallbackEmoji: String
    private let name: String
    private let valueCredits: Double

    @State private var appeared = false
    @State private var pulsing = false

    /// - Parameters:
    ///   - imageURL: URL de l'image du cadeau (repli sur `fallbackEmoji` si absente/invalide).
    ///   - fallbackEmoji: emoji affiché tant que l'image n'est pas prête (défaut 🎁).
    ///   - name: nom du cadeau.
    ///   - valueCredits: valeur en crédits, affichée « +N Crédits ».
    public init(imageURL: String?, fallbackEmoji: String = "🎁", name: String, valueCredits: Double) {
        self.imageURL = imageURL
        self.fallbackEmoji = fallbackEmoji
        self.name = name
        self.valueCredits = valueCredits
    }

    public var body: some View {
        HStack(spacing: 8) {
            DMRemoteImage(url: imageURL, fallback: fallbackEmoji)
                .frame(width: 34, height: 34)
                .clipShape(Circle())
                .scaleEffect(pulsing ? 1.18 : 1.0)

            VStack(alignment: .leading, spacing: 1) {
                Text(name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("+\(formatCredits(valueCredits))")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.9))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            LinearGradient(
                colors: [Color(hex: 0xFF4FA3, alpha: 0.95), Color(hex: 0x7C3AED, alpha: 0.95)],
                startPoint: .leading,
                endPoint: .trailing
            ),
            in: Capsule()
        )
        .offset(x: appeared ? 0 : -260)
        .opacity(appeared ? 1 : 0)
        .onAppear {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.75)) { appeared = true }
            withAnimation(.easeInOut(duration: 0.55).repeatForever(autoreverses: true)) { pulsing = true }
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack {
            Spacer()
            HStack {
                GiftReceivedCard(imageURL: nil, name: "Rose", valueCredits: 50)
                Spacer()
            }
            .padding(.bottom, 300)
            .padding(.leading, 10)
        }
    }
}

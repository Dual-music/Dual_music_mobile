import SwiftUI

/// Texte qui défile en continu de droite à gauche (façon ticker) — utilisé pour le nom du
/// meilleur donateur au-dessus des rooms de direct. Miroir de `ScrollingLabel` (Compose,
/// répété à l'identique dans `LiveRoomScreen.kt`/`DuelRoomScreen.kt`) : anime la translation X
/// de +largeur à -1,3×largeur sur 9s, en boucle linéaire, texte doré sur une ligne.
///
/// ```swift
/// if let donor = viewModel.topDonor {
///     HStack { ScrollingLabel("👑  \(donor.displayName)  ·  \(Int(donor.total)) 🎁") }
///         .padding(.horizontal, 10).padding(.vertical, 3)
///         .background(Color.white.opacity(0.2), in: Capsule())
/// }
/// ```
public struct ScrollingLabel: View {
    private let text: String

    @State private var phase: CGFloat = 1

    public init(_ text: String) {
        self.text = text
    }

    public var body: some View {
        GeometryReader { proxy in
            Text(text)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color(hex: 0xFFD54A))
                .lineLimit(1)
                .fixedSize()
                .offset(x: phase * proxy.size.width)
                .onAppear {
                    withAnimation(.linear(duration: 9).repeatForever(autoreverses: false)) {
                        phase = -1.3
                    }
                }
        }
        .frame(height: 16)
        .clipped()
    }
}

#Preview {
    ScrollingLabel("👑  Alex  ·  1 250 🎁")
        .frame(width: 220)
        .padding()
        .background(Color.black)
}

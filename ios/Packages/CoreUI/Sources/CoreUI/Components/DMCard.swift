import SwiftUI

/// Conteneur « carte » de Dual Music : surface `card`, rayon 12pt, bordure fine et ombre
/// élégante. Base visuelle des listes (duels, concerts, replays…).
///
/// ```swift
/// DMCard {
///     VStack(alignment: .leading) { Text("Duel du soir").font(DMFont.headline) }
/// }
/// ```
public struct DMCard<Content: View>: View {
    @Environment(\.dmTheme) private var theme

    private let padded: Bool
    private let content: Content

    /// - Parameter padded: ajoute un padding interne standard (défaut : oui).
    public init(padded: Bool = true, @ViewBuilder content: () -> Content) {
        self.padded = padded
        self.content = content()
    }

    public var body: some View {
        content
            .padding(padded ? theme.spacing.lg : 0)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.colors.card)
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous)
                    .strokeBorder(theme.colors.border, lineWidth: 1)
            )
            .dmElegantShadow()
    }
}

#Preview {
    DMCard {
        VStack(alignment: .leading, spacing: 8) {
            Text("Duel du soir").font(DMFont.headline).foregroundStyle(DMColors.dark.foreground)
            Text("Artiste A vs Artiste B").font(DMFont.caption).foregroundStyle(DMColors.dark.mutedForeground)
        }
    }
    .padding()
    .background(DMColors.dark.background)
    .dualMusicTheme(.dark)
}

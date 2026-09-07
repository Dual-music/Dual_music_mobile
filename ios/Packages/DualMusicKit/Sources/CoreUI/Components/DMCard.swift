import SwiftUI

/// Conteneur « carte » de Dual Music : surface `card`, rayon 12 pt, bordure fine et ombre
/// élégante. Base visuelle des listes (duels, concerts, replays…). Miroir de `DMCard` Compose.
///
/// ```swift
/// DMCard {
///     VStack(alignment: .leading) { Text("Duel du soir").font(DMFont.headline) }
/// }
/// ```
public struct DMCard<Content: View>: View {
    @Environment(\.dmTheme) private var theme

    private let padded: Bool
    private let isSelected: Bool
    private let content: Content

    /// - Parameters:
    ///   - padded: ajoute un padding interne standard (défaut : oui).
    ///   - isSelected: met en évidence la carte avec une bordure primaire épaisse
    ///     (sélection d'une méthode de retrait, d'un événement à sponsoriser…).
    ///   - content: contenu de la carte.
    public init(padded: Bool = true, isSelected: Bool = false, @ViewBuilder content: () -> Content) {
        self.padded = padded
        self.isSelected = isSelected
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
                    .strokeBorder(
                        isSelected ? theme.colors.primary : theme.colors.border,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
            .dmElegantShadow()
    }
}

#Preview {
    VStack(spacing: 12) {
        DMCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Duel du soir").font(DMFont.headline).foregroundStyle(DMColors.dark.foreground)
                Text("Artiste A vs Artiste B").font(DMFont.caption).foregroundStyle(DMColors.dark.mutedForeground)
            }
        }
        DMCard(isSelected: true) {
            Text("Méthode sélectionnée").foregroundStyle(DMColors.dark.foreground)
        }
    }
    .padding()
    .background(DMColors.dark.background)
    .dualMusicTheme(.dark)
}

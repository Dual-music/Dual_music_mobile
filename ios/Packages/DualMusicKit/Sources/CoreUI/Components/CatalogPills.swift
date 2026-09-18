import SwiftUI

/// Pilule d'onglet des catalogues à 3 onglets (En direct / À venir / Replays) — miroir de
/// `TabPill` Compose, répété à l'identique dans `DuelsListScreen.kt`, `ConcertsScreen.kt` et
/// `CompetitionsScreen.kt`.
public struct DMTabPill: View {
    @Environment(\.dmTheme) private var theme

    private let text: String
    private let selected: Bool
    private let action: () -> Void

    /// - Parameters:
    ///   - text: libellé (déjà formaté avec son compteur, ex. « En direct (3) »).
    ///   - selected: onglet actif.
    ///   - action: tap.
    public init(_ text: String, selected: Bool, action: @escaping () -> Void) {
        self.text = text
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(text)
                .font(DMFont.caption)
                .foregroundStyle(selected ? Color.white : theme.colors.mutedForeground)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? theme.colors.primary : Color.black.opacity(0.3), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Pastille de badge à fond translucide arrondi (statut, prix, mode…) — miroir du `Box` pilule
/// répété sur les cartes de catalogue (Duels/Concerts/Compétitions Android), couleurs/emoji au
/// choix de l'appelant pour matcher exactement chaque écran.
public struct DMBadgePill: View {
    private let text: String
    private let foreground: Color
    private let background: Color
    private let bold: Bool

    /// - Parameters:
    ///   - text: libellé (emoji inclus si besoin).
    ///   - foreground: couleur du texte.
    ///   - background: couleur de fond (généralement translucide).
    ///   - bold: gras (badges de statut) ou non (badges d'info).
    public init(_ text: String, foreground: Color, background: Color, bold: Bool = false) {
        self.text = text
        self.foreground = foreground
        self.background = background
        self.bold = bold
    }

    public var body: some View {
        Text(text)
            .font(bold ? DMFont.caption.weight(.bold) : DMFont.caption)
            .foregroundStyle(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(background, in: Capsule())
    }
}

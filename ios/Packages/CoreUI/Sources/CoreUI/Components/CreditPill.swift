import SwiftUI

/// Pastille affichant un solde/prix en crédits, avec l'accent rose néon.
///
/// Utilisée dans la barre de wallet, les panneaux de cadeaux, les prix de tickets.
/// ```swift
/// CreditPill(credits: 1250)          // « 💎 1 250 »
/// CreditPill(credits: 50, label: "Vote")
/// ```
public struct CreditPill: View {
    @Environment(\.dmTheme) private var theme

    private let credits: Double
    private let label: String?

    public init(credits: Double, label: String? = nil) {
        self.credits = credits
        self.label = label
    }

    public var body: some View {
        HStack(spacing: 6) {
            Text("💎")
            Text(formatted).font(DMFont.mono)
            if let label { Text(label).font(DMFont.caption).opacity(0.8) }
        }
        .foregroundStyle(theme.colors.accentForeground)
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.sm)
        .background(theme.colors.accent)
        .clipShape(RoundedRectangle(cornerRadius: theme.radius.pill, style: .continuous))
    }

    /// Formatage avec séparateur de milliers (espace insécable), sans décimales inutiles.
    private var formatted: String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = credits.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2
        return f.string(from: NSNumber(value: credits)) ?? "\(credits)"
    }
}

#Preview {
    HStack {
        CreditPill(credits: 1250)
        CreditPill(credits: 50, label: "Vote")
    }
    .padding()
    .background(DMColors.dark.background)
    .dualMusicTheme(.dark)
}

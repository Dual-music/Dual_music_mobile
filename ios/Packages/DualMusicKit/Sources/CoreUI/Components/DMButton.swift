import SwiftUI

/// Bouton d'action principal de Dual Music — CTA à **dégradé violet → rose** avec halo,
/// repris du bouton signature du web et strictement aligné sur `DMButton` (Compose).
///
/// ```swift
/// DMButton("Envoyer le cadeau 🎁") { send() }
/// DMButton("Confirmer", style: .secondary, isLoading: submitting) { confirm() }
/// ```
public struct DMButton: View {

    /// Variantes visuelles alignées sur le web.
    public enum Style: Sendable {
        case primary
        case secondary
        case outline
        case destructive
    }

    @Environment(\.dmTheme) private var theme

    private let title: String
    private let style: Style
    private let isLoading: Bool
    private let isEnabled: Bool
    private let action: () -> Void

    /// - Parameters:
    ///   - title: libellé du bouton.
    ///   - style: variante visuelle (défaut : dégradé primaire).
    ///   - isLoading: affiche un indicateur et bloque le tap.
    ///   - isEnabled: désactive et atténue le bouton.
    ///   - action: action déclenchée au tap.
    public init(
        _ title: String,
        style: Style = .primary,
        isLoading: Bool = false,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.style = style
        self.isLoading = isLoading
        self.isEnabled = isEnabled
        self.action = action
    }

    public var body: some View {
        Button {
            guard isEnabled, !isLoading else { return }
            action()
        } label: {
            ZStack {
                if isLoading {
                    ProgressView().tint(foreground)
                } else {
                    Text(title)
                        .font(DMFont.button)
                        .foregroundStyle(foreground)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .padding(.horizontal, theme.spacing.lg)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous)
                    .strokeBorder(style == .outline ? theme.colors.border : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled || isLoading)
        .opacity(isEnabled ? 1 : 0.5)
        .dmIf(style == .primary && isEnabled) { $0.dmGlow() }
        .accessibilityLabel(Text(title))
    }

    // MARK: Styles dérivés du thème

    @ViewBuilder private var background: some View {
        switch style {
        case .primary: theme.gradients.primary
        case .secondary: theme.colors.secondary
        case .outline: Color.clear
        case .destructive: theme.colors.destructive
        }
    }

    private var foreground: Color {
        switch style {
        case .primary, .destructive: return theme.colors.primaryForeground
        case .secondary: return theme.colors.secondaryForeground
        case .outline: return theme.colors.foreground
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        DMButton("Envoyer le cadeau 🎁") {}
        DMButton("Confirmer le retrait", style: .secondary) {}
        DMButton("Annuler", style: .outline) {}
        DMButton("Bannir", style: .destructive) {}
        DMButton("Chargement…", isLoading: true) {}
    }
    .padding()
    .background(DMColors.dark.background)
    .dualMusicTheme(.dark)
}

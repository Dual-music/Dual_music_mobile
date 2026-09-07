import SwiftUI
#if canImport(UIKit)
import UIKit // `UIKeyboardType` (utilisé par `keyboardType(_:)`)
#endif

/// Champ de saisie thémé — équivalent d'`OutlinedTextField` (Material 3) côté Android.
///
/// Rend un libellé flottant, une bordure au rayon de la marque et un texte d'aide optionnel.
/// Gère aussi le mode **mot de passe** et le type de clavier.
///
/// ```swift
/// DMTextField("Email", text: $email, keyboard: .emailAddress)
/// DMTextField("Mot de passe *", text: $password, isSecure: true, help: "Au moins 8 caractères")
/// ```
public struct DMTextField: View {
    @Environment(\.dmTheme) private var theme

    private let label: String
    private let placeholder: String?
    private let help: String?
    private let isSecure: Bool
    private let keyboard: UIKeyboardType
    private let submitLabel: SubmitLabel
    private let autocapitalization: TextInputAutocapitalization
    private let axis: Axis
    @Binding private var text: String
    private let onSubmit: (() -> Void)?

    /// - Parameters:
    ///   - label: libellé affiché au-dessus du champ.
    ///   - text: valeur liée.
    ///   - placeholder: texte d'exemple affiché à vide.
    ///   - help: texte d'aide/erreur affiché sous le champ.
    ///   - isSecure: masque la saisie (mots de passe, PIN).
    ///   - keyboard: type de clavier iOS.
    ///   - submitLabel: libellé de la touche de validation.
    ///   - autocapitalization: capitalisation automatique (`.never` pour email/code).
    ///   - axis: `.vertical` pour un champ multiligne (bio, description).
    ///   - onSubmit: action à la validation clavier.
    public init(
        _ label: String,
        text: Binding<String>,
        placeholder: String? = nil,
        help: String? = nil,
        isSecure: Bool = false,
        keyboard: UIKeyboardType = .default,
        submitLabel: SubmitLabel = .done,
        autocapitalization: TextInputAutocapitalization = .sentences,
        axis: Axis = .horizontal,
        onSubmit: (() -> Void)? = nil
    ) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.help = help
        self.isSecure = isSecure
        self.keyboard = keyboard
        self.submitLabel = submitLabel
        self.autocapitalization = autocapitalization
        self.axis = axis
        self.onSubmit = onSubmit
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            Text(label)
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.mutedForeground)

            Group {
                if isSecure {
                    SecureField(placeholder ?? "", text: $text)
                } else if axis == .vertical {
                    TextField(placeholder ?? "", text: $text, axis: .vertical)
                        .lineLimit(3...6)
                } else {
                    TextField(placeholder ?? "", text: $text)
                }
            }
            .font(DMFont.body)
            .foregroundStyle(theme.colors.foreground)
            .keyboardType(keyboard)
            .submitLabel(submitLabel)
            .textInputAutocapitalization(autocapitalization)
            .autocorrectionDisabled(keyboard == .emailAddress || isSecure)
            .padding(theme.spacing.md)
            .background(theme.colors.input.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous)
                    .strokeBorder(theme.colors.border, lineWidth: 1)
            )
            .onSubmit { onSubmit?() }

            if let help {
                Text(help)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

/// Sélecteur déroulant thémé — équivalent de `DropdownMenu` côté Android.
///
/// Utilise un `Menu` natif iOS (feuille contextuelle) : plus idiomatique qu'une liste
/// déroulante custom, et accessible par VoiceOver sans travail supplémentaire.
///
/// ```swift
/// DMPicker(label: "Pays *", selection: country.label, options: Countries.all) { c in
///     Text(c.label)
/// } onSelect: { country = $0 }
/// ```
public struct DMPicker<Item: Identifiable, Label: View>: View {
    @Environment(\.dmTheme) private var theme

    private let label: String?
    private let currentLabel: String
    private let options: [Item]
    private let rowLabel: (Item) -> Label
    private let onSelect: (Item) -> Void

    /// - Parameters:
    ///   - label: libellé au-dessus du sélecteur (optionnel).
    ///   - selection: texte de la valeur courante.
    ///   - options: éléments proposés.
    ///   - rowLabel: rendu d'une ligne du menu.
    ///   - onSelect: appelé avec l'élément choisi.
    public init(
        label: String? = nil,
        selection currentLabel: String,
        options: [Item],
        @ViewBuilder rowLabel: @escaping (Item) -> Label,
        onSelect: @escaping (Item) -> Void
    ) {
        self.label = label
        self.currentLabel = currentLabel
        self.options = options
        self.rowLabel = rowLabel
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xs) {
            if let label {
                Text(label)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
            Menu {
                ForEach(options) { option in
                    Button { onSelect(option) } label: { rowLabel(option) }
                }
            } label: {
                HStack {
                    Text(currentLabel)
                        .font(DMFont.body)
                        .foregroundStyle(theme.colors.foreground)
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                .padding(theme.spacing.md)
                .frame(maxWidth: .infinity)
                .background(theme.colors.input.opacity(0.35))
                .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous)
                        .strokeBorder(theme.colors.border, lineWidth: 1)
                )
            }
        }
    }
}

/// Titre de section (au-dessus d'un groupe de cartes).
public struct DMSectionTitle: View {
    @Environment(\.dmTheme) private var theme
    private let text: String

    /// - Parameter text: libellé de la section.
    public init(_ text: String) { self.text = text }

    public var body: some View {
        Text(text)
            .font(DMFont.headline)
            .foregroundStyle(theme.colors.foreground)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Message d'information (violet) ou d'erreur (rouge), affiché sous un formulaire.
public struct DMMessage: View {
    @Environment(\.dmTheme) private var theme

    /// Nature du message : information/succès ou erreur.
    public enum Kind: Sendable { case info, error }

    private let text: String
    private let kind: Kind

    /// - Parameters:
    ///   - text: message à afficher.
    ///   - kind: information (défaut) ou erreur.
    public init(_ text: String, kind: Kind = .info) {
        self.text = text
        self.kind = kind
    }

    public var body: some View {
        Text(text)
            .font(DMFont.caption)
            .foregroundStyle(kind == .error ? theme.colors.destructive : theme.colors.primaryGlow)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isStaticText)
    }
}

/// Fond d'écran standard de l'app : dégradé héro couvrant toute la surface.
///
/// Toutes les pages l'appliquent pour reproduire le fond du web et d'Android.
public struct DMScreenBackground: View {
    @Environment(\.dmTheme) private var theme

    public init() {}

    public var body: some View {
        theme.gradients.hero.ignoresSafeArea()
    }
}

public extension View {
    /// Applique le fond héro derrière la vue (raccourci de ``DMScreenBackground``).
    func dmScreenBackground() -> some View {
        background(DMScreenBackground())
    }
}

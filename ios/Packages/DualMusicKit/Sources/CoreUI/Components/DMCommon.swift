import SwiftUI

// MARK: - Logo

/// Logo Dual Music.
///
/// Affiche **exactement le même logo que le site web et l'app Android** (asset `dm_logo.png`
/// embarqué dans `CoreUI` via `Bundle.module`), pour la reconnaissance de marque entre
/// plateformes. Le ratio d'origine est préservé ; l'appelant contrôle la hauteur.
public struct DMLogo: View {
    private let height: CGFloat

    /// - Parameter height: hauteur cible en points (la largeur suit le ratio). Défaut : 64.
    public init(height: CGFloat = 64) {
        self.height = height
    }

    public var body: some View {
        (Self.logoImage ?? Image(systemName: "music.note"))
            .resizable()
            .scaledToFit()
            .frame(height: height)
            .accessibilityLabel(Text("Dual Music"))
    }

    /// Charge l'image du logo depuis les ressources du module (résilient si l'asset manque).
    private static let logoImage: Image? = {
        #if canImport(UIKit)
        if let ui = UIImage(named: "dm_logo", in: .module, with: nil) {
            return Image(uiImage: ui)
        }
        #endif
        return nil
    }()
}

// MARK: - En-tête de page

/// En-tête de page standardisé : **titre centré** + **flèche de retour en haut à gauche**.
///
/// À utiliser en tête de chaque sous-page (portefeuille, boutique, classements…) pour une
/// navigation cohérente. Miroir de `DMPageHeader` Compose.
public struct DMPageHeader: View {
    @Environment(\.dmTheme) private var theme

    private let title: String
    private let onBack: (() -> Void)?

    /// - Parameters:
    ///   - title: titre de la page, centré horizontalement.
    ///   - onBack: si non nul, affiche la flèche de retour (sinon l'espace reste réservé
    ///     pour garder le titre parfaitement centré).
    public init(title: String, onBack: (() -> Void)? = nil) {
        self.title = title
        self.onBack = onBack
    }

    public var body: some View {
        ZStack {
            Text(title)
                .font(DMFont.pageTitle)
                .foregroundStyle(theme.colors.foreground)
                .lineLimit(1)
                .truncationMode(.tail)
                .padding(.horizontal, 56)

            HStack {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.backward")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(theme.colors.foreground)
                            .frame(width: 44, height: 44) // cible tactile ≥ 44 pt (HIG)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(Text("Retour"))
                }
                Spacer()
            }
        }
        .frame(height: 56)
        .padding(.horizontal, theme.spacing.xs)
    }
}

// MARK: - État vide

/// État vide standardisé : icône ronde + titre + sous-texte, le tout **centré**.
///
/// À utiliser partout où une liste/section n'a pas (encore) de données, à la place d'une
/// simple phrase. Miroir de `DMEmptyState` Compose.
public struct DMEmptyState: View {
    @Environment(\.dmTheme) private var theme

    private let title: String
    private let subtitle: String?
    private let systemImage: String

    /// - Parameters:
    ///   - title: message principal (ex. « Aucune notification »).
    ///   - subtitle: explication optionnelle (ex. « Tes alertes apparaîtront ici »).
    ///   - systemImage: symbole SF illustratif (défaut : information).
    public init(title: String, subtitle: String? = nil, systemImage: String = "info.circle") {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            ZStack {
                Circle()
                    .fill(theme.colors.card)
                    .frame(width: 88, height: 88)
                Image(systemName: systemImage)
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(theme.colors.primaryGlow)
            }
            Text(title)
                .font(DMFont.headline)
                .foregroundStyle(theme.colors.foreground)
                .multilineTextAlignment(.center)
            if let subtitle {
                Text(subtitle)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(theme.spacing.xl)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Chargement

/// Indicateur de chargement **centré** dans l'espace disponible.
///
/// Remplace les `ProgressView` bruts alignés en haut à gauche : à placer dans un conteneur
/// qui occupe la hauteur restante. Miroir de `DMLoadingBox` Compose.
public struct DMLoadingBox: View {
    @Environment(\.dmTheme) private var theme

    public init() {}

    public var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .tint(theme.colors.primary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityLabel(Text("Chargement"))
    }
}

// MARK: - Pastille de crédits

/// Pastille affichant un solde/prix en crédits, avec l'accent rose néon.
///
/// ```swift
/// CreditPill(credits: 1250)              // « 💎 1 250 »
/// CreditPill(credits: 50, label: "Vote")
/// ```
public struct CreditPill: View {
    @Environment(\.dmTheme) private var theme

    private let credits: Double
    private let label: String?

    /// - Parameters:
    ///   - credits: montant à afficher.
    ///   - label: complément optionnel (ex. « Vote »).
    public init(credits: Double, label: String? = nil) {
        self.credits = credits
        self.label = label
    }

    public var body: some View {
        HStack(spacing: 6) {
            Text("💎")
            Text(formatAmount(credits)).font(DMFont.mono)
            if let label {
                Text(label).font(DMFont.caption).opacity(0.85)
            }
        }
        .foregroundStyle(theme.colors.accentForeground)
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.sm)
        .background(theme.colors.accent)
        .clipShape(RoundedRectangle(cornerRadius: theme.radius.pill, style: .continuous))
        .accessibilityLabel(Text(formatCredits(credits)))
    }
}

// MARK: - Image distante

/// Image distante (avatars, pochettes, cadeaux) avec repli.
///
/// S'appuie sur `AsyncImage` (cache URLSession intégré, décodage hors du thread principal) :
/// aucune dépendance tierce, contrairement à l'implémentation maison d'Android.
///
/// ```swift
/// DMRemoteImage(url: artist.avatarURL, fallback: "🎤").frame(width: 64, height: 64).clipShape(Circle())
/// ```
public struct DMRemoteImage: View {
    @Environment(\.dmTheme) private var theme

    private let url: String?
    private let fallback: String
    private let contentMode: ContentMode

    /// - Parameters:
    ///   - url: URL publique de l'image (peut être `nil`/vide).
    ///   - fallback: emoji affiché tant que l'image n'est pas prête ou en cas d'échec.
    ///   - contentMode: `.fill` (défaut, recadre) ou `.fit`.
    public init(url: String?, fallback: String = "🎁", contentMode: ContentMode = .fill) {
        self.url = url
        self.fallback = fallback
        self.contentMode = contentMode
    }

    public var body: some View {
        Group {
            if let url, let parsed = URL(string: url), !url.isEmpty {
                AsyncImage(url: parsed) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: contentMode)
                    case .failure:
                        placeholder
                    case .empty:
                        placeholder
                    @unknown default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .clipped()
    }

    /// Repli : l'emoji centré sur la surface de la carte.
    private var placeholder: some View {
        ZStack {
            theme.colors.card
            Text(fallback).font(.system(size: 28))
        }
    }
}

// MARK: - Barre d'onglets

/// Barre d'onglets segmentée aux couleurs de la marque (équivalent de `TabRow` Compose).
///
/// Utilisée par le portefeuille, les classements, le sponsoring et l'espace créateur.
public struct DMTabBar: View {
    @Environment(\.dmTheme) private var theme

    private let titles: [String]
    @Binding private var selection: Int

    /// - Parameters:
    ///   - titles: libellés des onglets, dans l'ordre.
    ///   - selection: index de l'onglet actif.
    public init(titles: [String], selection: Binding<Int>) {
        self.titles = titles
        self._selection = selection
    }

    public var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                Button {
                    selection = index
                } label: {
                    VStack(spacing: 6) {
                        Text(title)
                            .font(.system(.subheadline, design: .rounded).weight(selection == index ? .bold : .regular))
                            .foregroundStyle(selection == index ? theme.colors.foreground : theme.colors.mutedForeground)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Rectangle()
                            .fill(selection == index ? theme.colors.accent : .clear)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == index ? [.isSelected, .isButton] : .isButton)
            }
        }
        .padding(.top, theme.spacing.xs)
    }
}

// MARK: - Ligne d'interrupteur

/// Ligne « libellé + interrupteur », utilisée par l'espace créateur et l'espace admin.
public struct DMToggleRow: View {
    @Environment(\.dmTheme) private var theme

    private let label: String
    @Binding private var isOn: Bool

    /// - Parameters:
    ///   - label: libellé affiché à gauche.
    ///   - isOn: état de l'interrupteur.
    public init(_ label: String, isOn: Binding<Bool>) {
        self.label = label
        self._isOn = isOn
    }

    public var body: some View {
        Toggle(isOn: $isOn) {
            Text(label).foregroundStyle(theme.colors.foreground)
        }
        .tint(theme.colors.primary)
    }
}

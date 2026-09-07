import SwiftUI

/// Jetons de couleur sémantiques (miroir exact de `duel_music_frontend/src/index.css` et de
/// `core/ui/theme/Color.kt` côté Android).
///
/// Deux instances : ``DMColors/dark`` (thème par défaut de l'app, comme le web) et
/// ``DMColors/light``. Les valeurs sont exprimées via ``SwiftUI/Color/init(webHSL:_:_:alpha:)``
/// avec les MÊMES HSL que le web — **ne jamais introduire de teinte hors de cette liste**.
public struct DMColors: Sendable {
    public let background: Color
    public let foreground: Color
    public let card: Color
    public let cardForeground: Color
    public let primary: Color
    public let primaryForeground: Color
    public let primaryGlow: Color
    public let electricBlue: Color
    public let neonPink: Color
    public let neonCyan: Color
    public let secondary: Color
    public let secondaryForeground: Color
    public let muted: Color
    public let mutedForeground: Color
    public let accent: Color
    public let accentForeground: Color
    public let destructive: Color
    public let border: Color
    public let input: Color
    public let ring: Color

    /// Thème CLAIR (`:root` du web).
    public static let light = DMColors(
        background: Color(webHSL: 0, 0, 1),
        foreground: Color(webHSL: 240, 0.10, 0.10),
        card: Color(webHSL: 0, 0, 1),
        cardForeground: Color(webHSL: 240, 0.10, 0.10),
        primary: Color(webHSL: 280, 0.70, 0.55),
        primaryForeground: Color(webHSL: 0, 0, 1),
        primaryGlow: Color(webHSL: 280, 0.80, 0.65),
        electricBlue: Color(webHSL: 210, 1.0, 0.60),
        neonPink: Color(webHSL: 330, 1.0, 0.65),
        neonCyan: Color(webHSL: 180, 1.0, 0.60),
        secondary: Color(webHSL: 240, 0.15, 0.20),
        secondaryForeground: Color(webHSL: 0, 0, 1),
        muted: Color(webHSL: 240, 0.10, 0.90),
        mutedForeground: Color(webHSL: 240, 0.05, 0.40),
        accent: Color(webHSL: 330, 1.0, 0.65),
        accentForeground: Color(webHSL: 0, 0, 1),
        destructive: Color(webHSL: 0, 0.842, 0.602),
        border: Color(webHSL: 240, 0.10, 0.85),
        input: Color(webHSL: 240, 0.10, 0.85),
        ring: Color(webHSL: 280, 0.70, 0.55)
    )

    /// Thème SOMBRE (`.dark` du web) — **thème par défaut de l'application**.
    public static let dark = DMColors(
        background: Color(webHSL: 240, 0.15, 0.08),
        foreground: Color(webHSL: 0, 0, 0.98),
        card: Color(webHSL: 240, 0.15, 0.12),
        cardForeground: Color(webHSL: 0, 0, 0.98),
        primary: Color(webHSL: 280, 0.70, 0.55),
        primaryForeground: Color(webHSL: 0, 0, 1),
        primaryGlow: Color(webHSL: 280, 0.80, 0.65),
        electricBlue: Color(webHSL: 210, 1.0, 0.60),
        neonPink: Color(webHSL: 330, 1.0, 0.65),
        neonCyan: Color(webHSL: 180, 1.0, 0.60),
        secondary: Color(webHSL: 240, 0.15, 0.18),
        secondaryForeground: Color(webHSL: 0, 0, 0.98),
        muted: Color(webHSL: 240, 0.15, 0.18),
        mutedForeground: Color(webHSL: 240, 0.05, 0.65),
        accent: Color(webHSL: 330, 1.0, 0.65),
        accentForeground: Color(webHSL: 0, 0, 1),
        destructive: Color(webHSL: 0, 0.628, 0.50),
        border: Color(webHSL: 240, 0.15, 0.20),
        input: Color(webHSL: 240, 0.15, 0.20),
        ring: Color(webHSL: 280, 0.70, 0.55)
    )
}

/// Dégradés de marque (miroir des `--gradient-*` du web), construits depuis la palette
/// courante pour rester cohérents en thème clair comme sombre.
public struct DMGradients: Sendable {
    /// Violet → rose (CTA principaux). Web : `linear-gradient(135°, primary, neon-pink)`.
    public let primary: LinearGradient
    /// Bleu → cyan. Web : `linear-gradient(135°, electric-blue, neon-cyan)`.
    public let electric: LinearGradient
    /// Fond héro : fond de page → violet profond. Web : `linear-gradient(180°, bg, hsl(280 50% 15%))`.
    public let hero: LinearGradient
    /// Dégradé de la barre supérieure (violet foncé → magenta), identique à Android.
    public let topBar: LinearGradient
    /// Dégradé de la barre de navigation basse.
    public let bottomBar: LinearGradient
    /// Dégradé de marque des accents (titre d'accueil, pastilles d'accès rapide).
    public let brand: LinearGradient

    init(colors: DMColors) {
        primary = LinearGradient(
            colors: [colors.primary, colors.neonPink],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
        electric = LinearGradient(
            colors: [colors.electricBlue, colors.neonCyan],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
        hero = LinearGradient(
            colors: [colors.background, Color(webHSL: 280, 0.50, 0.15)],
            startPoint: .top, endPoint: .bottom
        )
        topBar = LinearGradient(
            colors: [Color(hex: 0x3A1D6E), Color(hex: 0x6D28D9), Color(hex: 0x9D2FB0)],
            startPoint: .leading, endPoint: .trailing
        )
        bottomBar = LinearGradient(
            colors: [Color(hex: 0x2A1257), Color(hex: 0x3A1D6E)],
            startPoint: .leading, endPoint: .trailing
        )
        brand = LinearGradient(
            colors: [Color(hex: 0xB07CFF), Color(hex: 0xFF4FA3)],
            startPoint: .leading, endPoint: .trailing
        )
    }
}

/// Rayons de coin (web `--radius: 0.75rem` = 12 pt, + dérivés).
public struct DMRadius: Sendable {
    public let sm: CGFloat = 8
    public let md: CGFloat = 12   // = --radius
    public let lg: CGFloat = 16
    public let pill: CGFloat = 999
}

/// Échelle d'espacement (base 4 pt) — identique aux `DMSpacing` Android.
public struct DMSpacing: Sendable {
    public let xs: CGFloat = 4
    public let sm: CGFloat = 8
    public let md: CGFloat = 12
    public let lg: CGFloat = 16
    public let xl: CGFloat = 24
    public let xxl: CGFloat = 32
}

/// Thème complet injecté dans l'environnement SwiftUI.
///
/// Accès dans une vue : `@Environment(\.dmTheme) private var theme`.
public struct DMTheme: Sendable {
    public let colors: DMColors
    public let gradients: DMGradients
    public let radius = DMRadius()
    public let spacing = DMSpacing()
    /// Vrai si le thème est sombre (utile pour la barre d'état / les `ColorScheme`).
    public let isDark: Bool

    init(colors: DMColors, isDark: Bool) {
        self.colors = colors
        self.gradients = DMGradients(colors: colors)
        self.isDark = isDark
    }

    /// Thème sombre (défaut de l'app, parité web).
    public static let dark = DMTheme(colors: .dark, isDark: true)
    /// Thème clair.
    public static let light = DMTheme(colors: .light, isDark: false)
}

// MARK: - Injection dans l'environnement

private struct DMThemeKey: EnvironmentKey {
    static let defaultValue: DMTheme = .dark // thème sombre par défaut, comme le web
}

public extension EnvironmentValues {
    /// Thème Dual Music courant.
    var dmTheme: DMTheme {
        get { self[DMThemeKey.self] }
        set { self[DMThemeKey.self] = newValue }
    }
}

public extension View {
    /// Applique le thème Dual Music (sombre par défaut) au sous-arbre de vues.
    ///
    /// Applique aussi le `colorScheme` correspondant pour que les composants système
    /// (claviers, feuilles, `TextField`…) suivent le même thème.
    /// - Parameter theme: thème à injecter.
    func dualMusicTheme(_ theme: DMTheme = .dark) -> some View {
        environment(\.dmTheme, theme)
            .environment(\.colorScheme, theme.isDark ? .dark : .light)
            .tint(theme.colors.primary)
    }
}

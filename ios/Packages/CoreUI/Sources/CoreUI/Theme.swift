import SwiftUI

/// Jetons de couleur sémantiques (miroir exact de `duel_music_frontend/src/index.css`).
///
/// Deux instances : ``DMColors/dark`` (thème par défaut de l'app) et ``DMColors/light``.
/// Les valeurs sont exprimées via ``Color/init(webHSL:_:_:alpha:)`` avec les MÊMES HSL
/// que le web — ne jamais introduire de teinte hors de cette liste.
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

/// Dégradés de marque (miroir des `--gradient-*` du web).
public struct DMGradients: Sendable {
    /// Violet → rose (CTA principaux). Web : `linear-gradient(135°, hsl(280 70% 55%), hsl(330 100% 65%))`.
    public let primary = LinearGradient(
        colors: [Color(webHSL: 280, 0.70, 0.55), Color(webHSL: 330, 1.0, 0.65)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    /// Bleu → cyan. Web : `linear-gradient(135°, hsl(210 100% 60%), hsl(180 100% 60%))`.
    public let electric = LinearGradient(
        colors: [Color(webHSL: 210, 1.0, 0.60), Color(webHSL: 180, 1.0, 0.60)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    /// Fond héro sombre → violet. Web : `linear-gradient(180°, hsl(240 15% 8%), hsl(280 50% 15%))`.
    public let hero = LinearGradient(
        colors: [Color(webHSL: 240, 0.15, 0.08), Color(webHSL: 280, 0.50, 0.15)],
        startPoint: .top, endPoint: .bottom
    )
}

/// Rayons de coin (web `--radius: 0.75rem` = 12pt, + dérivés).
public struct DMRadius: Sendable {
    public let sm: CGFloat = 8
    public let md: CGFloat = 12   // = --radius
    public let lg: CGFloat = 16
    public let pill: CGFloat = 999
}

/// Échelle d'espacement (base 4pt).
public struct DMSpacing: Sendable {
    public let xs: CGFloat = 4
    public let sm: CGFloat = 8
    public let md: CGFloat = 12
    public let lg: CGFloat = 16
    public let xl: CGFloat = 24
    public let xxl: CGFloat = 32
}

/// Thème complet injecté dans l'environnement SwiftUI.
public struct DMTheme: Sendable {
    public let colors: DMColors
    public let gradients = DMGradients()
    public let radius = DMRadius()
    public let spacing = DMSpacing()

    public static let dark = DMTheme(colors: .dark)
    public static let light = DMTheme(colors: .light)
}

// MARK: - Injection dans l'environnement

private struct DMThemeKey: EnvironmentKey {
    static let defaultValue: DMTheme = .dark // thème sombre par défaut, comme le web
}

public extension EnvironmentValues {
    /// Thème Dual Music courant. Accès dans une vue : `@Environment(\.dmTheme) private var theme`.
    var dmTheme: DMTheme {
        get { self[DMThemeKey.self] }
        set { self[DMThemeKey.self] = newValue }
    }
}

public extension View {
    /// Applique le thème Dual Music (sombre par défaut) au sous-arbre de vues.
    func dualMusicTheme(_ theme: DMTheme = .dark) -> some View {
        environment(\.dmTheme, theme)
    }
}

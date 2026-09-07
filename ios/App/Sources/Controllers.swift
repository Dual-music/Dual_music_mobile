import Foundation
import Observation
import SwiftUI
import CoreUI

/// Préférence de thème choisie par l'utilisateur.
public enum ThemeMode: String, CaseIterable, Identifiable, Sendable {
    case light
    case dark
    case system

    public var id: String { rawValue }
}

/// Gère le thème (clair / sombre / système) et le **persiste** entre les lancements.
///
/// Équivalent de `ThemeController` (SharedPreferences) côté Android ; ici `UserDefaults`.
@Observable
@MainActor
public final class ThemeController {

    private static let key = "dm_theme_mode"

    /// Mode courant, observable par la racine de l'app.
    public private(set) var mode: ThemeMode

    public init() {
        let raw = UserDefaults.standard.string(forKey: Self.key)
        mode = raw.flatMap(ThemeMode.init(rawValue:)) ?? .system
    }

    /// Change le mode et le persiste.
    /// - Parameter mode: nouveau mode.
    public func set(_ mode: ThemeMode) {
        self.mode = mode
        UserDefaults.standard.set(mode.rawValue, forKey: Self.key)
    }

    /// Résout le thème effectif à partir du mode et de l'apparence système.
    /// - Parameter systemScheme: apparence système courante (`@Environment(\.colorScheme)`).
    public func theme(for systemScheme: ColorScheme) -> DMTheme {
        switch mode {
        case .light: return .light
        case .dark: return .dark
        case .system: return systemScheme == .dark ? .dark : .light
        }
    }
}

/// Gère la langue de l'interface (FR / EN) et la **persiste** entre les lancements.
///
/// Met aussi à jour ``CoreUI/AppStrings`` (chaînes accessibles hors SwiftUI, utilisées par
/// les ViewModels pour leurs messages d'erreur), exactement comme `LanguageController`
/// côté Android met à jour `appStrings`.
@Observable
@MainActor
public final class LanguageController {

    private static let key = "dm_app_language"

    /// Langue courante, observable.
    public private(set) var language: AppLanguage

    public init() {
        let raw = UserDefaults.standard.string(forKey: Self.key)
        let resolved = raw.flatMap(AppLanguage.init(rawValue:)) ?? LanguageController.systemDefault()
        language = resolved
        AppStrings.setLanguage(resolved)
    }

    /// Change la langue, la persiste et aligne les chaînes globales.
    /// - Parameter language: nouvelle langue.
    public func set(_ language: AppLanguage) {
        self.language = language
        UserDefaults.standard.set(language.rawValue, forKey: Self.key)
        AppStrings.setLanguage(language)
    }

    /// Langue par défaut au **premier lancement** : celle de l'appareil si elle est
    /// supportée, français sinon (Dual Music est d'abord une plateforme francophone).
    private static func systemDefault() -> AppLanguage {
        let code = Locale.current.language.languageCode?.identifier ?? "fr"
        return code == "en" ? .en : .fr
    }
}

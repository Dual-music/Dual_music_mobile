import XCTest
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
@testable import CoreUI

/// Vérifie la **parité visuelle** avec le web et Android : conversion HSL, jetons de
/// dimension, formatage de la devise interne et complétude des traductions.
final class DesignSystemTests: XCTestCase {

    // MARK: - Couleurs

    /// La conversion HSL→sRGB doit reproduire exactement celle du CSS : c'est ce qui garantit
    /// qu'un violet de marque est le même pixel sur iOS, Android et le web.
    func testWebHSLMatchesCSSConversion() throws {
        // hsl(280 70% 55%) → rgb(167, 60, 221) d'après la formule CSS (m1/m2 de la spec
        // CSS Color : m2 = l+s-l*s = 0.865, m1 = 2l-m2 = 0.235 ; R=0.655, G=0.235, B=0.865).
        let components = try XCTUnwrap(rgbComponents(of: Color(webHSL: 280, 0.70, 0.55)))
        XCTAssertEqual(components.r, 167.0 / 255.0, accuracy: 0.01)
        XCTAssertEqual(components.g, 60.0 / 255.0, accuracy: 0.01)
        XCTAssertEqual(components.b, 221.0 / 255.0, accuracy: 0.01)
    }

    func testHexInitializer() throws {
        let components = try XCTUnwrap(rgbComponents(of: Color(hex: 0xFF4FA3)))
        XCTAssertEqual(components.r, 1.0, accuracy: 0.01)
        XCTAssertEqual(components.g, 0x4F / 255.0, accuracy: 0.01)
        XCTAssertEqual(components.b, 0xA3 / 255.0, accuracy: 0.01)
    }

    // MARK: - Jetons

    func testRadiusMatchesWebToken() {
        // Web : `--radius: 0.75rem` = 12 px.
        XCTAssertEqual(DMRadius().md, 12)
    }

    func testSpacingScaleIsBaseFour() {
        let spacing = DMSpacing()
        XCTAssertEqual([spacing.xs, spacing.sm, spacing.md, spacing.lg, spacing.xl, spacing.xxl],
                       [4, 8, 12, 16, 24, 32])
    }

    func testDarkIsDefaultTheme() {
        XCTAssertTrue(DMTheme.dark.isDark)
        XCTAssertFalse(DMTheme.light.isDark)
    }

    // MARK: - Devise interne

    func testFormatCreditsUsesSingularAndPlural() {
        XCTAssertTrue(formatCredits(1, locale: Locale(identifier: "fr_FR")).hasSuffix("Crédit"))
        XCTAssertTrue(formatCredits(2, locale: Locale(identifier: "fr_FR")).hasSuffix("Crédits"))
    }

    func testFormatCreditsDropsUselessDecimals() {
        XCTAssertEqual(formatAmount(12, locale: Locale(identifier: "en_US")), "12")
        XCTAssertEqual(formatAmount(12.5, locale: Locale(identifier: "en_US")), "12.5")
    }

    func testIsoHelpersTruncateSafely() {
        XCTAssertEqual(isoDay("2026-08-01T20:00:00Z"), "2026-08-01")
        XCTAssertEqual(isoMinute("2026-08-01T20:00:00Z"), "2026-08-01T20:00")
        XCTAssertNil(isoDay(nil))
    }

    // MARK: - Localisation

    /// Aucune chaîne ne doit rester vide : une clé oubliée se verrait comme un trou dans l'UI.
    func testNoEmptyStringInAnyLanguage() {
        for language in AppLanguage.allCases {
            let mirror = Mirror(reflecting: DMStrings.of(language))
            for child in mirror.children {
                guard let value = child.value as? String else { continue }
                XCTAssertFalse(
                    value.trimmingCharacters(in: .whitespaces).isEmpty,
                    "Chaîne vide : \(child.label ?? "?") en \(language.rawValue)"
                )
            }
        }
    }

    /// Les deux tables doivent exposer le même nombre de champs renseignés (garde-fou contre
    /// une traduction partiellement ajoutée).
    func testBothLanguagesExposeSameFieldCount() {
        let fr = Mirror(reflecting: DMStrings.fr).children.count
        let en = Mirror(reflecting: DMStrings.en).children.count
        XCTAssertEqual(fr, en)
    }

    @MainActor
    func testAppStringsFollowsSelectedLanguage() {
        AppStrings.setLanguage(.en)
        XCTAssertEqual(AppStrings.current.navHome, "Home")
        AppStrings.setLanguage(.fr)
        XCTAssertEqual(AppStrings.current.navHome, "Accueil")
    }

    // MARK: - Utilitaires

    /// Extrait les composantes sRGB d'une `Color` (via UIKit).
    private func rgbComponents(of color: Color) -> (r: Double, g: Double, b: Double)? {
        #if canImport(UIKit)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        return (Double(r), Double(g), Double(b))
        #else
        return nil
        #endif
    }
}

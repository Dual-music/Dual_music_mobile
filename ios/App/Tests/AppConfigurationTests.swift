import XCTest
@testable import DualMusic

/// Tests de la coque applicative.
///
/// La logique métier est couverte dans le paquet Swift (`swift test`) ; ici on vérifie ce
/// qui dépend du **bundle** de l'app : configuration injectée par les build settings et
/// persistance des préférences.
final class AppConfigurationTests: XCTestCase {

    /// L'URL d'API doit venir de l'`Info.plist` (donc des build settings), jamais d'une
    /// constante codée en dur.
    func testApiBaseURLIsInjectedFromInfoPlist() throws {
        let raw = try XCTUnwrap(
            Bundle.main.object(forInfoDictionaryKey: "DMApiBaseURL") as? String,
            "Clé DMApiBaseURL absente : vérifier ios/project.yml"
        )
        XCTAssertFalse(raw.isEmpty, "DM_API_BASE_URL n'est pas renseigné pour cette configuration")
        XCTAssertEqual(AppConfig.apiBaseURL.absoluteString, raw)
        XCTAssertFalse(
            raw.hasSuffix("/api/v1"),
            "La base URL ne doit PAS contenir /api/v1 — le client HTTP l'ajoute lui-même"
        )
    }

    /// Toutes les autorisations utilisées par l'app doivent être décrites, sans quoi iOS
    /// termine le processus au premier accès.
    func testAllUsageDescriptionsArePresent() {
        let requiredKeys = [
            "NSCameraUsageDescription",
            "NSMicrophoneUsageDescription",
            "NSPhotoLibraryUsageDescription",
            "NSFaceIDUsageDescription",
        ]
        for key in requiredKeys {
            let value = Bundle.main.object(forInfoDictionaryKey: key) as? String
            XCTAssertNotNil(value, "Clé \(key) manquante dans Info.plist")
            XCTAssertFalse(value?.isEmpty ?? true, "Description vide pour \(key)")
        }
    }

    @MainActor
    func testThemeControllerPersistsSelection() {
        let controller = ThemeController()
        let original = controller.mode
        defer { controller.set(original) }

        controller.set(.light)
        XCTAssertEqual(ThemeController().mode, .light, "Le mode doit survivre à un nouveau lancement")

        controller.set(.dark)
        XCTAssertEqual(ThemeController().mode, .dark)
    }

    @MainActor
    func testLanguageControllerPersistsSelection() {
        let controller = LanguageController()
        let original = controller.language
        defer { controller.set(original) }

        controller.set(.en)
        XCTAssertEqual(LanguageController().language, .en)
    }
}

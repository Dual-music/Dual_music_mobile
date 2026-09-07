import XCTest

/// Tests d'interface : parcours critiques vérifiables **sans backend**.
///
/// Objectif de ces tests dans ce projet : détecter en CI (runner macOS) une régression
/// bloquante — écran noir au lancement, crash immédiat, formulaire de connexion absent —
/// sans dépendre d'un serveur. Les parcours authentifiés se testent avec un backend de
/// recette et des identifiants dédiés (voir `docs/RELEASE-IOS.md`).
final class LaunchUITests: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// L'app se lance et affiche l'écran d'authentification (session vide sur un
    /// simulateur neuf).
    func testLaunchesToSignInScreen() {
        let app = XCUIApplication()
        app.launch()

        // Le CTA principal de connexion doit apparaître en moins de 10 s : au-delà, c'est
        // un blocage réseau ou un crash silencieux.
        let signIn = app.staticTexts["Se connecter"]
        let english = app.staticTexts["Sign in"]
        let appeared = signIn.waitForExistence(timeout: 10) || english.waitForExistence(timeout: 1)
        XCTAssertTrue(appeared, "L'écran de connexion ne s'est pas affiché")
    }

    /// Le formulaire refuse une soumission incomplète (règles alignées sur le backend).
    func testSubmitIsDisabledWithEmptyForm() {
        let app = XCUIApplication()
        app.launch()

        let button = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'connecter' OR label CONTAINS[c] 'Sign in'")).firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 10))
        XCTAssertFalse(button.isEnabled, "Le bouton doit rester inactif tant que le formulaire est vide")
    }

    /// Mesure le temps de lancement (suivi de régression de démarrage).
    func testLaunchPerformance() {
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}

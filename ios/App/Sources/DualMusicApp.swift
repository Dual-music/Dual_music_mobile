import SwiftUI
import CoreUI
import FeatureAuth

/// Point d'entrée de l'application iOS Dual Music.
///
/// Équivalent de `DualMusicApp` + `MainActivity` côté Android :
/// - construit le conteneur d'injection une seule fois ;
/// - applique le thème (clair/sombre/système) et la langue (FR/EN) persistés ;
/// - aiguille entre le tunnel d'authentification et la coque connectée.
@main
@MainActor
struct DualMusicApp: App {

    /// Délégué UIKit : nécessaire pour l'enregistrement APNs (SwiftUI seul ne l'expose pas).
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    /// Conteneur d'injection, créé une fois pour toute la durée de vie du processus.
    @State private var container = AppContainer()

    /// Apparence système, utilisée quand le mode de thème est « Système ».
    @Environment(\.colorScheme) private var systemScheme

    var body: some Scene {
        WindowGroup {
            RootView(container: container)
                .dualMusicTheme(container.themeController.theme(for: systemScheme))
                .dualMusicStrings(container.languageController.language)
                .onOpenURL { url in
                    // Retour du flux OAuth Google (schéma d'URL inversé).
                    GoogleSignInProvider.handle(url)
                }
        }
    }
}

/// Délégué applicatif minimal : configuration Firebase + relais du jeton APNs.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        MainActor.assumeIsolated {
            PushService.shared.configureIfPossible()
        }
        return true
    }

    /// APNs a fourni le jeton de l'appareil.
    ///
    /// Il est transmis à Firebase Messaging, qui l'échange contre un jeton FCM — c'est ce
    /// dernier que le backend attend.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        #if canImport(FirebaseMessaging)
        MainActor.assumeIsolated {
            if PushService.shared.isConfigured {
                FirebaseMessagingBridge.setAPNSToken(deviceToken)
            }
        }
        #endif
    }

    /// L'enregistrement APNs a échoué (pas de capacité Push, profil non signé, simulateur…).
    ///
    /// Non bloquant : l'app fonctionne sans notifications distantes.
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Volontairement silencieux : voir la note ci-dessus.
    }
}

#if canImport(FirebaseMessaging)
import FirebaseMessaging

/// Petit pont vers Firebase Messaging, isolé pour garder `AppDelegate` lisible et pour que
/// le fichier compile même sans le SDK (voir `#if canImport`).
enum FirebaseMessagingBridge {
    /// Transmet le jeton APNs à Firebase.
    static func setAPNSToken(_ token: Data) {
        Messaging.messaging().apnsToken = token
    }
}
#endif

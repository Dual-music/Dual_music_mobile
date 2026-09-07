import Foundation
import UserNotifications
import UIKit

#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseMessaging)
import FirebaseMessaging
#endif

/// Service de notifications push — équivalent iOS de `DualMusicMessagingService` (Android).
///
/// ## Pourquoi Firebase côté iOS ?
/// Le backend envoie les push via **FCM** (voir `firebase-service-account.json`). Pour que
/// l'endpoint `POST /notifications/devices` reçoive un jeton exploitable, iOS doit donc
/// fournir un **jeton FCM** (et non le jeton APNs brut). Firebase Messaging s'en charge :
/// il enregistre l'app auprès d'APNs puis échange le jeton contre un identifiant FCM.
///
/// ## Dégradation propre
/// Si `GoogleService-Info.plist` est absent (dépôt fraîchement cloné, build de test), le
/// service **ne configure pas Firebase** et n'expose aucun jeton : l'app se lance
/// normalement, seules les notifications distantes sont inactives. Le centre de
/// notifications in-app (Socket.IO) continue de fonctionner.
@MainActor
public final class PushService: NSObject {

    /// Instance partagée (le délégué APNs doit être unique).
    public static let shared = PushService()

    /// Vrai si Firebase a pu être configuré (plist présent).
    public private(set) var isConfigured = false

    private override init() { super.init() }

    /// Configure Firebase si possible. À appeler une seule fois, au démarrage.
    public func configureIfPossible() {
        #if canImport(FirebaseCore)
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            // Pas de configuration Firebase : on n'appelle surtout pas `configure()`,
            // qui terminerait le processus.
            return
        }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        isConfigured = true
        #endif

        UNUserNotificationCenter.current().delegate = self
        #if canImport(FirebaseMessaging)
        if isConfigured { Messaging.messaging().delegate = self }
        #endif
    }

    /// Demande l'autorisation d'afficher des notifications, puis enregistre l'app auprès
    /// d'APNs si elle est accordée.
    ///
    /// Appelée **après** la connexion (comme sur Android où la permission
    /// `POST_NOTIFICATIONS` est demandée une fois l'utilisateur authentifié) : la demande
    /// arrive à un moment où son intérêt est compréhensible.
    /// - Returns: `true` si l'utilisateur a accordé l'autorisation.
    @discardableResult
    public func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        if granted {
            UIApplication.shared.registerForRemoteNotifications()
        }
        return granted
    }

    /// Jeton d'appareil à envoyer au backend (`POST /notifications/devices`).
    ///
    /// - Returns: le jeton FCM, ou `nil` si Firebase n'est pas configuré / pas encore prêt
    ///   (simulateur sans APNs, autorisation refusée…).
    public func currentToken() async -> String? {
        #if canImport(FirebaseMessaging)
        guard isConfigured else { return nil }
        return try? await Messaging.messaging().token()
        #else
        return nil
        #endif
    }
}

// MARK: - Affichage des notifications au premier plan

extension PushService: UNUserNotificationCenterDelegate {

    /// Affiche bannière + son même quand l'app est au premier plan (parité avec le canal
    /// `IMPORTANCE_HIGH` d'Android).
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }

    /// Tap sur une notification : l'app est ramenée au premier plan.
    ///
    /// Le routage deeplink (ouvrir le duel/live ciblé) se branchera ici en lisant
    /// `response.notification.request.content.userInfo` — même charge utile que le champ
    /// `data` des notifications in-app.
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        // Aucune action spécifique pour l'instant : le centre in-app affiche déjà l'élément.
    }
}

#if canImport(FirebaseMessaging)
extension PushService: MessagingDelegate {

    /// Le jeton FCM a changé (réinstallation, restauration…).
    ///
    /// On ne le renvoie pas ici : l'enregistrement exige le Bearer de l'utilisateur. Il sera
    /// repris au prochain login via `AppContainer.registerPushTokenIfAvailable()` — même
    /// stratégie que `onNewToken` côté Android.
    public nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        // Volontairement vide : voir la note ci-dessus.
    }
}
#endif

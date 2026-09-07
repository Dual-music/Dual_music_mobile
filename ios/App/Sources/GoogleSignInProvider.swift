import Foundation
import UIKit

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Fournisseur d'**ID token Google** pour la connexion native.
///
/// Équivalent de `requestGoogleIdToken` (Credential Manager) côté Android : ouvre le
/// sélecteur de compte Google, récupère l'ID token signé, puis `FeatureAuth` l'échange
/// contre une session via `POST /auth/oauth/google/native`.
///
/// ## Configuration requise
/// 1. Créer un **client OAuth iOS** dans la console Google/Firebase du projet `dual-music`.
/// 2. Renseigner `GIDClientID` dans l'`Info.plist` (voir `ios/project.yml`).
/// 3. Déclarer le **schéma d'URL inversé** (`com.googleusercontent.apps.…`) dans
///    `CFBundleURLTypes`, sinon le retour du navigateur ne revient pas à l'app.
///
/// Sans configuration, ``makeProvider()`` renvoie `nil` : le bouton Google est simplement
/// masqué et l'app reste utilisable en email/mot de passe.
public enum GoogleSignInProvider {

    /// Construit le fournisseur d'ID token, ou `nil` si Google n'est pas configuré.
    @MainActor
    public static func makeProvider() -> (@MainActor () async throws -> String)? {
        #if canImport(GoogleSignIn)
        guard let clientID = AppConfig.googleClientID else { return nil }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        return { try await signIn() }
        #else
        return nil
        #endif
    }

    /// Traite l'URL de retour du flux Google (appelée par `onOpenURL`).
    /// - Parameter url: URL reçue par l'app.
    /// - Returns: `true` si l'URL a été consommée par Google Sign-In.
    @MainActor
    @discardableResult
    public static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }

    // MARK: - Interne

    #if canImport(GoogleSignIn)
    /// Erreurs propres au flux Google.
    private enum GoogleError: LocalizedError {
        case noPresenter
        case noIdToken

        var errorDescription: String? {
            switch self {
            case .noPresenter: return "Impossible d'ouvrir la connexion Google."
            case .noIdToken: return "Google n'a pas fourni de jeton d'identité."
            }
        }
    }

    /// Ouvre le sélecteur Google et renvoie l'ID token.
    @MainActor
    private static func signIn() async throws -> String {
        guard let presenter = topViewController() else { throw GoogleError.noPresenter }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else { throw GoogleError.noIdToken }
        return idToken
    }

    /// Contrôleur le plus haut de la scène active (requis pour présenter le flux Google).
    @MainActor
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
    #endif
}

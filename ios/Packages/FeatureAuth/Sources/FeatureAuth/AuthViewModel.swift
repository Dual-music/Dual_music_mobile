import Foundation
import Observation
import CoreNetwork

/// État global d'authentification de l'app.
public enum AuthState: Sendable, Equatable {
    case loading          // réhydratation au démarrage
    case signedOut
    case signedIn(AuthUser)
}

/// ViewModel de l'authentification (iOS 17 `@Observable`).
///
/// Gère l'état de session (réhydratation, connexion, inscription, déconnexion) et les
/// champs de l'écran de connexion. Les vues observent directement ses propriétés.
@Observable
@MainActor
public final class AuthViewModel {

    private let repository: AuthRepository

    // État de session
    public private(set) var state: AuthState = .loading

    // Champs de l'écran de connexion
    public var email: String = ""
    public var password: String = ""

    // État transitoire de l'UI
    public private(set) var isSubmitting: Bool = false
    public private(set) var errorMessage: String?

    public init(repository: AuthRepository) {
        self.repository = repository
    }

    /// Le formulaire est-il soumettable ? (activation du bouton)
    public var canSubmit: Bool {
        !isSubmitting && email.contains("@") && password.count >= 8
    }

    /// Réhydrate la session au lancement (via le refresh token persisté).
    public func bootstrap() async {
        do {
            let me = try await repository.me()
            state = .signedIn(me.user)
        } catch {
            state = .signedOut
        }
    }

    /// Connexion email + mot de passe.
    public func signIn() async {
        guard canSubmit else { return }
        await run {
            let session = try await self.repository.login(email: self.email, password: self.password)
            self.state = .signedIn(session.user)
        }
    }

    /// Déconnexion.
    public func signOut() async {
        await repository.logout()
        state = .signedOut
        email = ""; password = ""
    }

    /// Exécute une opération asynchrone en gérant `isSubmitting` et les erreurs typées.
    private func run(_ operation: @escaping () async throws -> Void) async {
        isSubmitting = true
        errorMessage = nil
        do {
            try await operation()
        } catch let api as APIError {
            errorMessage = friendlyMessage(for: api)
        } catch {
            errorMessage = "Une erreur est survenue. Réessaie."
        }
        isSubmitting = false
    }

    /// Traduit un code d'erreur backend en message utilisateur.
    private func friendlyMessage(for error: APIError) -> String {
        switch error.code {
        case "VALIDATION_ERROR": return "Identifiants invalides."
        case "UNAUTHORIZED", "HTTP_401": return "Email ou mot de passe incorrect."
        default: return error.isRetriable ? "Connexion instable. Réessaie." : error.message
        }
    }
}

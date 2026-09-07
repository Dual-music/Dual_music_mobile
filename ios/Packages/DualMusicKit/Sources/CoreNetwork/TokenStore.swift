import Foundation

/// Fournisseur/persistance des jetons d'authentification pour ``HTTPClient``.
///
/// Implémenté par `CoreAuth` (Keychain). Isolé ici en protocole pour découpler le réseau du
/// stockage sécurisé et faciliter les tests (mock en mémoire).
///
/// Marqué `Sendable` : consommé depuis l'acteur réseau et le flux de refresh.
public protocol TokenStore: Sendable {
    /// Jeton d'accès courant (JWT, ~15 min), ou `nil` si non connecté.
    func accessToken() async -> String?

    /// Jeton de refresh (~30 j), ou `nil`.
    func refreshToken() async -> String?

    /// Persiste la nouvelle paire après un login/refresh réussi.
    func setTokens(access: String, refresh: String) async

    /// Efface les jetons (déconnexion / refresh échoué).
    func clear() async
}

/// Nouvelle paire de jetons issue d'un refresh.
public struct RefreshedTokens: Sendable {
    public let access: String
    public let refresh: String
    public init(access: String, refresh: String) {
        self.access = access
        self.refresh = refresh
    }
}

/// Exécuteur du refresh (`POST /auth/refresh`).
///
/// Fourni par `CoreAuth` pour éviter une dépendance circulaire : ``HTTPClient`` a besoin
/// d'un `TokenRefresher`, donc le refresher ne peut pas dépendre de ``HTTPClient``.
public protocol TokenRefresher: Sendable {
    /// Échange le refresh token contre une nouvelle paire. Lève si le refresh est invalide.
    func refresh(using refreshToken: String) async throws -> RefreshedTokens
}

/// Implémentation en mémoire — **tests et previews uniquement**.
///
/// Ne persiste rien : à ne jamais utiliser en production (le Keychain est la seule
/// implémentation valide, voir `CoreAuth.KeychainTokenStore`).
public actor InMemoryTokenStore: TokenStore {
    private var access: String?
    private var refresh: String?

    public init(access: String? = nil, refresh: String? = nil) {
        self.access = access
        self.refresh = refresh
    }

    public func accessToken() async -> String? { access }
    public func refreshToken() async -> String? { refresh }
    public func setTokens(access: String, refresh: String) async {
        self.access = access
        self.refresh = refresh
    }
    public func clear() async {
        access = nil
        refresh = nil
    }
}

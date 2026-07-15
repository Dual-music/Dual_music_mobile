import Foundation

/// Fournisseur de jetons d'authentification pour `HTTPClient`.
///
/// Implémenté par `core-auth` (stockage Keychain). Isolé ici en protocole pour découpler
/// le réseau du stockage sécurisé et faciliter les tests (mock).
///
/// Marqué `Sendable` : consommé depuis l'acteur réseau et le flux de refresh.
public protocol TokenStore: Sendable {
    /// Jeton d'accès courant (JWT, ~15 min), ou `nil` si non connecté.
    func accessToken() async -> String?

    /// Jeton de refresh (~30 j), ou `nil`.
    func refreshToken() async -> String?

    /// Persiste la nouvelle paire après un refresh réussi.
    func setTokens(access: String, refresh: String) async

    /// Efface les jetons (déconnexion / refresh échoué).
    func clear() async
}

/// Résultat d'un refresh de session appelé par `HTTPClient`.
public struct RefreshedTokens: Sendable {
    public let access: String
    public let refresh: String
    public init(access: String, refresh: String) {
        self.access = access
        self.refresh = refresh
    }
}

/// Effectue l'appel de refresh (`POST /auth/refresh`). Fourni par `core-auth` pour éviter
/// une dépendance circulaire (le réseau ne connaît pas les routes d'auth).
public protocol TokenRefresher: Sendable {
    /// Échange le refresh token contre une nouvelle paire. Lève si le refresh est invalide.
    func refresh(using refreshToken: String) async throws -> RefreshedTokens
}

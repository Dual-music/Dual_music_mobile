import Foundation
import CoreNetwork
import CoreAuth

/// Accès aux opérations d'authentification du backend `/auth/*`.
///
/// Orchestre `HTTPClient` (appels REST) et `TokenStore` (persistance des jetons). Après
/// login/register, persiste la session ; après logout, l'efface.
public actor AuthRepository {

    private let http: HTTPClient
    private let tokenStore: TokenStore
    private let baseURL: URL

    public init(http: HTTPClient, tokenStore: TokenStore, baseURL: URL) {
        self.http = http
        self.tokenStore = tokenStore
        self.baseURL = baseURL
    }

    // MARK: Connexion / inscription

    /// Connexion email + mot de passe. Persiste la session en cas de succès.
    @discardableResult
    public func login(email: String, password: String) async throws -> AuthSession {
        let body = ["email": email, "password": password]
        let session: AuthSession = try await http.request(.init(.post, "/auth/login", body: body, anonymous: true))
        await tokenStore.setTokens(access: session.accessToken, refresh: session.refreshToken)
        return session
    }

    /// Inscription. Persiste la session en cas de succès.
    @discardableResult
    public func register(_ input: RegisterInput) async throws -> AuthSession {
        let session: AuthSession = try await http.request(.init(.post, "/auth/register", body: input, anonymous: true))
        await tokenStore.setTokens(access: session.accessToken, refresh: session.refreshToken)
        return session
    }

    /// Récupère l'utilisateur courant (`/auth/me`) — utilisé à l'ouverture pour réhydrater.
    public func me() async throws -> MeResponse {
        try await http.request(.get("/auth/me"))
    }

    /// Déconnexion : invalide le refresh côté serveur puis efface le stockage local.
    public func logout() async {
        let refresh = await tokenStore.refreshToken()
        _ = try? await http.send(.init(.post, "/auth/logout", body: ["refreshToken": refresh]))
        await tokenStore.clear()
    }

    // MARK: Vérification OTP téléphone (post-login)

    /// Envoie un code OTP au numéro de l'utilisateur connecté.
    public func sendPhoneOtp() async throws {
        try await http.send(.init(.post, "/auth/otp/phone/send"))
    }

    /// Vérifie le code OTP (4–8 chiffres) reçu par SMS.
    public func verifyPhoneOtp(code: String) async throws {
        try await http.send(.init(.post, "/auth/otp/phone/verify", body: ["code": code]))
    }

    // MARK: Google

    /// URL de démarrage du flux OAuth Google (redirect web).
    ///
    /// ⚠️ À ouvrir dans `ASWebAuthenticationSession`. Le retour nécessite un **redirect
    /// mobile (deep link)** que le backend doit gérer — OU un endpoint natif
    /// `POST /auth/oauth/google/native` (idToken) à ajouter. Voir DELIVERY-03.
    public func googleStartURL() -> URL {
        baseURL.appendingPathComponent("api/v1/auth/oauth/google")
    }
}

/// Entrée d'inscription (miroir de `RegisterRequest`).
public struct RegisterInput: Encodable, Sendable {
    public let email: String
    public let password: String
    public let fullName: String?
    public let phone: String?
    public let referralCode: String?

    public init(email: String, password: String, fullName: String? = nil, phone: String? = nil, referralCode: String? = nil) {
        self.email = email
        self.password = password
        self.fullName = fullName
        self.phone = phone
        self.referralCode = referralCode
    }
}

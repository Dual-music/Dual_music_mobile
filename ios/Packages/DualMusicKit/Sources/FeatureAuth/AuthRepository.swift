import Foundation
import CoreNetwork
import DomainModels

/// Accès aux opérations d'authentification `/auth/…`.
///
/// Orchestre ``CoreNetwork/HTTPClient`` (REST) et `TokenStore` (persistance Keychain).
/// Après login/register, persiste la session ; après logout, l'efface.
/// Miroir de `AuthRepository` côté Android.
public struct AuthRepository: Sendable {

    private let http: HTTPClient
    private let tokenStore: TokenStore
    private let baseURL: URL

    /// - Parameters:
    ///   - http: client HTTP applicatif.
    ///   - tokenStore: stockage sécurisé des jetons.
    ///   - baseURL: URL publique **SANS** `/api/v1` (pour l'URL de démarrage Google web).
    public init(http: HTTPClient, tokenStore: TokenStore, baseURL: URL) {
        self.http = http
        self.tokenStore = tokenStore
        self.baseURL = baseURL
    }

    /// Connexion email + mot de passe ; persiste la session.
    /// - Returns: la session (utilisateur, profil, rôles).
    @discardableResult
    public func login(email: String, password: String) async throws -> AuthSession {
        let session: AuthSession = try await http.request(
            .post(AuthEndpoints.login, body: LoginRequest(email: email, password: password), anonymous: true),
            as: AuthSession.self
        )
        await tokenStore.setTokens(access: session.accessToken, refresh: session.refreshToken)
        return session
    }

    /// Inscription ; persiste la session.
    @discardableResult
    public func register(_ request: RegisterRequest) async throws -> AuthSession {
        let session: AuthSession = try await http.request(
            .post(AuthEndpoints.register, body: request, anonymous: true),
            as: AuthSession.self
        )
        await tokenStore.setTokens(access: session.accessToken, refresh: session.refreshToken)
        return session
    }

    /// Connexion Google **native** : envoie l'ID token (obtenu via le SDK Google Sign-In) au
    /// backend qui le vérifie, résout/crée le compte et renvoie une session. La persiste.
    /// - Parameter idToken: ID token signé par Google.
    @discardableResult
    public func loginWithGoogle(idToken: String) async throws -> AuthSession {
        let session: AuthSession = try await http.request(
            .post(AuthEndpoints.oauthGoogleNative, body: GoogleNativeRequest(idToken: idToken), anonymous: true),
            as: AuthSession.self
        )
        await tokenStore.setTokens(access: session.accessToken, refresh: session.refreshToken)
        return session
    }

    /// Utilisateur courant (`GET /auth/me`) — réhydratation au démarrage.
    public func me() async throws -> MeResponse {
        try await http.request(.get(AuthEndpoints.me), as: MeResponse.self)
    }

    /// Complète/édite le profil du caller (`PATCH /users/me`) — étape 3 de l'inscription
    /// (nom, pays, numéro) et édition ultérieure. N'envoie que les champs non nuls.
    public func updateProfile(_ request: UpdateProfileRequest) async throws {
        try await http.send(.patch(UserEndpoints.updateMe, body: request))
    }

    /// Déconnexion : invalide le refresh côté serveur puis efface le stockage local.
    ///
    /// L'échec de l'appel serveur est **volontairement ignoré** : la session locale doit
    /// être effacée dans tous les cas (mode avion, backend indisponible…).
    public func logout() async {
        let refresh = await tokenStore.refreshToken()
        try? await http.send(.post(AuthEndpoints.logout, body: RefreshRequest(refreshToken: refresh)))
        await tokenStore.clear()
    }

    /// Envoie un OTP au numéro de l'utilisateur connecté.
    public func sendPhoneOtp() async throws {
        try await http.send(.post(AuthEndpoints.otpPhoneSend))
    }

    /// Vérifie le code OTP téléphone.
    public func verifyPhoneOtp(code: String) async throws {
        try await http.send(.post(AuthEndpoints.otpPhoneVerify, body: VerifyOtpRequest(code: code)))
    }

    /// (Re)envoie le code de vérification par email au compte connecté.
    public func sendEmailOtp() async throws {
        try await http.send(.post(AuthEndpoints.otpEmailSend))
    }

    /// Vérifie le code email → marque l'email comme vérifié côté serveur.
    public func verifyEmailOtp(code: String) async throws {
        try await http.send(.post(AuthEndpoints.otpEmailVerify, body: VerifyOtpRequest(code: code)))
    }

    /// Demande un email de réinitialisation (le backend répond toujours OK, sans révéler si
    /// le compte existe).
    public func forgotPassword(email: String) async throws {
        try await http.send(
            .post(AuthEndpoints.passwordForgot, body: ForgotPasswordRequest(email: email.trimmed), anonymous: true)
        )
    }

    /// Réinitialise le mot de passe avec le code reçu par email.
    public func resetPassword(email: String, code: String, newPassword: String) async throws {
        try await http.send(
            .post(
                AuthEndpoints.passwordReset,
                body: ResetPasswordRequest(email: email.trimmed, code: code.trimmed, newPassword: newPassword),
                anonymous: true
            )
        )
    }

    /// Enregistre le jeton push de cet appareil auprès du backend (appelé une fois connecté).
    /// - Parameter token: jeton FCM/APNs de l'appareil.
    public func registerDeviceToken(_ token: String) async throws {
        try await http.send(.post(NotificationEndpoints.devices, body: DeviceTokenRequest(token: token)))
    }

    /// URL de démarrage OAuth Google (flux redirect web) — repli si l'échange natif n'est
    /// pas disponible. À ouvrir dans un `ASWebAuthenticationSession`.
    public var googleStartURL: URL {
        baseURL
            .appendingPathComponent("api/v1")
            .appendingPathComponent(AuthEndpoints.oauthGoogleStart.trimmed(charactersIn: "/"))
    }
}

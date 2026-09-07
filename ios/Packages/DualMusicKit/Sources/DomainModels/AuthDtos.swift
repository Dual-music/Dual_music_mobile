import Foundation

/*
 * DTOs d'authentification — miroir de `shared-domain/auth/AuthDtos.kt`.
 *
 * Rappel du modèle réel :
 *  - Auth primaire = **email + mot de passe** (`/auth/login`, `/auth/register`).
 *  - `/auth/refresh` échange le refresh token.
 *  - OTP téléphone = **vérification post-login** du numéro (`/auth/otp/phone/…`), authentifiée.
 *  - Google = échange natif de l'`idToken` (`/auth/oauth/google/native`).
 *
 * ⚠️ Les corps de requête sont en **camelCase** (Joi `stripUnknown` côté backend) : tout
 * champ hors DTO est silencieusement supprimé par le serveur.
 */

/// Utilisateur authentifié (`AuthSession.user` / `/auth/me`.user).
public struct AuthUser: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let email: String
    public let phone: String?
    public let phoneVerified: Bool
    public let emailVerified: Bool
    public let isBanned: Bool
    /// Date ISO de suppression programmée si le compte est en délai de grâce, sinon `nil`.
    public let deletionScheduledAt: String?

    enum CodingKeys: String, CodingKey {
        case id, email, phone, phoneVerified, emailVerified, isBanned, deletionScheduledAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        email = c.val(String.self, .email, "")
        phone = c.opt(String.self, .phone)
        phoneVerified = c.bool(.phoneVerified)
        emailVerified = c.bool(.emailVerified)
        isBanned = c.bool(.isBanned)
        deletionScheduledAt = c.opt(String.self, .deletionScheduledAt)
    }

    public init(
        id: String,
        email: String,
        phone: String? = nil,
        phoneVerified: Bool = false,
        emailVerified: Bool = false,
        isBanned: Bool = false,
        deletionScheduledAt: String? = nil
    ) {
        self.id = id
        self.email = email
        self.phone = phone
        self.phoneVerified = phoneVerified
        self.emailVerified = emailVerified
        self.isBanned = isBanned
        self.deletionScheduledAt = deletionScheduledAt
    }
}

/// Session renvoyée par `login` / `register` / `refresh`.
public struct AuthSession: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let user: AuthUser
    public let profile: DisplayProfile?
    public let roles: [UserRole]
    public let expiresIn: Int
    public let tokenType: String

    enum CodingKeys: String, CodingKey {
        case accessToken, refreshToken, user, profile, roles, expiresIn, tokenType
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        accessToken = c.val(String.self, .accessToken, "")
        refreshToken = c.val(String.self, .refreshToken, "")
        user = try c.decode(AuthUser.self, forKey: .user)
        profile = c.opt(DisplayProfile.self, .profile)
        roles = c.val([UserRole].self, .roles, [])
        expiresIn = c.int(.expiresIn)
        tokenType = c.val(String.self, .tokenType, "Bearer")
    }

    /// Vrai si l'utilisateur possède le rôle admin.
    public var isAdmin: Bool { roles.contains(.admin) }
}

/// Réponse de `GET /auth/me`.
public struct MeResponse: Codable, Sendable {
    public let user: AuthUser
    public let profile: DisplayProfile?
    public let roles: [UserRole]

    enum CodingKeys: String, CodingKey { case user, profile, roles }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        user = try c.decode(AuthUser.self, forKey: .user)
        profile = c.opt(DisplayProfile.self, .profile)
        roles = c.val([UserRole].self, .roles, [])
    }

    public init(user: AuthUser, profile: DisplayProfile? = nil, roles: [UserRole] = []) {
        self.user = user
        self.profile = profile
        self.roles = roles
    }
}

// MARK: - Corps de requête

/// Corps de `POST /auth/login`.
public struct LoginRequest: Encodable, Sendable {
    public let email: String
    public let password: String
    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }
}

/// Corps de `POST /auth/register`.
public struct RegisterRequest: Encodable, Sendable {
    public let email: String
    public let password: String
    public let fullName: String?
    public let phone: String?
    public let countryCode: String?
    public let phoneCountryCode: String?
    public let referralCode: String?

    public init(
        email: String,
        password: String,
        fullName: String? = nil,
        phone: String? = nil,
        countryCode: String? = nil,
        phoneCountryCode: String? = nil,
        referralCode: String? = nil
    ) {
        self.email = email
        self.password = password
        self.fullName = fullName
        self.phone = phone
        self.countryCode = countryCode
        self.phoneCountryCode = phoneCountryCode
        self.referralCode = referralCode
    }
}

/// Corps de `POST /auth/refresh` et `POST /auth/logout`.
public struct RefreshRequest: Encodable, Sendable {
    public let refreshToken: String?
    public init(refreshToken: String?) { self.refreshToken = refreshToken }
}

/// Corps de `POST /auth/otp/phone/verify` et `/auth/otp/email/verify`.
public struct VerifyOtpRequest: Encodable, Sendable {
    public let code: String
    public init(code: String) { self.code = code }
}

/// Corps de `POST /auth/password/forgot`.
public struct ForgotPasswordRequest: Encodable, Sendable {
    public let email: String
    public init(email: String) { self.email = email }
}

/// Corps de `POST /auth/password/change` (utilisateur connecté).
public struct ChangePasswordRequest: Encodable, Sendable {
    public let currentPassword: String?
    public let newPassword: String
    public init(currentPassword: String?, newPassword: String) {
        self.currentPassword = currentPassword
        self.newPassword = newPassword
    }
}

/// Corps de `POST /auth/password/reset` — code reçu par email + nouveau mot de passe.
public struct ResetPasswordRequest: Encodable, Sendable {
    public let email: String
    public let code: String
    public let newPassword: String
    public init(email: String, code: String, newPassword: String) {
        self.email = email
        self.code = code
        self.newPassword = newPassword
    }
}

/// Corps de `POST /auth/oauth/google/native` — échange natif de l'ID token Google.
///
/// L'ID token provient du SDK Google Sign-In iOS ; le backend en vérifie la signature et
/// l'audience (`serverClientId` = Web client ID Firebase), puis résout/crée le compte.
public struct GoogleNativeRequest: Encodable, Sendable {
    public let idToken: String
    public init(idToken: String) { self.idToken = idToken }
}

/// Corps de `POST /notifications/devices` — enregistrement du jeton push de l'appareil.
public struct DeviceTokenRequest: Encodable, Sendable {
    public let token: String
    public init(token: String) { self.token = token }
}

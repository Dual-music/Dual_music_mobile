import Foundation

/// Miroirs Swift `Codable` des DTOs d'auth (voir `shared-domain/auth/AuthDtos.kt`).
/// Utilisés pour le décodage générique par `HTTPClient`. Garder alignés avec le backend.

public struct AuthUser: Codable, Sendable, Identifiable {
    public let id: String
    public let email: String
    public let phone: String?
    public let phoneVerified: Bool
    public let emailVerified: Bool
    public let isBanned: Bool
}

/// Profil d'affichage (souple pendant la migration).
public struct Profile: Codable, Sendable {
    public let id: String
    public let displayName: String?
    public let avatarUrl: String?

    enum CodingKeys: String, CodingKey {
        case id
        case displayName = "display_name"
        case avatarUrl = "avatar_url"
    }
}

public enum UserRole: String, Codable, Sendable {
    case fan, artist, manager, moderator, admin
}

/// Session renvoyée par login/register/refresh.
public struct AuthSession: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let user: AuthUser
    public let profile: Profile?
    public let roles: [UserRole]

    public var isAdmin: Bool { roles.contains(.admin) }
}

/// Réponse de `GET /auth/me`.
public struct MeResponse: Codable, Sendable {
    public let user: AuthUser
    public let profile: Profile?
    public let roles: [UserRole]
}

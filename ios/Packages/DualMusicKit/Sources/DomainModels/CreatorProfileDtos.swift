import Foundation

/// DTOs des **profils publics créateurs** (artiste / manager) — miroir exact de
/// `shared-domain/creator/CreatorProfileDtos.kt`.
///
/// Les liens sociaux sont stockés dans une colonne JSON libre `social_links` (clés non
/// contraintes côté serveur) ; on partage ici la liste **canonique** des plateformes
/// (``SocialPlatform``) pour garder web et mobile cohérents.

/// Profil public d'un artiste (colonnes de `artist_profiles`).
public struct ArtistProfile: Decodable, Sendable, Equatable {
    public let userId: String?
    public let stageName: String?
    public let bio: String?
    public let avatarURL: String?
    public let coverImageURL: String?
    public let isPublic: Bool
    public let socialLinks: [String: String]

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case stageName = "stage_name"
        case bio
        case avatarURL = "avatar_url"
        case coverImageURL = "cover_image_url"
        case isPublic = "is_public"
        case socialLinks = "social_links"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.opt(String.self, .userId)
        stageName = c.opt(String.self, .stageName)
        bio = c.opt(String.self, .bio)
        avatarURL = c.opt(String.self, .avatarURL)
        coverImageURL = c.opt(String.self, .coverImageURL)
        isPublic = c.val(Bool.self, .isPublic, true)
        socialLinks = c.val([String: String].self, .socialLinks, [:])
    }
}

/// Profil public d'un manager (colonnes de `manager_profiles`).
public struct ManagerProfile: Decodable, Sendable, Equatable {
    public let userId: String?
    public let displayName: String?
    public let bio: String?
    public let experience: String?
    public let avatarURL: String?
    public let coverImageURL: String?
    public let isPublic: Bool
    public let socialLinks: [String: String]

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case displayName = "display_name"
        case bio
        case experience
        case avatarURL = "avatar_url"
        case coverImageURL = "cover_image_url"
        case isPublic = "is_public"
        case socialLinks = "social_links"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.opt(String.self, .userId)
        displayName = c.opt(String.self, .displayName)
        bio = c.opt(String.self, .bio)
        experience = c.opt(String.self, .experience)
        avatarURL = c.opt(String.self, .avatarURL)
        coverImageURL = c.opt(String.self, .coverImageURL)
        isPublic = c.val(Bool.self, .isPublic, true)
        socialLinks = c.val([String: String].self, .socialLinks, [:])
    }
}

/// Réponse de `GET /users/:id` — profil de compte + profil artiste éventuel + suivi.
public struct PublicProfileResponse: Decodable, Sendable {
    public let profile: DisplayProfile?
    public let artistProfile: ArtistProfile?
    public let followerCount: Int
    public let isFollowing: Bool

    enum CodingKeys: String, CodingKey {
        case profile, artistProfile, followerCount, isFollowing
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        profile = c.opt(DisplayProfile.self, .profile)
        artistProfile = c.opt(ArtistProfile.self, .artistProfile)
        followerCount = c.val(Int.self, .followerCount, 0)
        isFollowing = c.val(Bool.self, .isFollowing, false)
    }
}

/// Corps de `PATCH /artists/me` — n'envoyer que les champs modifiés (les `nil` sont omis).
public struct UpdateArtistProfileRequest: Encodable, Sendable {
    public let stageName: String?
    public let bio: String?
    public let avatarURL: String?
    public let coverImageURL: String?
    public let isPublic: Bool?
    public let socialLinks: [String: String]?

    enum CodingKeys: String, CodingKey {
        case stageName = "stage_name"
        case bio
        case avatarURL = "avatar_url"
        case coverImageURL = "cover_image_url"
        case isPublic = "is_public"
        case socialLinks = "social_links"
    }

    public init(
        stageName: String? = nil,
        bio: String? = nil,
        avatarURL: String? = nil,
        coverImageURL: String? = nil,
        isPublic: Bool? = nil,
        socialLinks: [String: String]? = nil
    ) {
        self.stageName = stageName
        self.bio = bio
        self.avatarURL = avatarURL
        self.coverImageURL = coverImageURL
        self.isPublic = isPublic
        self.socialLinks = socialLinks
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(stageName, forKey: .stageName)
        try c.encodeIfPresent(bio, forKey: .bio)
        try c.encodeIfPresent(avatarURL, forKey: .avatarURL)
        try c.encodeIfPresent(coverImageURL, forKey: .coverImageURL)
        try c.encodeIfPresent(isPublic, forKey: .isPublic)
        try c.encodeIfPresent(socialLinks, forKey: .socialLinks)
    }
}

/// Corps de `PATCH /managers/me`.
public struct UpdateManagerProfileRequest: Encodable, Sendable {
    public let displayName: String?
    public let bio: String?
    public let experience: String?
    public let avatarURL: String?
    public let coverImageURL: String?
    public let isPublic: Bool?
    public let socialLinks: [String: String]?

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
        case bio
        case experience
        case avatarURL = "avatar_url"
        case coverImageURL = "cover_image_url"
        case isPublic = "is_public"
        case socialLinks = "social_links"
    }

    public init(
        displayName: String? = nil,
        bio: String? = nil,
        experience: String? = nil,
        avatarURL: String? = nil,
        coverImageURL: String? = nil,
        isPublic: Bool? = nil,
        socialLinks: [String: String]? = nil
    ) {
        self.displayName = displayName
        self.bio = bio
        self.experience = experience
        self.avatarURL = avatarURL
        self.coverImageURL = coverImageURL
        self.isPublic = isPublic
        self.socialLinks = socialLinks
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(displayName, forKey: .displayName)
        try c.encodeIfPresent(bio, forKey: .bio)
        try c.encodeIfPresent(experience, forKey: .experience)
        try c.encodeIfPresent(avatarURL, forKey: .avatarURL)
        try c.encodeIfPresent(coverImageURL, forKey: .coverImageURL)
        try c.encodeIfPresent(isPublic, forKey: .isPublic)
        try c.encodeIfPresent(socialLinks, forKey: .socialLinks)
    }
}

/// Plateformes sociales canoniques éditables sur le profil public.
///
/// `key` = clé stockée dans `social_links` (identique au web), `label` = libellé affiché,
/// `hint` = exemple d'URL complète. L'artiste expose Spotify en plus ; le manager non.
public enum SocialPlatform: String, CaseIterable, Sendable, Hashable {
    case instagram, tiktok, youtube, twitter, facebook, spotify

    public var key: String { rawValue }

    public var label: String {
        switch self {
        case .instagram: return "Instagram"
        case .tiktok: return "TikTok"
        case .youtube: return "YouTube"
        case .twitter: return "X (Twitter)"
        case .facebook: return "Facebook"
        case .spotify: return "Spotify"
        }
    }

    public var hint: String {
        switch self {
        case .instagram: return "https://instagram.com/…"
        case .tiktok: return "https://tiktok.com/@…"
        case .youtube: return "https://youtube.com/@…"
        case .twitter: return "https://x.com/…"
        case .facebook: return "https://facebook.com/…"
        case .spotify: return "https://open.spotify.com/artist/…"
        }
    }

    /// Plateformes proposées à un artiste (6).
    public static let artist: [SocialPlatform] = [.instagram, .tiktok, .youtube, .twitter, .facebook, .spotify]
    /// Plateformes proposées à un manager (5 — pas de Spotify).
    public static let manager: [SocialPlatform] = [.instagram, .twitter, .facebook, .youtube, .tiktok]
}

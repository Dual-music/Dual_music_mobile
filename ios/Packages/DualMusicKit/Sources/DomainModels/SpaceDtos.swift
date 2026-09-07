import Foundation

/*
 * DTOs des espaces utilisateur : rôles, admin, sponsor, créateur, abonnement, contenu,
 * parrainage, notifications, média, upload.
 * Miroir des paquets `shared-domain/{role,admin,sponsor,creator,subscription,content,
 * referral,notification,media,upload}`.
 */

// MARK: - Candidatures de rôle

/// Corps de `POST /artists/requests` — candidature artiste (camelCase strict).
public struct ApplyArtistRequest: Encodable, Sendable {
    public let description: String
    public let socialLinks: [String: String]
    public let justificationDocumentUrl: String?

    public init(description: String, socialLinks: [String: String] = [:], justificationDocumentUrl: String? = nil) {
        self.description = description
        self.socialLinks = socialLinks
        self.justificationDocumentUrl = justificationDocumentUrl
    }
}

/// Corps de `POST /managers/requests` — candidature manager.
public struct ApplyManagerRequest: Encodable, Sendable {
    public let bio: String
    public let experience: String
    public init(bio: String, experience: String) {
        self.bio = bio
        self.experience = experience
    }
}

/// Élément de `GET /artists|managers/requests/me` (statut de ma candidature).
public struct RoleRequest: Codable, Sendable, Identifiable {
    public let id: String
    /// `pending` | `approved` | `rejected`.
    public let status: String
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, status
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        status = c.val(String.self, .status, "pending")
        createdAt = c.opt(String.self, .createdAt)
    }
}

/// Valeur d'un réglage `*_requests_enabled` (`{ enabled }`).
public struct EnabledFlag: Codable, Sendable {
    public let enabled: Bool

    enum CodingKeys: String, CodingKey { case enabled }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.bool(.enabled, true)
    }

    public init(enabled: Bool) { self.enabled = enabled }
}

/// Réponse de `GET /settings/public/:key` — `{ key, value }`.
public struct PublicSetting: Codable, Sendable {
    public let key: String?
    public let value: EnabledFlag?

    enum CodingKeys: String, CodingKey { case key, value }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = c.opt(String.self, .key)
        value = c.opt(EnabledFlag.self, .value)
    }
}

// MARK: - Admin

/// Utilisateur retourné par la recherche admin (`GET /admin/users/search?q=`).
public struct AdminUser: Codable, Sendable, Identifiable {
    public let id: String
    public let fullName: String?
    public let email: String?
    public let avatarURL: String?
    public let isBanned: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case fullName = "full_name"
        case email
        case avatarURL = "avatar_url"
        case isBanned = "is_banned"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        fullName = c.opt(String.self, .fullName)
        email = c.opt(String.self, .email)
        avatarURL = c.opt(String.self, .avatarURL)
        isBanned = c.bool(.isBanned)
    }

    /// Libellé affichable : nom complet, sinon email, sinon début d'identifiant.
    public var label: String {
        if let n = fullName, !n.isEmpty { return n }
        if let e = email { return e }
        return String(id.prefix(8))
    }
}

/// Corps de `POST /admin/roles` (assigner) et `DELETE /admin/roles` (révoquer).
public struct AssignRoleRequest: Encodable, Sendable {
    public let userId: String
    public let role: String
    public init(userId: String, role: String) {
        self.userId = userId
        self.role = role
    }
}

/// Corps de `PUT /admin/settings/:key` — le backend attend `{ value: { enabled } }`.
public struct SettingBody: Encodable, Sendable {
    public let value: SettingEnabled
    public init(value: SettingEnabled) { self.value = value }
}

/// Valeur d'un réglage booléen envoyée à l'admin (`{ enabled }`).
public struct SettingEnabled: Encodable, Sendable {
    public let enabled: Bool
    public init(enabled: Bool) { self.enabled = enabled }
}

// MARK: - Sponsoring

/// Palier tarifaire de sponsoring (`sponsor_price_tiers`) — prix par durée de média.
public struct SponsorTier: Codable, Sendable, Identifiable {
    public let id: String
    public let label: String?
    public let minSeconds: Int
    public let maxSeconds: Int
    public let priceCredits: Double

    enum CodingKeys: String, CodingKey {
        case id, label
        case minSeconds = "min_seconds"
        case maxSeconds = "max_seconds"
        case priceCredits = "price_credits"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        label = c.opt(String.self, .label)
        minSeconds = c.int(.minSeconds)
        maxSeconds = c.int(.maxSeconds)
        priceCredits = c.amount(.priceCredits)
    }
}

/// Demande de sponsoring du caller (`sponsor_requests`).
public struct SponsorRequest: Codable, Sendable, Identifiable {
    public let id: String
    public let eventType: String?
    public let eventId: String?
    public let mediaType: String?
    public let mediaDurationSeconds: Int?
    /// `pending` | `approved` | `rejected`.
    public let status: String
    public let priceCredits: Double
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case eventType = "event_type"
        case eventId = "event_id"
        case mediaType = "media_type"
        case mediaDurationSeconds = "media_duration_seconds"
        case status
        case priceCredits = "price_credits"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        eventType = c.opt(String.self, .eventType)
        eventId = c.opt(String.self, .eventId)
        mediaType = c.opt(String.self, .mediaType)
        mediaDurationSeconds = c.opt(Int.self, .mediaDurationSeconds)
        status = c.val(String.self, .status, "pending")
        priceCredits = c.amount(.priceCredits)
        createdAt = c.opt(String.self, .createdAt)
    }

    /// Vrai si la demande est approuvée et en attente de paiement.
    public var payable: Bool { status == "approved" }
}

/// Corps de `POST /sponsors/requests` — création d'une demande de sponsoring.
///
/// ⚠️ camelCase STRICT : le backend (Joi `stripUnknown`) supprime silencieusement tout champ
/// hors DTO. Le prix n'est PAS envoyé : il est calculé serveur d'après
/// ``mediaDurationSeconds`` (barème par palier). ``eventType`` ∈
/// `duel|concert|artist_concert|competition`, ``mediaType`` ∈ `image|video`, durée 1…600 s.
public struct CreateSponsorRequest: Encodable, Sendable {
    public let eventType: String
    public let eventId: String
    public let mediaType: String
    public let mediaUrl: String
    public let mediaDurationSeconds: Int
    public let description: String?

    public init(
        eventType: String,
        eventId: String,
        mediaType: String,
        mediaUrl: String,
        mediaDurationSeconds: Int,
        description: String? = nil
    ) {
        self.eventType = eventType
        self.eventId = eventId
        self.mediaType = mediaType
        self.mediaUrl = mediaUrl
        self.mediaDurationSeconds = mediaDurationSeconds
        self.description = description
    }
}

// MARK: - Espace créateur

/// Demande/défi de duel (`duel_requests`).
///
/// Le caller peut en être l'émetteur (``requesterId``) ou le destinataire (``opponentId``).
/// Seul le destinataire peut accepter/refuser (le backend l'impose).
public struct DuelRequestItem: Codable, Sendable, Identifiable {
    public let id: String
    public let requesterId: String?
    public let opponentId: String?
    /// `pending` | `accepted` | `declined`.
    public let status: String
    public let proposedDate: String?
    public let message: String?

    enum CodingKeys: String, CodingKey {
        case id
        case requesterId = "requester_id"
        case opponentId = "opponent_id"
        case status
        case proposedDate = "proposed_date"
        case message
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        requesterId = c.opt(String.self, .requesterId)
        opponentId = c.opt(String.self, .opponentId)
        status = c.val(String.self, .status, "pending")
        proposedDate = c.opt(String.self, .proposedDate)
        message = c.opt(String.self, .message)
    }
}

/// Corps de `POST /duels/requests/:id/respond` — accepter/refuser un défi.
public struct RespondDuelRequest: Encodable, Sendable {
    public let accept: Bool
    public init(accept: Bool) { self.accept = accept }
}

/// Corps de `POST /duels/requests` — envoyer un défi à un autre artiste.
public struct CreateDuelRequest: Encodable, Sendable {
    public let opponentId: String
    public let proposedDate: String?
    public let message: String?
    public init(opponentId: String, proposedDate: String? = nil, message: String? = nil) {
        self.opponentId = opponentId
        self.proposedDate = proposedDate
        self.message = message
    }
}

/// Corps de `POST /artist-concerts` — création d'un concert d'artiste.
///
/// ⚠️ camelCase STRICT (Joi `stripUnknown`). Le concert est créé en
/// `approval_status=pending` (invisible jusqu'à validation admin). ``coverImageUrl`` = URL
/// publique issue de l'upload (catégorie `image`, ≤ 5 Mo). ``scheduledDate`` en ISO 8601.
public struct CreateArtistConcert: Encodable, Sendable {
    public let title: String
    public let description: String?
    public let scheduledDate: String
    public let ticketPrice: Double
    public let maxTickets: Int?
    public let coverImageUrl: String?
    public let allowsDedications: Bool
    public let allowsSponsorAds: Bool

    public init(
        title: String,
        description: String? = nil,
        scheduledDate: String,
        ticketPrice: Double = 0,
        maxTickets: Int? = nil,
        coverImageUrl: String? = nil,
        allowsDedications: Bool = true,
        allowsSponsorAds: Bool = true
    ) {
        self.title = title
        self.description = description
        self.scheduledDate = scheduledDate
        self.ticketPrice = ticketPrice
        self.maxTickets = maxTickets
        self.coverImageUrl = coverImageUrl
        self.allowsDedications = allowsDedications
        self.allowsSponsorAds = allowsSponsorAds
    }
}

// MARK: - Abonnements

/// Offre d'abonnement (`subscription_plans`).
public struct SubscriptionPlan: Codable, Sendable, Identifiable {
    public let id: String
    public let name: String?
    /// `pro` | `premium`.
    public let tier: String?
    public let price: Double
    public let durationDays: Int?
    public let description: String?

    enum CodingKeys: String, CodingKey {
        case id, name, tier, price, description
        case durationDays = "duration_days"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        name = c.opt(String.self, .name)
        tier = c.opt(String.self, .tier)
        price = c.amount(.price)
        durationDays = c.opt(Int.self, .durationDays)
        description = c.opt(String.self, .description)
    }
}

/// Abonnement courant du caller (`GET /subscriptions/me`).
public struct MySubscription: Codable, Sendable {
    public let subscriptionType: String?
    public let isActive: Bool
    public let expiresAt: String?

    enum CodingKeys: String, CodingKey {
        case subscriptionType = "subscription_type"
        case isActive = "is_active"
        case expiresAt = "expires_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        subscriptionType = c.opt(String.self, .subscriptionType)
        isActive = c.bool(.isActive)
        expiresAt = c.opt(String.self, .expiresAt)
    }

    public init(subscriptionType: String? = nil, isActive: Bool = false, expiresAt: String? = nil) {
        self.subscriptionType = subscriptionType
        self.isActive = isActive
        self.expiresAt = expiresAt
    }
}

// MARK: - Contenu

/// Vidéo lifestyle d'un artiste (`lifestyle_videos`).
public struct LifestyleVideo: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String?
    public let description: String?
    public let artistId: String?
    public let artistName: String?
    public let videoURL: String?
    public let thumbnailURL: String?
    public let likesCount: Int
    public let commentsCount: Int
    public let viewsCount: Int

    enum CodingKeys: String, CodingKey {
        case id, title, description
        case artistId = "artist_id"
        case artistName = "artist_name"
        case videoURL = "video_url"
        case thumbnailURL = "thumbnail_url"
        case likesCount = "likes_count"
        case commentsCount = "comments_count"
        case viewsCount = "views_count"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        title = c.opt(String.self, .title)
        description = c.opt(String.self, .description)
        artistId = c.opt(String.self, .artistId)
        artistName = c.opt(String.self, .artistName)
        videoURL = c.opt(String.self, .videoURL)
        thumbnailURL = c.opt(String.self, .thumbnailURL)
        likesCount = c.int(.likesCount)
        commentsCount = c.int(.commentsCount)
        viewsCount = c.int(.viewsCount)
    }
}

/// Article de blog (`blogs`).
public struct BlogPost: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String?
    public let excerpt: String?
    /// Contenu complet (HTML/Markdown) — chargé au détail.
    public let content: String?
    public let category: String?
    public let authorName: String?
    public let imageURL: String?
    public let viewsCount: Int
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, title, excerpt, content, category
        case authorName = "author_name"
        case imageURL = "image_url"
        case viewsCount = "views_count"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        title = c.opt(String.self, .title)
        excerpt = c.opt(String.self, .excerpt)
        content = c.opt(String.self, .content)
        category = c.opt(String.self, .category)
        authorName = c.opt(String.self, .authorName)
        imageURL = c.opt(String.self, .imageURL)
        viewsCount = c.int(.viewsCount)
        createdAt = c.opt(String.self, .createdAt)
    }
}

// MARK: - Parrainage

/// Une personne parrainée (`referrals`).
public struct ReferralItem: Codable, Sendable, Identifiable {
    public let id: String
    public let referredId: String?
    public let rewardClaimed: Bool
    public let createdAt: String?
    /// Profil d'affichage du filleul (hydraté serveur).
    public let referred: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case referredId = "referred_id"
        case rewardClaimed = "reward_claimed"
        case createdAt = "created_at"
        case referred
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        referredId = c.opt(String.self, .referredId)
        rewardClaimed = c.bool(.rewardClaimed)
        createdAt = c.opt(String.self, .createdAt)
        referred = c.opt(DisplayProfile.self, .referred)
    }
}

/// Statistiques de parrainage.
public struct ReferralStats: Codable, Sendable {
    public let total: Int
    public let completed: Int
    public let pendingRewardCredits: Double
    public let rewardCredits: Double

    enum CodingKeys: String, CodingKey { case total, completed, pendingRewardCredits, rewardCredits }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        total = c.int(.total)
        completed = c.int(.completed)
        pendingRewardCredits = c.amount(.pendingRewardCredits)
        rewardCredits = c.amount(.rewardCredits)
    }

    public init(total: Int = 0, completed: Int = 0, pendingRewardCredits: Double = 0, rewardCredits: Double = 0) {
        self.total = total
        self.completed = completed
        self.pendingRewardCredits = pendingRewardCredits
        self.rewardCredits = rewardCredits
    }
}

/// Réponse de `GET /referrals/me`.
public struct MyReferrals: Codable, Sendable {
    public let referralCode: String?
    public let referrals: [ReferralItem]
    public let stats: ReferralStats

    enum CodingKeys: String, CodingKey { case referralCode, referrals, stats }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        referralCode = c.opt(String.self, .referralCode)
        referrals = c.val([ReferralItem].self, .referrals, [])
        stats = c.val(ReferralStats.self, .stats, ReferralStats())
    }

    public init(referralCode: String? = nil, referrals: [ReferralItem] = [], stats: ReferralStats = ReferralStats()) {
        self.referralCode = referralCode
        self.referrals = referrals
        self.stats = stats
    }
}

// MARK: - Notifications

/// Notification in-app (`notifications`).
public struct AppNotification: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String?
    public let message: String?
    /// Type métier (ex. `duel_invite`, `gift_received`) — sert aussi au routage deeplink.
    public let type: String?
    public private(set) var read: Bool
    /// Charge utile libre (cible du deeplink, ids…). `nil` si absente.
    public let data: JSONValue?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, title, message, type, read, data
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        title = c.opt(String.self, .title)
        message = c.opt(String.self, .message)
        type = c.opt(String.self, .type)
        read = c.bool(.read)
        data = c.opt(JSONValue.self, .data)
        createdAt = c.opt(String.self, .createdAt)
    }

    /// Copie marquée comme lue (mise à jour optimiste côté UI).
    /// - Returns: la même notification avec ``read`` à `true`.
    public func markedRead() -> AppNotification {
        var copy = self
        copy.read = true
        return copy
    }
}

/// Réponse de `GET /notifications/unread-count`.
public struct UnreadCount: Codable, Sendable {
    public let count: Int

    enum CodingKeys: String, CodingKey { case count }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        count = c.int(.count)
    }
}

// MARK: - Média (LiveKit)

/// Corps de `POST /livekit/token`.
public struct LiveKitTokenRequest: Encodable, Sendable {
    public let roomName: String
    public let isHost: Bool
    public let participantName: String?
    public let canPublish: Bool

    public init(roomName: String, isHost: Bool = false, participantName: String? = nil, canPublish: Bool = false) {
        self.roomName = roomName
        self.isHost = isHost
        self.participantName = participantName
        self.canPublish = canPublish
    }
}

/// Réponse : jeton d'accès à la room + URL du SFU + identité du participant.
public struct LiveKitToken: Codable, Sendable, Equatable {
    public let token: String
    public let url: String
    public let identity: String

    enum CodingKeys: String, CodingKey { case token, url, identity }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        token = c.val(String.self, .token, "")
        url = c.val(String.self, .url, "")
        identity = c.val(String.self, .identity, "")
    }

    public init(token: String, url: String, identity: String) {
        self.token = token
        self.url = url
        self.identity = identity
    }
}

// MARK: - Upload

/// Catégories de média acceptées par le backend (détermine bucket + contraintes mime/taille).
public enum UploadCategory {
    public static let avatar = "avatar"
    public static let image = "image"
    public static let video = "video"
    public static let lifestyle = "lifestyle"
    public static let replay = "replay"
    public static let sponsor = "sponsor"
    public static let attachment = "attachment"
}

/// Corps de `POST /uploads/presign` (camelCase).
public struct PresignRequest: Encodable, Sendable {
    public let category: String
    public let filename: String
    public let contentType: String
    public let size: Int

    public init(category: String, filename: String, contentType: String, size: Int) {
        self.category = category
        self.filename = filename
        self.contentType = contentType
        self.size = size
    }
}

/// Réponse de `POST /uploads/presign` — URL signée pour le PUT + identifiants de ressource.
public struct PresignResult: Codable, Sendable {
    /// URL signée vers laquelle envoyer les octets en PUT (absolue, **hors API**).
    public let uploadUrl: String
    /// Clé de stockage (à renvoyer à `/uploads/confirm`).
    public let key: String
    /// URL publique finale (si le bucket est public).
    public let publicUrl: String?

    enum CodingKeys: String, CodingKey { case uploadUrl, key, publicUrl }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        uploadUrl = c.val(String.self, .uploadUrl, "")
        key = c.val(String.self, .key, "")
        publicUrl = c.opt(String.self, .publicUrl)
    }
}

/// Corps de `POST /uploads/confirm` — valide l'objet uploadé côté serveur.
public struct ConfirmRequest: Encodable, Sendable {
    public let key: String
    public init(key: String) { self.key = key }
}

/// Réponse de `POST /uploads/confirm` — URL exploitable du média confirmé.
public struct ConfirmResult: Codable, Sendable {
    public let url: String?
    public let publicUrl: String?

    enum CodingKeys: String, CodingKey { case url, publicUrl }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = c.opt(String.self, .url)
        publicUrl = c.opt(String.self, .publicUrl)
    }
}

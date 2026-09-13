import Foundation

/*
 * DTOs des catalogues : compétitions, concerts, replays, cadeaux, classements, artistes.
 * Miroir des paquets `shared-domain/{competition,concert,replay,gift,leaderboard,artist}`.
 */

// MARK: - Compétitions

/// Candidat d'une compétition (`competition_candidates`).
///
/// Les tallies (``totalVotes``, ``totalGiftsCredits``) sont maintenus par les procédures
/// atomiques du backend et alimentent le classement final.
public struct CompetitionCandidate: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let competitionId: String
    public let artistId: String
    /// `pending` | `approved` | `rejected`.
    public let status: String
    public let totalVotes: Double
    public let totalGiftsCredits: Double
    /// Profil d'affichage de l'artiste (hydraté serveur quand disponible).
    public let artist: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case competitionId = "competition_id"
        case artistId = "artist_id"
        case status
        case totalVotes = "total_votes"
        case totalGiftsCredits = "total_gifts_credits"
        case artist
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        competitionId = c.val(String.self, .competitionId, "")
        artistId = c.val(String.self, .artistId, "")
        status = c.val(String.self, .status, "pending")
        totalVotes = c.amount(.totalVotes)
        totalGiftsCredits = c.amount(.totalGiftsCredits)
        artist = c.opt(DisplayProfile.self, .artist)
    }

    /// Score d'engagement affiché (votes + cadeaux).
    public var score: Double { totalVotes + totalGiftsCredits }
}

/// Corps de `POST /competitions/:id/vote` — vote payant pour un candidat.
public struct CompetitionVoteRequest: Encodable, Sendable {
    public let candidateId: String
    public let credits: Int
    public init(candidateId: String, credits: Int) {
        self.candidateId = candidateId
        self.credits = credits
    }
}

/// Corps de `POST /competitions/:id/gifts` — cadeau à un candidat OU au manager.
///
/// Exactement un des deux destinataires doit être fourni (contrainte backend `xor`).
public struct CompetitionGiftRequest: Encodable, Sendable {
    public let candidateId: String?
    public let recipientUserId: String?
    public let giftId: String
    public let credits: Int

    public init(candidateId: String? = nil, recipientUserId: String? = nil, giftId: String, credits: Int) {
        self.candidateId = candidateId
        self.recipientUserId = recipientUserId
        self.giftId = giftId
        self.credits = credits
    }
}

// MARK: - Concerts

/// Infos de billetterie d'un concert (`GET /concerts/:id/ticket-info`).
public struct ConcertTicketInfo: Codable, Sendable {
    /// Prix du billet en crédits.
    public let price: Double
    /// Vrai si le caller possède déjà un billet.
    public let hasTicket: Bool
    /// Vrai si la jauge est atteinte.
    public let soldOut: Bool
    public let remaining: Int?

    enum CodingKeys: String, CodingKey { case price, hasTicket, soldOut, remaining }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        price = c.amount(.price)
        hasTicket = c.bool(.hasTicket)
        soldOut = c.bool(.soldOut)
        remaining = c.opt(Int.self, .remaining)
    }
}

/// Corps d'achat d'une dédicace (`POST /concerts/dedications`).
public struct DedicationRequest: Encodable, Sendable {
    public let concertId: String
    public let concertType: String
    public let message: String
    public let priceCredits: Double

    /// - Parameter priceCredits: **obligatoire** côté backend — l'omettre échoue en 400.
    ///   Absent de ce DTO jusqu'à ce correctif (le flux d'achat de dédicace concert n'a
    ///   jamais été branché à une UI côté iOS, donc jamais exercé).
    public init(concertId: String, concertType: String = "artist_concert", message: String, priceCredits: Double) {
        self.concertId = concertId
        self.concertType = concertType
        self.message = message
        self.priceCredits = priceCredits
    }
}

// MARK: - Replays

/// Rediffusion d'un duel/concert/compétition (`replay_videos`).
public struct ReplayVideo: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String?
    public let description: String?
    public let thumbnailURL: String?
    /// URL de la vidéo (HLS/MP4) — lue par `AVPlayer` une fois l'accès obtenu.
    public let videoURL: String?
    /// Prix de déblocage en crédits (0 = gratuit).
    public let replayPrice: Double
    /// Vrai si l'accès requiert un déblocage payant.
    public let isPremium: Bool
    public let duration: Int?
    public let viewsCount: Int
    public let sourceType: String?

    enum CodingKeys: String, CodingKey {
        case id, title, description, duration
        case thumbnailURL = "thumbnail_url"
        case videoURL = "video_url"
        case replayPrice = "replay_price"
        case isPremium = "is_premium"
        case viewsCount = "views_count"
        case sourceType = "source_type"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        title = c.opt(String.self, .title)
        description = c.opt(String.self, .description)
        thumbnailURL = c.opt(String.self, .thumbnailURL)
        videoURL = c.opt(String.self, .videoURL)
        replayPrice = c.amount(.replayPrice)
        isPremium = c.bool(.isPremium)
        duration = c.opt(Int.self, .duration)
        viewsCount = c.int(.viewsCount)
        sourceType = c.opt(String.self, .sourceType)
    }

    /// Vrai si un déblocage payant est nécessaire (premium **et** prix > 0).
    public var requiresUnlock: Bool { isPremium && replayPrice > 0 }
}

/// Réponse de `GET /replays/:id/access` — le caller a-t-il accès à la vidéo.
public struct ReplayAccess: Codable, Sendable {
    public let hasAccess: Bool

    enum CodingKeys: String, CodingKey { case hasAccess }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hasAccess = c.bool(.hasAccess)
    }
}

/// Corps de `POST /wallet/replays/unlock` — déblocage payant d'un replay.
public struct UnlockReplayRequest: Encodable, Sendable {
    public let replayId: String
    public init(replayId: String) { self.replayId = replayId }
}

// MARK: - Cadeaux

/// Cadeau possédé dans l'inventaire du caller (`GET /gifts/inventory`).
public struct InventoryItem: Codable, Sendable, Identifiable {
    public let giftId: String
    public let quantity: Int
    public let name: String?
    public let price: Double
    public let imageURL: String?

    public var id: String { giftId }

    enum CodingKeys: String, CodingKey {
        case giftId = "gift_id"
        case quantity, name, price
        case imageURL = "image_url"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        giftId = c.val(String.self, .giftId, "")
        quantity = c.int(.quantity)
        name = c.opt(String.self, .name)
        price = c.amount(.price)
        imageURL = c.opt(String.self, .imageURL)
    }
}

// MARK: - Classements

/// Entrée de classement (artiste ou donateur).
public struct LeaderboardEntry: Codable, Sendable, Identifiable {
    public let entryId: String?
    public let userId: String?
    public let fullName: String?
    public let stageName: String?
    public let avatarURL: String?
    public let total: Double

    /// Identité stable pour SwiftUI (id d'entrée, sinon id utilisateur, sinon UUID).
    public var id: String { entryId ?? userId ?? UUID().uuidString }

    enum CodingKeys: String, CodingKey {
        case entryId = "id"
        case userId = "user_id"
        case fullName = "full_name"
        case stageName = "stage_name"
        case avatarURL = "avatar_url"
        case total
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        entryId = c.opt(String.self, .entryId)
        userId = c.opt(String.self, .userId)
        fullName = c.opt(String.self, .fullName)
        stageName = c.opt(String.self, .stageName)
        avatarURL = c.opt(String.self, .avatarURL)
        total = c.amount(.total)
    }

    /// Nom à afficher : nom de scène sinon nom complet sinon repli.
    public var displayName: String {
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return "Utilisateur"
    }
}

/// Saison de classement (onglet « Périodique ») — `GET /leaderboards/seasons`.
public struct LeaderboardSeason: Codable, Sendable, Identifiable {
    public let id: String
    public let name: String
    public let type: String?
    public let startDate: String?
    public let endDate: String?
    public let isActive: Bool
    public let isMysteryReward: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, type
        case startDate = "start_date"
        case endDate = "end_date"
        case isActive = "is_active"
        case isMysteryReward = "is_mystery_reward"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        name = c.val(String.self, .name, "Saison")
        type = c.opt(String.self, .type)
        startDate = c.opt(String.self, .startDate)
        endDate = c.opt(String.self, .endDate)
        isActive = c.bool(.isActive)
        isMysteryReward = c.bool(.isMysteryReward)
    }
}

// MARK: - Artistes

/// Résumé d'artiste pour l'annuaire (`GET /artists`).
public struct ArtistSummary: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    /// Id de l'UTILISATEUR (≠ id du profil artiste) : clé pour défier un adversaire et
    /// s'exclure soi-même — voir ``opponentUserId``.
    public let userId: String?
    public let fullName: String?
    public let stageName: String?
    public let avatarURL: String?
    public let followersCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case fullName = "full_name"
        case stageName = "stage_name"
        case avatarURL = "avatar_url"
        case followersCount = "followers_count"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        userId = c.opt(String.self, .userId)
        fullName = c.opt(String.self, .fullName)
        stageName = c.opt(String.self, .stageName)
        avatarURL = c.opt(String.self, .avatarURL)
        followersCount = c.int(.followersCount)
    }

    /// Nom à afficher : nom de scène sinon nom complet.
    public var displayName: String {
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return "Artiste"
    }

    /// Id utilisateur de l'artiste (repli sur ``id`` si absent) — à utiliser pour défier un
    /// adversaire (`artist1_id`/`artist2_id` attendent un id UTILISATEUR, pas un id de profil).
    public var opponentUserId: String { userId ?? id }
}

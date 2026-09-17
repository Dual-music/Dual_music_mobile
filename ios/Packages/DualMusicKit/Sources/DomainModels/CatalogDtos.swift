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

/// Le caller possède-t-il un billet pour cette compétition ? `GET /competitions/:id/my-ticket`.
public struct CompetitionTicketInfo: Decodable, Sendable {
    public let hasTicket: Bool
    public let count: Int

    enum CodingKeys: String, CodingKey { case hasTicket, count }

    public init(hasTicket: Bool = false, count: Int = 0) {
        self.hasTicket = hasTicket
        self.count = count
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hasTicket = c.bool(.hasTicket, false)
        count = c.int(.count)
    }
}

/// Entrée du classement des donateurs (`GET /leaderboards/gifts`).
public struct CompetitionDonorEntry: Decodable, Sendable, Identifiable {
    public let id: String
    public let userId: String?
    public let fullName: String?
    public let stageName: String?
    public let total: Double
    public let score: Double
    public let user: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case fullName = "full_name"
        case stageName = "stage_name"
        case total, score, user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.opt(String.self, .userId)
        fullName = c.opt(String.self, .fullName)
        stageName = c.opt(String.self, .stageName)
        total = c.amount(.total)
        score = c.amount(.score)
        user = c.opt(DisplayProfile.self, .user)
        id = userId ?? UUID().uuidString
    }

    /// Nom affiché : profil hydraté, sinon nom de scène, sinon nom complet, sinon repli
    /// générique. Pas de ``AppStrings`` ici : `DomainModels` ne dépend pas de `CoreUI`
    /// (dépendance inverse — c'est `CoreUI` qui dépend de `DomainModels`), contrairement à
    /// `DuelDonorEntry` (dans `FeatureDuel`, qui importe déjà `CoreUI`).
    public var displayName: String {
        if let user { return user.displayName }
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return "Donateur"
    }

    public var value: Int { Int(total > 0 ? total : score) }
}

/// Version allégée d'une compétition, embarquée dans ``MyCandidacy``.
public struct CompetitionLite: Decodable, Sendable {
    public let id: String
    public let title: String?
    public let startAt: String?
    public let status: String?
    public let coverURL: String?

    enum CodingKeys: String, CodingKey {
        case id, title, status
        case startAt = "start_at"
        case coverURL = "cover_url"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        title = c.opt(String.self, .title)
        startAt = c.opt(String.self, .startAt)
        status = c.opt(String.self, .status)
        coverURL = c.opt(String.self, .coverURL)
    }
}

/// Candidature du caller (artiste) enrichie de sa compétition — `GET /competitions/candidacies/mine`.
public struct MyCandidacy: Decodable, Sendable, Identifiable {
    public let id: String
    public let competitionId: String
    /// `pending` | `approved` | `rejected`.
    public let status: String
    public let totalVotes: Double
    public let totalGiftsCredits: Double
    public let competition: CompetitionLite?

    enum CodingKeys: String, CodingKey {
        case id
        case competitionId = "competition_id"
        case status
        case totalVotes = "total_votes"
        case totalGiftsCredits = "total_gifts_credits"
        case competition
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        competitionId = c.val(String.self, .competitionId, "")
        status = c.val(String.self, .status, "pending")
        totalVotes = c.amount(.totalVotes)
        totalGiftsCredits = c.amount(.totalGiftsCredits)
        competition = c.opt(CompetitionLite.self, .competition)
    }

    public var score: Double { totalVotes + totalGiftsCredits }
}

/// Corps de `POST /competitions/:id/apply` — auto-candidature de l'artiste (distincte de
/// l'ajout manuel par le manager).
public struct CompetitionApplyRequest: Encodable, Sendable {
    public let pitch: String?
    public let videoDemoUrl: String?

    public init(pitch: String? = nil, videoDemoUrl: String? = nil) {
        self.pitch = pitch
        self.videoDemoUrl = videoDemoUrl
    }
}

/// Entrée de l'annuaire artistes pour l'ajout manuel d'un candidat (walk-in, présentiel).
/// Distinct de ``ArtistSummary`` : `userId` toujours présent (pas d'équivalent
/// ``ArtistSummary/opponentUserId``), forme dédiée au picker manager.
public struct ArtistDirectoryEntry: Decodable, Sendable, Identifiable {
    public let id: String
    public let userId: String
    public let stageName: String?
    public let fullName: String?
    public let avatarURL: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case stageName = "stage_name"
        case fullName = "full_name"
        case avatarURL = "avatar_url"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        userId = c.val(String.self, .userId, "")
        stageName = c.opt(String.self, .stageName)
        fullName = c.opt(String.self, .fullName)
        avatarURL = c.opt(String.self, .avatarURL)
    }

    public var displayName: String {
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return String(userId.prefix(8))
    }
}

/// Corps de `POST /competitions` (création) / `PATCH /competitions/:id` (édition) — parité
/// stricte avec le formulaire web/Android (`CompetitionForm`). Les champs `nil` sont omis à
/// l'encodage : le backend applique ses propres défauts.
public struct CreateCompetitionBody: Encodable, Sendable {
    public let managerId: String
    public let title: String
    public let description: String?
    public let coverUrl: String?
    public let mode: String
    public let maxCandidates: Int
    public let rewardDescription: String?
    public let rewardAmount: Double
    public let entryFeeRequired: Bool
    public let entryFeeAmount: Double
    /// Le manager accepte-t-il les sponsors (défaut oui).
    public let acceptsSponsors: Bool
    public let sponsorSubmissionDeadline: String?
    public let eligibilityScope: String
    public let eligibleCountries: [String]
    // Présentiel (onsite) — laisser vide en ligne.
    public let country: String?
    public let city: String?
    public let commune: String?
    public let district: String?
    public let venueName: String?
    public let venueAddress: String?
    public let venueContact: String?
    // Dates (ISO). `applicationOpensAt` optionnel.
    public let applicationOpensAt: String?
    public let applicationDeadline: String?
    public let startAt: String?
    public let endAt: String?
    public let status: String

    public init(
        managerId: String,
        title: String,
        description: String? = nil,
        coverUrl: String? = nil,
        mode: String = "online",
        maxCandidates: Int = 10,
        rewardDescription: String? = nil,
        rewardAmount: Double = 0,
        entryFeeRequired: Bool = false,
        entryFeeAmount: Double = 0,
        acceptsSponsors: Bool = true,
        sponsorSubmissionDeadline: String? = nil,
        eligibilityScope: String = "country",
        eligibleCountries: [String] = [],
        country: String? = nil,
        city: String? = nil,
        commune: String? = nil,
        district: String? = nil,
        venueName: String? = nil,
        venueAddress: String? = nil,
        venueContact: String? = nil,
        applicationOpensAt: String? = nil,
        applicationDeadline: String? = nil,
        startAt: String? = nil,
        endAt: String? = nil,
        status: String = "open"
    ) {
        self.managerId = managerId
        self.title = title
        self.description = description
        self.coverUrl = coverUrl
        self.mode = mode
        self.maxCandidates = maxCandidates
        self.rewardDescription = rewardDescription
        self.rewardAmount = rewardAmount
        self.entryFeeRequired = entryFeeRequired
        self.entryFeeAmount = entryFeeAmount
        self.acceptsSponsors = acceptsSponsors
        self.sponsorSubmissionDeadline = sponsorSubmissionDeadline
        self.eligibilityScope = eligibilityScope
        self.eligibleCountries = eligibleCountries
        self.country = country
        self.city = city
        self.commune = commune
        self.district = district
        self.venueName = venueName
        self.venueAddress = venueAddress
        self.venueContact = venueContact
        self.applicationOpensAt = applicationOpensAt
        self.applicationDeadline = applicationDeadline
        self.startAt = startAt
        self.endAt = endAt
        self.status = status
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
    /// Vrai si visible sur la page publique des replays (réglable par le propriétaire).
    public let isPublic: Bool
    public let duration: Int?
    public let viewsCount: Int
    public let sourceType: String?
    public let artistId: String?
    /// Créateur du replay (peut différer de ``artistId`` — ex. un manager de duel/compétition).
    public let createdBy: String?

    enum CodingKeys: String, CodingKey {
        case id, title, description, duration
        case thumbnailURL = "thumbnail_url"
        case videoURL = "video_url"
        case replayPrice = "replay_price"
        case isPremium = "is_premium"
        case isPublic = "is_public"
        case viewsCount = "views_count"
        case sourceType = "source_type"
        case artistId = "artist_id"
        case createdBy = "created_by"
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
        isPublic = c.val(Bool.self, .isPublic, true)
        duration = c.opt(Int.self, .duration)
        viewsCount = c.int(.viewsCount)
        sourceType = c.opt(String.self, .sourceType)
        artistId = c.opt(String.self, .artistId)
        createdBy = c.opt(String.self, .createdBy)
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

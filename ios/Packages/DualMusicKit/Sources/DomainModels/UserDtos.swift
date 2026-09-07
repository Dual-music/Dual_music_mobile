import Foundation

/// Statistiques de l'utilisateur (`GET /users/me/stats`).
///
/// Regroupe trois facettes selon les rôles : artiste, manager, fan. Toutes les valeurs
/// sont calculées côté serveur. Miroir de `shared-domain/user/UserDtos.kt`.
public struct UserStats: Codable, Sendable {
    public let artistStats: ArtistStats
    public let managerStats: ManagerStats
    public let fanStats: FanStats

    enum CodingKeys: String, CodingKey { case artistStats, managerStats, fanStats }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        artistStats = c.val(ArtistStats.self, .artistStats, ArtistStats())
        managerStats = c.val(ManagerStats.self, .managerStats, ManagerStats())
        fanStats = c.val(FanStats.self, .fanStats, FanStats())
    }

    public init(
        artistStats: ArtistStats = ArtistStats(),
        managerStats: ManagerStats = ManagerStats(),
        fanStats: FanStats = FanStats()
    ) {
        self.artistStats = artistStats
        self.managerStats = managerStats
        self.fanStats = fanStats
    }
}

/// Facette artiste : votes/cadeaux reçus, duels joués/gagnés.
public struct ArtistStats: Codable, Sendable {
    public let totalVotes: Double
    public let totalGifts: Int
    public let totalDuels: Int
    public let wonDuels: Int

    enum CodingKeys: String, CodingKey { case totalVotes, totalGifts, totalDuels, wonDuels }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        totalVotes = c.amount(.totalVotes)
        totalGifts = c.int(.totalGifts)
        totalDuels = c.int(.totalDuels)
        wonDuels = c.int(.wonDuels)
    }

    public init(totalVotes: Double = 0, totalGifts: Int = 0, totalDuels: Int = 0, wonDuels: Int = 0) {
        self.totalVotes = totalVotes
        self.totalGifts = totalGifts
        self.totalDuels = totalDuels
        self.wonDuels = wonDuels
    }
}

/// Facette manager : duels gérés, en cours, cadeaux reçus.
public struct ManagerStats: Codable, Sendable {
    public let totalDuelsManaged: Int
    public let activeDuels: Int
    public let totalGiftsReceived: Int

    enum CodingKeys: String, CodingKey { case totalDuelsManaged, activeDuels, totalGiftsReceived }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        totalDuelsManaged = c.int(.totalDuelsManaged)
        activeDuels = c.int(.activeDuels)
        totalGiftsReceived = c.int(.totalGiftsReceived)
    }

    public init(totalDuelsManaged: Int = 0, activeDuels: Int = 0, totalGiftsReceived: Int = 0) {
        self.totalDuelsManaged = totalDuelsManaged
        self.activeDuels = activeDuels
        self.totalGiftsReceived = totalGiftsReceived
    }
}

/// Facette fan : votes émis, cadeaux envoyés, billets achetés.
public struct FanStats: Codable, Sendable {
    public let totalVotesCast: Double
    public let totalGiftsSent: Int
    public let totalTickets: Int

    enum CodingKeys: String, CodingKey { case totalVotesCast, totalGiftsSent, totalTickets }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        totalVotesCast = c.amount(.totalVotesCast)
        totalGiftsSent = c.int(.totalGiftsSent)
        totalTickets = c.int(.totalTickets)
    }

    public init(totalVotesCast: Double = 0, totalGiftsSent: Int = 0, totalTickets: Int = 0) {
        self.totalVotesCast = totalVotesCast
        self.totalGiftsSent = totalGiftsSent
        self.totalTickets = totalTickets
    }
}

/// Corps de `PATCH /users/me` — mise à jour du profil.
///
/// ⚠️ **snake_case** (le backend attend ces noms exacts, contrairement aux autres DTOs de
/// requête). Tous les champs sont optionnels : n'envoyer que ceux modifiés — les `nil` sont
/// omis à l'encodage grâce à `encodeIfPresent`.
public struct UpdateProfileRequest: Encodable, Sendable {
    public let fullName: String?
    public let countryCode: String?
    public let phone: String?
    public let phoneCountryCode: String?
    public let bio: String?
    public let avatarURL: String?

    enum CodingKeys: String, CodingKey {
        case fullName = "full_name"
        case countryCode = "country_code"
        case phone
        case phoneCountryCode = "phone_country_code"
        case bio
        case avatarURL = "avatar_url"
    }

    public init(
        fullName: String? = nil,
        countryCode: String? = nil,
        phone: String? = nil,
        phoneCountryCode: String? = nil,
        bio: String? = nil,
        avatarURL: String? = nil
    ) {
        self.fullName = fullName
        self.countryCode = countryCode
        self.phone = phone
        self.phoneCountryCode = phoneCountryCode
        self.bio = bio
        self.avatarURL = avatarURL
    }

    /// Encodage explicite : les champs `nil` sont **omis** (et non envoyés en `null`), pour
    /// que le backend ne réinitialise pas les valeurs non modifiées.
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(fullName, forKey: .fullName)
        try c.encodeIfPresent(countryCode, forKey: .countryCode)
        try c.encodeIfPresent(phone, forKey: .phone)
        try c.encodeIfPresent(phoneCountryCode, forKey: .phoneCountryCode)
        try c.encodeIfPresent(bio, forKey: .bio)
        try c.encodeIfPresent(avatarURL, forKey: .avatarURL)
    }
}

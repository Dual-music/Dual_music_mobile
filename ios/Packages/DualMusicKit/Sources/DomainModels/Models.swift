import Foundation

/*
 * Modèles de domaine partagés — miroir Swift de `shared-domain/model/Models.kt`.
 *
 * Les champs suivent le nommage `snake_case` du backend (mappé par `CodingKeys`) pour un
 * décodage direct de l'enveloppe `data`. La spec `openapi/openapi.json` fait foi sur les
 * champs exacts ; tout ajout se fait ICI d'abord, puis côté Android.
 */

/// Profil d'affichage minimal d'un utilisateur (repris partout : listes, chat, cadeaux).
public struct DisplayProfile: Codable, Sendable, Identifiable, Equatable, Hashable {
    public let id: String
    public let fullName: String?
    public let avatarURL: String?
    /// Nom de scène (artistes). Peut être absent → retomber sur ``fullName``.
    public let stageName: String?
    public let countryCode: String?
    public let phone: String?
    public let phoneCountryCode: String?

    enum CodingKeys: String, CodingKey {
        case id
        case fullName = "full_name"
        case avatarURL = "avatar_url"
        case stageName = "stage_name"
        case countryCode = "country_code"
        case phone
        case phoneCountryCode = "phone_country_code"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        fullName = c.opt(String.self, .fullName)
        avatarURL = c.opt(String.self, .avatarURL)
        stageName = c.opt(String.self, .stageName)
        countryCode = c.opt(String.self, .countryCode)
        phone = c.opt(String.self, .phone)
        phoneCountryCode = c.opt(String.self, .phoneCountryCode)
    }

    public init(
        id: String,
        fullName: String? = nil,
        avatarURL: String? = nil,
        stageName: String? = nil,
        countryCode: String? = nil,
        phone: String? = nil,
        phoneCountryCode: String? = nil
    ) {
        self.id = id
        self.fullName = fullName
        self.avatarURL = avatarURL
        self.stageName = stageName
        self.countryCode = countryCode
        self.phone = phone
        self.phoneCountryCode = phoneCountryCode
    }

    /// Nom à afficher : nom de scène si présent, sinon nom complet, sinon repli court.
    public var displayName: String {
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return "Utilisateur"
    }
}

/// Solde du portefeuille de crédits (`GET /wallet` version « brute »).
public struct Wallet: Codable, Sendable {
    public let userId: String
    /// Solde en crédits (unité interne ; 1 crédit = 0,50 € — invariant métier).
    public let balance: Double
    public let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case balance
        case updatedAt = "updated_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.val(String.self, .userId, "")
        balance = c.amount(.balance)
        updatedAt = c.opt(String.self, .updatedAt)
    }
}

/// Cadeau virtuel du catalogue.
public struct VirtualGift: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let name: String
    public let emoji: String?
    /// Prix en crédits.
    public let price: Double
    public let imageURL: String?
    public let isActive: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, emoji, price
        case imageURL = "image_url"
        case isActive = "is_active"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        name = c.val(String.self, .name, "Cadeau")
        emoji = c.opt(String.self, .emoji)
        price = c.amount(.price)
        imageURL = c.opt(String.self, .imageURL)
        isActive = c.bool(.isActive, true)
    }
}

/// Duel 1v1 entre deux artistes, avec votes payants et cadeaux.
public struct Duel: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let artist1Id: String
    public let artist2Id: String
    public let managerId: String?
    public let status: EventStatus
    public let winnerId: String?
    public let scheduledTime: String?
    public let ticketPrice: Double
    /// État du minuteur en cours (persisté pour les arrivants tardifs).
    public let currentTimerEndsAt: String?
    public let currentTimerTargetId: String?
    public let roomId: String?
    /// L'organisateur autorise-t-il la diffusion de pubs sponsor sur ce duel.
    public let allowsSponsorAds: Bool
    /// Le manager (hôte) a-t-il activé le chat pour ce duel (défaut vrai côté backend).
    public let chatEnabled: Bool
    /// Profils hydratés côté serveur (peuvent être absents selon l'endpoint).
    public let artist1: DisplayProfile?
    public let artist2: DisplayProfile?
    public let manager: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case artist1Id = "artist1_id"
        case artist2Id = "artist2_id"
        case managerId = "manager_id"
        case status
        case winnerId = "winner_id"
        case scheduledTime = "scheduled_time"
        case ticketPrice = "ticket_price"
        case currentTimerEndsAt = "current_timer_ends_at"
        case currentTimerTargetId = "current_timer_target_id"
        case roomId = "room_id"
        case allowsSponsorAds = "allows_sponsor_ads"
        case chatEnabled = "chat_enabled"
        case artist1, artist2, manager
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        artist1Id = c.val(String.self, .artist1Id, "")
        artist2Id = c.val(String.self, .artist2Id, "")
        managerId = c.opt(String.self, .managerId)
        status = c.val(EventStatus.self, .status, .upcoming)
        winnerId = c.opt(String.self, .winnerId)
        scheduledTime = c.opt(String.self, .scheduledTime)
        ticketPrice = c.amount(.ticketPrice)
        currentTimerEndsAt = c.opt(String.self, .currentTimerEndsAt)
        currentTimerTargetId = c.opt(String.self, .currentTimerTargetId)
        roomId = c.opt(String.self, .roomId)
        allowsSponsorAds = c.bool(.allowsSponsorAds, true)
        chatEnabled = c.bool(.chatEnabled, true)
        artist1 = c.opt(DisplayProfile.self, .artist1)
        artist2 = c.opt(DisplayProfile.self, .artist2)
        manager = c.opt(DisplayProfile.self, .manager)
    }

    /// Room LiveKit effective (repli sur `duel:id` si `room_id` est absent) — même règle
    /// que le conteneur d'injection Android.
    /// Room LiveKit de BASE — les rooms réelles où l'on publie/écoute sont
    /// `"<liveKitRoom>-artist1"`/`"-artist2"`/`"-manager"` (voir le mode diffusion multi-slot).
    ///
    /// ⚠️ Le tiret compte : le backend/Android dérivent `"duel-$id"` (tiret) — le repli
    /// utilisait `"duel:\(id)"` (deux-points) avant ce correctif, ce qui aurait empêché de
    /// rejoindre les bonnes rooms LiveKit pour tout duel sans `room_id` explicite (bug jamais
    /// exercé jusqu'ici : aucune room de duel n'était rejointe en pratique avant ce chantier).
    public var liveKitRoom: String { roomId ?? "duel-\(id)" }
}

/// Total de votes (crédits) par artiste pour un duel.
public struct DuelVoteTotal: Codable, Sendable {
    public let duelId: String?
    public let artistId: String
    public let total: Double

    enum CodingKeys: String, CodingKey {
        case duelId = "duel_id"
        case artistId = "artist_id"
        case total
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        duelId = c.opt(String.self, .duelId)
        artistId = c.val(String.self, .artistId, "")
        total = c.amount(.total)
    }
}

/// Concert d'artiste (billetterie + dédicaces).
public struct Concert: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let artistId: String
    public let title: String
    public let description: String?
    public let status: EventStatus
    public let scheduledDate: String?
    public let scheduledTime: String?
    public let ticketPrice: Double
    public let allowsDedications: Bool
    /// L'artiste (hôte) autorise-t-il la diffusion de pubs sponsor sur ce concert.
    public let allowsSponsorAds: Bool
    public let sponsorSubmissionDeadline: String?
    public let dedicationSubmissionDeadline: String?
    public let coverImageURL: String?
    public let imageURL: String?
    public let location: String?
    public let artistName: String?
    public let artist: DisplayProfile?
    public let isArtistConcert: Bool
    public let recordingURL: String?
    public let ticketsSold: Int
    public let revenue: Double
    public let maxTickets: Int?
    public let approvalStatus: String?
    /// L'artiste (hôte) a-t-il activé le chat pour ce concert (défaut vrai côté backend).
    public let chatEnabled: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case artistId = "artist_id"
        case title, description, status
        case scheduledDate = "scheduled_date"
        case scheduledTime = "scheduled_time"
        case ticketPrice = "ticket_price"
        case allowsDedications = "allows_dedications"
        case allowsSponsorAds = "allows_sponsor_ads"
        case sponsorSubmissionDeadline = "sponsor_submission_deadline"
        case dedicationSubmissionDeadline = "dedication_submission_deadline"
        case coverImageURL = "cover_image_url"
        case imageURL = "image_url"
        case location
        case artistName = "artist_name"
        case artist
        case isArtistConcert = "is_artist_concert"
        case recordingURL = "recording_url"
        case ticketsSold = "tickets_sold"
        case revenue
        case maxTickets = "max_tickets"
        case approvalStatus = "approval_status"
        case chatEnabled = "chat_enabled"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        artistId = c.val(String.self, .artistId, "")
        title = c.val(String.self, .title, "Concert")
        description = c.opt(String.self, .description)
        status = c.val(EventStatus.self, .status, .upcoming)
        scheduledDate = c.opt(String.self, .scheduledDate)
        scheduledTime = c.opt(String.self, .scheduledTime)
        ticketPrice = c.amount(.ticketPrice)
        allowsDedications = c.bool(.allowsDedications)
        allowsSponsorAds = c.bool(.allowsSponsorAds)
        sponsorSubmissionDeadline = c.opt(String.self, .sponsorSubmissionDeadline)
        dedicationSubmissionDeadline = c.opt(String.self, .dedicationSubmissionDeadline)
        coverImageURL = c.opt(String.self, .coverImageURL)
        imageURL = c.opt(String.self, .imageURL)
        location = c.opt(String.self, .location)
        artistName = c.opt(String.self, .artistName)
        artist = c.opt(DisplayProfile.self, .artist)
        isArtistConcert = c.bool(.isArtistConcert)
        recordingURL = c.opt(String.self, .recordingURL)
        ticketsSold = c.val(Int.self, .ticketsSold, 0)
        revenue = c.amount(.revenue)
        maxTickets = c.opt(Int.self, .maxTickets)
        approvalStatus = c.opt(String.self, .approvalStatus)
        chatEnabled = c.bool(.chatEnabled, true)
    }

    /// Room LiveKit : toujours dérivée, jamais de `room_id` backend pour ce type d'évènement
    /// (contrairement à Live/Duel) — miroir exact d'Android (`"concert-$id"`, tiret).
    public var liveKitRoom: String { "concert-\(id)" }

    /// Image de couverture effective (`cover_image_url` artiste OU `image_url` admin) —
    /// miroir exact du getter `cover` d'Android.
    public var cover: String? { coverImageURL ?? imageURL }
}

/// Compétition (candidats, votes, cadeaux, classement).
public struct Competition: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String
    public let description: String?
    /// Statut libre côté backend (`draft`, `open`, `running`, `ended`…).
    public let status: String
    public let managerId: String?
    /// `"online"` (les candidats approuvés diffusent leur caméra, façon meet) ou `"onsite"`
    /// (présentiel : seul l'appareil du manager filme la scène). Pilote qui peut publier.
    public let mode: String?
    /// Room LiveKit — override backend, repli `"comp-<id>"` (voir ``liveKitRoom``).
    public let livekitRoom: String?
    /// Caméra épinglée par le manager (identité LiveKit = userId) — focus imposé au chargement.
    public let forcedFocusParticipantId: String?
    public let maxCandidates: Int?
    public let startAt: String?
    public let endAt: String?
    public let rewardAmount: Double
    public let rewardDescription: String?
    /// Prix du billet spectateur en crédits (0 = gratuit).
    public let viewerTicketPrice: Double
    /// Vrai si l'accès public est payant (miroir `isPublicPaid` — distinct de `entryFeeRequired`,
    /// qui concerne les CANDIDATS, pas les spectateurs).
    public let isPublicPaid: Bool
    public let entryFeeRequired: Bool
    public let entryFeeAmount: Double
    public let applicationOpensAt: String?
    public let applicationDeadline: String?
    public let coverURL: String?
    public let country: String?
    public let city: String?
    public let commune: String?
    public let district: String?
    public let venueName: String?
    public let venueAddress: String?
    public let venueContact: String?
    public let acceptsSponsors: Bool
    public let sponsorSubmissionDeadline: String?
    /// Le manager (hôte) a-t-il activé le chat pour cette compétition (défaut vrai côté backend).
    public let chatEnabled: Bool

    enum CodingKeys: String, CodingKey {
        case id, title, description, status, mode, country, city, commune, district
        case managerId = "manager_id"
        case livekitRoom = "livekit_room"
        case forcedFocusParticipantId = "forced_focus_participant_id"
        case maxCandidates = "max_candidates"
        case startAt = "start_at"
        case endAt = "end_at"
        case rewardAmount = "reward_amount"
        case rewardDescription = "reward_description"
        case viewerTicketPrice = "viewer_ticket_price"
        case isPublicPaid = "is_public_paid"
        case entryFeeRequired = "entry_fee_required"
        case entryFeeAmount = "entry_fee_amount"
        case applicationOpensAt = "application_opens_at"
        case applicationDeadline = "application_deadline"
        case coverURL = "cover_url"
        case venueName = "venue_name"
        case venueAddress = "venue_address"
        case venueContact = "venue_contact"
        case acceptsSponsors = "accepts_sponsors"
        case sponsorSubmissionDeadline = "sponsor_submission_deadline"
        case chatEnabled = "chat_enabled"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        title = c.val(String.self, .title, "Compétition")
        description = c.opt(String.self, .description)
        status = c.val(String.self, .status, "")
        managerId = c.opt(String.self, .managerId)
        mode = c.opt(String.self, .mode)
        livekitRoom = c.opt(String.self, .livekitRoom)
        forcedFocusParticipantId = c.opt(String.self, .forcedFocusParticipantId)
        maxCandidates = c.opt(Int.self, .maxCandidates)
        startAt = c.opt(String.self, .startAt)
        endAt = c.opt(String.self, .endAt)
        rewardAmount = c.amount(.rewardAmount)
        rewardDescription = c.opt(String.self, .rewardDescription)
        viewerTicketPrice = c.amount(.viewerTicketPrice)
        isPublicPaid = c.bool(.isPublicPaid, false)
        entryFeeRequired = c.bool(.entryFeeRequired, false)
        entryFeeAmount = c.amount(.entryFeeAmount)
        applicationOpensAt = c.opt(String.self, .applicationOpensAt)
        applicationDeadline = c.opt(String.self, .applicationDeadline)
        coverURL = c.opt(String.self, .coverURL)
        country = c.opt(String.self, .country)
        city = c.opt(String.self, .city)
        commune = c.opt(String.self, .commune)
        district = c.opt(String.self, .district)
        venueName = c.opt(String.self, .venueName)
        venueAddress = c.opt(String.self, .venueAddress)
        venueContact = c.opt(String.self, .venueContact)
        acceptsSponsors = c.bool(.acceptsSponsors, true)
        sponsorSubmissionDeadline = c.opt(String.self, .sponsorSubmissionDeadline)
        chatEnabled = c.bool(.chatEnabled, true)
    }

    /// Room LiveKit effective (repli sur `"comp-<id>"` si `livekit_room` est absent, tiret —
    /// miroir Android).
    public var liveKitRoom: String { livekitRoom?.isEmpty == false ? livekitRoom! : "comp-\(id)" }
}

/// Demande de retrait de crédits par un artiste/manager.
public struct WithdrawalRequest: Codable, Sendable, Identifiable {
    public let id: String
    public let userId: String
    public let amount: Double
    public let status: WithdrawalStatus
    public let paymentMethod: String?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case amount, status
        case paymentMethod = "payment_method"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        userId = c.val(String.self, .userId, "")
        amount = c.amount(.amount)
        status = c.val(WithdrawalStatus.self, .status, .pending)
        paymentMethod = c.opt(String.self, .paymentMethod)
        createdAt = c.opt(String.self, .createdAt)
    }
}

/// Aperçu net d'un retrait (`POST /withdrawals/net`). **Montants faisant foi.**
public struct WithdrawalNet: Codable, Sendable {
    public let feePct: Double
    public let fee: Double
    public let net: Double

    enum CodingKeys: String, CodingKey {
        case feePct = "fee_pct"
        case fee, net
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        feePct = c.amount(.feePct)
        fee = c.amount(.fee)
        net = c.amount(.net)
    }
}

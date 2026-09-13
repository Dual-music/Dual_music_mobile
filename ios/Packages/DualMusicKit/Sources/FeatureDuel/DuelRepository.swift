import Foundation
import CoreNetwork
import CoreUI
import DomainModels

/// Message de chat d'un duel (auteur hydraté par le backend).
public struct DuelChatMessage: Decodable, Sendable, Identifiable, Equatable {
    public let messageId: String?
    public let userId: String
    public let content: String
    public let user: DisplayProfile?
    /// Id du message parent si ce message est une RÉPONSE (résolu côté UI pour la citation).
    public let parentId: String?

    /// Identité stable pour SwiftUI.
    public let id: String

    enum CodingKeys: String, CodingKey {
        case messageId = "id"
        case userId = "user_id"
        case content
        case user
        case parentId = "parent_id"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        messageId = c.opt(String.self, .messageId)
        userId = c.val(String.self, .userId, "")
        content = c.val(String.self, .content, "")
        user = c.opt(DisplayProfile.self, .user)
        parentId = c.opt(String.self, .parentId)
        id = messageId ?? UUID().uuidString
    }

    public init(id: String? = nil, userId: String, content: String, user: DisplayProfile? = nil, parentId: String? = nil) {
        self.messageId = id
        self.userId = userId
        self.content = content
        self.user = user
        self.parentId = parentId
        self.id = id ?? UUID().uuidString
    }

    /// Nom d'auteur affiché (repli « Fan »).
    @MainActor
    public var authorName: String { user?.displayName ?? AppStrings.current.fan }
}

/// Corps de `POST /duels/:id/messages` — `parentId` présent = RÉPONSE à ce message.
struct DuelMessageBody: Encodable, Sendable {
    let content: String
    let parentId: String?

    enum CodingKeys: String, CodingKey { case content, parentId }

    /// Encodage manuel : `parentId` est **omis** (pas envoyé `null`) quand ce n'est pas une
    /// réponse — le backend n'attend la clé que pour un vrai fil de discussion.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(content, forKey: .content)
        try c.encodeIfPresent(parentId, forKey: .parentId)
    }
}

/// Le caller possède-t-il un billet pour ce duel ? `GET /duels/:id/my-ticket`.
public struct DuelTicketInfo: Decodable, Sendable {
    public let hasTicket: Bool
    public let count: Int

    enum CodingKeys: String, CodingKey { case hasTicket, count }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hasTicket = c.bool(.hasTicket, false)
        count = c.int(.count)
    }
}

/// Entrée du classement des donateurs (`GET /leaderboards/gifts`).
public struct DuelDonorEntry: Decodable, Sendable, Identifiable {
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
        // Identité STABLE (générée une seule fois) : `userId` peut être absent selon
        // l'endpoint, un id recalculé à chaque accès casserait le diffing SwiftUI (`ForEach`).
        id = userId ?? UUID().uuidString
    }

    /// Nom affiché : profil hydraté, sinon nom de scène, sinon nom complet, sinon repli générique.
    @MainActor
    public var displayName: String {
        if let user { return user.displayName }
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return AppStrings.current.donors
    }

    /// Valeur affichée (crédits) : `total` si positif, sinon repli sur `score`.
    public var value: Int { Int(total > 0 ? total : score) }
}

/// Corps de `POST /wallet/tickets/duel`.
struct BuyDuelTicketBody: Encodable, Sendable {
    let duelId: String
}

/// Corps de `PATCH /duels/:id` — changement de statut (fin de duel).
struct DuelStatusBody: Encodable, Sendable {
    let status: String
}

/// Corps de `PATCH /duels/:id` — annonce du vainqueur (ne termine pas le duel).
struct DuelWinnerBody: Encodable, Sendable {
    let winnerId: String
}

/// Corps de `PATCH /duels/:id` — chat activé/désactivé pour tous (manager).
struct DuelChatBody: Encodable, Sendable {
    let chatEnabled: Bool
}

/// Corps de `PATCH /duels/:id` — minuteur de parole (start/stop). Encodage MANUEL : à l'arrêt,
/// les deux champs doivent être envoyés `null` explicitement (pas omis), sinon le backend ne
/// sait pas qu'il faut effacer le minuteur en cours (le synthétisé `Encodable` de Swift OMET
/// silencieusement une propriété optionnelle `nil`, il ne l'encode jamais en `null`).
struct DuelTimerBody: Encodable, Sendable {
    let currentTimerEndsAt: String?
    let currentTimerTargetId: String?

    enum CodingKeys: String, CodingKey { case currentTimerEndsAt, currentTimerTargetId }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(currentTimerEndsAt, forKey: .currentTimerEndsAt)
        try c.encode(currentTimerTargetId, forKey: .currentTimerTargetId)
    }
}

/// Corps de `POST /duels` — création (le manager s'assigne arbitre).
struct CreateDuelBody: Encodable, Sendable {
    let artist1Id: String
    let artist2Id: String
    let scheduledTime: String?
    let managerId: String
    let status = "upcoming"

    enum CodingKeys: String, CodingKey {
        case artist1Id = "artist1_id"
        case artist2Id = "artist2_id"
        case scheduledTime = "scheduled_time"
        case managerId = "manager_id"
        case status
    }

    /// Encodage manuel : `scheduledTime` doit rester **absent** si `nil` (repli backend sur
    /// aucune date), contrairement au minuteur — ici `encodeIfPresent` est le bon choix.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(artist1Id, forKey: .artist1Id)
        try c.encode(artist2Id, forKey: .artist2Id)
        try c.encodeIfPresent(scheduledTime, forKey: .scheduledTime)
        try c.encode(managerId, forKey: .managerId)
        try c.encode(status, forKey: .status)
    }
}

/// Réglage public `manager_duel_creation` : `GET /settings/public/manager_duel_creation`.
private struct DuelPublicSetting: Decodable, Sendable {
    let value: DuelPublicSettingValue?
}
private struct DuelPublicSettingValue: Decodable, Sendable {
    let enabled: Bool
}

/// Réglage public `vote_config` : `GET /settings/public/vote_config`.
private struct VoteConfigSetting: Decodable, Sendable {
    let value: VoteConfigValue?
}
private struct VoteConfigSettingValue: Decodable, Sendable {
    let pricePerVote: Double
    enum CodingKeys: String, CodingKey { case pricePerVote = "price_per_vote" }
}
private typealias VoteConfigValue = VoteConfigSettingValue

/// Corps de `POST /moderation/reports/live` (live/duel/concert, distingués par `streamType`).
struct ReportStreamBody: Encodable, Sendable {
    let liveId: String
    let streamType: String
    let reason: String
}

/// Corps de `POST /moderation/stream-bans` (live/duel/concert, distingués par `streamType`).
struct StreamBanBody: Encodable, Sendable {
    let streamId: String
    let streamType: String
    let bannedUserId: String
    let reason: String?
}

/// Accès REST au catalogue et à l'état d'un duel.
///
/// Le temps réel (votes, minuteur, statut, cadeaux, chat) arrive par Socket.IO ; ce
/// repository couvre le chargement initial + les actions non financières.
/// ⚠️ Le **vote payant** n'est pas ici : c'est un débit, géré par `FeatureWallet`.
public struct DuelRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Liste des duels.
    /// - Parameters:
    ///   - status: filtre optionnel (`upcoming` | `live` | `ended`).
    ///   - limit: taille de page.
    public func duels(status: String? = nil, limit: Int = 50) async throws -> [Duel] {
        var query = ["limit": String(limit)]
        if let status { query["status"] = status }
        return try await http.request(.get(DuelEndpoints.list, query: query), as: [Duel].self)
    }

    /// Détail d'un duel (artistes hydratés).
    public func duel(id: String) async throws -> Duel {
        try await http.request(.get(DuelEndpoints.detail(id)), as: Duel.self)
    }

    /// Tallies de votes du duel : total de crédits par artiste.
    public func voteTotals(id: String) async throws -> [DuelVoteTotal] {
        try await http.request(.get(DuelEndpoints.votes(id)), as: [DuelVoteTotal].self)
    }

    /// Historique de chat (dernière page) pour amorcer l'overlay.
    public func chatHistory(duelId: String) async throws -> [DuelChatMessage] {
        try await http.request(
            .get(DuelEndpoints.messages(duelId), query: ["limit": "50"]),
            as: [DuelChatMessage].self
        )
    }

    /// Poste un message — ou une RÉPONSE si `parentId` (le backend le diffuse ensuite via
    /// Socket.IO).
    public func postMessage(duelId: String, content: String, parentId: String? = nil) async throws {
        try await http.send(.post(DuelEndpoints.messages(duelId), body: DuelMessageBody(content: content, parentId: parentId)))
    }

    /// Signale ce duel à la modération.
    public func reportLive(liveId: String, reason: ReportReason) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.reportsLive,
                body: ReportStreamBody(liveId: liveId, streamType: "duel", reason: reason.rawValue)
            )
        )
    }

    /// Bannit un spectateur (manager uniquement) : il ne peut plus écrire ni rejoindre.
    public func createStreamBan(streamId: String, bannedUserId: String, reason: String?) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.streamBans,
                body: StreamBanBody(streamId: streamId, streamType: "duel", bannedUserId: bannedUserId, reason: reason)
            )
        )
    }

    /// Spectateurs déjà bannis de ce duel (ids) — amorce l'affichage pour un arrivant tardif.
    /// Best-effort : une erreur réseau donne juste une liste vide, jamais un throw bloquant.
    public func listStreamBans(duelId: String) async -> [String] {
        let rows = (try? await http.request(
            .get(ModerationReportEndpoints.streamBans, query: ["streamId": duelId, "streamType": "duel"]),
            as: [StreamBanRow].self
        )) ?? []
        return rows.compactMap(\.bannedUserId)
    }

    /// Spectateurs actuellement connectés (manager uniquement — vivier du picker).
    public func listCurrentViewers(duelId: String) async throws -> [DisplayProfile] {
        try await http.request(.get(ModerationEndpoints.viewers("duel", duelId)), as: [DisplayProfile].self)
    }

    /// Modérateurs désignés de ce duel (manager + jusqu'à ``maxEventModerators`` spectateurs).
    public func listEventModerators(duelId: String) async throws -> [EventModerator] {
        try await http.request(.get(ModerationEndpoints.moderators("duel", duelId)), as: [EventModerator].self)
    }

    /// Manager : désigne un spectateur modérateur.
    public func appointModerator(duelId: String, userId: String) async throws {
        try await http.send(.post(ModerationEndpoints.moderators("duel", duelId), body: AppointModeratorBody(userId: userId)))
    }

    /// Manager : révoque un modérateur désigné.
    public func revokeModerator(duelId: String, userId: String) async throws {
        try await http.send(.delete(ModerationEndpoints.revokeModerator("duel", duelId, userId)))
    }

    // MARK: - Billetterie / gate d'accès programmé

    /// Le caller possède-t-il un billet pour ce duel ? `GET /duels/:id/my-ticket`.
    public func ticketInfo(id: String) async -> DuelTicketInfo {
        (try? await http.request(.get(DuelEndpoints.myTicket(id)), as: DuelTicketInfo.self)) ?? DuelTicketInfo(hasTicket: false, count: 0)
    }

    /// Achète le billet spectateur du duel (débit atomique + idempotent).
    public func buyTicket(duelId: String, idempotencyKey: String = UUID().uuidString) async throws {
        try await http.send(.post(WalletEndpoints.ticketDuel, body: BuyDuelTicketBody(duelId: duelId), idempotencyKey: idempotencyKey))
    }

    /// Vrai si le caller est admin (parité web `userRoles.includes("admin")`) — l'admin est
    /// toujours considéré comme acteur (exempté de billet) par ``ScheduledAccessGate``, mais
    /// PAS exempté de l'attente de l'heure programmée.
    public func amIAdmin() async -> Bool {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.roles.contains(.admin) ?? false
    }

    /// Prix d'UN vote en crédits — configuré par l'admin, lu via l'endpoint PUBLIC (défaut 1).
    public func votePricePerVote() async -> Double {
        (try? await http.request(.get("/settings/public/vote_config"), as: VoteConfigSetting.self))?.value?.pricePerVote ?? 1.0
    }

    // MARK: - J'aime persistés

    /// Compteur de j'aime PERSISTÉ (les duels partagent l'endpoint des lives).
    public func likesCount(duelId: String) async -> Int {
        (try? await http.request(.get(LiveEndpoints.likes(duelId)), as: DuelLikesResponse.self))?.likes ?? 0
    }

    /// Incrémente + persiste le j'aime côté serveur.
    public func likeDuel(duelId: String) async {
        try? await http.send(.post(LiveEndpoints.likes(duelId)))
    }

    // MARK: - Cadeaux : classement des donateurs

    /// Classement des donateurs du duel (`GET /leaderboards/gifts?contextType=duel`).
    public func giftLeaderboard(duelId: String) async throws -> [DuelDonorEntry] {
        try await http.request(
            .get(LeaderboardEndpoints.gifts, query: ["contextType": "duel", "contextId": duelId]),
            as: [DuelDonorEntry].self
        )
    }

    // MARK: - Espace MANAGER (organisateur de duels)

    /// Les managers ont-ils le droit de CRÉER des duels ? Réglage admin `manager_duel_creation`.
    /// Défaut **faux** (les duels sont assignés par l'admin) si le réglage est absent/illisible.
    public func managerDuelCreationEnabled() async -> Bool {
        (try? await http.request(
            .get(RoleEndpoints.publicSetting(RoleEndpoints.managerDuelCreation)),
            as: DuelPublicSetting.self
        ))?.value?.enabled ?? false
    }

    /// Id du caller (manager) — sert de `manager_id` à la création + à filtrer ses duels.
    public func myUserId() async -> String? {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.user.id
    }

    /// Duels gérés par ce manager (`GET /duels?managerId=&limit=`).
    public func managedDuels(managerId: String, status: String? = nil) async throws -> [Duel] {
        var query = ["managerId": managerId, "limit": "200"]
        if let status { query["status"] = status }
        return try await http.request(.get(DuelEndpoints.list, query: query), as: [Duel].self)
    }

    /// Annuaire des artistes (pour choisir les 2 adversaires).
    public func artists() async throws -> [ArtistSummary] {
        try await http.request(.get(ArtistEndpoints.list), as: [ArtistSummary].self)
    }

    /// Crée un duel entre 2 artistes (le manager s'assigne arbitre).
    /// - Parameters:
    ///   - artist1Id: id UTILISATEUR de l'artiste 1 (``ArtistSummary/opponentUserId``).
    ///   - artist2Id: id UTILISATEUR de l'artiste 2.
    public func createDuel(artist1Id: String, artist2Id: String, scheduledTime: String?, managerId: String) async throws {
        try await http.send(
            .post(DuelEndpoints.list, body: CreateDuelBody(artist1Id: artist1Id, artist2Id: artist2Id, scheduledTime: scheduledTime, managerId: managerId))
        )
    }

    // MARK: - Contrôles manager en direct

    /// Termine le duel.
    public func endDuel(id: String) async throws {
        try await http.send(.patch(DuelEndpoints.detail(id), body: DuelStatusBody(status: "ended")))
    }

    /// Annonce le vainqueur (ne termine pas le duel).
    public func announceWinner(id: String, artistId: String) async throws {
        try await http.send(.patch(DuelEndpoints.detail(id), body: DuelWinnerBody(winnerId: artistId)))
    }

    /// Active/désactive le chat de ce duel pour tous (manager uniquement).
    public func toggleChat(id: String, enabled: Bool) async throws {
        try await http.send(.patch(DuelEndpoints.detail(id), body: DuelChatBody(chatEnabled: enabled)))
    }

    /// Donne la parole à un artiste pendant `seconds` (minuteur persisté).
    public func startTimer(id: String, targetId: String, seconds: Int) async throws {
        let endsAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(TimeInterval(seconds)))
        try await http.send(.patch(DuelEndpoints.detail(id), body: DuelTimerBody(currentTimerEndsAt: endsAt, currentTimerTargetId: targetId)))
    }

    /// Arrête le minuteur de parole.
    public func stopTimer(id: String) async throws {
        try await http.send(.patch(DuelEndpoints.detail(id), body: DuelTimerBody(currentTimerEndsAt: nil, currentTimerTargetId: nil)))
    }
}

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

    /// Identité stable pour SwiftUI.
    public let id: String

    enum CodingKeys: String, CodingKey {
        case messageId = "id"
        case userId = "user_id"
        case content
        case user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        messageId = c.opt(String.self, .messageId)
        userId = c.val(String.self, .userId, "")
        content = c.val(String.self, .content, "")
        user = c.opt(DisplayProfile.self, .user)
        id = messageId ?? UUID().uuidString
    }

    public init(id: String? = nil, userId: String, content: String, user: DisplayProfile? = nil) {
        self.messageId = id
        self.userId = userId
        self.content = content
        self.user = user
        self.id = id ?? UUID().uuidString
    }

    /// Nom d'auteur affiché (repli « Fan »).
    @MainActor
    public var authorName: String { user?.displayName ?? AppStrings.current.fan }
}

/// Corps de `POST /duels/:id/messages`.
struct DuelMessageBody: Encodable, Sendable {
    let content: String
}

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

    /// Poste un message (le backend le diffuse ensuite via Socket.IO).
    public func postMessage(duelId: String, content: String) async throws {
        try await http.send(.post(DuelEndpoints.messages(duelId), body: DuelMessageBody(content: content)))
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
}

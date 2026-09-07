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
}

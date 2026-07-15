import Foundation
import CoreNetwork

/// Message de chat affiché dans le live.
public struct LiveChatMessage: Identifiable, Sendable, Decodable {
    public let id: String
    public let userId: String
    public let content: String
    public let authorName: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case content
        case user
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id)) ?? UUID().uuidString
        userId = try c.decode(String.self, forKey: .userId)
        content = try c.decode(String.self, forKey: .content)
        // Profil auteur hydraté par le backend : { user: { full_name } }.
        if let user = try? c.decodeIfPresent([String: String].self, forKey: .user) {
            authorName = user["full_name"] ?? user["stage_name"]
        } else { authorName = nil }
    }
    public init(id: String, userId: String, content: String, authorName: String?) {
        self.id = id; self.userId = userId; self.content = content; self.authorName = authorName
    }
}

/// Accès REST aux actions et à l'historique d'un live.
///
/// Le temps réel (nouveaux messages, cadeaux, présence) passe par Socket.IO ; ce
/// repository couvre l'historique initial + les actions (vote, cadeau, envoi de message).
public actor LiveRepository {

    private let http: HTTPClient
    public init(http: HTTPClient) { self.http = http }

    /// Historique de chat (dernière page) pour amorcer l'overlay avant le temps réel.
    public func chatHistory(liveId: String) async throws -> [LiveChatMessage] {
        try await http.request(.get("/lives/\(liveId)/messages", query: ["limit": "50"]))
    }

    /// Poste un message de chat (le backend diffuse ensuite via Socket.IO).
    public func postMessage(liveId: String, content: String) async throws {
        try await http.send(.init(.post, "/lives/\(liveId)/messages", body: ["content": content]))
    }

    /// Envoie un cadeau à un destinataire dans le contexte du live.
    /// Débit atomique côté backend (procédure stockée) ; `Idempotency-Key` anti double-débit.
    public func sendGift(liveId: String, giftId: String, toUserId: String) async throws {
        let body = ["giftId": giftId, "toUserId": toUserId, "liveId": liveId]
        try await http.send(.init(.post, "/wallet/gifts/send", body: body, idempotencyKey: UUID().uuidString))
    }
}

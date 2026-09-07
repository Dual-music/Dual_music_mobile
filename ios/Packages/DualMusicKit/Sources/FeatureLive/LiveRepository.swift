import Foundation
import CoreNetwork
import CoreUI
import DomainModels

/// Message de chat affiché dans un live (auteur hydraté par le backend).
public struct LiveChatMessage: Decodable, Sendable, Identifiable, Equatable {
    public let messageId: String?
    public let userId: String
    public let content: String
    public let user: DisplayProfile?

    /// Identité stable pour SwiftUI (le backend n'envoie pas toujours d'`id`).
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

    /// Nom d'auteur affiché (repli « Fan » comme sur Android).
    @MainActor
    public var authorName: String { user?.displayName ?? AppStrings.current.fan }
}

/// Corps de `POST /lives/:id/messages` et `POST /duels/:id/messages`.
struct ChatMessageBody: Encodable, Sendable {
    let content: String
}

/// Accès REST aux actions et à l'historique d'un live.
///
/// Le temps réel (messages, cadeaux, présence) passe par Socket.IO ; ce repository couvre
/// l'historique initial + les actions (message, cadeau). Miroir de `LiveRepository` Android.
public struct LiveRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Historique de chat (dernière page) pour amorcer l'overlay.
    /// - Parameter liveId: identifiant du live.
    public func chatHistory(liveId: String) async throws -> [LiveChatMessage] {
        try await http.request(
            .get(LiveEndpoints.messages(liveId), query: ["limit": "50"]),
            as: [LiveChatMessage].self
        )
    }

    /// Poste un message (le backend le diffuse ensuite via Socket.IO).
    public func postMessage(liveId: String, content: String) async throws {
        try await http.send(.post(LiveEndpoints.messages(liveId), body: ChatMessageBody(content: content)))
    }

    /// Envoie un cadeau au host dans le contexte du live.
    ///
    /// Débit atomique côté backend ; l'`Idempotency-Key` empêche tout double débit sur
    /// rejeu réseau.
    public func sendGift(liveId: String, giftId: String, toUserId: String) async throws {
        try await http.send(
            .post(
                WalletEndpoints.giftsSend,
                body: SendGiftRequest(giftId: giftId, toUserId: toUserId, liveId: liveId),
                idempotencyKey: "gift-\(liveId)-\(giftId)-\(toUserId)-\(UUID().uuidString)"
            )
        )
    }
}

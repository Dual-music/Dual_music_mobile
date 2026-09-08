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

/// Corps de `POST /lives`.
struct CreateLiveBody: Encodable, Sendable {
    let title: String
    let allowsDedications: Bool
    let allowGuests: Bool
    let dedicationMinPriceCredits: Double?
}

/// Corps de `PATCH /lives/:id/settings` — mise à jour **partielle** : un champ `nil` est
/// omis du JSON envoyé (comportement natif de `Encodable` pour un `Optional`), pas envoyé
/// comme `null`. Miroir du corps construit dynamiquement par `updateLiveSettings` Android.
struct UpdateLiveSettingsBody: Encodable, Sendable {
    let allowsDedications: Bool?
    let dedicationMinPriceCredits: Double?
    let allowGuests: Bool?
    let chatEnabled: Bool?
}

/// Dédicace payante reçue par l'artiste (`concert_dedications`, `concert_type="artist_live"`).
/// `status` : `pending` (à traiter) | `paid`/`delivered` (déjà acceptée, éventuellement
/// livrée) | `rejected`.
public struct LiveDedication: Decodable, Sendable, Identifiable, Equatable {
    public let id: String
    public let fanId: String
    public let message: String
    public let priceCredits: Double
    public let status: String
    public let concertId: String?
    public let concertType: String?
    public let fan: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case fanId = "fan_id"
        case message
        case priceCredits = "price_credits"
        case status
        case concertId = "concert_id"
        case concertType = "concert_type"
        case fan
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        fanId = c.val(String.self, .fanId, "")
        message = c.val(String.self, .message, "")
        priceCredits = c.amount(.priceCredits)
        status = c.val(String.self, .status, "paid")
        concertId = c.opt(String.self, .concertId)
        concertType = c.opt(String.self, .concertType)
        fan = c.opt(DisplayProfile.self, .fan)
    }

    /// Nom d'affichage du fan (repli « Fan » comme sur Android).
    @MainActor
    public var fanName: String { fan?.displayName ?? AppStrings.current.fan }
}

/// Réglage public `economic_config` (`GET /settings/public/economic_config`) — seul le prix
/// minimum de dédicace en direct nous intéresse ici.
struct EconomicConfigSetting: Decodable, Sendable {
    let value: EconomicConfigValue?
}
struct EconomicConfigValue: Decodable, Sendable {
    let dedicationLive: DedicationConfigSection?
    let dedication: DedicationConfigSection?

    enum CodingKeys: String, CodingKey {
        case dedicationLive = "dedication_live"
        case dedication
    }
}
struct DedicationConfigSection: Decodable, Sendable {
    let minPriceCredits: Double?

    enum CodingKeys: String, CodingKey { case minPriceCredits = "min_price_credits" }
}

/// Demande d'un spectateur pour rejoindre le live en invité (`live_join_requests`).
/// Le backend renvoie les lignes brutes (pas toujours de profil hydraté) → repli d'affichage.
public struct LiveJoinRequest: Decodable, Sendable, Identifiable, Equatable {
    public let id: String
    public let userId: String
    /// `pending` | `accepted` | `rejected` | `ended` | `cancelled`.
    public let status: String
    public let user: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case status
        case user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        userId = c.val(String.self, .userId, "")
        status = c.val(String.self, .status, "pending")
        user = c.opt(DisplayProfile.self, .user)
    }

    /// Nom d'affichage (repli « Spectateur » comme sur Android).
    @MainActor
    public var displayName: String { user?.displayName ?? AppStrings.current.viewerFallback }
}

/// Corps de `POST /lives/join-requests/:id/respond`.
struct RespondJoinBody: Encodable, Sendable {
    let status: String
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

    /// Signale ce live à la modération.
    public func reportLive(liveId: String, reason: ReportReason) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.reportsLive,
                body: ReportStreamBody(liveId: liveId, streamType: "live", reason: reason.rawValue)
            )
        )
    }

    /// Bannit un spectateur (hôte uniquement) : il ne peut plus écrire ni rejoindre.
    public func createStreamBan(streamId: String, bannedUserId: String, reason: String?) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.streamBans,
                body: StreamBanBody(streamId: streamId, streamType: "live", bannedUserId: bannedUserId, reason: reason)
            )
        )
    }

    /// Lives de l'artiste passé (`GET /lives?artistId=`) — alimente « Mes lives ».
    public func myLives(artistId: String) async throws -> [Live] {
        try await http.request(.get(LiveEndpoints.list, query: ["artistId": artistId]), as: [Live].self)
    }

    /// Lance un nouveau live (hôte).
    /// - Parameters:
    ///   - title: titre affiché (vide → « Live » côté backend).
    ///   - allowsDedications: dédicaces activées pour ce live.
    ///   - allowGuests: demandes d'invité (« lever la main ») activées.
    ///   - dedicationMinPriceCredits: prix minimum propre à ce live (`nil` = défaut global).
    public func createLive(
        title: String,
        allowsDedications: Bool = true,
        allowGuests: Bool = true,
        dedicationMinPriceCredits: Double? = nil
    ) async throws -> Live {
        try await http.request(
            .post(
                LiveEndpoints.list,
                body: CreateLiveBody(
                    title: title.isEmpty ? "Live" : title,
                    allowsDedications: allowsDedications,
                    allowGuests: allowGuests,
                    dedicationMinPriceCredits: dedicationMinPriceCredits
                )
            ),
            as: Live.self
        )
    }

    /// Termine le live actif (hôte).
    public func endLive(liveId: String) async throws {
        try await http.send(.post(LiveEndpoints.end(liveId)))
    }

    /// Détail d'un live (réglages inclus — dédicaces/invités/chat).
    public func live(id: String) async throws -> Live {
        try await http.request(.get(LiveEndpoints.detail(id)), as: Live.self)
    }

    /// Hôte : met à jour les réglages du live (mise à jour partielle — ne passer que ce qui
    /// change).
    public func updateLiveSettings(
        liveId: String,
        allowsDedications: Bool? = nil,
        dedicationMinPriceCredits: Double? = nil,
        allowGuests: Bool? = nil,
        chatEnabled: Bool? = nil
    ) async throws {
        try await http.send(
            .patch(
                LiveEndpoints.settings(liveId),
                body: UpdateLiveSettingsBody(
                    allowsDedications: allowsDedications,
                    dedicationMinPriceCredits: dedicationMinPriceCredits,
                    allowGuests: allowGuests,
                    chatEnabled: chatEnabled
                )
            )
        )
    }

    /// Prix minimum global d'une dédicace (`economic_config.dedication_live`, repli sur
    /// `.dedication`, repli final `10`) — utilisé tant qu'un live n'a pas de surcharge propre.
    public func dedicationMinPrice() async throws -> Double {
        let setting = try await http.request(
            .get(RoleEndpoints.publicSetting("economic_config")),
            as: EconomicConfigSetting.self
        )
        return setting.value?.dedicationLive?.minPriceCredits ?? setting.value?.dedication?.minPriceCredits ?? 10
    }

    /// Fan : envoie une dédicace payante (`priceCredits` obligatoire — l'omettre échoue en
    /// 400 côté backend).
    public func sendDedication(liveId: String, message: String, priceCredits: Double) async throws {
        try await http.send(
            .post(
                ConcertEndpoints.dedicationsPurchase,
                body: DedicationRequest(concertId: liveId, concertType: "artist_live", message: message, priceCredits: priceCredits)
            )
        )
    }

    /// Hôte : dédicaces reçues sur TOUS ses évènements (concerts + lives) — l'appelant filtre
    /// par `concertId == liveId`.
    public func artistDedications() async throws -> [LiveDedication] {
        try await http.request(.get(ConcertEndpoints.dedicationsArtistMine), as: [LiveDedication].self)
    }

    /// Hôte : accepte une dédicace EN ATTENTE — débite le fan maintenant.
    public func acceptDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationAccept(id)))
    }

    /// Hôte : rejette une dédicace EN ATTENTE — aucun débit.
    public func rejectDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationReject(id)))
    }

    /// Hôte : marque une dédicace acceptée comme interprétée en direct.
    public func deliverDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationDeliver(id)))
    }

    /// Spectateur : demande à rejoindre en invité (« lever la main »). Renvoie l'id de la
    /// demande créée, à conserver pour l'annuler.
    public func requestJoin(liveId: String) async throws -> String {
        try await http.request(.post(LiveEndpoints.join(liveId)), as: LiveJoinRequest.self).id
    }

    /// Annule SA PROPRE demande (autorisé au demandeur quel que soit son statut courant —
    /// contrairement à ``respondJoin(requestId:accept:)``, réservé à l'hôte).
    public func cancelJoin(requestId: String) async throws {
        try await http.send(.delete(LiveEndpoints.cancelJoin(requestId)))
    }

    /// Demandes d'invité de ce live, filtrées par statut (`pending` par défaut).
    public func joinRequests(liveId: String, status: String = "pending") async throws -> [LiveJoinRequest] {
        try await http.request(
            .get(LiveEndpoints.joinRequests(liveId), query: ["status": status]),
            as: [LiveJoinRequest].self
        )
    }

    /// Hôte : accepte/refuse une demande EN ATTENTE.
    public func respondJoin(requestId: String, accept: Bool) async throws {
        try await http.send(
            .post(LiveEndpoints.respondJoin(requestId), body: RespondJoinBody(status: accept ? "accepted" : "rejected"))
        )
    }

    /// Hôte : retire un invité déjà accepté (état `ended` persistant).
    public func kickGuest(requestId: String) async throws {
        try await http.send(.post(LiveEndpoints.respondJoin(requestId), body: RespondJoinBody(status: "ended")))
    }
}

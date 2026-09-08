import Foundation

/*
 * Contrat temps réel Dual Music — **Socket.IO** (et NON Supabase).
 *
 * Miroir de `shared-domain/realtime/RealtimeContract.kt` : namespaces, nommage des rooms,
 * catalogue d'événements et formes de payloads. `CoreRealtime` implémente ce contrat ;
 * toute évolution se fait d'abord dans `shared-domain`, puis ici et côté Android.
 *
 * Modèle backend :
 *  - 3 namespaces : `/chat`, `/live`, `/notifications` ;
 *  - handshake authentifié par JWT (fourni à la connexion) ;
 *  - on rejoint une room en émettant `join { type, id }` ; nom interne = `type:id` ;
 *  - le serveur pousse dans la room (voir ``Realtime/Event``) et à l'utilisateur (`tx:*`).
 */
public enum Realtime {

    /// Namespaces Socket.IO exposés par le backend.
    public enum Namespace: String, Sendable, Hashable {
        case chat = "/chat"
        case live = "/live"
        case notifications = "/notifications"
    }

    /// Types de room joignables (correspondent à ``EventContext`` + `leaderboard`).
    /// La valeur brute est celle utilisée dans le payload `join { type, id }`.
    public enum RoomType: String, Sendable {
        case duel, concert, competition, live, leaderboard

        /// Convertit un contexte métier en type de room temps réel.
        /// - Parameter context: contexte d'événement.
        public static func from(_ context: EventContext) -> RoomType {
            switch context {
            case .duel: return .duel
            case .concert: return .concert
            case .competition: return .competition
            case .live: return .live
            }
        }
    }

    /// Nom interne d'une room : `type:id` (identique au `roomName` backend).
    public static func roomName(_ type: RoomType, _ id: String) -> String { "\(type.rawValue):\(id)" }

    /// Événement client → serveur pour rejoindre une room.
    public static let eventJoin = "join"
    /// Événement client → serveur pour quitter une room.
    public static let eventLeave = "leave"

    /// Catalogue des événements serveur → client.
    public enum Event {
        /// `/live` · room duel — un vote payant vient d'être enregistré. Payload ``VotePayload``.
        public static let vote = "vote"
        /// `/live` · room event — un cadeau a été envoyé. Payload ``GiftPayload``.
        public static let gift = "gift"
        /// par-utilisateur — le destinataire d'un cadeau est notifié. Payload ``TxGiftPayload``.
        public static let txGift = "tx:gift"
        /// par-utilisateur — un crédit a été porté au solde. Payload ``TxCreditPayload``.
        public static let txCredit = "tx:credit"
        /// par-utilisateur — un retrait a changé d'état. Payload ``TxWithdrawalPayload``.
        public static let txWithdrawal = "tx:withdrawal"
        /// `/live` · room event — changement de statut. Payload ``StatusPayload``.
        public static let status = "status"
        /// `/live` · room duel — minuteur (start/stop, cible). Payload ``TimerPayload``.
        public static let timer = "timer"
        /// `/live` · room event — présence (viewers). Payload ``PresencePayload``.
        public static let presence = "presence"
        /// `/chat` · room event — nouveau message. Payload ``ChatMessagePayload``.
        public static let chatMessage = "message"
        /// `/notifications` — nouvelle notification in-app. Payload ``AppNotification``.
        public static let notification = "notification"
        /// `/live` · room event — réglages modifiés par l'hôte (dédicaces/invités/chat).
        /// Payload ``LiveSettingsPayload``.
        public static let settings = "settings"
        /// `/live` · room live — nouvelle demande de dédicace. Payload ``DedicationEventPayload``.
        public static let dedicationNew = "dedication:new"
        /// `/live` · room live — dédicace acceptée/rejetée/livrée. Payload
        /// ``DedicationEventPayload``.
        public static let dedicationUpdate = "dedication:update"
        /// `/live` · room live — nouvelle demande d'invité (« lever la main »). Payload
        /// ``JoinEventPayload``.
        public static let joinNew = "join:new"
        /// `/live` · room live — demande d'invité acceptée/rejetée/terminée/annulée. Payload
        /// ``JoinEventPayload``.
        public static let joinUpdate = "join:update"
    }
}

// MARK: - Payloads

/// `vote` — nouveau vote sur un duel.
public struct VotePayload: Decodable, Sendable {
    public let duelId: String
    public let artistId: String
    public let amount: Double
    public let userId: String?

    enum CodingKeys: String, CodingKey {
        case duelId = "duel_id"
        case artistId = "artist_id"
        case amount
        case userId = "user_id"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        duelId = c.val(String.self, .duelId, "")
        artistId = c.val(String.self, .artistId, "")
        amount = c.amount(.amount)
        userId = c.opt(String.self, .userId)
    }
}

/// `gift` — cadeau envoyé dans une room d'événement.
public struct GiftPayload: Decodable, Sendable {
    public let toUserId: String?
    public let fromUserId: String?
    public let value: Double
    public let giftId: String?
    public let giftName: String?
    public let giftImage: String?

    enum CodingKeys: String, CodingKey {
        case toUserId = "to_user_id"
        case fromUserId = "from_user_id"
        case value
        case giftId = "gift_id"
        case giftName = "gift_name"
        case giftImage = "gift_image"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        toUserId = c.opt(String.self, .toUserId)
        fromUserId = c.opt(String.self, .fromUserId)
        value = c.amount(.value)
        giftId = c.opt(String.self, .giftId)
        giftName = c.opt(String.self, .giftName)
        giftImage = c.opt(String.self, .giftImage)
    }
}

/// `tx:gift` — notification au destinataire d'un cadeau.
public struct TxGiftPayload: Decodable, Sendable {
    public let amount: Double

    enum CodingKeys: String, CodingKey { case amount }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        amount = c.amount(.amount)
    }
}

/// `tx:credit` — crédit porté au solde de l'utilisateur.
public struct TxCreditPayload: Decodable, Sendable {
    public let amount: Double
    public let currency: String?

    enum CodingKeys: String, CodingKey { case amount, currency }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        amount = c.amount(.amount)
        currency = c.opt(String.self, .currency)
    }
}

/// `tx:withdrawal` — mise à jour d'un retrait.
public struct TxWithdrawalPayload: Decodable, Sendable {
    public let amount: Double
    public let status: String

    enum CodingKeys: String, CodingKey { case amount, status }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        amount = c.amount(.amount)
        status = c.val(String.self, .status, "")
    }
}

/// `status` — changement d'état d'un duel/live/concert/compétition.
public struct StatusPayload: Decodable, Sendable {
    public let duelId: String?
    public let liveId: String?
    public let status: String
    public let winnerId: String?
    public let roomId: String?

    enum CodingKeys: String, CodingKey {
        case duelId = "duel_id"
        case liveId = "live_id"
        case status
        case winnerId = "winner_id"
        case roomId = "room_id"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        duelId = c.opt(String.self, .duelId)
        liveId = c.opt(String.self, .liveId)
        status = c.val(String.self, .status, "")
        winnerId = c.opt(String.self, .winnerId)
        roomId = c.opt(String.self, .roomId)
    }
}

/// `timer` — minuteur d'un duel (arrivants tardifs inclus).
public struct TimerPayload: Decodable, Sendable {
    public let duelId: String?
    public let endsAt: String?
    public let targetId: String?

    enum CodingKeys: String, CodingKey {
        case duelId = "duel_id"
        case endsAt = "ends_at"
        case targetId = "target_id"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        duelId = c.opt(String.self, .duelId)
        endsAt = c.opt(String.self, .endsAt)
        targetId = c.opt(String.self, .targetId)
    }
}

/// `presence` — compteur de spectateurs d'un live.
public struct PresencePayload: Decodable, Sendable {
    public let liveId: String?
    public let count: Int

    enum CodingKeys: String, CodingKey {
        case liveId = "live_id"
        case count
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        liveId = c.opt(String.self, .liveId)
        count = c.int(.count)
    }
}

/// `message` — message de chat d'une room.
public struct ChatMessagePayload: Decodable, Sendable {
    public let id: String?
    public let userId: String
    public let content: String
    public let createdAt: String?
    public let user: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case content
        case createdAt = "created_at"
        case user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.opt(String.self, .id)
        userId = c.val(String.self, .userId, "")
        content = c.val(String.self, .content, "")
        createdAt = c.opt(String.self, .createdAt)
        user = c.opt(DisplayProfile.self, .user)
    }
}

/// `settings` — réglages d'un live modifiés par l'hôte, en direct (dédicaces/invités/chat).
/// `dedicationMinPriceCredits` absent = pas de surcharge sur CE live (garder le prix déjà
/// chargé), à ne pas confondre avec 0.
public struct LiveSettingsPayload: Decodable, Sendable {
    public let allowsDedications: Bool
    public let dedicationMinPriceCredits: Double?
    public let allowGuests: Bool
    public let chatEnabled: Bool?

    enum CodingKeys: String, CodingKey {
        case allowsDedications = "allows_dedications"
        case dedicationMinPriceCredits = "dedication_min_price_credits"
        case allowGuests = "allow_guests"
        case chatEnabled = "chat_enabled"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        allowsDedications = c.bool(.allowsDedications, true)
        dedicationMinPriceCredits = c.amountIfPresent(.dedicationMinPriceCredits)
        allowGuests = c.bool(.allowGuests, true)
        chatEnabled = c.opt(Bool.self, .chatEnabled)
    }
}

/// `dedication:new` / `dedication:update` — même room que `join:live`. Sert seulement à
/// détecter "est-ce que ça me concerne" (fan) ou "dois-je recharger" (hôte) : l'état fait
/// toujours l'objet d'un rechargement REST complet, jamais appliqué depuis ce payload.
public struct DedicationEventPayload: Decodable, Sendable {
    public let dedicationId: String?
    public let fanId: String?
    public let status: String?
    public let priceCredits: Double?

    enum CodingKeys: String, CodingKey {
        case dedicationId = "dedication_id"
        case fanId = "fan_id"
        case status
        case priceCredits = "price_credits"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        dedicationId = c.opt(String.self, .dedicationId)
        fanId = c.opt(String.self, .fanId)
        status = c.opt(String.self, .status)
        priceCredits = c.amountIfPresent(.priceCredits)
    }
}

/// `join:new` / `join:update` — même room que `join:live` (dédicaces). Sert seulement à
/// détecter "dois-je recharger" (hôte, ou tout le monde pour recalculer les abonnements aux
/// rooms d'invités) et "ma propre demande a changé d'état" (spectateur concerné) : l'état
/// fait toujours l'objet d'un rechargement REST complet.
public struct JoinEventPayload: Decodable, Sendable {
    public let liveId: String?
    public let userId: String?
    public let status: String?

    enum CodingKeys: String, CodingKey {
        case liveId = "live_id"
        case userId = "user_id"
        case status
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        liveId = c.opt(String.self, .liveId)
        userId = c.opt(String.self, .userId)
        status = c.opt(String.self, .status)
    }
}

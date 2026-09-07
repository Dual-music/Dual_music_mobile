import Foundation

/*
 * DTOs du portefeuille — miroir de `shared-domain/wallet/WalletDtos.kt`.
 *
 * Rappel économie : le solde est en CRÉDITS (1 crédit = 0,50 € — invariant métier). Les
 * DÉBITS (vote, cadeau, ticket, déblocage replay) passent par des procédures stockées
 * atomiques côté serveur : le mobile n'affiche que des aperçus et laisse le backend faire
 * foi sur les montants.
 */

/// Réponse de `GET /wallet` — solde courant + contre-valeur euro calculée serveur.
public struct WalletBalance: Codable, Sendable {
    public let balance: Double
    /// Contre-valeur en euros calculée par le backend (**fait foi**).
    public let eurValue: Double

    enum CodingKeys: String, CodingKey { case balance, eurValue }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        balance = c.amount(.balance)
        eurValue = c.amount(.eurValue)
    }

    public init(balance: Double = 0, eurValue: Double = 0) {
        self.balance = balance
        self.eurValue = eurValue
    }
}

/// Élément de `GET /wallet/revenues` — revenus agrégés **par événement source**
/// (ce que l'utilisateur a GAGNÉ : artiste/manager).
public struct RevenueEvent: Codable, Sendable, Identifiable {
    public let sourceId: String
    public let sourceType: String
    /// Total de crédits reçus par le caller sur cet événement.
    public let totalReceived: Double
    public let txCount: Int
    public let lastAt: String?

    /// Identité stable pour les listes SwiftUI (l'API ne fournit pas d'`id` propre).
    public var id: String { "\(sourceType):\(sourceId)" }

    enum CodingKeys: String, CodingKey {
        case sourceId = "source_id"
        case sourceType = "source_type"
        case totalReceived = "total_received"
        case txCount = "tx_count"
        case lastAt = "last_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sourceId = c.val(String.self, .sourceId, "")
        sourceType = c.val(String.self, .sourceType, "")
        totalReceived = c.amount(.totalReceived)
        txCount = c.int(.txCount)
        lastAt = c.opt(String.self, .lastAt)
    }
}

/// Élément de `GET /wallet/spending` — sorties du caller (cadeaux envoyés, votes, tickets…).
public struct SpendItem: Codable, Sendable, Identifiable {
    public let id: String
    public let sourceType: String
    public let sourceId: String?
    /// Montant total débité pour cette opération.
    public let totalCredits: Double
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case sourceType = "source_type"
        case sourceId = "source_id"
        case totalCredits = "total_credits"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        sourceType = c.val(String.self, .sourceType, "")
        sourceId = c.opt(String.self, .sourceId)
        totalCredits = c.amount(.totalCredits)
        createdAt = c.opt(String.self, .createdAt)
    }
}

/// Élément de `GET /wallet/revenues/breakdown` — total par type de source.
public struct RevenueBreakdown: Codable, Sendable, Identifiable {
    public let sourceType: String
    public let total: Double

    public var id: String { sourceType }

    enum CodingKeys: String, CodingKey {
        case sourceType = "source_type"
        case total
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sourceType = c.val(String.self, .sourceType, "")
        total = c.amount(.total)
    }
}

// MARK: - Corps de requête (débits)

/// Corps de `POST /wallet/vote` — vote payant pour un artiste dans un duel.
public struct VoteRequest: Encodable, Sendable {
    public let duelId: String
    public let artistId: String
    public let amount: Double
    public init(duelId: String, artistId: String, amount: Double) {
        self.duelId = duelId
        self.artistId = artistId
        self.amount = amount
    }
}

/// Corps de `POST /wallet/gifts/send` — envoi d'un cadeau de l'inventaire.
public struct SendGiftRequest: Encodable, Sendable {
    public let giftId: String
    public let toUserId: String
    public let duelId: String?
    public let liveId: String?
    public let concertId: String?

    public init(
        giftId: String,
        toUserId: String,
        duelId: String? = nil,
        liveId: String? = nil,
        concertId: String? = nil
    ) {
        self.giftId = giftId
        self.toUserId = toUserId
        self.duelId = duelId
        self.liveId = liveId
        self.concertId = concertId
    }
}

/// Corps de `POST /wallet/gifts/purchase` — achat de cadeaux dans l'inventaire.
public struct PurchaseGiftRequest: Encodable, Sendable {
    public let giftId: String
    public let quantity: Int
    public init(giftId: String, quantity: Int = 1) {
        self.giftId = giftId
        self.quantity = quantity
    }
}

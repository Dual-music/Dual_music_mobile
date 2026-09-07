import Foundation
import CoreNetwork
import DomainModels

/// Accès aux opérations du portefeuille `/wallet/…`.
///
/// Lectures (solde, historiques) et **débits** (vote, cadeau). Les débits sont
/// **idempotents** : on envoie une clé d'idempotence pour qu'un rejeu réseau ne débite
/// jamais deux fois. Aucun calcul d'argent n'est fait ici — le backend fait foi.
/// Miroir de `WalletRepository` côté Android.
public struct WalletRepository: Sendable {

    private let http: HTTPClient

    /// - Parameter http: client HTTP partagé (enveloppe + Bearer + refresh gérés en amont).
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Solde courant en crédits + contre-valeur € (calculée serveur, fait foi).
    public func balance() async throws -> WalletBalance {
        try await http.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
    }

    /// Revenus agrégés par événement source (ce que l'utilisateur a gagné).
    /// - Parameter since: borne ISO-8601 optionnelle (filtrage serveur par période).
    public func revenues(since: String? = nil) async throws -> [RevenueEvent] {
        let query = since.map { ["since": $0] } ?? [:]
        return try await http.request(.get(WalletEndpoints.revenues, query: query), as: [RevenueEvent].self)
    }

    /// Dépenses du caller (cadeaux envoyés, votes, tickets…), les plus récentes d'abord.
    public func spending() async throws -> [SpendItem] {
        try await http.request(.get(WalletEndpoints.spending), as: [SpendItem].self)
    }

    /// Achats de crédits (recharges) du caller — `GET /payments/history`.
    public func purchases() async throws -> [CreditPurchase] {
        try await http.request(.get(PaymentEndpoints.history), as: [CreditPurchase].self)
    }

    /// Vote payant pour un artiste dans un duel (débit atomique côté serveur).
    ///
    /// - Parameters:
    ///   - duelId: duel concerné.
    ///   - artistId: artiste bénéficiaire.
    ///   - amount: montant en crédits.
    ///   - idempotencyKey: clé unique de l'action (générée par défaut).
    public func vote(
        duelId: String,
        artistId: String,
        amount: Double,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                WalletEndpoints.vote,
                body: VoteRequest(duelId: duelId, artistId: artistId, amount: amount),
                idempotencyKey: idempotencyKey
            )
        )
    }

    /// Envoie un cadeau de l'inventaire dans un contexte d'événement (duel/live/concert).
    public func sendGift(
        _ request: SendGiftRequest,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(.post(WalletEndpoints.giftsSend, body: request, idempotencyKey: idempotencyKey))
    }

    /// Achète des cadeaux dans l'inventaire (débit du solde).
    public func purchaseGift(
        giftId: String,
        quantity: Int = 1,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                WalletEndpoints.giftsPurchase,
                body: PurchaseGiftRequest(giftId: giftId, quantity: quantity),
                idempotencyKey: idempotencyKey
            )
        )
    }
}

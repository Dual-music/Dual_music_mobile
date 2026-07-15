import Foundation

/// Erreur unifiée de la couche réseau iOS.
///
/// Toute défaillance — HTTP non-2xx, enveloppe `error`, réseau, décodage, annulation —
/// est convertie en `APIError`. Les couches Domain/Presentation branchent sur `code`
/// (stable) plutôt que sur `message` (localisé).
public struct APIError: Error, Equatable, Sendable {
    /// Code HTTP. `0` pour une erreur locale (réseau, décodage, annulation).
    public let httpStatus: Int
    /// Code métier stable renvoyé par le backend (ex. `WALLET_INSUFFICIENT`).
    public let code: String
    /// Message lisible.
    public let message: String

    public init(httpStatus: Int, code: String, message: String) {
        self.httpStatus = httpStatus
        self.code = code
        self.message = message
    }

    /// Rejeu possible : réseau ou 5xx serveur.
    public var isRetriable: Bool { httpStatus == 0 || (500...599).contains(httpStatus) }

    /// La session a expiré → déclencher un refresh ou renvoyer vers l'auth.
    public var isAuthExpired: Bool { httpStatus == 401 }

    /// Solde de crédits insuffisant → proposer une recharge.
    public var isInsufficientBalance: Bool { code == "WALLET_INSUFFICIENT" }

    // MARK: Fabriques d'erreurs locales

    static func network(_ underlying: Error) -> APIError {
        APIError(httpStatus: 0, code: "NETWORK_ERROR", message: underlying.localizedDescription)
    }

    static func decoding(_ underlying: Error) -> APIError {
        APIError(httpStatus: 0, code: "DECODING_ERROR", message: underlying.localizedDescription)
    }

    static let cancelled = APIError(httpStatus: 0, code: "CANCELLED", message: "Requête annulée")
    static let unauthorizedNoRefresh = APIError(httpStatus: 401, code: "UNAUTHORIZED", message: "Session expirée")
}

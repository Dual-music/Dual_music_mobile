import Foundation
import DomainModels

/// Erreur unifiée de la couche réseau iOS — équivalent de `DomainError` côté Android.
///
/// Toute défaillance — HTTP non-2xx, enveloppe `error`, réseau, décodage, annulation — est
/// convertie en ``APIError``. Les couches supérieures branchent sur ``code`` (stable,
/// voir `ErrorCode`) plutôt que sur ``message`` (localisé côté serveur).
public struct APIError: Error, Equatable, Sendable, LocalizedError {
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

    /// Message présenté par `localizedDescription` (utilisé par les écrans).
    public var errorDescription: String? { message }

    /// Rejeu possible : erreur réseau locale ou 5xx serveur.
    public var isRetriable: Bool { httpStatus == 0 || (500...599).contains(httpStatus) }

    /// La session a expiré → déclencher un refresh ou renvoyer vers l'auth.
    public var isAuthExpired: Bool { httpStatus == 401 }

    /// Solde de crédits insuffisant → proposer une recharge.
    public var isInsufficientBalance: Bool { code == ErrorCode.walletInsufficient }

    // MARK: Fabriques d'erreurs locales

    /// Échec de transport (pas de réseau, DNS, TLS…).
    public static func network(_ underlying: Error) -> APIError {
        APIError(httpStatus: 0, code: ErrorCode.network, message: underlying.localizedDescription)
    }

    /// Réponse illisible (schéma inattendu).
    public static func decoding(_ underlying: Error) -> APIError {
        APIError(httpStatus: 0, code: "DECODING_ERROR", message: underlying.localizedDescription)
    }

    /// Requête annulée (vue démontée, `Task` annulée).
    public static let cancelled = APIError(httpStatus: 0, code: "CANCELLED", message: "Requête annulée")

    /// 401 sans refresh possible → l'utilisateur doit se reconnecter.
    public static let unauthorizedNoRefresh = APIError(httpStatus: 401, code: "UNAUTHORIZED", message: "Session expirée")
}

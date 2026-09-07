import Foundation

/// Miroir Swift de l'enveloppe de réponse du backend (`shared-domain/api/Envelope.kt`).
///
/// Décodée **une seule fois** par ``HTTPClient`` ; les repositories reçoivent `data`
/// (type `T`) ou une ``APIError``. Les couches supérieures ne manipulent jamais
/// l'enveloppe directement.
struct Envelope<T: Decodable>: Decodable {
    let data: T?
    let meta: Meta?
    let error: ErrorBody?
}

/// Métadonnées de réponse (traçabilité + pagination éventuelle).
public struct Meta: Decodable, Sendable {
    /// Identifiant de corrélation renvoyé par le backend (logs/support).
    public let requestId: String?
    /// Présent uniquement sur les listes paginées.
    public let pagination: Pagination?
}

/// Pagination — supporte les deux modes du backend : `page` (offset) et `cursor` (keyset).
public struct Pagination: Decodable, Sendable {
    /// `"page"` ou `"cursor"`.
    public let mode: String?
    public let page: Int?
    public let limit: Int?
    public let total: Int?
    public let totalPages: Int?
    /// Indique s'il reste des éléments.
    public let hasMore: Bool?
    /// Curseur d'appel suivant en mode cursor (`nil` sur la dernière page).
    public let nextCursor: String?
}

/// Corps d'erreur métier `{ code, message, details? }`.
struct ErrorBody: Decodable {
    let code: String
    let message: String
}

/// Résultat d'une requête qui expose aussi `meta` (utile pour la pagination keyset).
public struct Page<T: Decodable & Sendable>: Sendable {
    public let data: T
    public let meta: Meta?

    /// Curseur de la page suivante, ou `nil` s'il n'y en a plus.
    public var nextCursor: String? { meta?.pagination?.nextCursor }

    /// Vrai s'il reste des éléments à charger.
    public var hasMore: Bool { meta?.pagination?.hasMore ?? (meta?.pagination?.nextCursor != nil) }
}

/// Marqueur pour les réponses sans contenu utile (204 / endpoints d'action).
public struct EmptyBody: Codable, Sendable {
    public init() {}
}

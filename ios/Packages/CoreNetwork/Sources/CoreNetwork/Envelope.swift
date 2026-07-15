import Foundation

/// Miroir Swift de l'enveloppe de réponse du backend (voir `shared-domain/api/Envelope.kt`).
///
/// Décodée une seule fois par `HTTPClient` ; les repositories reçoivent `data` (type `T`)
/// ou une `APIError`.
struct Envelope<T: Decodable>: Decodable {
    let data: T?
    let meta: Meta?
    let error: ErrorBody?
}

/// Métadonnées (traçabilité + pagination).
public struct Meta: Decodable, Sendable {
    public let requestId: String?
    public let pagination: Pagination?
}

/// Pagination — modes `page` (offset) et `cursor` (keyset).
public struct Pagination: Decodable, Sendable {
    public let mode: String?
    public let page: Int?
    public let limit: Int?
    public let total: Int?
    public let totalPages: Int?
    public let hasMore: Bool?
    public let nextCursor: String?
}

/// Corps d'erreur métier `{ code, message, details? }`.
struct ErrorBody: Decodable {
    let code: String
    let message: String
}

/// Résultat d'une requête qui expose aussi `meta` (utile pour la pagination).
public struct Page<T: Decodable & Sendable>: Sendable {
    public let data: T
    public let meta: Meta?
}

import Foundation

/// Verbes HTTP supportés.
public enum HTTPMethod: String, Sendable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"
}

/// Description déclarative d'une requête, indépendante du transport.
///
/// Les modules `feature-*` composent des `Endpoint` ; `HTTPClient` les exécute. Le
/// `body` est un `Encodable` optionnel, encodé en JSON.
public struct Endpoint: Sendable {
    public let method: HTTPMethod
    /// Chemin relatif à la base `/api/v1` (ex. `/duels/123/votes`).
    public let path: String
    /// Paramètres de requête (query string).
    public let query: [String: String]
    /// Corps JSON (pour POST/PUT/PATCH, et DELETE avec corps).
    public let body: (any Encodable & Sendable)?
    /// Requête anonyme : n'attache pas le Bearer et ne tente pas de refresh.
    public let anonymous: Bool
    /// Clé d'idempotence (débits : votes, cadeaux, achats) — envoyée en en-tête.
    public let idempotencyKey: String?

    public init(
        _ method: HTTPMethod,
        _ path: String,
        query: [String: String] = [:],
        body: (any Encodable & Sendable)? = nil,
        anonymous: Bool = false,
        idempotencyKey: String? = nil
    ) {
        self.method = method
        self.path = path
        self.query = query
        self.body = body
        self.anonymous = anonymous
        self.idempotencyKey = idempotencyKey
    }

    // Fabriques concises pour les cas courants.
    public static func get(_ path: String, query: [String: String] = [:], anonymous: Bool = false) -> Endpoint {
        Endpoint(.get, path, query: query, anonymous: anonymous)
    }
    public static func post(_ path: String, body: (any Encodable & Sendable)? = nil, idempotencyKey: String? = nil) -> Endpoint {
        Endpoint(.post, path, body: body, idempotencyKey: idempotencyKey)
    }
    public static func patch(_ path: String, body: (any Encodable & Sendable)? = nil) -> Endpoint {
        Endpoint(.patch, path, body: body)
    }
    public static func delete(_ path: String, body: (any Encodable & Sendable)? = nil) -> Endpoint {
        Endpoint(.delete, path, body: body)
    }
}

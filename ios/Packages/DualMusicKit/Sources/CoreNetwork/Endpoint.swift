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
/// Les modules `Feature*` composent des ``Endpoint`` ; ``HTTPClient`` les exécute. Le
/// `body` est un `Encodable` optionnel, encodé en JSON par le client (contrairement à
/// Android où le corps est pré-sérialisé en `String` — ici on garde le typage fort).
///
/// ```swift
/// let e = Endpoint.post(WalletEndpoints.vote, body: VoteRequest(...), idempotencyKey: UUID().uuidString)
/// try await http.send(e)
/// ```
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
    /// Clé d'idempotence (débits : votes, cadeaux, achats) — envoyée en en-tête
    /// `Idempotency-Key` pour qu'un rejeu réseau ne débite jamais deux fois.
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

    // MARK: Fabriques concises

    /// Lecture simple.
    public static func get(_ path: String, query: [String: String] = [:], anonymous: Bool = false) -> Endpoint {
        Endpoint(.get, path, query: query, anonymous: anonymous)
    }

    /// Création / action. `idempotencyKey` est **obligatoire en pratique** pour tout débit.
    public static func post(
        _ path: String,
        body: (any Encodable & Sendable)? = nil,
        anonymous: Bool = false,
        idempotencyKey: String? = nil
    ) -> Endpoint {
        Endpoint(.post, path, body: body, anonymous: anonymous, idempotencyKey: idempotencyKey)
    }

    /// Remplacement complet (réglages admin).
    public static func put(_ path: String, body: (any Encodable & Sendable)? = nil) -> Endpoint {
        Endpoint(.put, path, body: body)
    }

    /// Mise à jour partielle (profil).
    public static func patch(_ path: String, body: (any Encodable & Sendable)? = nil) -> Endpoint {
        Endpoint(.patch, path, body: body)
    }

    /// Suppression (avec corps optionnel : révocation de rôle, désabonnement…).
    public static func delete(_ path: String, body: (any Encodable & Sendable)? = nil) -> Endpoint {
        Endpoint(.delete, path, body: body)
    }
}

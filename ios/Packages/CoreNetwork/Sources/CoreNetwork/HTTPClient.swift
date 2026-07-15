import Foundation

/// Client HTTP typé de Dual Music (iOS).
///
/// - Préfixe chaque requête par la base `…/api/v1` et attache le Bearer JWT.
/// - Décode l'enveloppe `{ data, meta }` / `{ error }` ; renvoie `data` ou lève `APIError`.
/// - Sur `401`, effectue **un** refresh partagé (single-flight) puis rejoue la requête.
///
/// Implémenté en `actor` : l'état de refresh (`refreshTask`) est protégé des accès
/// concurrents, garantissant qu'un seul refresh part même si 10 requêtes échouent en 401
/// simultanément (les autres attendent son résultat).
public actor HTTPClient {

    private let baseURL: URL
    private let session: URLSession
    private let tokenStore: TokenStore
    private let refresher: TokenRefresher
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    /// Tâche de refresh en cours (single-flight). `nil` quand aucun refresh n'est actif.
    private var refreshTask: Task<Bool, Never>?

    /// - Parameters:
    ///   - baseURL: URL publique de l'API SANS `/api/v1` (ex. `https://api.dualmusic.app`).
    ///   - tokenStore: fournisseur/persistance des jetons (Keychain).
    ///   - refresher: exécuteur du refresh (`core-auth`).
    ///   - session: `URLSession` injectable (tests, pinning). Par défaut `.shared`.
    public init(
        baseURL: URL,
        tokenStore: TokenStore,
        refresher: TokenRefresher,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.tokenStore = tokenStore
        self.refresher = refresher
        self.session = session
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    // MARK: API publique

    /// Exécute une requête et renvoie uniquement `data`.
    public func request<T: Decodable & Sendable>(_ endpoint: Endpoint, as type: T.Type = T.self) async throws -> T {
        try await send(endpoint, type: T.self).data
    }

    /// Exécute une requête et renvoie `data` + `meta` (pagination).
    public func requestWithMeta<T: Decodable & Sendable>(_ endpoint: Endpoint, as type: T.Type = T.self) async throws -> Page<T> {
        let result = try await send(endpoint, type: T.self)
        return Page(data: result.data, meta: result.meta)
    }

    /// Variante sans corps de réponse attendu (204 / actions).
    public func send(_ endpoint: Endpoint) async throws {
        _ = try await send(endpoint, type: EmptyBody.self)
    }

    // MARK: Cœur d'exécution

    private func send<T: Decodable & Sendable>(_ endpoint: Endpoint, type: T.Type, isRetry: Bool = false) async throws -> (data: T, meta: Meta?) {
        let request = try await buildRequest(endpoint)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw APIError.cancelled
        } catch {
            throw APIError.network(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw APIError(httpStatus: 0, code: "NO_RESPONSE", message: "Réponse HTTP absente")
        }

        // Refresh single-flight sur 401 (une seule fois, hors requêtes anonymes).
        if http.statusCode == 401, !endpoint.anonymous, !isRetry {
            let refreshed = await performSingleFlightRefresh()
            if refreshed {
                return try await send(endpoint, type: T.self, isRetry: true)
            }
            await tokenStore.clear()
            throw APIError.unauthorizedNoRefresh
        }

        return try decodeEnvelope(data: data, status: http.statusCode, type: T.self)
    }

    /// Décode l'enveloppe et applique la règle succès/erreur.
    private func decodeEnvelope<T: Decodable & Sendable>(data: Data, status: Int, type: T.Type) throws -> (data: T, meta: Meta?) {
        // 204 / corps vide → succès sans contenu.
        if status == 204 || data.isEmpty {
            if let empty = EmptyBody() as? T { return (empty, nil) }
            throw APIError(httpStatus: status, code: "EMPTY_BODY", message: "Corps de réponse vide")
        }

        let envelope: Envelope<T>
        do {
            envelope = try decoder.decode(Envelope<T>.self, from: data)
        } catch {
            // Si le statut est déjà en échec, préférer une erreur HTTP claire au décodage.
            if !(200...299).contains(status) {
                throw APIError(httpStatus: status, code: "HTTP_\(status)", message: HTTPURLResponse.localizedString(forStatusCode: status))
            }
            throw APIError.decoding(error)
        }

        if !(200...299).contains(status) || envelope.error != nil {
            let body = envelope.error
            throw APIError(
                httpStatus: status,
                code: body?.code ?? "HTTP_\(status)",
                message: body?.message ?? HTTPURLResponse.localizedString(forStatusCode: status)
            )
        }

        guard let payload = envelope.data else {
            // Succès sans data (endpoints d'action). Tolérer si T == EmptyBody.
            if let empty = EmptyBody() as? T { return (empty, envelope.meta) }
            throw APIError(httpStatus: status, code: "NO_DATA", message: "Champ data absent")
        }
        return (payload, envelope.meta)
    }

    // MARK: Refresh single-flight

    /// Lance (ou rejoint) l'unique tâche de refresh. Renvoie `true` si la session est valide.
    private func performSingleFlightRefresh() async -> Bool {
        if let existing = refreshTask {
            return await existing.value // Rejoindre le refresh déjà en vol.
        }
        let task = Task { () -> Bool in
            guard let token = await tokenStore.refreshToken() else { return false }
            do {
                let pair = try await refresher.refresh(using: token)
                await tokenStore.setTokens(access: pair.access, refresh: pair.refresh)
                return true
            } catch {
                return false
            }
        }
        refreshTask = task
        let result = await task.value
        refreshTask = nil
        return result
    }

    // MARK: Construction de requête

    private func buildRequest(_ endpoint: Endpoint) async throws -> URLRequest {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/v1").appendingPathComponent(endpoint.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))),
            resolvingAgainstBaseURL: false
        )
        if !endpoint.query.isEmpty {
            components?.queryItems = endpoint.query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components?.url else {
            throw APIError(httpStatus: 0, code: "BAD_URL", message: "URL invalide: \(endpoint.path)")
        }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let key = endpoint.idempotencyKey {
            request.setValue(key, forHTTPHeaderField: "Idempotency-Key")
        }
        if !endpoint.anonymous, let token = await tokenStore.accessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body = endpoint.body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }
        return request
    }
}

/// Marqueur pour les réponses sans contenu utile (204 / actions).
public struct EmptyBody: Codable, Sendable { public init() {} }

/// Efface le type d'un `Encodable` pour l'encodage du corps.
private struct AnyEncodable: Encodable {
    private let encodeFunc: (Encoder) throws -> Void
    init(_ wrapped: any Encodable) { self.encodeFunc = wrapped.encode }
    func encode(to encoder: Encoder) throws { try encodeFunc(encoder) }
}

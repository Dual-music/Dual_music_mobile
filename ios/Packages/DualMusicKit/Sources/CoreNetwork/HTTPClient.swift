import Foundation

/// Client HTTP typé de Dual Music (iOS) — équivalent de `ApiClient` côté Android.
///
/// - Préfixe chaque requête par la base `…/api/v1` et attache le Bearer JWT.
/// - Décode l'enveloppe `{ data, meta }` / `{ error }` ; renvoie `data` ou lève ``APIError``.
/// - Sur `401`, effectue **un seul** refresh partagé (single-flight) puis rejoue la requête.
/// - Envoie `Idempotency-Key` quand l'endpoint en fournit une (débits).
///
/// Implémenté en `actor` : l'état de refresh est protégé des accès concurrents, garantissant
/// qu'un seul refresh part même si 10 requêtes échouent en 401 simultanément (les autres
/// attendent son résultat au lieu d'en déclencher d'autres — sinon le backend invaliderait
/// la chaîne de refresh tokens).
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
    ///   - baseURL: URL publique de l'API **SANS** `/api/v1` (ex. `https://api.dualmusic.app`).
    ///   - tokenStore: fournisseur/persistance des jetons (Keychain).
    ///   - refresher: exécuteur du refresh (`CoreAuth`).
    ///   - session: `URLSession` injectable (tests, pinning). Par défaut une session avec
    ///     timeouts explicites (30 s requête / 60 s ressource) plutôt que `.shared`.
    public init(
        baseURL: URL,
        tokenStore: TokenStore,
        refresher: TokenRefresher,
        session: URLSession? = nil
    ) {
        self.baseURL = baseURL
        self.tokenStore = tokenStore
        self.refresher = refresher
        self.session = session ?? HTTPClient.makeDefaultSession()
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    /// Session par défaut : timeouts bornés + pas de cache disque pour les réponses d'API
    /// (les données financières ne doivent jamais être resservies depuis un cache).
    ///
    /// ⚠️ `waitsForConnectivity = true` ignore `timeoutIntervalForRequest` tant qu'aucun
    /// chemin réseau n'est établi : c'est `timeoutIntervalForResource` qui borne alors
    /// l'attente réelle. Avec 60 s, `bootstrap()` (réhydratation au lancement, avant même
    /// l'écran de connexion) restait bloqué une minute entière backend éteint/injoignable —
    /// contraire à l'exigence de recette « backend arrêté → écran de connexion affiché
    /// rapidement ». 15 s reste large pour une requête API mobile et rend l'app réactive.
    private static func makeDefaultSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 15
        config.waitsForConnectivity = true
        return URLSession(configuration: config)
    }

    // MARK: - API publique

    /// Exécute une requête et renvoie uniquement `data`.
    /// - Parameter endpoint: description de la requête.
    /// - Returns: la charge utile décodée.
    /// - Throws: ``APIError``.
    @discardableResult
    public func request<T: Decodable & Sendable>(_ endpoint: Endpoint, as type: T.Type = T.self) async throws -> T {
        try await send(endpoint, type: T.self).data
    }

    /// Exécute une requête et renvoie `data` + `meta` (pagination keyset).
    public func requestWithMeta<T: Decodable & Sendable>(_ endpoint: Endpoint, as type: T.Type = T.self) async throws -> Page<T> {
        let result = try await send(endpoint, type: T.self)
        return Page(data: result.data, meta: result.meta)
    }

    /// Exécute une requête **sans corps de réponse attendu** (204 / endpoints d'action).
    public func send(_ endpoint: Endpoint) async throws {
        _ = try await send(endpoint, type: EmptyBody.self)
    }

    // MARK: - Cœur d'exécution

    private func send<T: Decodable & Sendable>(
        _ endpoint: Endpoint,
        type: T.Type,
        isRetry: Bool = false
    ) async throws -> (data: T, meta: Meta?) {
        let request = try await buildRequest(endpoint)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw APIError.cancelled
        } catch let urlError as URLError where urlError.code == .cancelled {
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
    private func decodeEnvelope<T: Decodable & Sendable>(
        data: Data,
        status: Int,
        type: T.Type
    ) throws -> (data: T, meta: Meta?) {
        // 204 / corps vide → succès sans contenu.
        if status == 204 || data.isEmpty {
            if let empty = EmptyBody() as? T { return (empty, nil) }
            throw APIError(httpStatus: status, code: "EMPTY_BODY", message: "Corps de réponse vide")
        }

        let envelope: Envelope<T>
        do {
            envelope = try decoder.decode(Envelope<T>.self, from: data)
        } catch {
            // Statut déjà en échec → préférer une erreur HTTP claire à une erreur de décodage.
            if !(200...299).contains(status) {
                throw APIError(
                    httpStatus: status,
                    code: "HTTP_\(status)",
                    message: HTTPURLResponse.localizedString(forStatusCode: status)
                )
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
            // Succès sans `data` (endpoints d'action) : toléré si T == EmptyBody.
            if let empty = EmptyBody() as? T { return (empty, envelope.meta) }
            throw APIError(httpStatus: status, code: "NO_DATA", message: "Champ data absent")
        }
        return (payload, envelope.meta)
    }

    // MARK: - Refresh single-flight

    /// Lance (ou rejoint) l'unique tâche de refresh. Renvoie `true` si la session est valide.
    private func performSingleFlightRefresh() async -> Bool {
        if let existing = refreshTask {
            return await existing.value // Rejoindre le refresh déjà en vol.
        }
        let store = tokenStore
        let refresher = self.refresher
        let task = Task { () -> Bool in
            guard let token = await store.refreshToken() else { return false }
            do {
                let pair = try await refresher.refresh(using: token)
                await store.setTokens(access: pair.access, refresh: pair.refresh)
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

    // MARK: - Construction de requête

    private func buildRequest(_ endpoint: Endpoint) async throws -> URLRequest {
        let cleanPath = endpoint.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/v1").appendingPathComponent(cleanPath),
            resolvingAgainstBaseURL: false
        )
        if !endpoint.query.isEmpty {
            // Tri par clé : URL déterministe (utile aux logs, aux tests et au cache HTTP).
            components?.queryItems = endpoint.query
                .sorted { $0.key < $1.key }
                .map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components?.url else {
            throw APIError(httpStatus: 0, code: "BAD_URL", message: "URL invalide : \(endpoint.path)")
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
            do {
                request.httpBody = try encoder.encode(AnyEncodable(body))
            } catch {
                throw APIError(httpStatus: 0, code: "ENCODING_ERROR", message: error.localizedDescription)
            }
        }
        return request
    }
}

/// Efface le type d'un `Encodable` pour l'encodage du corps de requête.
private struct AnyEncodable: Encodable {
    private let encodeFunc: (Encoder) throws -> Void
    init(_ wrapped: any Encodable) { self.encodeFunc = wrapped.encode }
    func encode(to encoder: Encoder) throws { try encodeFunc(encoder) }
}

import Foundation
import CoreNetwork
import DomainModels

/// Exécute le refresh de session (`POST /auth/refresh`).
///
/// Implémente `CoreNetwork.TokenRefresher`. Utilise une `URLSession` **directe** (et non
/// ``CoreNetwork/HTTPClient``) pour éviter la dépendance circulaire : `HTTPClient` a besoin
/// d'un `TokenRefresher`, donc le refresher ne peut pas dépendre de `HTTPClient`.
/// Miroir strict de `AuthRefresher` côté Android.
public struct AuthRefresher: TokenRefresher {

    private let baseURL: URL
    private let session: URLSession

    /// - Parameters:
    ///   - baseURL: URL publique de l'API **SANS** `/api/v1`.
    ///   - session: session injectable (tests). Par défaut, éphémère avec timeout court :
    ///     un refresh lent doit échouer vite pour ne pas bloquer toutes les requêtes en
    ///     attente derrière le single-flight.
    public init(baseURL: URL, session: URLSession? = nil) {
        self.baseURL = baseURL
        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.ephemeral
            config.timeoutIntervalForRequest = 15
            self.session = URLSession(configuration: config)
        }
    }

    public func refresh(using refreshToken: String) async throws -> RefreshedTokens {
        let url = baseURL
            .appendingPathComponent("api/v1")
            .appendingPathComponent(AuthEndpoints.refresh.trimmingCharacters(in: CharacterSet(charactersIn: "/")))

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(RefreshRequest(refreshToken: refreshToken))

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            // Refresh invalide/expiré → `HTTPClient` effacera la session.
            throw APIError.unauthorizedNoRefresh
        }

        // L'enveloppe backend est `{ data: { accessToken, refreshToken, ... } }`.
        let decoded = try JSONDecoder().decode(RefreshEnvelope.self, from: data)
        guard let tokens = decoded.data else { throw APIError.unauthorizedNoRefresh }
        return RefreshedTokens(access: tokens.accessToken, refresh: tokens.refreshToken)
    }

    /// Enveloppe minimale : on ne décode que ce qui est nécessaire au refresh.
    private struct RefreshEnvelope: Decodable { let data: Tokens? }
    private struct Tokens: Decodable {
        let accessToken: String
        let refreshToken: String
    }
}

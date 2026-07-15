import Foundation
import CoreNetwork

/// Exécute le refresh de session (`POST /auth/refresh`).
///
/// Implémente `CoreNetwork.TokenRefresher`. Utilise une `URLSession` **directe** (et non
/// `HTTPClient`) pour éviter la dépendance circulaire : `HTTPClient` a besoin d'un
/// `TokenRefresher`, donc le refresher ne peut pas dépendre de `HTTPClient`.
public struct AuthRefresher: TokenRefresher {

    private let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    public func refresh(using refreshToken: String) async throws -> RefreshedTokens {
        let url = baseURL.appendingPathComponent("api/v1/auth/refresh")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(["refreshToken": refreshToken])

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw APIError.unauthorizedNoRefresh // refresh invalide → session à réinitialiser
        }
        // L'enveloppe backend est `{ data: { accessToken, refreshToken, ... } }`.
        let decoded = try JSONDecoder().decode(RefreshEnvelope.self, from: data)
        guard let tokens = decoded.data else { throw APIError.unauthorizedNoRefresh }
        return RefreshedTokens(access: tokens.accessToken, refresh: tokens.refreshToken)
    }

    private struct RefreshEnvelope: Decodable { let data: Tokens? }
    private struct Tokens: Decodable { let accessToken: String; let refreshToken: String }
}

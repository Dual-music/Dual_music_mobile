import Foundation
import CoreNetwork
import DomainModels

/// Récupère les jetons LiveKit via `POST /livekit/token`.
///
/// Le backend est la **seule autorité** qui émet des jetons signés (les grants dépendent du
/// rôle) : l'app ne fabrique jamais de jeton localement. Miroir de `LiveKitTokenService`
/// côté Android.
public struct LiveKitTokenService: Sendable {

    private let http: HTTPClient

    /// - Parameter http: client HTTP applicatif (Bearer + enveloppe gérés en amont).
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Demande un jeton pour rejoindre une room.
    ///
    /// - Parameters:
    ///   - roomName: nom de room fourni par l'API (ex. `live:abc` ou `duel:123`).
    ///   - isHost: vrai pour l'artiste/host (droits de publication) ; faux pour un viewer.
    ///   - canPublish: vrai pour un invité qui publie dans SA PROPRE room (`live-guest-…`)
    ///     sans être l'hôte — le backend applique `isHost` par défaut si omis.
    /// - Returns: jeton + URL du SFU + identité du participant.
    /// - Throws: ``CoreNetwork/APIError``.
    public func token(roomName: String, isHost: Bool = false, canPublish: Bool = false) async throws -> LiveKitToken {
        try await http.request(
            .post(
                MediaEndpoints.livekitToken,
                body: LiveKitTokenRequest(roomName: roomName, isHost: isHost, canPublish: canPublish)
            ),
            as: LiveKitToken.self
        )
    }
}

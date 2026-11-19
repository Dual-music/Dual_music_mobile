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
    private let deviceId: String?

    /// - Parameters:
    ///   - http: client HTTP applicatif (Bearer + enveloppe gérés en amont).
    ///   - deviceId: identifiant stable de cet appareil (voir ``DeviceIdentity``) : rend
    ///     l'identité LiveKit unique par appareil (même compte iPhone + web sans éjection).
    public init(http: HTTPClient, deviceId: String? = nil) {
        self.http = http
        self.deviceId = deviceId
    }

    /// Demande un jeton pour rejoindre une room.
    ///
    /// - Parameters:
    ///   - roomName: nom de room fourni par l'API (ex. `live-abc` ou `duel-123-artist1`).
    ///   - isHost: vrai pour l'artiste/host (droits de publication) ; faux pour un viewer.
    ///   - canPublish: vrai pour un invité qui publie dans SA PROPRE room (`live-guest-…`)
    ///     sans être l'hôte — le backend applique `isHost` par défaut si omis.
    /// - Returns: jeton + URL du SFU + identité du participant.
    /// - Throws: ``CoreNetwork/APIError``.
    public func token(roomName: String, isHost: Bool = false, canPublish: Bool = false) async throws -> LiveKitToken {
        try await http.request(
            .post(
                MediaEndpoints.livekitToken,
                body: LiveKitTokenRequest(roomName: roomName, isHost: isHost, canPublish: canPublish, deviceId: deviceId)
            ),
            as: LiveKitToken.self
        )
    }
}

/// Identifiant d'installation stable (UUID généré une fois, conservé dans `UserDefaults`).
public enum DeviceIdentity {
    private static let key = "dm_device_id"

    public static var current: String {
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: key), !existing.isEmpty { return existing }
        let fresh = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(16))
        defaults.set(fresh, forKey: key)
        return fresh
    }

    /// `userId` porté par une identité LiveKit (`userId` ou `userId#deviceId`).
    public static func userId(fromIdentity identity: String?) -> String? {
        guard let identity else { return nil }
        return identity.split(separator: "#", maxSplits: 1).first.map(String.init)
    }
}

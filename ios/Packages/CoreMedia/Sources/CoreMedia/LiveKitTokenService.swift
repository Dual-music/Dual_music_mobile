import Foundation
import CoreNetwork

/// Jeton d'accès à une room LiveKit (`{ token, url, identity }`).
public struct LiveKitToken: Decodable, Sendable {
    public let token: String
    public let url: String
    public let identity: String
}

/// Récupère les jetons LiveKit via `POST /livekit/token`.
///
/// Le backend est la seule autorité qui émet des jetons signés (grants selon le rôle) :
/// l'app ne fabrique jamais de jeton localement.
public struct LiveKitTokenService {

    private let http: HTTPClient
    public init(http: HTTPClient) { self.http = http }

    /// Demande un jeton pour rejoindre une room.
    /// - Parameters:
    ///   - roomName: nom de room (ex. `duel:123` ou l'identifiant de room fourni par l'API).
    ///   - isHost: vrai pour l'artiste/host (droits de publication).
    public func token(roomName: String, isHost: Bool = false) async throws -> LiveKitToken {
        let body = ["roomName": roomName, "isHost": isHost] as [String: any Encodable & Sendable]
        return try await http.request(.init(.post, "/livekit/token", body: EncodableDict(body)))
    }
}

/// Petit wrapper pour encoder un dictionnaire hétérogène `[String: Encodable]`.
private struct EncodableDict: Encodable {
    let values: [String: any Encodable & Sendable]
    init(_ values: [String: any Encodable & Sendable]) { self.values = values }
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: DynamicKey.self)
        for (key, value) in values {
            try container.encode(AnyEnc(value), forKey: DynamicKey(stringValue: key)!)
        }
    }
    private struct AnyEnc: Encodable {
        let wrapped: any Encodable
        init(_ w: any Encodable) { wrapped = w }
        func encode(to encoder: Encoder) throws { try wrapped.encode(to: encoder) }
    }
    private struct DynamicKey: CodingKey {
        var stringValue: String; var intValue: Int?
        init?(stringValue: String) { self.stringValue = stringValue }
        init?(intValue: Int) { return nil }
    }
}

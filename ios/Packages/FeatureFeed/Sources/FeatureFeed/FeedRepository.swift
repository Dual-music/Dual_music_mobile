import Foundation
import CoreNetwork

/// Profil d'affichage du host (miroir de `DisplayProfile`).
public struct FeedArtist: Codable, Sendable {
    public let id: String
    public let fullName: String?
    public let avatarUrl: String?
    public let stageName: String?

    enum CodingKeys: String, CodingKey {
        case id
        case fullName = "full_name"
        case avatarUrl = "avatar_url"
        case stageName = "stage_name"
    }
    /// Nom à afficher : nom de scène > nom complet > repli.
    public var displayName: String { stageName ?? fullName ?? "Artiste" }
}

/// Élément du feed = un live en cours (miroir de `shared-domain` `Live`).
public struct LiveItem: Codable, Sendable, Identifiable {
    public let id: String
    public let title: String?
    public let status: String?
    public let roomId: String?
    public let viewerCount: Int?
    public let artistId: String
    public let artist: FeedArtist?

    enum CodingKeys: String, CodingKey {
        case id, title, status, artist
        case roomId = "room_id"
        case viewerCount = "viewer_count"
        case artistId = "artist_id"
    }
    /// Room LiveKit effective (repli `live:id`).
    public var liveKitRoom: String { roomId ?? "live:\(id)" }
}

/// Charge le feed des lives (`GET /lives`).
public struct FeedRepository {

    private let http: HTTPClient
    public init(http: HTTPClient) { self.http = http }

    /// Récupère une page de lives actifs (les plus récents d'abord).
    /// - Parameter cursor: curseur de pagination (keyset) pour la page suivante.
    /// - Returns: les items + le curseur de la page suivante (nil si dernière page).
    public func lives(cursor: String? = nil) async throws -> (items: [LiveItem], nextCursor: String?) {
        var query = ["status": "live", "limit": "10"]
        if let cursor { query["cursor"] = cursor }
        let page: Page<[LiveItem]> = try await http.requestWithMeta(.get("/lives", query: query))
        return (page.data, page.meta?.pagination?.nextCursor)
    }
}

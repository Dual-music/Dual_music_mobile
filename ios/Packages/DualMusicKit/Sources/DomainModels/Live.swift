import Foundation

/// Live spontané d'un artiste (élément du feed vertical).
///
/// Aligné sur la table `artist_lives` + l'`artist` hydraté par le backend. ``liveKitRoom``
/// est le nom de room LiveKit à rejoindre ; ``id`` sert au chat et aux cadeaux (contexte
/// `live:id`). Miroir de `shared-domain/model/Live.kt`.
public struct Live: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let title: String?
    public let status: EventStatus
    public let artistId: String
    public let roomId: String?
    public let viewerCount: Int
    public let recordingURL: String?
    /// Profil d'affichage du host (nom, avatar, nom de scène).
    public let artist: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id, title, status
        case artistId = "artist_id"
        case roomId = "room_id"
        case viewerCount = "viewer_count"
        case recordingURL = "recording_url"
        case artist
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        title = c.opt(String.self, .title)
        status = c.val(EventStatus.self, .status, .live)
        artistId = c.val(String.self, .artistId, "")
        roomId = c.opt(String.self, .roomId)
        viewerCount = c.int(.viewerCount)
        recordingURL = c.opt(String.self, .recordingURL)
        artist = c.opt(DisplayProfile.self, .artist)
    }

    /// Room LiveKit effective (repli sur `live:id` si `room_id` est absent).
    public var liveKitRoom: String { roomId ?? "live:\(id)" }
}

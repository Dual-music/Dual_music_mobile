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
    /// Dédicaces activées pour ce live (réglage hôte, réactif en direct via l'événement
    /// temps réel `settings`).
    public let allowsDedications: Bool
    /// Prix minimum d'une dédicace propre à CE live (`nil` = défaut global,
    /// `GET /settings/public/economic_config`).
    public let dedicationMinPriceCredits: Double?
    /// Demandes d'invité (« lever la main ») activées pour ce live.
    public let allowGuests: Bool
    /// Chat activé pour ce live (jamais délégué à un modérateur, réservé à l'hôte).
    public let chatEnabled: Bool
    /// Profil d'affichage du host (nom, avatar, nom de scène).
    public let artist: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id, title, status
        case artistId = "artist_id"
        case roomId = "room_id"
        case viewerCount = "viewer_count"
        case recordingURL = "recording_url"
        case allowsDedications = "allows_dedications"
        case dedicationMinPriceCredits = "dedication_min_price_credits"
        case allowGuests = "allow_guests"
        case chatEnabled = "chat_enabled"
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
        allowsDedications = c.bool(.allowsDedications, true)
        dedicationMinPriceCredits = c.amountIfPresent(.dedicationMinPriceCredits)
        allowGuests = c.bool(.allowGuests, true)
        chatEnabled = c.bool(.chatEnabled, true)
        artist = c.opt(DisplayProfile.self, .artist)
    }

    /// Room LiveKit effective (repli sur `live-id` si `room_id` est absent).
    ///
    /// ⚠️ Le tiret compte : le backend/Android dérivent `"live-$id"` (`Live.kt`) — le repli
    /// utilisait `"live:\(id)"` (deux-points) avant ce correctif, ce qui aurait empêché de
    /// rejoindre la bonne room LiveKit pour tout live sans `room_id` explicite.
    public var liveKitRoom: String { roomId ?? "live-\(id)" }
}

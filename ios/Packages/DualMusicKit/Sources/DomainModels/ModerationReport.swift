import Foundation

/// Signalement de contenu + bannissement — miroir des `*Repository.kt` Android
/// (`LiveRepository`, `DuelRepository`, `CompetitionRepository`, `ConcertRepository`).
///
/// ⚠️ Contrairement au reste du contrat réseau, il n'existe **aucun DTO Kotlin partagé** pour
/// ces deux actions : chaque repository Android construit son propre JSON inline, et
/// Compétition utilise même des endpoints et une forme de requête entièrement différents des
/// trois autres (table `competition_reports`/`competition_bans` dédiée, pas de `streamType`).
/// Ce fichier centralise seulement ce qui est réellement commun aux quatre repositories iOS :
/// les chemins et le vocabulaire des motifs de signalement.
public enum ModerationReportEndpoints {
    /// Signalement d'un live, duel ou concert (table `live_reports`, distinguée par
    /// `streamType` dans le corps de la requête).
    public static let reportsLive = "/moderation/reports/live"
    /// Signalement d'une compétition (table `competition_reports` dédiée).
    public static let reportsCompetition = "/moderation/reports/competition"
    /// Bannissement d'un spectateur d'un live, duel ou concert (table `stream_bans`,
    /// distinguée par `streamType`).
    public static let streamBans = "/moderation/stream-bans"
    /// Bannissement d'un spectateur/candidat d'une compétition (table `competition_bans`
    /// dédiée, événement temps réel `competition:banned`).
    public static let competitionBans = "/moderation/competition-bans"
}

/// Motif de signalement — **clé stable** envoyée au backend, jamais le texte localisé
/// affiché à l'écran (pour que les signalements restent agrégeables côté admin quelle que
/// soit la langue de l'app qui les a envoyés).
///
/// Miroir des clés du composant partagé `core/ui/live/ReportDialog` Android (utilisé par
/// Duel/Compétition/Concert). L'écran Live d'Android envoie encore le texte localisé
/// affiché — écart connu côté Android, non reproduit ici.
public enum ReportReason: String, Sendable, CaseIterable {
    case inappropriate
    case harassment
    case spam
    case violence
}

/// Modération par évènement — hôte principal (artiste pour live/concert, manager pour
/// duel/compétition) + jusqu'à ``maxEventModerators`` spectateurs qu'il désigne pour
/// l'accompagner (bannir, masquer un message — jamais activer/désactiver le chat, réservé à
/// l'hôte). Miroir de `shared-domain/moderation/ModerationDtos.kt` (DTO Kotlin PARTAGÉ,
/// contrairement au signalement/bannissement ci-dessus).
public enum ModerationEndpoints {
    /// Spectateurs actuellement connectés à la room (hôte uniquement) — vivier du picker.
    public static func viewers(_ type: String, _ id: String) -> String { "/moderation/events/\(type)/\(id)/viewers" }
    /// Modérateurs désignés de cet évènement.
    public static func moderators(_ type: String, _ id: String) -> String { "/moderation/events/\(type)/\(id)/moderators" }
    /// Révoque un modérateur désigné (hôte uniquement).
    public static func revokeModerator(_ type: String, _ id: String, _ userId: String) -> String {
        "/moderation/events/\(type)/\(id)/moderators/\(userId)"
    }
}

/// Nombre maximum de modérateurs désignés par évènement (miroir du backend).
public let maxEventModerators = 2

/// Un modérateur désigné, hydraté avec son profil d'affichage.
public struct EventModerator: Decodable, Sendable, Identifiable, Equatable {
    public let id: String
    public let eventType: String
    public let eventId: String
    public let userId: String
    public let appointedBy: String
    public let user: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case eventType = "event_type"
        case eventId = "event_id"
        case userId = "user_id"
        case appointedBy = "appointed_by"
        case user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        eventType = c.val(String.self, .eventType, "")
        eventId = c.val(String.self, .eventId, "")
        userId = c.val(String.self, .userId, "")
        appointedBy = c.val(String.self, .appointedBy, "")
        user = c.opt(DisplayProfile.self, .user)
    }

    /// Nom à afficher : profil hydraté, sinon les 8 premiers caractères de l'id.
    @MainActor
    public var displayName: String { user?.displayName ?? String(userId.prefix(8)) }
}

/// Corps de `POST /moderation/events/:type/:id/moderators`.
public struct AppointModeratorBody: Encodable, Sendable {
    public let userId: String
    public init(userId: String) { self.userId = userId }
}

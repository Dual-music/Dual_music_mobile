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

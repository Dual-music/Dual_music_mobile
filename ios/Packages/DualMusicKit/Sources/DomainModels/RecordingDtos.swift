import Foundation

/// État d'enregistrement serveur (LiveKit Egress) d'un direct — miroir exact de
/// `RecordingStatus` (shared-domain, `com.dualmusic.domain.recording`). Pas de `CodingKeys` :
/// le DTO Kotlin n'a aucun `@SerialName`, les clés JSON sont donc déjà en camelCase.
///
/// L'egress LiveKit n'a pas de vraie pause : « Pause » arrête le segment en cours, « Reprendre »
/// en démarre un nouveau sous la même session (recollés en un seul fichier à la sauvegarde). Le
/// chrono côté client s'obtient en additionnant `accumulatedSeconds` (segments déjà clos) au
/// temps écoulé depuis `runStartedAt` quand `active` est vrai.
public struct RecordingStatus: Decodable, Sendable, Equatable {
    /// `off` (aucun) | `auto` (auto au passage en live) | `manual` (l'hôte lance).
    public let mode: String
    /// Un segment est en cours d'enregistrement.
    public let active: Bool
    /// En pause (segment arrêté, session ouverte).
    public let paused: Bool
    /// Sauvegarde en cours (dernier segment en clôture / recollage ffmpeg).
    public let finalizing: Bool
    /// Un enregistrement récent a échoué (signalé une fois, `error` porte le détail technique).
    public let failed: Bool
    public let error: String?
    /// Durée cumulée des segments déjà clos (secondes).
    public let accumulatedSeconds: Int
    /// Horodatage ISO de départ du segment en cours (nil si pas actif).
    public let runStartedAt: String?

    public init(
        mode: String = "off",
        active: Bool = false,
        paused: Bool = false,
        finalizing: Bool = false,
        failed: Bool = false,
        error: String? = nil,
        accumulatedSeconds: Int = 0,
        runStartedAt: String? = nil
    ) {
        self.mode = mode
        self.active = active
        self.paused = paused
        self.finalizing = finalizing
        self.failed = failed
        self.error = error
        self.accumulatedSeconds = accumulatedSeconds
        self.runStartedAt = runStartedAt
    }
}

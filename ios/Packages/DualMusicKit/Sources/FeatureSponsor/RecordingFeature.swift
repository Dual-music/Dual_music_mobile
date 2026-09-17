import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Corps commun des actions d'enregistrement (`POST /recordings/...`).
private struct RecordingActionBody: Encodable, Sendable {
    let sourceType: String
    let sourceId: String
}

/// Pilotage de l'enregistrement serveur (LiveKit Egress) d'un direct.
///
/// Colocalisé dans le module partagé des contrôles live (comme la pub sponsor), car utilisé
/// par toutes les rooms (live/duel/concert/compétition). Le mode par type est choisi par
/// l'admin (`recording_config`) ; l'autorisation est imposée côté serveur.
public struct RecordingRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// État : mode admin (off/auto/manual) + enregistrement en cours.
    public func status(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(
            .get(RecordingEndpoints.status, query: ["sourceType": sourceType, "sourceId": sourceId]),
            as: RecordingStatus.self
        )
    }

    /// L'hôte/manager lance l'enregistrement (mode manual).
    public func start(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(.post(RecordingEndpoints.start, body: RecordingActionBody(sourceType: sourceType, sourceId: sourceId)), as: RecordingStatus.self)
    }

    /// Met en pause (arrête le segment en cours ; l'egress LiveKit n'a pas de vraie pause).
    public func pause(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(.post(RecordingEndpoints.pause, body: RecordingActionBody(sourceType: sourceType, sourceId: sourceId)), as: RecordingStatus.self)
    }

    /// Reprend un enregistrement en pause (nouveau segment sous la même session).
    public func resume(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(.post(RecordingEndpoints.resume, body: RecordingActionBody(sourceType: sourceType, sourceId: sourceId)), as: RecordingStatus.self)
    }

    /// Annule tout l'enregistrement en cours — aucun replay n'est créé.
    public func cancel(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(.post(RecordingEndpoints.cancel, body: RecordingActionBody(sourceType: sourceType, sourceId: sourceId)), as: RecordingStatus.self)
    }

    /// L'hôte/manager sauvegarde l'enregistrement (les segments sont recollés en un seul fichier).
    public func stop(sourceType: String, sourceId: String) async throws -> RecordingStatus {
        try await http.request(.post(RecordingEndpoints.stop, body: RecordingActionBody(sourceType: sourceType, sourceId: sourceId)), as: RecordingStatus.self)
    }
}

/// État + actions d'enregistrement d'une room, réutilisable par les ViewModels live/duel/
/// concert/compétition (comme un futur `SponsorAdHolder`). L'UI n'affiche les contrôles que
/// si le mode admin est `manual` ; un indicateur « REC » suffit en `auto`.
///
/// L'egress LiveKit n'a pas de vraie pause : ``pause()`` arrête le segment en cours, ``resume()``
/// en démarre un nouveau sous la même session (le serveur les recolle en UNE vidéo à ``save()``).
@Observable
@MainActor
public final class RecordingHolder {

    /// Mode admin : off | auto | manual.
    public private(set) var mode = "off"
    /// Un segment est en cours d'enregistrement.
    public private(set) var active = false
    /// En pause (session ouverte, rien n'enregistre actuellement).
    public private(set) var paused = false
    /// Sauvegarde en cours (dernier segment en clôture / recollage ffmpeg).
    public private(set) var finalizing = false
    /// Un enregistrement récent a échoué côté serveur — reste vrai ~2 min.
    public private(set) var failed = false
    /// Détail technique du dernier échec (voir ``failed``).
    public private(set) var error: String?
    /// Durée cumulée des segments déjà clos (secondes).
    public private(set) var accumulatedSeconds = 0
    /// Horodatage ISO de départ du segment en cours (nil si rien n'est actif).
    public private(set) var runStartedAt: String?
    /// Verrou anti double-tap.
    public private(set) var busy = false

    private let sourceType: String
    private let sourceId: String
    private let repo: RecordingRepository
    private var pollTask: Task<Void, Never>?

    /// - Parameters:
    ///   - sourceType: `live` | `duel` | `concert` | `competition`.
    ///   - sourceId: identifiant de l'événement.
    ///   - repo: accès REST (status/start/pause/resume/cancel/stop).
    public init(sourceType: String, sourceId: String, repo: RecordingRepository) {
        self.sourceType = sourceType
        self.sourceId = sourceId
        self.repo = repo
    }

    /// Sonde périodiquement l'état côté serveur (parité web, toutes les 6 s) — sans ça, un
    /// dénouement arrivant APRÈS la réponse immédiate d'une action (ex. le dernier segment
    /// échoue au webhook egress, bien après que ``save()`` ait déjà répondu « stopping ») n'est
    /// jamais repris côté client. Auto-stoppée en se désallouant (capture `weak self`).
    public func startPolling(intervalNanos: UInt64 = 6_000_000_000) {
        pollTask?.cancel()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                self.refresh()
                try? await Task.sleep(nanoseconds: intervalNanos)
            }
        }
    }

    private func apply(_ s: RecordingStatus) {
        mode = s.mode
        active = s.active
        paused = s.paused
        finalizing = s.finalizing
        failed = s.failed
        error = s.error
        accumulatedSeconds = s.accumulatedSeconds
        runStartedAt = s.runStartedAt
    }

    /// Rafraîchit l'état depuis le serveur (mode + session en cours + chrono).
    public func refresh() {
        Task {
            if let s = try? await repo.status(sourceType: sourceType, sourceId: sourceId) { apply(s) }
        }
    }

    private func run(_ action: @escaping () async throws -> RecordingStatus, onError: ((String) -> Void)? = nil) {
        Task {
            busy = true
            do {
                apply(try await action())
            } catch {
                onError?((error as? APIError)?.message ?? "error")
                // Auto-resynchronisation : un échec ici peut survenir APRÈS que le serveur ait
                // déjà changé d'état — sans ce refresh, l'état local reste bloqué sur l'ancienne
                // valeur et tous les boutons suivants semblent « ne rien faire ».
                refresh()
            }
            busy = false
        }
    }

    /// Démarre l'enregistrement (mode manual).
    public func start(onError: ((String) -> Void)? = nil) { run({ try await self.repo.start(sourceType: self.sourceType, sourceId: self.sourceId) }, onError: onError) }
    /// Met en pause l'enregistrement en cours.
    public func pause(onError: ((String) -> Void)? = nil) { run({ try await self.repo.pause(sourceType: self.sourceType, sourceId: self.sourceId) }, onError: onError) }
    /// Reprend un enregistrement en pause.
    public func resume(onError: ((String) -> Void)? = nil) { run({ try await self.repo.resume(sourceType: self.sourceType, sourceId: self.sourceId) }, onError: onError) }
    /// Annule tout l'enregistrement en cours — aucun replay n'est créé.
    public func cancel(onError: ((String) -> Void)? = nil) { run({ try await self.repo.cancel(sourceType: self.sourceType, sourceId: self.sourceId) }, onError: onError) }
    /// Sauvegarde l'enregistrement (segments recollés en un seul fichier).
    public func save(onError: ((String) -> Void)? = nil) { run({ try await self.repo.stop(sourceType: self.sourceType, sourceId: self.sourceId) }, onError: onError) }

    /// Démarre/sauvegarde en un seul geste (UI à bouton unique, sans pause/annuler).
    public func toggle() {
        if active || paused { save() } else { start() }
    }
}

/// Contrôle d'enregistrement pour l'hôte/manager, piloté par le mode admin :
/// `manual` → bouton Enregistrer/Arrêter ; `auto` (+ actif) → simple indicateur « REC ».
public struct RecordingHostButton: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let mode: String
    private let active: Bool
    private let busy: Bool
    private let onToggle: () -> Void

    public init(mode: String, active: Bool, busy: Bool, onToggle: @escaping () -> Void) {
        self.mode = mode
        self.active = active
        self.busy = busy
        self.onToggle = onToggle
    }

    public var body: some View {
        if mode == "manual" {
            DMButton(active ? s.recordStop : s.recordStart, style: active ? .destructive : .secondary, isEnabled: !busy, action: onToggle)
        } else if mode == "auto" && active {
            HStack(spacing: 4) {
                Text("●").foregroundStyle(.white)
                Text(s.recording).foregroundStyle(.white).font(DMFont.caption).bold()
            }
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(Color(red: 0.86, green: 0.15, blue: 0.15), in: Capsule())
        }
    }
}

/// Petite pastille rouge/orange à poser sur une icône de rail dédiée à l'enregistrement.
public struct RecordingRailBadge: View {
    private let active: Bool
    private let paused: Bool

    public init(active: Bool, paused: Bool) {
        self.active = active
        self.paused = paused
    }

    public var body: some View {
        if active || paused {
            Circle()
                .fill(active ? Color(red: 0.86, green: 0.15, blue: 0.15) : Color(red: 0.92, green: 0.7, blue: 0.03))
                .frame(width: 14, height: 14)
        }
    }
}

/// `125` → `"02:05"` ; `4000` → `"1:06:40"`.
private func formatRecDuration(_ totalSeconds: Int) -> String {
    let sec = max(totalSeconds, 0)
    let h = sec / 3600, m = (sec % 3600) / 60, ss = sec % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, ss) : String(format: "%02d:%02d", m, ss)
}

/// Contrôles complets d'enregistrement (mode `manual`) : gros chrono + Pause/Reprendre +
/// Annuler + Sauvegarder — pensés pour une feuille dédiée.
public struct RecordingSessionControls: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let mode: String
    private let active: Bool
    private let paused: Bool
    private let finalizing: Bool
    private let accumulatedSeconds: Int
    private let runStartedAt: String?
    private let busy: Bool
    private let onStart: () -> Void
    private let onPause: () -> Void
    private let onResume: () -> Void
    private let onCancel: () -> Void
    private let onSave: () -> Void

    @State private var liveSeconds = 0

    public init(
        mode: String, active: Bool, paused: Bool, finalizing: Bool,
        accumulatedSeconds: Int, runStartedAt: String?, busy: Bool,
        onStart: @escaping () -> Void, onPause: @escaping () -> Void, onResume: @escaping () -> Void,
        onCancel: @escaping () -> Void, onSave: @escaping () -> Void
    ) {
        self.mode = mode
        self.active = active
        self.paused = paused
        self.finalizing = finalizing
        self.accumulatedSeconds = accumulatedSeconds
        self.runStartedAt = runStartedAt
        self.busy = busy
        self.onStart = onStart
        self.onPause = onPause
        self.onResume = onResume
        self.onCancel = onCancel
        self.onSave = onSave
    }

    private func recompute() {
        var seconds = accumulatedSeconds
        if active, let runStartedAt, let started = parseISODate(runStartedAt) {
            seconds += max(Int(Date().timeIntervalSince(started)), 0)
        }
        liveSeconds = seconds
    }

    public var body: some View {
        Group {
            if mode != "manual" {
                EmptyView()
            } else if finalizing {
                HStack {
                    Spacer()
                    Text("⏳ \(s.recordFinalizing)").foregroundStyle(.white).font(DMFont.body).bold()
                    Spacer()
                }
                .padding(.vertical, 12)
                .background(theme.colors.mutedForeground, in: Capsule())
            } else if !active && !paused {
                VStack(alignment: .leading, spacing: 8) {
                    Text(s.recordingNotStartedHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    DMButton(s.recordStart, style: .secondary, isEnabled: !busy, action: onStart)
                }
            } else {
                VStack(spacing: 10) {
                    HStack {
                        Spacer()
                        Text("● \(formatRecDuration(liveSeconds))\(paused ? "  ·  \(s.recordPausedSuffix)" : "")")
                            .foregroundStyle(.white).font(DMFont.headline).bold()
                        Spacer()
                    }
                    .padding(.vertical, 10)
                    .background(Color(red: 0.86, green: 0.15, blue: 0.15), in: Capsule())

                    if active {
                        DMButton(s.recordPause, style: .secondary, isEnabled: !busy, action: onPause)
                    } else {
                        DMButton(s.recordResume, style: .secondary, isEnabled: !busy, action: onResume)
                    }
                    HStack(spacing: 8) {
                        DMButton(s.cancel, style: .destructive, isEnabled: !busy, action: onCancel)
                        DMButton(s.save, style: .primary, isEnabled: !busy, action: onSave)
                    }
                }
                .task(id: "\(active)-\(runStartedAt ?? "")") {
                    recompute()
                    guard active else { return }
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        recompute()
                    }
                }
            }
        }
    }
}

private func parseISODate(_ iso: String) -> Date? {
    ISO8601DateFormatter().date(from: iso)
        ?? { let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f.date(from: iso) }()
}

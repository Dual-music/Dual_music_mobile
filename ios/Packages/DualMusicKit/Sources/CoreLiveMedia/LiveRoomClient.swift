import Foundation
import Observation
import LiveKit
import DomainModels

/// État de connexion simplifié pour l'UI (miroir de `LiveConnectionState` Android).
public enum LiveConnectionState: Sendable, Equatable {
    case idle
    case connecting
    case connected
    case reconnecting
    case failed(String)
}

/// Client d'une room live LiveKit (viewer) — iOS.
///
/// Enveloppe le `Room` LiveKit : connexion via jeton backend, souscription automatique aux
/// pistes, et exposition de la **piste vidéo primaire** (le host) à rendre avec
/// `SwiftUIVideoView`. Le décodage est matériel (VideoToolbox), géré par le SDK.
///
/// `@Observable @MainActor` : les vues observent ``connectionState`` et
/// ``primaryVideoTrack`` sans `ObservableObject` ni `@Published`.
///
/// > Robustesse : en plus du `RoomDelegate`, un rafraîchissement périodique léger
/// > (1 s, uniquement pendant que la room est connectée) recalcule la piste primaire. Cela
/// > garantit l'apparition de la vidéo même si une notification de souscription est manquée
/// > — le coût est négligeable (une lecture de dictionnaire par seconde).
@Observable
@MainActor
public final class LiveRoomClient {

    /// Room LiveKit sous-jacente (exposée pour `SwiftUIVideoView` et le diagnostic).
    public let room = Room()

    /// État de connexion observable.
    public private(set) var connectionState: LiveConnectionState = .idle

    /// Piste vidéo du host à afficher (`nil` tant qu'aucune piste n'est souscrite).
    public private(set) var primaryVideoTrack: VideoTrack?

    /// MA propre piste vidéo publiée (hôte/invité qui diffuse) — pour l'auto-aperçu.
    /// `nil` tant que la caméra n'est pas activée.
    public private(set) var localVideoTrack: LocalVideoTrack?

    /// Vrai si MA caméra est active (publiée).
    public private(set) var isCameraEnabled = false

    /// Vrai si MON micro est actif (publié).
    public private(set) var isMicrophoneEnabled = false

    /// Filtre couleur actif (id de ``VideoFilterPresets/all``, `"none"` par défaut).
    public private(set) var activeFilterId = "none"

    private let tokenService: LiveKitTokenService
    private let colorFilterProcessor = ColorFilterVideoProcessor()
    private var delegateProxy: RoomDelegateProxy?
    private var pollTask: Task<Void, Never>?

    /// Paramètres du dernier ``join(roomName:isHost:prewarmedToken:canPublish:)`` — pour
    /// rejoindre automatiquement après une coupure.
    private struct JoinParams { let roomName: String; let isHost: Bool; let canPublish: Bool }
    private var lastJoin: JoinParams?
    /// Vrai entre ``leave()`` et le prochain `join` : aucune reconnexion automatique.
    private var intentionalLeave = false
    private var reconnectTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private static let reconnectDelays: [UInt64] = [1_500, 3_000, 6_000, 10_000, 15_000, 30_000]

    /// - Parameter tokenService: service d'émission de jetons (backend).
    public init(tokenService: LiveKitTokenService) {
        self.tokenService = tokenService
    }

    /// Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
    /// connecte au SFU. En cas d'échec ou de coupure non définitive, rejoint automatiquement
    /// (backoff progressif) — avant, une déconnexion laissait la room morte sans recours :
    /// spectateur sans son, ou invité qui n'entendait plus UN participant précis.
    ///
    /// - Parameters:
    ///   - roomName: nom de room fourni par l'API.
    ///   - isHost: vrai pour l'artiste (publication), faux pour un viewer.
    ///   - prewarmedToken: jeton déjà obtenu par le feed (prefetch) — évite un aller-retour
    ///     réseau au scroll et réduit fortement la latence d'entrée-live.
    ///   - canPublish: vrai pour un invité qui publie dans SA PROPRE room (`live-guest-…`)
    ///     sans être l'hôte de la room principale.
    public func join(
        roomName: String,
        isHost: Bool = false,
        prewarmedToken: LiveKitToken? = nil,
        canPublish: Bool = false
    ) async {
        guard connectionState != .connecting, connectionState != .connected else { return }
        intentionalLeave = false
        lastJoin = JoinParams(roomName: roomName, isHost: isHost, canPublish: canPublish)
        reconnectTask?.cancel()
        reconnectTask = nil
        connectionState = reconnectAttempt > 0 ? .reconnecting : .connecting
        do {
            if delegateProxy == nil {
                let proxy = RoomDelegateProxy { [weak self] state in
                    Task { @MainActor in self?.handleRoomEvent(state) }
                }
                delegateProxy = proxy
                room.add(delegate: proxy)
            }

            let creds: LiveKitToken
            if let prewarmedToken {
                creds = prewarmedToken
            } else {
                creds = try await tokenService.token(roomName: roomName, isHost: isHost, canPublish: canPublish)
            }

            // `autoSubscribe` par défaut : un viewer reçoit la couche simulcast adaptée à
            // la taille de rendu et à la bande passante disponible.
            try await room.connect(url: creds.url, token: creds.token)
            reconnectAttempt = 0
            connectionState = .connected
            refreshPrimaryTrack()
            refreshRemoteTiles()
            startPolling()
        } catch {
            connectionState = .failed(error.localizedDescription)
            // Jeton pré-chauffé périmé ou réseau instable : on retente avec un jeton frais.
            scheduleReconnect(final: false)
        }
    }

    /// Programme une reconnexion après une coupure NON définitive (`final == false`).
    /// Connecté ailleurs (`duplicateIdentity`), expulsé, room fermée ou départ volontaire → stop.
    private func scheduleReconnect(final: Bool) {
        guard !intentionalLeave, let params = lastJoin else { return }
        if final { return }
        guard reconnectAttempt < Self.reconnectDelays.count else {
            connectionState = .failed("Connexion au direct perdue")
            return
        }
        let delayMs = Self.reconnectDelays[reconnectAttempt]
        reconnectAttempt += 1
        connectionState = .reconnecting
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: delayMs * 1_000_000)
            guard let self, !Task.isCancelled, !self.intentionalLeave else { return }
            let wasCam = self.isCameraEnabled
            let wasMic = self.isMicrophoneEnabled
            await self.join(roomName: params.roomName, isHost: params.isHost, canPublish: params.canPublish)
            // Une déconnexion complète dépublie nos pistes : on republie si on diffusait.
            if case .connected = self.connectionState {
                if wasCam { await self.setCamera(enabled: true) }
                if wasMic { await self.setMicrophone(enabled: true) }
            }
        }
    }

    /// Quitte la room (sortie d'écran, scroll hors du live, mise en arrière-plan). Aucune
    /// reconnexion automatique ensuite.
    public func leave() async {
        intentionalLeave = true
        reconnectTask?.cancel()
        reconnectTask = nil
        reconnectAttempt = 0
        stopPolling()
        await room.disconnect()
        primaryVideoTrack = nil
        remoteTiles = [:]
        localVideoTrack = nil
        isCameraEnabled = false
        isMicrophoneEnabled = false
        colorFilterProcessor.matrix = nil
        activeFilterId = "none"
        connectionState = .idle
    }

    // MARK: - Diffusion (hôte/invité qui publie)

    /// Démarre la diffusion : active caméra + micro sur la room déjà rejointe (`join(isHost:
    /// true)`). Chaque piste est activée indépendamment : un refus de permission caméra
    /// n'empêche pas le micro de démarrer, et inversement.
    public func startBroadcast() async {
        await setCamera(enabled: true)
        await setMicrophone(enabled: true)
    }

    /// Coupe caméra + micro sans quitter la room (pause du direct — `endLive` reste une
    /// action séparée côté appelant).
    public func stopBroadcast() async {
        await setCamera(enabled: false)
        await setMicrophone(enabled: false)
    }

    /// Coupe/rétablit MA caméra.
    public func setCamera(enabled: Bool) async {
        do {
            let publication = try await room.localParticipant.setCamera(enabled: enabled)
            localVideoTrack = enabled ? (publication?.track as? LocalVideoTrack) : nil
            // La piste locale change d'identité à chaque (ré)activation caméra : le filtre
            // doit être réattaché à chaque fois pour survivre à un cycle coupure/rétablissement.
            localVideoTrack?.processor = colorFilterProcessor
            isCameraEnabled = enabled
        } catch {
            // Échec (permission refusée, appareil occupé…) : l'état affiché reste inchangé,
            // l'utilisateur peut retenter via le même bouton.
        }
    }

    /// Coupe/rétablit MON micro.
    public func setMicrophone(enabled: Bool) async {
        do {
            _ = try await room.localParticipant.setMicrophone(enabled: enabled)
            isMicrophoneEnabled = enabled
        } catch {
            // Idem : état inchangé en cas d'échec.
        }
    }

    /// Bascule caméra avant/arrière (aucun effet si la caméra n'est pas active).
    public func switchCamera() async {
        guard
            let track = room.localParticipant.localVideoTracks.first?.track as? LocalVideoTrack,
            let camera = track.capturer as? CameraCapturer
        else { return }
        _ = try? await camera.switchCameraPosition()
    }

    /// Applique un filtre couleur (voir ``VideoFilterPresets/all``). Persiste même si la
    /// caméra est coupée/rétablie ensuite — la matrice est réappliquée à la nouvelle piste
    /// locale par ``setCamera(enabled:)``.
    /// - Parameters:
    ///   - id: identifiant du preset (juste pour ``activeFilterId``, l'affichage du picker).
    ///   - matrix: matrice 4×4 ligne-major, `nil` = passthrough (« Aucun »).
    public func setColorFilter(id: String, matrix: [Float]?) {
        colorFilterProcessor.matrix = matrix
        activeFilterId = id
    }

    // MARK: - Interne

    /// Applique un événement de room remonté par le proxy de délégué.
    private func handleRoomEvent(_ event: RoomProxyEvent) {
        switch event {
        case .tracksChanged:
            refreshPrimaryTrack()
            refreshRemoteTiles()
        case .reconnecting:
            connectionState = .reconnecting
        case .reconnected:
            connectionState = .connected
            refreshPrimaryTrack()
            refreshRemoteTiles()
        case .disconnected(let final):
            connectionState = .idle
            primaryVideoTrack = nil
            remoteTiles = [:]
            stopPolling()
            scheduleReconnect(final: final)
        }
    }

    /// Piste vidéo primaire = première piste vidéo distante souscrite (le host). Convient à
    /// tout écran à UN SEUL publieur potentiel par room (Live, Duel par slot, Concert).
    private func refreshPrimaryTrack() {
        let track = room.remoteParticipants.values
            .flatMap { $0.videoTracks }
            .compactMap { $0.track as? VideoTrack }
            .first
        if track !== primaryVideoTrack { primaryVideoTrack = track }
    }

    /// Pistes distantes indexées par `userId` — pour un mode multi-diffuseur où PLUSIEURS
    /// participants publient dans la MÊME room (ex. Compétition en mode `"online"` : manager +
    /// candidats approuvés). N'affecte jamais ``primaryVideoTrack``.
    ///
    /// L'identité LiveKit brute est `userId#deviceId` (voir `LiveKitTokenRequest.deviceId`) ; on
    /// la ramène au `userId` pour que les comparaisons avec les ids métier (candidats, bannis,
    /// focus imposé) restent valables.
    public private(set) var remoteTiles: [String: VideoTrack] = [:]

    /// État micro de CHAQUE participant distant, par `userId` (piste audio publiée et non coupée).
    public private(set) var remoteMicByUser: [String: Bool] = [:]

    /// Vrai si le jeton courant (ou une mise à jour à chaud du SFU) autorise la publication.
    public var hasPublishPermission: Bool { room.localParticipant.permissions.canPublish }

    private func refreshRemoteTiles() {
        var tiles: [String: VideoTrack] = [:]
        var mics: [String: Bool] = [:]
        for participant in room.remoteParticipants.values {
            guard let identity = DeviceIdentity.userId(fromIdentity: participant.identity?.stringValue) else { continue }
            mics[identity] = participant.audioTracks.contains { !$0.isMuted }
            guard let track = participant.videoTracks.compactMap({ $0.track as? VideoTrack }).first else { continue }
            tiles[identity] = track
        }
        remoteTiles = tiles
        if mics != remoteMicByUser { remoteMicByUser = mics }
    }

    /// MON identité ramenée au `userId` — pour indexer MA propre tuile aux côtés de
    /// ``remoteTiles`` dans un rendu multi-diffuseur (ex. Compétition, focus imposé par
    /// identité). `nil` avant la connexion à la room.
    public var localIdentity: String? { DeviceIdentity.userId(fromIdentity: room.localParticipant.identity?.stringValue) }

    /// Filet de sécurité : recalcule la piste primaire chaque seconde tant qu'on est connecté.
    private func startPolling() {
        stopPolling()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                if case .connected = self.connectionState {
                    self.refreshPrimaryTrack()
                    self.refreshRemoteTiles()
                } else if case .reconnecting = self.connectionState {
                    continue
                } else {
                    return
                }
            }
        }
    }

    private func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
    }

    // Pas de `deinit { pollTask?.cancel() }` : un `deinit` tourne dans un contexte non
    // isolé et ne peut pas accéder à une propriété @MainActor de façon synchrone. Inutile
    // de toute façon : la boucle de `startPolling()` capture `self` en `weak` et s'arrête
    // d'elle-même (au plus 1 s) dès que l'instance est désallouée.
}

/// Événements de room simplifiés remontés à ``LiveRoomClient``.
private enum RoomProxyEvent: Sendable {
    case tracksChanged
    case reconnecting
    case reconnected
    /// `final == true` : ne pas retenter (déconnexion volontaire, connecté ailleurs, expulsé,
    /// room fermée).
    case disconnected(final: Bool)
}

/// Une déconnexion est « définitive » quand retenter serait inutile ou nuisible (ping-pong entre
/// deux connexions du même compte, room supprimée, participant retiré, droits insuffisants).
/// `nil` = déconnexion sans erreur (volontaire) : pas de reconnexion non plus — `leave()` pose de
/// toute façon `intentionalLeave`. Cas vérifiés dans `LiveKitErrorType` (client-sdk-swift 2.x).
private func isFinalDisconnect(_ error: LiveKitError?) -> Bool {
    guard let error else { return true }
    switch error.type {
    case .duplicateIdentity, .participantRemoved, .roomDeleted, .insufficientPermissions, .cancelled:
        return true
    default:
        return false
    }
}

/// Proxy de `RoomDelegate` : traduit les callbacks du SDK en ``RoomProxyEvent``.
///
/// `RoomDelegate` fournit des implémentations par défaut : on n'implémente que ce dont
/// l'app a besoin (souscription de pistes + état de connexion).
private final class RoomDelegateProxy: RoomDelegate {
    private let onEvent: (RoomProxyEvent) -> Void

    init(onEvent: @escaping (RoomProxyEvent) -> Void) {
        self.onEvent = onEvent
    }

    func room(_ room: Room, participant: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        onEvent(.tracksChanged)
    }

    func room(_ room: Room, participant: RemoteParticipant, didUnsubscribeTrack publication: RemoteTrackPublication) {
        onEvent(.tracksChanged)
    }

    func room(_ room: Room, participantDidConnect participant: RemoteParticipant) {
        onEvent(.tracksChanged)
    }

    func room(_ room: Room, participantDidDisconnect participant: RemoteParticipant) {
        onEvent(.tracksChanged)
    }

    func room(_ room: Room, didUpdateConnectionState connectionState: ConnectionState, from oldConnectionState: ConnectionState) {
        switch connectionState {
        case .reconnecting: onEvent(.reconnecting)
        case .connected: onEvent(.reconnected)
        // La déconnexion est traitée par `didDisconnectWithError` (porte la raison).
        default: break
        }
    }

    func room(_ room: Room, didDisconnectWithError error: LiveKitError?) {
        onEvent(.disconnected(final: isFinalDisconnect(error)))
    }
}

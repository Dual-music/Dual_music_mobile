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

    private let tokenService: LiveKitTokenService
    private var delegateProxy: RoomDelegateProxy?
    private var pollTask: Task<Void, Never>?

    /// - Parameter tokenService: service d'émission de jetons (backend).
    public init(tokenService: LiveKitTokenService) {
        self.tokenService = tokenService
    }

    /// Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
    /// connecte au SFU.
    ///
    /// - Parameters:
    ///   - roomName: nom de room fourni par l'API.
    ///   - isHost: vrai pour l'artiste (publication), faux pour un viewer.
    ///   - prewarmedToken: jeton déjà obtenu par le feed (prefetch) — évite un aller-retour
    ///     réseau au scroll et réduit fortement la latence d'entrée-live.
    public func join(roomName: String, isHost: Bool = false, prewarmedToken: LiveKitToken? = nil) async {
        guard connectionState != .connecting, connectionState != .connected else { return }
        connectionState = .connecting
        do {
            let proxy = RoomDelegateProxy { [weak self] state in
                Task { @MainActor in self?.handleRoomEvent(state) }
            }
            delegateProxy = proxy
            room.add(delegate: proxy)

            let creds: LiveKitToken
            if let prewarmedToken {
                creds = prewarmedToken
            } else {
                creds = try await tokenService.token(roomName: roomName, isHost: isHost)
            }

            // `autoSubscribe` par défaut : un viewer reçoit la couche simulcast adaptée à
            // la taille de rendu et à la bande passante disponible.
            try await room.connect(url: creds.url, token: creds.token)
            connectionState = .connected
            refreshPrimaryTrack()
            startPolling()
        } catch {
            connectionState = .failed(error.localizedDescription)
        }
    }

    /// Quitte la room (sortie d'écran, scroll hors du live, mise en arrière-plan).
    public func leave() async {
        stopPolling()
        await room.disconnect()
        primaryVideoTrack = nil
        localVideoTrack = nil
        isCameraEnabled = false
        isMicrophoneEnabled = false
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

    // MARK: - Interne

    /// Applique un événement de room remonté par le proxy de délégué.
    private func handleRoomEvent(_ event: RoomProxyEvent) {
        switch event {
        case .tracksChanged:
            refreshPrimaryTrack()
        case .reconnecting:
            connectionState = .reconnecting
        case .reconnected:
            connectionState = .connected
            refreshPrimaryTrack()
        case .disconnected:
            connectionState = .idle
            primaryVideoTrack = nil
            stopPolling()
        }
    }

    /// Piste vidéo primaire = première piste vidéo distante souscrite (le host).
    private func refreshPrimaryTrack() {
        let track = room.remoteParticipants.values
            .flatMap { $0.videoTracks }
            .compactMap { $0.track as? VideoTrack }
            .first
        if track !== primaryVideoTrack { primaryVideoTrack = track }
    }

    /// Filet de sécurité : recalcule la piste primaire chaque seconde tant qu'on est connecté.
    private func startPolling() {
        stopPolling()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }
                if case .connected = self.connectionState {
                    self.refreshPrimaryTrack()
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

    deinit {
        pollTask?.cancel()
    }
}

/// Événements de room simplifiés remontés à ``LiveRoomClient``.
private enum RoomProxyEvent: Sendable {
    case tracksChanged
    case reconnecting
    case reconnected
    case disconnected
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
        case .disconnected: onEvent(.disconnected)
        default: break
        }
    }
}

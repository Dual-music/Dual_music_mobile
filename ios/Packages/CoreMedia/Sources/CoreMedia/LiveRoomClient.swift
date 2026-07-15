import Foundation
import Observation
import LiveKit

/// État de connexion simplifié pour l'UI.
public enum LiveConnectionState: Sendable, Equatable {
    case idle
    case connecting
    case connected
    case reconnecting
    case failed(String)
}

/// Client d'une room live LiveKit (viewer).
///
/// Enveloppe le `Room` LiveKit : connexion via jeton backend, souscription automatique aux
/// pistes, et exposition de la **piste vidéo primaire** (le host) à rendre avec
/// `SwiftUIVideoView` dans la feature. Décodage matériel (VideoToolbox) géré par le SDK.
///
/// `@Observable @MainActor` : les vues observent `connectionState` et `primaryVideoTrack`.
@Observable
@MainActor
public final class LiveRoomClient {

    /// Room LiveKit sous-jacente (exposée pour `SwiftUIVideoView`/diagnostics avancés).
    public let room = Room()

    public private(set) var connectionState: LiveConnectionState = .idle
    /// Piste vidéo du host à afficher (nil tant qu'aucune piste n'est souscrite).
    public private(set) var primaryVideoTrack: VideoTrack?

    private let tokenService: LiveKitTokenService
    private var delegateProxy: RoomDelegateProxy?

    public init(tokenService: LiveKitTokenService) {
        self.tokenService = tokenService
    }

    /// Rejoint une room : récupère un jeton (ou utilise un jeton pré-chauffé) puis se
    /// connecte au SFU.
    /// - Parameters:
    ///   - roomName: nom de room fourni par l'API (ex. id de room du live).
    ///   - isHost: vrai pour l'artiste (publication), faux pour un viewer.
    ///   - prewarmedToken: jeton déjà obtenu par le feed (prefetch) — évite un aller-retour
    ///     réseau au moment du scroll, réduisant la latence d'entrée-live.
    public func join(roomName: String, isHost: Bool = false, prewarmedToken: LiveKitToken? = nil) async {
        connectionState = .connecting
        do {
            let proxy = RoomDelegateProxy { [weak self] in self?.refreshPrimaryTrack() }
            self.delegateProxy = proxy
            room.add(delegate: proxy)

            let creds: LiveKitToken
            if let prewarmedToken {
                creds = prewarmedToken
            } else {
                creds = try await tokenService.token(roomName: roomName, isHost: isHost)
            }
            // autoSubscribe true : un viewer reçoit la couche simulcast adaptée.
            try await room.connect(url: creds.url, token: creds.token)
            connectionState = .connected
            refreshPrimaryTrack()
        } catch {
            connectionState = .failed(error.localizedDescription)
        }
    }

    /// Quitte la room (mise en arrière-plan / scroll hors du live).
    public func leave() async {
        await room.disconnect()
        primaryVideoTrack = nil
        connectionState = .idle
    }

    /// Recalcule la piste vidéo primaire = première piste vidéo distante souscrite.
    private func refreshPrimaryTrack() {
        primaryVideoTrack = room.remoteParticipants.values
            .flatMap { $0.videoTracks }
            .compactMap { $0.track as? VideoTrack }
            .first
    }
}

/// Proxy de `RoomDelegate` : relaie les changements de piste vers une closure MainActor.
private final class RoomDelegateProxy: RoomDelegate {
    private let onTracksChanged: @Sendable () -> Void
    init(onTracksChanged: @escaping @Sendable () -> Void) { self.onTracksChanged = onTracksChanged }

    func room(_ room: Room, participant: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        onTracksChanged()
    }
    func room(_ room: Room, participant: RemoteParticipant, didUnsubscribeTrack publication: RemoteTrackPublication) {
        onTracksChanged()
    }
    func room(_ room: Room, participantDidDisconnect participant: RemoteParticipant) {
        onTracksChanged()
    }
}

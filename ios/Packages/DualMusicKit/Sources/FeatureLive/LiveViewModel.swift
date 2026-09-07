import Foundation
import Observation
import CoreLiveMedia
import CoreRealtime
import CoreUI
import DomainModels

/// Cadeau reçu à animer dans le live.
public struct LiveGift: Identifiable, Sendable, Equatable {
    public let id: Int
    public let fromUserId: String?
    public let giftName: String?
    public let giftImage: String?
    public let value: Double
}

/// Orchestre l'expérience d'un live (viewer) : vidéo LiveKit + chat/cadeaux/présence temps
/// réel (Socket.IO) + actions (message, cadeau).
///
/// Miroir de `LiveViewModel` Android. La vidéo est exposée via ``media`` ; l'écran rend la
/// piste avec `SwiftUIVideoView`.
@Observable
@MainActor
public final class LiveViewModel {

    /// Client média (une connexion SFU par room affichée).
    public let media: LiveRoomClient

    public private(set) var messages: [LiveChatMessage] = []
    public private(set) var giftFeed: [LiveGift] = []
    public private(set) var viewerCount: Int = 0

    private let liveId: String
    private let roomName: String
    private let realtime: RealtimeClient
    private let repository: LiveRepository

    private var giftCounter = 0
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var started = false

    /// - Parameters:
    ///   - liveId: identifiant du live (contexte chat/cadeaux).
    ///   - roomName: room LiveKit à rejoindre.
    ///   - media: client média dédié à ce live.
    ///   - realtime: client Socket.IO partagé.
    ///   - repository: lectures/actions REST du live.
    public init(
        liveId: String,
        roomName: String,
        media: LiveRoomClient,
        realtime: RealtimeClient,
        repository: LiveRepository
    ) {
        self.liveId = liveId
        self.roomName = roomName
        self.media = media
        self.realtime = realtime
        self.repository = repository
    }

    /// Démarre : vidéo, historique de chat, rooms temps réel.
    ///
    /// - Parameter prewarmedToken: jeton LiveKit pré-obtenu par le feed (réduit la latence).
    public func start(prewarmedToken: LiveKitToken? = nil) async {
        guard !started else { return }
        started = true

        // La vidéo, l'historique et le temps réel démarrent en parallèle : aucun n'attend
        // l'autre (l'utilisateur voit le chat même si le SFU est lent, et inversement).
        Task { await media.join(roomName: roomName, isHost: false, prewarmedToken: prewarmedToken) }
        Task { [weak self] in
            guard let self else { return }
            if let history = try? await self.repository.chatHistory(liveId: self.liveId) {
                self.messages = history
            }
        }
        await connectRealtime()
    }

    /// Arrête tout (sortie d'écran) : temps réel + SFU.
    public func stop() async {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        await media.leave()
        started = false
    }

    /// Envoie un message de chat (le serveur le diffuse ensuite à la room).
    /// - Parameter text: contenu saisi.
    public func sendMessage(_ text: String) async {
        let content = text.trimmed
        guard !content.isEmpty else { return }
        try? await repository.postMessage(liveId: liveId, content: content)
    }

    /// Envoie un cadeau au host.
    /// - Parameters:
    ///   - giftId: cadeau de l'inventaire.
    ///   - toUserId: destinataire (le host du live).
    public func sendGift(giftId: String, toUserId: String) async {
        guard !giftId.isEmpty else { return }
        try? await repository.sendGift(liveId: liveId, giftId: giftId, toUserId: toUserId)
    }

    /// Retire le cadeau le plus ancien après son animation.
    public func consumeOldestGift() {
        if !giftFeed.isEmpty { giftFeed.removeFirst() }
    }

    // MARK: - Temps réel

    private func connectRealtime() async {
        let live = realtime.session(.live)
        let chat = realtime.session(.chat)
        liveSession = live
        chatSession = chat

        // (Re)join à chaque connexion : le serveur ne mémorise pas l'appartenance aux rooms.
        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.live, id: self.liveId)
        })
        subscriptions.append(chat.onConnect { [weak self] in
            guard let self else { return }
            chat.join(.live, id: self.liveId)
        })

        subscriptions.append(chat.onEvent(Realtime.Event.chatMessage, as: ChatMessagePayload.self) { [weak self] payload in
            self?.messages.append(
                LiveChatMessage(id: payload.id, userId: payload.userId, content: payload.content, user: payload.user)
            )
        })
        subscriptions.append(live.onEvent(Realtime.Event.gift, as: GiftPayload.self) { [weak self] payload in
            guard let self else { return }
            self.giftCounter += 1
            self.giftFeed.append(
                LiveGift(
                    id: self.giftCounter,
                    fromUserId: payload.fromUserId,
                    giftName: payload.giftName,
                    giftImage: payload.giftImage,
                    value: payload.value
                )
            )
        })
        subscriptions.append(live.onEvent(Realtime.Event.presence, as: PresencePayload.self) { [weak self] payload in
            self?.viewerCount = payload.count
        })

        await live.connect()
        await chat.connect()
    }
}

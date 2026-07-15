import Foundation
import Observation
import CoreMedia
import CoreRealtime

/// Cadeau reçu à animer dans le live (feed).
public struct LiveGift: Identifiable, Sendable {
    public let id = UUID()
    public let fromUserId: String?
    public let giftName: String?
    public let giftImage: String?
    public let value: Double
}

// Miroirs Swift des payloads Socket.IO (voir shared-domain/realtime).
private struct GiftPayloadDTO: Decodable {
    let from_user_id: String?; let gift_name: String?; let gift_image: String?; let value: Double?
}
private struct PresencePayloadDTO: Decodable { let count: Int }
private struct ChatPayloadDTO: Decodable {
    let id: String?; let user_id: String; let content: String
    struct U: Decodable { let full_name: String? }
    let user: U?
}

/// Orchestre l'expérience d'un live (viewer) : vidéo LiveKit + chat/cadeaux/présence
/// temps réel (Socket.IO) + actions (message, cadeau).
///
/// `@Observable @MainActor` : la vue observe `messages`, `viewerCount`, `giftFeed` et
/// l'état vidéo (`media.connectionState`, `media.primaryVideoTrack`).
@Observable
@MainActor
public final class LiveViewModel {

    /// Client vidéo (exposé pour le rendu `SwiftUIVideoView`).
    public let media: LiveRoomClient

    private let realtime: RealtimeClient
    private let repository: LiveRepository
    private let liveId: String
    /// Room LiveKit du live (nom fourni par l'API — souvent l'id de room du live).
    private let roomName: String

    public private(set) var messages: [LiveChatMessage] = []
    public private(set) var giftFeed: [LiveGift] = []
    public private(set) var viewerCount: Int = 0
    public var draftMessage: String = ""

    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var subscriptions: [Subscription] = []

    public init(liveId: String, roomName: String, media: LiveRoomClient, realtime: RealtimeClient, repository: LiveRepository) {
        self.liveId = liveId
        self.roomName = roomName
        self.media = media
        self.realtime = realtime
        self.repository = repository
    }

    /// Démarre : connecte la vidéo, rejoint les rooms temps réel, charge l'historique.
    /// - Parameter prewarmedToken: jeton LiveKit pré-obtenu par le feed (réduit la latence).
    public func start(prewarmedToken: LiveKitToken? = nil) async {
        // 1) Vidéo
        await media.join(roomName: roomName, isHost: false, prewarmedToken: prewarmedToken)

        // 2) Historique de chat (amorce avant le temps réel)
        if let history = try? await repository.chatHistory(liveId: liveId) {
            messages = history
        }

        // 3) Temps réel — chat + live (cadeaux/présence)
        connectRealtime()
    }

    /// Arrête tout (sortie d'écran).
    public func stop() async {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        await media.leave()
    }

    /// Envoie le message en cours (optimistic : on l'ajoute, le serveur diffusera la version finale).
    public func sendMessage() async {
        let text = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        draftMessage = ""
        try? await repository.postMessage(liveId: liveId, content: text)
    }

    /// Envoie un cadeau au host.
    public func sendGift(giftId: String, toUserId: String) async {
        try? await repository.sendGift(liveId: liveId, giftId: giftId, toUserId: toUserId)
    }

    // MARK: Temps réel

    private func connectRealtime() {
        let live = realtime.session(.live)
        let chat = realtime.session(.chat)
        liveSession = live
        chatSession = chat

        // (re)join des rooms à chaque (re)connexion
        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.live, id: self.liveId)
        })
        subscriptions.append(chat.onConnect { [weak self] in
            guard let self else { return }
            chat.join(.live, id: self.liveId)
        })

        // Nouveaux messages de chat
        subscriptions.append(chat.onEvent(Realtime.Event.chatMessage, as: ChatPayloadDTO.self) { [weak self] p in
            Task { @MainActor in
                self?.messages.append(LiveChatMessage(id: p.id ?? UUID().uuidString, userId: p.user_id, content: p.content, authorName: p.user?.full_name))
            }
        })
        // Cadeaux
        subscriptions.append(live.onEvent(Realtime.Event.gift, as: GiftPayloadDTO.self) { [weak self] p in
            Task { @MainActor in
                self?.giftFeed.append(LiveGift(fromUserId: p.from_user_id, giftName: p.gift_name, giftImage: p.gift_image, value: p.value ?? 0))
            }
        })
        // Présence (viewers)
        subscriptions.append(live.onEvent(Realtime.Event.presence, as: PresencePayloadDTO.self) { [weak self] p in
            Task { @MainActor in self?.viewerCount = p.count }
        })

        live.connect()
        chat.connect()
    }
}

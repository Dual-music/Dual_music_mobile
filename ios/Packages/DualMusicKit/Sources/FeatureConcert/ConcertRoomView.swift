import SwiftUI
import LiveKit
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels

/// Message de chat d'un concert (auteur hydraté par le backend).
///
/// ⚠️ Le backend utilise la clé `message` (colonne DB) pour le contenu et `author` pour
/// l'auteur hydraté — pas `content`/`user` comme Live/Duel.
public struct ConcertChatMessage: Decodable, Sendable, Identifiable, Equatable {
    public let messageId: String?
    public let userId: String
    public let content: String
    public let user: DisplayProfile?

    public let id: String

    enum CodingKeys: String, CodingKey {
        case messageId = "id"
        case userId = "user_id"
        case content = "message"
        case user = "author"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        messageId = c.opt(String.self, .messageId)
        userId = c.val(String.self, .userId, "")
        content = c.val(String.self, .content, "")
        user = c.opt(DisplayProfile.self, .user)
        id = messageId ?? UUID().uuidString
    }

    public init(id: String? = nil, userId: String, content: String, user: DisplayProfile? = nil) {
        self.messageId = id
        self.userId = userId
        self.content = content
        self.user = user
        self.id = id ?? UUID().uuidString
    }

    /// Nom d'auteur affiché (repli « Fan »).
    @MainActor
    public var authorName: String { user?.displayName ?? AppStrings.current.fan }
}

/// Cadeau reçu à animer dans le concert.
public struct ConcertGift: Identifiable, Sendable, Equatable {
    public let id: Int
    public let fromUserId: String?
    public let giftName: String?
    public let giftImage: String?
    public let value: Double
}

/// Orchestre l'expérience d'un concert (viewer + artiste) : vidéo LiveKit + chat/cadeaux/
/// présence temps réel (Socket.IO) + billetterie + report/ban.
///
/// Miroir de `ConcertRoomViewModel` Android, réduit à un premier périmètre (chantier suivant :
/// dédicaces en direct avec accepter/rejeter côté artiste — aujourd'hui achat one-shot
/// seulement via `ConcertRepository.purchaseDedication` — et modérateurs désignés).
@Observable
@MainActor
public final class ConcertRoomViewModel {

    /// Client média (une connexion SFU par concert affiché).
    public let media: LiveRoomClient

    public private(set) var messages: [ConcertChatMessage] = []
    public private(set) var giftFeed: [ConcertGift] = []
    public private(set) var viewerCount: Int = 0
    public private(set) var errorMessage: String?

    /// Spectateurs bannis de ce concert (ids) — leurs messages restent en mémoire mais sont
    /// masqués de l'affichage (``visibleMessages``), pas supprimés.
    public private(set) var bannedUserIds: Set<String> = []
    /// Messages à afficher : ceux d'un spectateur banni sont masqués pour tout le monde.
    public var visibleMessages: [ConcertChatMessage] { messages.filter { !bannedUserIds.contains($0.userId) } }

    /// Vrai pour l'artiste (diffuse) — contrôle l'accès au bannissement et aux contrôles hôte.
    public let isHost: Bool
    /// Vrai tant qu'un billet payant est requis et non possédé — bloque l'accès à la vidéo
    /// (jamais vrai pour l'artiste, ni pour un concert gratuit).
    public private(set) var needsTicket = false

    private let concertId: String
    private let roomName: String
    private let hostUserId: String
    private let ticketPrice: Double
    private let callerId: String?
    private let realtime: RealtimeClient
    private let repository: ConcertRepository

    private var giftCounter = 0
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var started = false

    /// - Parameters:
    ///   - concertId: identifiant du concert (contexte chat/cadeaux/billet).
    ///   - roomName: room LiveKit à rejoindre (`Concert.liveKitRoom`).
    ///   - media: client média dédié à ce concert.
    ///   - realtime: client Socket.IO partagé.
    ///   - repository: lectures/actions REST du concert.
    ///   - hostUserId: id de l'artiste (exclu du bannissement, destinataire des cadeaux).
    ///   - ticketPrice: prix du billet — `0` = accès libre, jamais de billet requis.
    ///   - isHost: vrai pour l'artiste qui diffuse.
    ///   - callerId: id du caller (spectateur) — sert au filtrage des événements temps réel.
    public init(
        concertId: String,
        roomName: String,
        media: LiveRoomClient,
        realtime: RealtimeClient,
        repository: ConcertRepository,
        hostUserId: String,
        ticketPrice: Double,
        isHost: Bool = false,
        callerId: String? = nil
    ) {
        self.concertId = concertId
        self.roomName = roomName
        self.media = media
        self.realtime = realtime
        self.repository = repository
        self.hostUserId = hostUserId
        self.ticketPrice = ticketPrice
        self.isHost = isHost
        self.callerId = callerId
    }

    /// Démarre : billetterie, vidéo (si accès autorisé), historique de chat, rooms temps réel.
    public func start() async {
        guard !started else { return }
        started = true

        if !isHost && ticketPrice > 0 {
            let info = try? await repository.ticketInfo(id: concertId)
            needsTicket = info?.hasTicket != true
        }
        if !needsTicket {
            Task { await media.join(roomName: roomName, isHost: isHost) }
        }
        Task { [weak self] in
            guard let self else { return }
            if let history = try? await self.repository.chatHistory(concertId: self.concertId) {
                self.messages = history
            }
        }
        Task { [weak self] in
            guard let self else { return }
            let banned = await self.repository.listStreamBans(concertId: self.concertId)
            self.bannedUserIds.formUnion(banned)
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

    /// Spectateur : achète le billet puis rejoint immédiatement la vidéo (sans recharger
    /// l'écran).
    public func buyTicket() async {
        do {
            try await repository.buyTicket(concertId: concertId)
            needsTicket = false
            await media.join(roomName: roomName, isHost: false)
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Envoie un message de chat (le serveur le diffuse ensuite à la room).
    public func sendMessage(_ text: String) async {
        let content = text.trimmed
        guard !content.isEmpty else { return }
        try? await repository.postMessage(concertId: concertId, content: content)
    }

    /// Envoie un cadeau à l'artiste.
    public func sendGift(giftId: String) async {
        guard !giftId.isEmpty else { return }
        try? await repository.sendGift(concertId: concertId, giftId: giftId, toUserId: hostUserId)
    }

    /// Signale ce concert avec un motif (modération).
    public func report(reason: ReportReason) async {
        try? await repository.reportLive(liveId: concertId, reason: reason)
    }

    /// Artiste : bannit un spectateur (optimiste + persistant).
    /// - Parameters:
    ///   - userId: spectateur ciblé.
    ///   - reason: motif libre (ex. le message signalé), optionnel.
    public func banUser(userId: String, reason: String?) async {
        bannedUserIds.insert(userId)
        do {
            try await repository.createStreamBan(streamId: concertId, bannedUserId: userId, reason: reason)
        } catch {
            bannedUserIds.remove(userId)
        }
    }

    /// Artiste : démarre la diffusion caméra/micro puis notifie le backend (`status: live`) —
    /// seulement si la publication a réellement réussi.
    public func startBroadcast() async {
        await media.startBroadcast()
        guard media.isCameraEnabled || media.isMicrophoneEnabled else { return }
        try? await repository.goLive(concertId: concertId)
    }

    /// Artiste : coupe/rétablit le micro.
    public func toggleMic() async {
        await media.setMicrophone(enabled: !media.isMicrophoneEnabled)
    }

    /// Artiste : coupe/rétablit la caméra.
    public func toggleCamera() async {
        await media.setCamera(enabled: !media.isCameraEnabled)
    }

    /// Artiste : bascule caméra avant/arrière.
    public func switchCamera() async {
        await media.switchCamera()
    }

    /// Artiste : termine ce concert côté backend (arrête aussi la diffusion locale).
    public func endConcert() async throws {
        try await repository.endConcert(concertId: concertId)
        await media.stopBroadcast()
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

        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.concert, id: self.concertId)
        })
        subscriptions.append(chat.onConnect { [weak self] in
            guard let self else { return }
            chat.join(.concert, id: self.concertId)
        })

        subscriptions.append(chat.onEvent(Realtime.Event.chatMessage, as: ChatMessagePayload.self) { [weak self] payload in
            self?.messages.append(
                ConcertChatMessage(id: payload.id, userId: payload.userId, content: payload.content, user: payload.user)
            )
        })
        subscriptions.append(live.onEvent(Realtime.Event.gift, as: GiftPayload.self) { [weak self] payload in
            guard let self else { return }
            self.giftCounter += 1
            self.giftFeed.append(
                ConcertGift(
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
        subscriptions.append(live.onEvent(Realtime.Event.streamBanned, as: StreamBannedPayload.self) { [weak self] payload in
            guard let self, payload.streamId == nil || payload.streamId == self.concertId else { return }
            self.bannedUserIds.insert(payload.userId)
        })

        await live.connect()
        await chat.connect()
    }
}

/// Écran d'un concert (viewer + artiste) : vidéo plein écran + overlays chat/cadeaux/
/// présence, paywall billetterie pour un concert payant sans billet.
///
/// Miroir de `ConcertRoomScreen` Android, réduit au premier périmètre (voir
/// ``ConcertRoomViewModel``).
@MainActor
public struct ConcertRoomView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ConcertRoomViewModel
    private let concertTitle: String
    private let hostUserId: String
    private let quickGiftId: String
    private let onEnded: () -> Void

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: ConcertChatMessage?

    /// - Parameters:
    ///   - viewModel: état + actions du concert.
    ///   - concertTitle: affiché derrière le paywall billetterie.
    ///   - hostUserId: id de l'artiste — jamais bannissable, même par lui-même.
    ///   - quickGiftId: cadeau rapide (vide → bouton inactif tant qu'aucun cadeau choisi).
    ///   - onEnded: artiste uniquement — appelé une fois le concert terminé.
    public init(
        viewModel: ConcertRoomViewModel,
        concertTitle: String,
        hostUserId: String,
        quickGiftId: String = "",
        onEnded: @escaping () -> Void = {}
    ) {
        self.viewModel = viewModel
        self.concertTitle = concertTitle
        self.hostUserId = hostUserId
        self.quickGiftId = quickGiftId
        self.onEnded = onEnded
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let track = displayTrack {
                SwiftUIVideoView(track, layoutMode: .fill).ignoresSafeArea()
            } else {
                theme.gradients.hero.ignoresSafeArea()
                if viewModel.isHost {
                    if !viewModel.media.isCameraEnabled { startBroadcastButton }
                } else if case .connecting = viewModel.media.connectionState {
                    DMLoadingBox()
                }
            }

            LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            if let gift = viewModel.giftFeed.last {
                GiftBurstView(symbol: "🎁", label: gift.giftName) { viewModel.consumeOldestGift() }
                    .id(gift.id)
            }

            VStack {
                viewerBadge
                Spacer()
                chatOverlay
                if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }
                if viewModel.isHost { hostControls } else { actionBar }
            }
            .padding(theme.spacing.md)

            if viewModel.needsTicket { ticketPaywall }
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
        .alert(
            "🚫 \(s.banAction) \(banTarget?.authorName ?? "") ?",
            isPresented: Binding(get: { banTarget != nil }, set: { if !$0 { banTarget = nil } }),
            presenting: banTarget
        ) { target in
            Button(s.cancel, role: .cancel) {}
            Button(s.banAction, role: .destructive) {
                Task { await viewModel.banUser(userId: target.userId, reason: String(target.content.prefix(200))) }
            }
        } message: { _ in
            Text(s.banConfirmMessage)
        }
    }

    /// L'artiste voit son propre aperçu (`localVideoTrack`), le spectateur le flux `primary`.
    private var displayTrack: VideoTrack? {
        viewModel.isHost ? viewModel.media.localVideoTrack : viewModel.media.primaryVideoTrack
    }

    /// Compteur de spectateurs + bouton signaler.
    private var viewerBadge: some View {
        HStack {
            if !viewModel.isHost {
                Button { showReport = true } label: {
                    Image(systemName: "flag.fill")
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                        .padding(theme.spacing.xs)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))
            }
            Spacer()
            HStack(spacing: 4) {
                Image(systemName: "eye.fill")
                Text("\(viewModel.viewerCount)")
            }
            .font(DMFont.caption)
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.md)
            .padding(.vertical, theme.spacing.xs)
            .background(.black.opacity(0.4), in: Capsule())
        }
    }

    /// Les 6 derniers messages visibles. L'artiste peut bannir l'auteur d'un message en tapant
    /// sur son nom — jamais lui-même.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(viewModel.visibleMessages.suffix(6)) { message in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(message.authorName)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                        .onTapGesture {
                            guard viewModel.isHost, message.userId != hostUserId else { return }
                            banTarget = message
                        }
                    Text(message.content)
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Libellé localisé d'un motif de signalement.
    private func reportLabel(_ reason: ReportReason) -> String {
        switch reason {
        case .inappropriate: return s.reportInappropriate
        case .harassment: return s.reportHarassment
        case .spam: return s.reportSpam
        case .violence: return s.reportViolence
        }
    }

    /// Bouton plein écran pour démarrer la diffusion (artiste, avant publication).
    private var startBroadcastButton: some View {
        Button {
            Task { await viewModel.startBroadcast() }
        } label: {
            VStack(spacing: theme.spacing.sm) {
                Image(systemName: "video.fill").font(.system(size: 40))
                Text(s.startLive)
            }
            .foregroundStyle(.white)
        }
    }

    /// Barre d'action spectateur : saisie de message + bouton cadeau.
    private var actionBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(s.saySomething, text: $draft)
                .textFieldStyle(.plain)
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(.white.opacity(0.15), in: Capsule())
                .submitLabel(.send)
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(theme.colors.accent, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(draft.trimmed.isEmpty)
            Button {
                Task { await viewModel.sendGift(giftId: quickGiftId) }
            } label: {
                Image(systemName: "gift.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(.black.opacity(0.4), in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(quickGiftId.isEmpty)
        }
    }

    /// Contrôles artiste : mic/caméra/bascule + fin du concert.
    private var hostControls: some View {
        HStack(spacing: theme.spacing.md) {
            controlButton(viewModel.media.isMicrophoneEnabled ? "mic.fill" : "mic.slash.fill") {
                Task { await viewModel.toggleMic() }
            }
            controlButton(viewModel.media.isCameraEnabled ? "video.fill" : "video.slash.fill") {
                Task { await viewModel.toggleCamera() }
            }
            controlButton("arrow.triangle.2.circlepath.camera.fill") {
                Task { await viewModel.switchCamera() }
            }
            Spacer()
            Button {
                Task { try? await viewModel.endConcert(); onEnded() }
            } label: {
                Text(s.endLive)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.sm)
                    .background(theme.colors.destructive, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func controlButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .foregroundStyle(.white)
                .padding(theme.spacing.sm)
                .background(.black.opacity(0.4), in: Circle())
        }
        .buttonStyle(.plain)
    }

    /// Paywall plein écran : bloque la vidéo tant que le billet n'est pas acheté.
    private var ticketPaywall: some View {
        ZStack {
            Color.black.opacity(0.85).ignoresSafeArea()
            VStack(spacing: theme.spacing.md) {
                Text(concertTitle)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(.white)
                Text(s.ticketRequired)
                    .font(DMFont.body)
                    .foregroundStyle(.white.opacity(0.8))
                DMButton(s.buyTicket) {
                    Task { await viewModel.buyTicket() }
                }
            }
            .padding(theme.spacing.lg)
        }
    }

    /// Envoie le brouillon puis vide le champ.
    private func send() {
        let text = draft
        draft = ""
        Task { await viewModel.sendMessage(text) }
    }
}

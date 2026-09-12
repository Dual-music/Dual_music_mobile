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

/// Réaction emoji flottante à animer (la mienne ou celle d'un autre spectateur, relayées
/// identiquement une fois émises).
public struct FloatingEmoji: Identifiable, Sendable, Equatable {
    public let id: Int
    public let emoji: String
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
    /// Compteur de « j'aime » partagé (persistant + valeur absolue relayée en direct, jamais
    /// un delta — une valeur plus ancienne reçue en désordre est ignorée).
    public private(set) var likes: Int = 0
    public private(set) var emojiFeed: [FloatingEmoji] = []

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

    /// Modérateurs désignés de ce concert (artiste + jusqu'à ``maxEventModerators``
    /// spectateurs) — visible par tous, pour que chacun sache qui d'autre a le pouvoir de
    /// bannir.
    public private(set) var moderators: [EventModerator] = []
    /// Artiste : spectateurs actuellement connectés (vivier du picker de désignation).
    public private(set) var viewers: [DisplayProfile] = []
    /// Vrai si le caller est un modérateur désigné (jamais vrai pour l'artiste lui-même, qui a
    /// déjà tous les pouvoirs via ``isHost``).
    public var isModerator: Bool {
        guard let callerId else { return false }
        return moderators.contains { $0.userId == callerId }
    }
    /// Vrai si le caller peut bannir/masquer un message : l'artiste ou un modérateur désigné.
    public var canModerate: Bool { isHost || isModerator }

    /// Dédicaces activées pour ce concert (réglage fixé à la création, pas modifiable en
    /// direct côté backend — contrairement à Live).
    public let allowsDedications: Bool
    /// Prix minimum global d'une dédicace (`economic_config.dedication`) — pas de surcharge
    /// par concert côté backend, contrairement à Live.
    public private(set) var dedicationMinPriceCredits: Double = 10
    /// Artiste : demandes de dédicace EN ATTENTE pour ce concert (à accepter/rejeter).
    public private(set) var dedications: [ConcertDedication] = []
    /// Artiste : dédicaces déjà ACCEPTÉES ou LIVRÉES (historique, sous les demandes).
    public private(set) var dedicationHistory: [ConcertDedication] = []
    /// Confirmation « dédicace envoyée » (ou message d'échec) affichée au fan.
    public private(set) var dedicationFeedback: String?
    public func clearDedicationFeedback() { dedicationFeedback = nil }

    private let concertId: String
    private let roomName: String
    private let hostUserId: String
    private let ticketPrice: Double
    private let callerId: String?
    private let realtime: RealtimeClient
    private let repository: ConcertRepository

    private var giftCounter = 0
    private var emojiCounter = 0
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
    ///   - allowsDedications: dédicaces activées pour ce concert (fixé à la création).
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
        allowsDedications: Bool,
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
        self.allowsDedications = allowsDedications
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
        Task { [weak self] in await self?.loadModerators() }
        Task { [weak self] in
            guard let self else { return }
            self.likes = await self.repository.likesCount(concertId: self.concertId)
        }
        if allowsDedications {
            Task { [weak self] in
                guard let self else { return }
                self.dedicationMinPriceCredits = (try? await self.repository.dedicationMinPrice()) ?? 10
            }
            if isHost {
                Task { [weak self] in await self?.loadDedications() }
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

    // MARK: - Likes + réactions emoji

    /// « J'aime » : incrément local + compteur partagé (broadcast) + persistance + réaction
    /// cœur flottante (parité Android : un like déclenche aussi une réaction ❤️).
    public func sendLike() {
        likes += 1
        liveSession?.broadcast(channel: "concert-likes-\(concertId)", event: "like", payload: ["count": likes])
        sendReaction("❤️")
        Task { await repository.likeConcert(concertId: concertId) }
    }

    /// Envoie une réaction emoji : effet local + relais aux autres membres de la room.
    public func sendReaction(_ emoji: String) {
        pushEmoji(emoji)
        liveSession?.broadcast(channel: "concert-emojis-\(concertId)", event: "emoji_reaction", payload: ["emoji": emoji])
    }

    private func pushEmoji(_ emoji: String) {
        emojiCounter += 1
        emojiFeed = (emojiFeed + [FloatingEmoji(id: emojiCounter, emoji: emoji)]).suffix(12).map { $0 }
    }

    /// Retire la réaction la plus ancienne après son animation.
    public func consumeOldestEmoji() {
        if !emojiFeed.isEmpty { emojiFeed.removeFirst() }
    }

    // MARK: - Modérateurs désignés

    /// (Re)charge les modérateurs désignés — appelé au démarrage + sur événement temps réel,
    /// pour TOUT LE MONDE (pas que l'artiste : chacun doit savoir qui d'autre peut bannir).
    public func loadModerators() async {
        moderators = (try? await repository.listEventModerators(concertId: concertId)) ?? []
    }

    /// Artiste : (re)charge les spectateurs connectés (vivier du picker « désigner »).
    public func loadViewers() async {
        guard isHost else { return }
        viewers = (try? await repository.listCurrentViewers(concertId: concertId)) ?? []
    }

    /// Artiste : désigne un spectateur modérateur (ban/masquer message).
    public func appointModerator(userId: String) async {
        try? await repository.appointModerator(concertId: concertId, userId: userId)
        await loadModerators()
    }

    /// Artiste : révoque un modérateur désigné.
    public func revokeModerator(userId: String) async {
        try? await repository.revokeModerator(concertId: concertId, userId: userId)
        await loadModerators()
    }

    // MARK: - Dédicaces

    /// Fan : envoie une dédicace (message dédié). `price` est choisi par le fan (≥ prix
    /// minimum global — le champ de saisie le clamp déjà, revalidé ici par sécurité).
    public func dedicate(message: String, price: Double) async {
        let text = message.trimmed
        guard !text.isEmpty else { return }
        let effectivePrice = max(price, dedicationMinPriceCredits)
        do {
            try await repository.purchaseDedication(concertId: concertId, message: text, priceCredits: effectivePrice)
            dedicationFeedback = "🎤 Dédicace envoyée à l'artiste !"
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Artiste : (re)charge les dédicaces de CE concert — séparées en « en attente » (badge +
    /// actions accepter/rejeter) et « acceptées/livrées » (historique, même feuille).
    public func loadDedications() async {
        guard isHost else { return }
        let all = (try? await repository.artistDedications()) ?? []
        let mine = all.filter { $0.concertId == concertId }
        dedications = mine.filter { $0.status == "pending" }
        dedicationHistory = mine.filter { $0.status == "paid" || $0.status == "delivered" }
    }

    /// Artiste : accepte une demande EN ATTENTE — débite le fan MAINTENANT, puis recharge.
    public func acceptDedication(id: String) async {
        do {
            try await repository.acceptDedication(id: id)
            await loadDedications()
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Artiste : rejette une demande EN ATTENTE — aucun débit, puis recharge.
    public func rejectDedication(id: String) async {
        do {
            try await repository.rejectDedication(id: id)
            await loadDedications()
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Artiste : marque une dédicace ACCEPTÉE comme livrée (interprétée) puis recharge.
    public func deliverDedication(id: String) async {
        try? await repository.deliverDedication(id: id)
        await loadDedications()
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
        // Modération : un modérateur a été désigné/révoqué par l'artiste → recharge pour tous.
        subscriptions.append(live.onEvent(Realtime.Event.moderatorAppointed, as: EventModeratorPayload.self) { [weak self] _ in
            Task { await self?.loadModerators() }
        })
        subscriptions.append(live.onEvent(Realtime.Event.moderatorRevoked, as: EventModeratorPayload.self) { [weak self] _ in
            Task { await self?.loadModerators() }
        })
        // Dédicaces : l'état fait toujours l'objet d'un rechargement REST complet, jamais
        // appliqué depuis le seul payload temps réel (parité Live).
        subscriptions.append(live.onEvent(Realtime.Event.dedicationNew, as: DedicationEventPayload.self) { [weak self] _ in
            guard let self, self.isHost else { return }
            Task { await self.loadDedications() }
        })
        subscriptions.append(live.onEvent(Realtime.Event.dedicationUpdate, as: DedicationEventPayload.self) { [weak self] _ in
            guard let self else { return }
            if self.isHost { Task { await self.loadDedications() } }
        })
        // Relais broadcast (jamais reçu par l'émetteur lui-même) : réactions emoji + compteur
        // de likes partagé.
        subscriptions.append(live.onEvent(Realtime.Event.broadcast, as: BroadcastEnvelope.self) { [weak self] envelope in
            guard let self else { return }
            switch envelope.event {
            case "emoji_reaction":
                if let emoji = envelope.payload?.emoji { self.pushEmoji(emoji) }
            case "like":
                if let count = envelope.payload?.count, count > self.likes { self.likes = count }
            default:
                break
            }
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
    @State private var showModeratorsSheet = false
    @State private var showDedicationSheet = false
    @State private var showDedicationRequests = false
    @State private var dedicationMessage = ""
    @State private var dedicationPriceText = ""
    @State private var showReactionBar = false

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

            if let reaction = viewModel.emojiFeed.last {
                GiftBurstView(symbol: reaction.emoji) { viewModel.consumeOldestEmoji() }
                    .id(reaction.id)
            }

            VStack {
                viewerBadge
                Spacer()
                chatOverlay
                if showReactionBar { reactionBar }
                if let feedback = viewModel.dedicationFeedback {
                    dedicationFeedbackBanner(feedback)
                }
                if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }
                if viewModel.isHost { hostControls } else { actionBar }
            }
            .padding(theme.spacing.md)

            if viewModel.needsTicket { ticketPaywall }
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        .onChange(of: viewModel.dedicationFeedback) { _, feedback in
            guard feedback != nil else { return }
            Task {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                viewModel.clearDedicationFeedback()
            }
        }
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        // Fan : demande de dédicace (message + prix, prix plancher forcé par l'artiste).
        .sheet(isPresented: $showDedicationSheet) { dedicationRequestSheet }
        // Artiste : demandes en attente (accepter/rejeter) + historique (marquer comme livrée).
        .sheet(isPresented: $showDedicationRequests) { dedicationRequestsSheet }
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
            // Visible de l'artiste ET des modérateurs eux-mêmes (pour qu'ils voient qui
            // d'autre a ce pouvoir) — pas seulement l'artiste.
            if viewModel.canModerate {
                Button {
                    showModeratorsSheet = true
                    if viewModel.isHost { Task { await viewModel.loadViewers() } }
                } label: {
                    Image(systemName: "person.2.fill")
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                        .padding(theme.spacing.xs)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.moderators))
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
                            guard viewModel.canModerate, message.userId != hostUserId else { return }
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

    /// Barre d'emojis réactions (togglée par le bouton emoji de ``actionBar``).
    private var reactionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.sm) {
                ForEach(Self.reactionEmojis, id: \.self) { emoji in
                    Button {
                        viewModel.sendReaction(emoji)
                    } label: {
                        Text(emoji)
                            .padding(theme.spacing.sm)
                            .background(.black.opacity(0.35), in: Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// Réactions rapides proposées (parité `ConcertReactionEmojis` Android).
    private static let reactionEmojis = ["🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂"]

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
                viewModel.sendLike()
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "heart.fill")
                        .foregroundStyle(.white)
                        .padding(theme.spacing.sm)
                        .background(.black.opacity(0.4), in: Circle())
                    if viewModel.likes > 0 {
                        Text("\(viewModel.likes)")
                            .font(.system(size: 10)).bold()
                            .foregroundStyle(.white)
                            .padding(4)
                            .background(theme.colors.accent, in: Circle())
                            .offset(x: 4, y: -4)
                    }
                }
            }
            .buttonStyle(.plain)
            Button {
                showReactionBar.toggle()
            } label: {
                Image(systemName: "face.smiling.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(.black.opacity(0.4), in: Circle())
            }
            .buttonStyle(.plain)
            if viewModel.allowsDedications {
                Button {
                    showDedicationSheet = true
                } label: {
                    Image(systemName: "megaphone.fill")
                        .foregroundStyle(.white)
                        .padding(theme.spacing.sm)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
            }
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

    /// Contrôles artiste : mic/caméra/bascule + dédicaces (badge = demandes en attente) + fin
    /// du concert.
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
            if viewModel.allowsDedications {
                Button {
                    showDedicationRequests = true
                } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "megaphone.fill")
                            .foregroundStyle(.white)
                            .padding(theme.spacing.sm)
                            .background(.black.opacity(0.4), in: Circle())
                        if !viewModel.dedications.isEmpty {
                            Text("\(viewModel.dedications.count)")
                                .font(.system(size: 10)).bold()
                                .foregroundStyle(.white)
                                .padding(4)
                                .background(theme.colors.destructive, in: Circle())
                                .offset(x: 4, y: -4)
                        }
                    }
                }
                .buttonStyle(.plain)
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

    /// Bannière de confirmation/décision de dédicace (fan) — auto-masquée après quelques
    /// secondes (voir `.onChange(of: viewModel.dedicationFeedback)`).
    private func dedicationFeedbackBanner(_ text: String) -> some View {
        Text(text)
            .font(DMFont.caption).bold()
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.md)
            .padding(.vertical, theme.spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    /// Feuille fan : compose et envoie une dédicace (prix jamais sous le minimum global).
    private var dedicationRequestSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text(s.dedication).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            Text(s.dedicationHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            DMTextField(s.dedication, text: $dedicationMessage, placeholder: s.saySomething, axis: .vertical)
            DMTextField(
                "\(s.credits) (min \(Int(viewModel.dedicationMinPriceCredits)))",
                text: $dedicationPriceText,
                keyboard: .numberPad
            )
            DMButton("\(s.send) (\(dedicationPriceValue) \(s.credits))") {
                Task {
                    await viewModel.dedicate(message: dedicationMessage, price: Double(dedicationPriceValue))
                    dedicationMessage = ""
                    showDedicationSheet = false
                }
            }
            Spacer()
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Prix saisi par le fan, jamais sous le minimum global effectif.
    private var dedicationPriceValue: Int {
        max(Int(dedicationPriceText) ?? Int(viewModel.dedicationMinPriceCredits), Int(viewModel.dedicationMinPriceCredits))
    }

    /// Feuille artiste : demandes en attente (accepter/rejeter) + historique (marquer livrée).
    private var dedicationRequestsSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.dedicationsLabel).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)

                if viewModel.dedications.isEmpty && viewModel.dedicationHistory.isEmpty {
                    Text(s.noDedicationsYet).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }

                if !viewModel.dedications.isEmpty {
                    DMSectionTitle("\(s.pendingLabel) (\(viewModel.dedications.count))")
                    ForEach(viewModel.dedications) { dedication in
                        pendingDedicationRow(dedication)
                    }
                }

                if !viewModel.dedicationHistory.isEmpty {
                    DMSectionTitle("\(s.dedicationsAcceptedDelivered) (\(viewModel.dedicationHistory.count))")
                    ForEach(viewModel.dedicationHistory) { dedication in
                        historyDedicationRow(dedication)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.medium, .large])
        .dmScreenBackground()
    }

    /// Ligne d'une demande de dédicace en attente : accepter (débite maintenant) ou rejeter.
    private func pendingDedicationRow(_ dedication: ConcertDedication) -> some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("\(dedication.fanName)  ·  \(Int(dedication.priceCredits)) \(s.credits)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
                Text(dedication.message)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                HStack(spacing: theme.spacing.sm) {
                    DMButton(s.accept) { Task { await viewModel.acceptDedication(id: dedication.id) } }
                    DMButton(s.rejectAction, style: .destructive) { Task { await viewModel.rejectDedication(id: dedication.id) } }
                }
            }
        }
    }

    /// Ligne d'une dédicace acceptée/livrée : marquer comme interprétée (si pas déjà livrée).
    private func historyDedicationRow(_ dedication: ConcertDedication) -> some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("\(dedication.fanName)  ·  \(Int(dedication.priceCredits)) \(s.credits)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
                Text(dedication.message)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                if dedication.status == "delivered" {
                    Text("✅ \(s.delivered)")
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.primary)
                } else {
                    DMButton(s.markDelivered) { Task { await viewModel.deliverDedication(id: dedication.id) } }
                }
            }
        }
    }

    /// Feuille : modérateurs désignés (révocables par l'artiste) + désignation d'un
    /// spectateur connecté (artiste uniquement). Visible aussi des modérateurs eux-mêmes, en
    /// lecture seule pour la partie désignation.
    private var moderatorsSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text(s.moderators).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            Text(s.moderatorsHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)

            if viewModel.moderators.isEmpty {
                Text(s.noModeratorsYet).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ForEach(viewModel.moderators) { moderator in
                    moderatorRow(moderator)
                }
            }

            if viewModel.isHost {
                Divider()
                Text(s.designateViewer).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                designateViewerSection
            }

            Spacer()
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Ligne d'un modérateur désigné — révocable par l'artiste seulement.
    private func moderatorRow(_ moderator: EventModerator) -> some View {
        HStack {
            Text(moderator.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
            Spacer()
            if viewModel.isHost {
                Button { Task { await viewModel.revokeModerator(userId: moderator.userId) } } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(theme.colors.destructive)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.revokeAction))
            }
        }
    }

    /// Picker de désignation (artiste uniquement) : spectateurs connectés, hors modérateurs
    /// déjà désignés, désactivé à la limite (``maxEventModerators``).
    private var designateViewerSection: some View {
        let appointedIds = Set(viewModel.moderators.map(\.userId))
        let pickable = viewModel.viewers.filter { !appointedIds.contains($0.id) }
        return Group {
            if viewModel.moderators.count >= maxEventModerators {
                Text(s.atModeratorLimit).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else if pickable.isEmpty {
                Text(s.noViewersConnected).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        ForEach(pickable) { viewer in
                            HStack {
                                Text(viewer.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
                                Spacer()
                                Button(s.appointAction) { Task { await viewModel.appointModerator(userId: viewer.id) } }
                                    .font(DMFont.caption).bold()
                                    .buttonStyle(.plain)
                                    .foregroundStyle(theme.colors.primary)
                            }
                        }
                    }
                }
                .frame(maxHeight: 180)
            }
        }
    }
}

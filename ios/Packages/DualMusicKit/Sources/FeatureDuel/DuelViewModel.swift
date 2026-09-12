import Foundation
import Observation
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels
import FeatureWallet

/// Cadeau reçu en direct dans le duel (pour l'animation).
public struct DuelGift: Identifiable, Sendable, Equatable {
    public let id: Int
    public let fromUserId: String?
    public let name: String?
    public let image: String?
    public let value: Double
}

/// État du minuteur du duel (persisté serveur → visible aussi pour les arrivants tardifs).
public struct DuelTimer: Sendable, Equatable {
    public let endsAt: String?
    public let targetId: String?

    public init(endsAt: String? = nil, targetId: String? = nil) {
        self.endsAt = endsAt
        self.targetId = targetId
    }

    /// Vrai si un minuteur est en cours.
    public var isRunning: Bool { endsAt != nil }
}

/// ViewModel de la room de duel.
///
/// Orchestre : vidéo LiveKit (``media``), tallies de votes + minuteur + statut + cadeaux
/// (Socket.IO `/live`), chat (`/chat`), et le **vote payant** délégué au portefeuille
/// (procédure atomique serveur — aucun calcul d'argent ici).
///
/// Choix clé, identique à Android : le tally n'est mis à jour qu'à réception de l'événement
/// temps réel `vote`. Pas d'optimisme local sur l'argent.
@Observable
@MainActor
public final class DuelViewModel {

    /// Client média (une connexion SFU par duel ouvert).
    public let media: LiveRoomClient

    public private(set) var duel: Duel?
    /// Total de crédits votés par artiste (`artistId` → total), mis à jour en direct.
    public private(set) var voteTotals: [String: Double] = [:]
    public private(set) var timer = DuelTimer()
    public private(set) var messages: [DuelChatMessage] = []
    public private(set) var giftFeed: [DuelGift] = []
    public private(set) var errorMessage: String?

    /// Spectateurs bannis de ce duel (ids) — leurs messages restent en mémoire mais sont
    /// masqués de l'affichage (``visibleMessages``), pas supprimés.
    public private(set) var bannedUserIds: Set<String> = []
    /// Messages à afficher : ceux d'un spectateur banni sont masqués pour tout le monde.
    public var visibleMessages: [DuelChatMessage] { messages.filter { !bannedUserIds.contains($0.userId) } }
    /// Vrai si le caller est le manager de ce duel.
    public var isManager: Bool {
        guard let callerId, let managerId = duel?.managerId else { return false }
        return managerId == callerId
    }

    /// Modérateurs désignés de ce duel (manager + jusqu'à ``maxEventModerators`` spectateurs)
    /// — visible par tous, pour que chacun sache qui d'autre a le pouvoir de bannir.
    public private(set) var moderators: [EventModerator] = []
    /// Manager : spectateurs actuellement connectés (vivier du picker de désignation).
    public private(set) var viewers: [DisplayProfile] = []
    /// Vrai si le caller est un modérateur désigné (jamais vrai pour le manager lui-même, qui
    /// a déjà tous les pouvoirs via ``isManager``).
    public var isModerator: Bool {
        guard let callerId else { return false }
        return moderators.contains { $0.userId == callerId }
    }
    /// Vrai si le caller peut bannir/masquer un message : le manager ou un modérateur désigné.
    public var canModerate: Bool { isManager || isModerator }

    private let duelId: String
    private let roomName: String
    private let callerId: String?
    private let realtime: RealtimeClient
    private let repository: DuelRepository
    private let wallet: WalletRepository

    private var giftCounter = 0
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var started = false

    /// - Parameters:
    ///   - duelId: identifiant du duel (contexte chat/cadeaux/votes).
    ///   - roomName: room LiveKit à rejoindre.
    ///   - media: client média dédié.
    ///   - realtime: client Socket.IO partagé.
    ///   - repository: lectures REST du duel.
    ///   - wallet: opérations de débit (vote).
    ///   - callerId: id du caller — détermine ``isManager`` une fois le duel chargé.
    public init(
        duelId: String,
        roomName: String,
        media: LiveRoomClient,
        realtime: RealtimeClient,
        repository: DuelRepository,
        wallet: WalletRepository,
        callerId: String? = nil
    ) {
        self.duelId = duelId
        self.roomName = roomName
        self.media = media
        self.realtime = realtime
        self.repository = repository
        self.wallet = wallet
        self.callerId = callerId
    }

    /// Démarre : détail du duel, tallies, vidéo, chat, temps réel.
    public func start() async {
        guard !started else { return }
        started = true

        Task { await media.join(roomName: roomName, isHost: false) }
        Task { [weak self] in
            guard let self else { return }
            if let detail = try? await self.repository.duel(id: self.duelId) {
                self.duel = detail
                // Réhydrate le minuteur persisté (arrivants tardifs).
                self.timer = DuelTimer(endsAt: detail.currentTimerEndsAt, targetId: detail.currentTimerTargetId)
            }
            if let totals = try? await self.repository.voteTotals(id: self.duelId) {
                self.voteTotals = Dictionary(totals.map { ($0.artistId, $0.total) }, uniquingKeysWith: { _, last in last })
            }
            if let history = try? await self.repository.chatHistory(duelId: self.duelId) {
                self.messages = history
            }
        }
        Task { [weak self] in
            guard let self else { return }
            let banned = await self.repository.listStreamBans(duelId: self.duelId)
            self.bannedUserIds.formUnion(banned)
        }
        Task { [weak self] in await self?.loadModerators() }
        await connectRealtime()
    }

    /// Arrête tout (sortie d'écran).
    public func stop() async {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        await media.leave()
        started = false
    }

    /// Vote payant pour un artiste.
    ///
    /// Débit atomique côté serveur ; le tally se met à jour via l'événement temps réel
    /// `vote`, jamais localement.
    /// - Parameters:
    ///   - artistId: artiste bénéficiaire.
    ///   - amount: montant en crédits.
    public func vote(artistId: String, amount: Double) async {
        do {
            try await wallet.vote(duelId: duelId, artistId: artistId, amount: amount)
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.errVoteFailed
        }
    }

    /// Envoie un message de chat.
    public func sendMessage(_ text: String) async {
        let content = text.trimmed
        guard !content.isEmpty else { return }
        try? await repository.postMessage(duelId: duelId, content: content)
    }

    /// Efface l'erreur affichée.
    public func clearError() { errorMessage = nil }

    /// Signale ce duel avec un motif (modération).
    public func report(reason: ReportReason) async {
        try? await repository.reportLive(liveId: duelId, reason: reason)
    }

    /// Manager : bannit un spectateur (optimiste + persistant). Il ne peut plus écrire ni
    /// rejoindre ; ses messages passés sont masqués (``visibleMessages``).
    /// - Parameters:
    ///   - userId: spectateur ciblé.
    ///   - reason: motif libre (ex. le message signalé), optionnel.
    public func banUser(userId: String, reason: String?) async {
        bannedUserIds.insert(userId)
        do {
            try await repository.createStreamBan(streamId: duelId, bannedUserId: userId, reason: reason)
        } catch {
            bannedUserIds.remove(userId)
        }
    }

    /// Retire le cadeau le plus ancien après son animation.
    public func consumeOldestGift() {
        if !giftFeed.isEmpty { giftFeed.removeFirst() }
    }

    // MARK: - Modérateurs désignés

    /// (Re)charge les modérateurs désignés — appelé au démarrage + sur événement temps réel,
    /// pour TOUT LE MONDE (pas que le manager : chacun doit savoir qui d'autre peut bannir).
    public func loadModerators() async {
        moderators = (try? await repository.listEventModerators(duelId: duelId)) ?? []
    }

    /// Manager : (re)charge les spectateurs connectés (vivier du picker « désigner »).
    public func loadViewers() async {
        guard isManager else { return }
        viewers = (try? await repository.listCurrentViewers(duelId: duelId)) ?? []
    }

    /// Manager : désigne un spectateur modérateur (ban/masquer message).
    public func appointModerator(userId: String) async {
        try? await repository.appointModerator(duelId: duelId, userId: userId)
        await loadModerators()
    }

    /// Manager : révoque un modérateur désigné.
    public func revokeModerator(userId: String) async {
        try? await repository.revokeModerator(duelId: duelId, userId: userId)
        await loadModerators()
    }

    // MARK: - Temps réel

    private func connectRealtime() async {
        let live = realtime.session(.live)
        let chat = realtime.session(.chat)
        liveSession = live
        chatSession = chat

        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.duel, id: self.duelId)
        })
        subscriptions.append(chat.onConnect { [weak self] in
            guard let self else { return }
            chat.join(.duel, id: self.duelId)
        })

        // Vote payant enregistré → on cumule le tally de l'artiste visé.
        subscriptions.append(live.onEvent(Realtime.Event.vote, as: VotePayload.self) { [weak self] payload in
            guard let self else { return }
            self.voteTotals[payload.artistId] = (self.voteTotals[payload.artistId] ?? 0) + payload.amount
        })
        // Minuteur (start/stop) piloté par le manager.
        subscriptions.append(live.onEvent(Realtime.Event.timer, as: TimerPayload.self) { [weak self] payload in
            self?.timer = DuelTimer(endsAt: payload.endsAt, targetId: payload.targetId)
        })
        // Statut / vainqueur.
        subscriptions.append(live.onEvent(Realtime.Event.status, as: StatusPayload.self) { [weak self] payload in
            guard let self, let winnerId = payload.winnerId, let current = self.duel else { return }
            // `Duel` est immuable : on recharge le détail pour refléter le vainqueur.
            Task { [weak self] in
                guard let self, let refreshed = try? await self.repository.duel(id: current.id) else { return }
                self.duel = refreshed
                _ = winnerId
            }
        })
        // Cadeaux (alimente l'animation).
        subscriptions.append(live.onEvent(Realtime.Event.gift, as: GiftPayload.self) { [weak self] payload in
            guard let self else { return }
            self.giftCounter += 1
            self.giftFeed.append(
                DuelGift(
                    id: self.giftCounter,
                    fromUserId: payload.fromUserId,
                    name: payload.giftName,
                    image: payload.giftImage,
                    value: payload.value
                )
            )
        })
        // Chat.
        subscriptions.append(chat.onEvent(Realtime.Event.chatMessage, as: ChatMessagePayload.self) { [weak self] payload in
            self?.messages.append(
                DuelChatMessage(id: payload.id, userId: payload.userId, content: payload.content, user: payload.user)
            )
        })
        // Bannissement poussé par le serveur — y compris quand ce n'est pas MOI (le manager)
        // qui ai banni depuis un autre appareil.
        subscriptions.append(live.onEvent(Realtime.Event.streamBanned, as: StreamBannedPayload.self) { [weak self] payload in
            guard let self, payload.streamId == nil || payload.streamId == self.duelId else { return }
            self.bannedUserIds.insert(payload.userId)
        })
        // Modération : un modérateur a été désigné/révoqué par le manager → recharge pour tous.
        subscriptions.append(live.onEvent(Realtime.Event.moderatorAppointed, as: EventModeratorPayload.self) { [weak self] _ in
            Task { await self?.loadModerators() }
        })
        subscriptions.append(live.onEvent(Realtime.Event.moderatorRevoked, as: EventModeratorPayload.self) { [weak self] _ in
            Task { await self?.loadModerators() }
        })

        await live.connect()
        await chat.connect()
    }
}

/// ViewModel de la liste des duels (catalogue).
@Observable
@MainActor
public final class DuelsListViewModel {

    public private(set) var duels: [Duel] = []
    public private(set) var isLoading = false

    private let repository: DuelRepository

    /// - Parameter repository: lectures REST des duels.
    public init(repository: DuelRepository) {
        self.repository = repository
    }

    /// Charge le catalogue (les duels en direct d'abord, tri côté serveur).
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        duels = (try? await repository.duels()) ?? []
        isLoading = false
    }
}

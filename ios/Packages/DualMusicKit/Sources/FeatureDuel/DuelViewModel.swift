import Foundation
import Observation
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels
import FeatureGiftShop
import FeatureWallet

/// Cadeau reçu en direct dans le duel (pour l'animation).
public struct DuelGift: Identifiable, Sendable, Equatable {
    public let id: Int
    public let fromUserId: String?
    public let name: String?
    public let image: String?
    public let value: Double
}

/// Vainqueur annoncé (célébration plein écran synchronisée pour tous les spectateurs).
public struct DuelWinner: Sendable, Equatable {
    public let name: String
    public let avatar: String?
    public let votes: Int
    public let percent: Int

    public init(name: String, avatar: String?, votes: Int, percent: Int = 0) {
        self.name = name
        self.avatar = avatar
        self.votes = votes
        self.percent = percent
    }
}

/// Réaction emoji flottante (id stable pour SwiftUI, contenu de l'emoji).
public struct DuelFloatingReaction: Identifiable, Sendable, Equatable {
    public let id: Int
    public let emoji: String
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

    /// Client média de la room de l'artiste 1 (`<baseRoom>-artist1`) — publie si ``mySlot`` ==
    /// `"artist1"`, sinon visionnage seul.
    public let mediaA1: LiveRoomClient
    /// Client média de la room de l'artiste 2 (`<baseRoom>-artist2`).
    public let mediaA2: LiveRoomClient
    /// Client média de la room du manager (`<baseRoom>-manager`) — le manager peut aussi
    /// diffuser (présentation/animation), pas réservé aux deux artistes.
    public let mediaMgr: LiveRoomClient

    /// Mon rôle de publication (`"artist1"`/`"artist2"`/`"manager"`), `nil` = spectateur pur.
    /// Déterminé une seule fois à la construction (``AppContainer``), pas par un fetch réseau.
    public let mySlot: String?
    /// Vrai si je peux publier dans une des 3 rooms (mon rôle a un slot).
    public var canPublish: Bool { mySlot != nil }
    /// Le client média où JE publie — `nil` pour un spectateur pur.
    public var myMedia: LiveRoomClient? {
        switch mySlot {
        case "artist1": mediaA1
        case "artist2": mediaA2
        case "manager": mediaMgr
        default: nil
        }
    }

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
    /// Vrai si JE suis banni de ce direct → l'UI bloque ma saisie de message et affiche une
    /// barrière plein écran (parité `BannedAccessGate`).
    public var iAmBanned: Bool {
        guard let callerId else { return false }
        return bannedUserIds.contains(callerId)
    }
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
    /// Vrai si le caller est un ACTEUR du duel (manager, artiste 1/2, ou admin) — exempté du
    /// billet côté ``ScheduledAccessGate`` (jamais exempté de l'attente de l'heure programmée).
    public var isActor: Bool {
        isManager || isAdmin || (callerId != nil && (callerId == duel?.artist1Id || callerId == duel?.artist2Id))
    }

    // MARK: - Billetterie / gate d'accès programmé

    public private(set) var hasTicket = false
    public private(set) var isAdmin = false

    // MARK: - Likes / réactions emoji

    public private(set) var likes = 0
    public private(set) var emojiFeed: [DuelFloatingReaction] = []
    private var emojiCounter = 0

    // MARK: - Cadeaux : boutique + classement

    public private(set) var topDonor: DuelDonorEntry?
    public private(set) var leaderboard: [DuelDonorEntry] = []
    public private(set) var giftCatalog: [VirtualGift] = []
    public private(set) var inventory: [InventoryItem] = []
    /// Bannière transitoire « vous avez reçu un cadeau » (destinataire uniquement).
    public private(set) var giftReceived: String?
    public func clearGiftReceived() { giftReceived = nil }

    // MARK: - Présence + prix du vote

    public private(set) var viewerCount = 0
    /// Prix d'UN vote en crédits — configuré par l'admin (défaut 1 tant que non chargé).
    public private(set) var votePrice: Double = 1

    // MARK: - Contrôles manager (arbitre)

    /// Artistes coupés d'AUTORITÉ par le manager (hard-mute) — ids utilisateur.
    public private(set) var mutedArtists: Set<String> = []
    /// Case épinglée par le manager (focus imposé, synchronisé) — `nil` = focus local libre.
    public private(set) var forcedFocus: String?
    /// Vainqueur annoncé (broadcast) → célébration plein écran persistante ; `nil` = arrêtée.
    public private(set) var winnerInfo: DuelWinner?
    private var shownWinnerId: String?

    private let duelId: String
    private let baseRoom: String
    private let callerId: String?
    private let realtime: RealtimeClient
    private let repository: DuelRepository
    private let wallet: WalletRepository
    private let giftShop: GiftShopRepository

    private var giftCounter = 0
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var started = false

    /// - Parameters:
    ///   - duelId: identifiant du duel (contexte chat/cadeaux/votes).
    ///   - baseRoom: room LiveKit de base (`Duel.liveKitRoom`) — les 3 rooms de slot en
    ///     dérivent (`<baseRoom>-artist1`/`-artist2`/`-manager`).
    ///   - mediaA1: client média de la room de l'artiste 1.
    ///   - mediaA2: client média de la room de l'artiste 2.
    ///   - mediaMgr: client média de la room du manager.
    ///   - mySlot: mon rôle de publication, précalculé par ``AppContainer``
    ///     (`"artist1"`/`"artist2"`/`"manager"`/`nil`).
    ///   - realtime: client Socket.IO partagé.
    ///   - repository: lectures REST du duel.
    ///   - wallet: opérations de débit (vote, cadeau, billet).
    ///   - giftShop: catalogue + inventaire de cadeaux (partagé avec la boutique).
    ///   - callerId: id du caller — détermine ``isManager``/``isModerator`` une fois le duel
    ///     chargé.
    public init(
        duelId: String,
        baseRoom: String,
        mediaA1: LiveRoomClient,
        mediaA2: LiveRoomClient,
        mediaMgr: LiveRoomClient,
        mySlot: String?,
        realtime: RealtimeClient,
        repository: DuelRepository,
        wallet: WalletRepository,
        giftShop: GiftShopRepository,
        callerId: String? = nil
    ) {
        self.duelId = duelId
        self.baseRoom = baseRoom
        self.mediaA1 = mediaA1
        self.mediaA2 = mediaA2
        self.mediaMgr = mediaMgr
        self.mySlot = mySlot
        self.realtime = realtime
        self.repository = repository
        self.wallet = wallet
        self.giftShop = giftShop
        self.callerId = callerId
    }

    /// Démarre : détail du duel, tallies, vidéo, chat, temps réel.
    public func start() async {
        guard !started else { return }
        started = true

        // Rejoint les 3 rooms de slot (parité Android) : publieur sur MA room, spectateur sur
        // les 2 autres — chacune indépendante, aucune n'attend les autres.
        joinSlot(mediaA1, "artist1")
        joinSlot(mediaA2, "artist2")
        joinSlot(mediaMgr, "manager")
        Task { [weak self] in
            guard let self else { return }
            if let detail = try? await self.repository.duel(id: self.duelId) {
                self.duel = detail
                // Réhydrate le minuteur persisté (arrivants tardifs).
                self.timer = DuelTimer(endsAt: detail.currentTimerEndsAt, targetId: detail.currentTimerTargetId)
                // Gate d'accès programmé : l'admin est un acteur (exempté de billet), un
                // participant l'est déjà via isManager/artist1/artist2 côté ``isActor``.
                self.isAdmin = await self.repository.amIAdmin()
                let isParticipant = self.isManager || self.callerId == detail.artist1Id || self.callerId == detail.artist2Id
                if !isParticipant, !self.isAdmin, detail.ticketPrice > 0 {
                    self.hasTicket = await self.repository.ticketInfo(id: self.duelId).hasTicket
                }
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
        Task { [weak self] in
            guard let self else { return }
            self.likes = await self.repository.likesCount(duelId: self.duelId)
        }
        Task { [weak self] in
            guard let self else { return }
            self.votePrice = await self.repository.votePricePerVote()
        }
        Task { [weak self] in await self?.loadGiftCatalog() }
        Task { [weak self] in await self?.loadInventory() }
        Task { [weak self] in await self?.loadGiftLeaderboard() }
        Task { [weak self] in await self?.loadTopDonor() }
        await connectRealtime()
    }

    /// Arrête tout (sortie d'écran) : temps réel + les 3 rooms de slot.
    public func stop() async {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        async let leaveA1: Void = mediaA1.leave()
        async let leaveA2: Void = mediaA2.leave()
        async let leaveMgr: Void = mediaMgr.leave()
        _ = await (leaveA1, leaveA2, leaveMgr)
        started = false
    }

    // MARK: - Diffusion (artiste 1/2 ou manager)

    /// Rejoint la room d'un slot : publieur si c'est MON slot, spectateur sinon.
    private func joinSlot(_ client: LiveRoomClient, _ slot: String) {
        let publish = slot == mySlot
        Task { await client.join(roomName: "\(baseRoom)-\(slot)", isHost: publish, canPublish: publish) }
    }

    /// Participant : démarre la diffusion caméra + micro dans MA room de slot.
    public func startBroadcast() async {
        await myMedia?.startBroadcast()
    }

    /// Participant : coupe/rétablit MON micro.
    public func toggleMic() async {
        guard let m = myMedia else { return }
        await m.setMicrophone(enabled: !m.isMicrophoneEnabled)
    }

    /// Participant : coupe/rétablit MA caméra.
    public func toggleCamera() async {
        guard let m = myMedia else { return }
        await m.setCamera(enabled: !m.isCameraEnabled)
    }

    /// Participant : bascule caméra avant/arrière.
    public func switchCamera() async {
        await myMedia?.switchCamera()
    }

    /// Participant : applique un filtre couleur à MA diffusion.
    public func setColorFilter(id: String, matrix: [Float]?) {
        myMedia?.setColorFilter(id: id, matrix: matrix)
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

    // MARK: - Billetterie

    /// Achète le billet spectateur de ce duel (accès programmé/payant). Débit atomique +
    /// idempotent côté serveur. Marque l'accès acquis en cas de succès.
    public func buyTicket() async -> Result<Void, Error> {
        do {
            try await repository.buyTicket(duelId: duelId)
            hasTicket = true
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    // MARK: - Likes / réactions emoji

    /// « J'aime » : incrément local + compteur partagé (broadcast) + persistance + réaction
    /// cœur flottante (parité Android : un like déclenche aussi une réaction ❤️).
    public func sendLike() {
        likes += 1
        liveSession?.broadcast(channel: "duel-likes-\(duelId)", event: "like", payload: ["count": likes])
        sendReaction("❤️")
        Task { await repository.likeDuel(duelId: duelId) }
    }

    /// Envoie une réaction emoji : effet local + relais aux autres membres de la room.
    public func sendReaction(_ emoji: String) {
        pushEmoji(emoji)
        liveSession?.broadcast(channel: "duel-emojis-\(duelId)", event: "emoji_reaction", payload: ["emoji": emoji])
    }

    private func pushEmoji(_ emoji: String) {
        emojiCounter += 1
        emojiFeed = (emojiFeed + [DuelFloatingReaction(id: emojiCounter, emoji: emoji)]).suffix(12).map { $0 }
    }

    /// Retire la réaction la plus ancienne après son animation.
    public func consumeOldestEmoji() {
        if !emojiFeed.isEmpty { emojiFeed.removeFirst() }
    }

    // MARK: - Cadeaux

    /// Recharge le catalogue des cadeaux virtuels (boutique).
    public func loadGiftCatalog() async {
        giftCatalog = (try? await giftShop.catalog()) ?? giftCatalog
    }

    /// Recharge l'inventaire (après achat/envoi).
    public func loadInventory() async {
        inventory = (try? await giftShop.inventory()) ?? inventory
    }

    /// Recharge le classement des donateurs.
    public func loadGiftLeaderboard() async {
        leaderboard = (try? await repository.giftLeaderboard(duelId: duelId)) ?? leaderboard
    }

    /// Recharge le meilleur donateur courant (bulle top-donateur).
    private func loadTopDonor() async {
        topDonor = (try? await repository.giftLeaderboard(duelId: duelId))?.first
    }

    /// Achète un cadeau (boutique) puis recharge l'inventaire.
    public func purchaseGift(giftId: String) async {
        do {
            try await wallet.purchaseGift(giftId: giftId)
            await loadInventory()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Envoie un cadeau possédé à un artiste/manager du duel (débit atomique serveur).
    public func sendGift(giftId: String, toUserId: String) async {
        do {
            try await wallet.sendGift(SendGiftRequest(giftId: giftId, toUserId: toUserId, duelId: duelId))
            await loadInventory()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Envoie un message — ou une RÉPONSE si `parentId` (résolution de la citation côté UI).
    public func sendMessage(_ text: String, parentId: String? = nil) async {
        let content = text.trimmed
        guard !content.isEmpty else { return }
        try? await repository.postMessage(duelId: duelId, content: content, parentId: parentId)
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

    // MARK: - Contrôles MANAGER (arbitre) — persistés + rediffusés par le backend

    /// Manager : active/désactive le chat de ce duel pour tous (jamais délégué aux modérateurs).
    public func toggleChat(_ enabled: Bool) async {
        do {
            try await repository.toggleChat(id: duelId, enabled: enabled)
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Donne la parole à un artiste pendant `seconds` (minuteur).
    public func startTimer(targetId: String, seconds: Int) async {
        try? await repository.startTimer(id: duelId, targetId: targetId, seconds: seconds)
    }

    /// Arrête le minuteur de parole.
    public func stopTimer() async {
        try? await repository.stopTimer(id: duelId)
    }

    /// Manager : coupe/réactive d'AUTORITÉ le micro d'un artiste. Diffuse `FORCE_MUTE`/
    /// `FORCE_UNMUTE` → l'artiste visé se coupe partout et tous les clients marquent
    /// l'artiste comme coupé (indicateur + panneau manager synchronisés).
    public func toggleMuteArtist(_ artistId: String) {
        let shouldMute = !mutedArtists.contains(artistId)
        applyMuteState(artistId, muted: shouldMute)
        liveSession?.broadcast(
            channel: "duel-mute-\(duelId)",
            event: shouldMute ? "FORCE_MUTE" : "FORCE_UNMUTE",
            payload: ["artistId": artistId]
        )
    }

    /// Applique l'état de coupure d'un artiste : ensemble partagé + hard-mute réel de MON
    /// micro si c'est moi. Le mute ne concerne QUE le micro : aucune opération caméra ici.
    private func applyMuteState(_ artistId: String, muted: Bool) {
        if muted { mutedArtists.insert(artistId) } else { mutedArtists.remove(artistId) }
        if artistId == callerId, let m = myMedia {
            Task { await m.setMicrophone(enabled: !muted) }
        }
    }

    /// Manager : épingle (ou libère avec `nil`) une case en plein écran pour TOUS.
    public func setFocus(_ slot: String?) {
        forcedFocus = slot
        liveSession?.broadcast(channel: "duel-focus-\(duelId)", event: "focus", payload: slot.map { ["slot": $0] } ?? [:])
    }

    /// Construit la carte vainqueur (nom/avatar/voix/%) à partir de l'id de l'artiste gagnant.
    private func winnerFromId(_ winnerId: String?) -> DuelWinner? {
        guard let duel, let winnerId else { return nil }
        let v1 = voteTotals[duel.artist1Id] ?? 0
        let v2 = voteTotals[duel.artist2Id] ?? 0
        let profile = winnerId == duel.artist1Id ? duel.artist1 : (winnerId == duel.artist2Id ? duel.artist2 : nil)
        let winnerVotes = winnerId == duel.artist1Id ? v1 : v2
        let total = v1 + v2
        let percent = total > 0 ? Int((winnerVotes / total) * 100) : 100
        return DuelWinner(name: profile?.displayName ?? "Vainqueur", avatar: profile?.avatarURL, votes: Int(winnerVotes), percent: percent)
    }

    /// Annonce AUTOMATIQUEMENT le vainqueur = artiste avec le PLUS de votes : sauve `winnerId`
    /// SANS terminer le duel, et diffuse la célébration en plein écran à tous.
    public func announceWinnerAuto() {
        guard let duel else { return }
        let v1 = voteTotals[duel.artist1Id] ?? 0
        let v2 = voteTotals[duel.artist2Id] ?? 0
        let winnerId = v1 >= v2 ? duel.artist1Id : duel.artist2Id
        guard let w = winnerFromId(winnerId) else { return }
        shownWinnerId = winnerId
        winnerInfo = w
        Task { try? await repository.announceWinner(id: duelId, artistId: winnerId) }
        liveSession?.broadcast(
            channel: "duel-winner-\(duelId)",
            event: "winner_announced",
            payload: ["name": w.name, "avatar": w.avatar ?? "", "votes": w.votes, "percent": w.percent]
        )
    }

    /// Manager : arrête la célébration du vainqueur pour TOUS (ne termine PAS le direct).
    public func stopWinnerAnnouncement() {
        winnerInfo = nil
        liveSession?.broadcast(channel: "duel-winner-\(duelId)", event: "winner_stopped", payload: [:])
    }

    /// Termine le duel puis notifie l'appelant (sortie d'écran).
    public func endDuel(onEnded: @escaping () -> Void) {
        Task {
            try? await repository.endDuel(id: duelId)
            onEnded()
        }
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
        // Statut / vainqueur. La désignation d'un `winnerId` déclenche la célébration PLEIN
        // ÉCRAN chez TOUS (propagation FIABLE via la room du duel) — pas seulement via le
        // broadcast éphémère, qui peut être manqué.
        subscriptions.append(live.onEvent(Realtime.Event.status, as: StatusPayload.self) { [weak self] payload in
            guard let self, let current = self.duel else { return }
            Task { [weak self] in
                guard let self, let refreshed = try? await self.repository.duel(id: current.id) else { return }
                self.duel = refreshed
                let wid = payload.winnerId
                if wid == nil {
                    self.shownWinnerId = nil
                } else if wid != self.shownWinnerId {
                    self.shownWinnerId = wid
                    if let w = self.winnerFromId(wid) { self.winnerInfo = w }
                }
            }
        })
        // Cadeaux (alimente l'animation + le classement + la bannière « reçu » si c'est pour moi).
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
            Task { await self.loadTopDonor() }
            if let toUserId = payload.toUserId, toUserId == self.callerId {
                self.giftReceived = "🎁 Vous avez reçu un cadeau (\(Int(payload.value)) crédits) !"
            }
        })
        // Présence (spectateurs).
        subscriptions.append(live.onEvent(Realtime.Event.presence, as: PresencePayload.self) { [weak self] payload in
            self?.viewerCount = payload.count
        })
        // Relais broadcast (jamais reçu par l'émetteur lui-même) : réactions emoji, compteur de
        // likes partagé, focus imposé par le manager, vainqueur annoncé, hard-mute d'un artiste.
        subscriptions.append(live.onEvent(Realtime.Event.broadcast, as: BroadcastEnvelope.self) { [weak self] envelope in
            guard let self else { return }
            switch envelope.event {
            case "emoji_reaction":
                if let emoji = envelope.payload?.emoji { self.pushEmoji(emoji) }
            case "like":
                if let count = envelope.payload?.count, count > self.likes { self.likes = count }
            case "focus":
                self.forcedFocus = envelope.payload?.slot
            case "winner_announced":
                if let p = envelope.payload {
                    self.winnerInfo = DuelWinner(name: p.name ?? "Vainqueur", avatar: p.avatar, votes: p.votes ?? 0, percent: p.percent ?? 0)
                }
            case "winner_stopped":
                self.winnerInfo = nil
            case "FORCE_MUTE":
                if let artistId = envelope.payload?.artistId { self.applyMuteState(artistId, muted: true) }
            case "FORCE_UNMUTE":
                if let artistId = envelope.payload?.artistId { self.applyMuteState(artistId, muted: false) }
            default:
                break
            }
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

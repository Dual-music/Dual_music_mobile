import Foundation
import Observation
import CoreLiveMedia
import CoreNetwork
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

    /// Spectateurs bannis de ce live (ids) — leurs messages restent en mémoire mais sont
    /// masqués de l'affichage (``visibleMessages``), pas supprimés.
    public private(set) var bannedUserIds: Set<String> = []

    /// Messages à afficher : ceux d'un spectateur banni sont masqués pour tout le monde.
    public var visibleMessages: [LiveChatMessage] { messages.filter { !bannedUserIds.contains($0.userId) } }

    /// Vrai pour l'hôte (artiste qui diffuse) — contrôle l'accès au bannissement.
    public let isHost: Bool

    /// Hôte : demandes de dédicace EN ATTENTE pour ce live (à accepter/rejeter) — alimente le badge.
    public private(set) var dedications: [LiveDedication] = []
    /// Hôte : dédicaces déjà ACCEPTÉES ou LIVRÉES pour ce live (historique, sous les demandes).
    public private(set) var dedicationHistory: [LiveDedication] = []
    /// Prix minimum EFFECTIF d'une dédicace pour CE live (crédits) : la surcharge propre au
    /// live si l'hôte en a fixé une, sinon le défaut global (`economic_config`).
    public private(set) var dedicationMinPriceCredits: Double = 10
    /// Dédicaces activées pour CE live (réglage hôte, réactif en direct via `settings`).
    public private(set) var liveAllowsDedications: Bool = true
    /// Demandes d'invité (« lever la main ») activées pour CE live (réglage hôte).
    public private(set) var liveAllowGuests: Bool = true
    /// Chat activé pour CE live (réglage hôte, réactif en direct via `settings`) — pouvoir
    /// EXCLUSIF de l'hôte, jamais délégué aux modérateurs désignés.
    public private(set) var liveChatEnabled: Bool = true
    /// Confirmation « dédicace envoyée » (ou message d'échec/décision de l'hôte) affichée au fan.
    public private(set) var dedicationFeedback: String?
    public func clearDedicationFeedback() { dedicationFeedback = nil }

    /// Client média dédié à MA PROPRE publication en tant qu'INVITÉ (room
    /// `live-guest-<liveId>-<monId>`) — jamais la room principale (``media``), qui reste
    /// réservée à l'hôte. Parité `LiveViewModel.kt`.
    public let guestMedia: LiveRoomClient
    /// Hôte : demandes d'invité EN ATTENTE (« lever la main ») — alimente le badge.
    public private(set) var joinRequests: [LiveJoinRequest] = []
    /// TOUT LE MONDE : invités actuellement acceptés (sur scène) — pilote les abonnements
    /// de visionnage (``guestClients``), pas seulement affiché à l'hôte.
    public private(set) var acceptedGuests: [LiveJoinRequest] = []
    /// Spectateur : id de MA demande en cours, `nil` si aucune.
    public private(set) var myJoinRequestId: String?
    /// Spectateur : vrai une fois ma demande acceptée (je publie alors dans ``guestMedia``).
    public private(set) var isGuestAccepted = false
    /// Clients de VISIONNAGE des invités actifs (hors moi-même) — un par invité, room
    /// `live-guest-<liveId>-<userId>`, `canPublish: false`.
    public private(set) var guestClients: [String: LiveRoomClient] = [:]

    /// Modérateurs désignés de ce live (hôte + jusqu'à ``maxEventModerators`` spectateurs) —
    /// visible par tous, pour que chacun sache qui d'autre a le pouvoir de bannir.
    public private(set) var moderators: [EventModerator] = []
    /// Hôte : spectateurs actuellement connectés (vivier du picker de désignation).
    public private(set) var viewers: [DisplayProfile] = []
    /// Vrai si le caller est un modérateur désigné (jamais vrai pour l'hôte lui-même, qui a
    /// déjà tous les pouvoirs via ``isHost``).
    public var isModerator: Bool {
        guard let callerId else { return false }
        return moderators.contains { $0.userId == callerId }
    }
    /// Vrai si le caller peut bannir/masquer un message : l'hôte ou un modérateur désigné.
    public var canModerate: Bool { isHost || isModerator }

    private let liveId: String
    private let roomName: String
    private let callerId: String?
    private let tokenService: LiveKitTokenService
    private let realtime: RealtimeClient
    private let repository: LiveRepository

    private var giftCounter = 0
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?
    private var started = false
    private var guestViewTasks: [String: Task<Void, Never>] = [:]

    /// - Parameters:
    ///   - liveId: identifiant du live (contexte chat/cadeaux).
    ///   - roomName: room LiveKit à rejoindre.
    ///   - media: client média dédié à ce live.
    ///   - tokenService: service de jetons LiveKit — sert à créer les clients d'invité
    ///     (ma propre publication + le visionnage des autres invités actifs).
    ///   - realtime: client Socket.IO partagé.
    ///   - repository: lectures/actions REST du live.
    ///   - isHost: vrai pour l'artiste qui diffuse — autorise le bannissement.
    ///   - callerId: id du caller (fan) — sert à cibler la bannière de décision de dédicace
    ///     et à me reconnaître dans les événements d'invité.
    public init(
        liveId: String,
        roomName: String,
        media: LiveRoomClient,
        tokenService: LiveKitTokenService,
        realtime: RealtimeClient,
        repository: LiveRepository,
        isHost: Bool = false,
        callerId: String? = nil
    ) {
        self.liveId = liveId
        self.roomName = roomName
        self.media = media
        self.tokenService = tokenService
        self.guestMedia = LiveRoomClient(tokenService: tokenService)
        self.realtime = realtime
        self.repository = repository
        self.isHost = isHost
        self.callerId = callerId
    }

    /// Démarre : vidéo, historique de chat, rooms temps réel.
    ///
    /// - Parameter prewarmedToken: jeton LiveKit pré-obtenu par le feed (réduit la latence).
    public func start(prewarmedToken: LiveKitToken? = nil) async {
        guard !started else { return }
        started = true

        // La vidéo, l'historique et le temps réel démarrent en parallèle : aucun n'attend
        // l'autre (l'utilisateur voit le chat même si le SFU est lent, et inversement).
        Task { await media.join(roomName: roomName, isHost: isHost, prewarmedToken: prewarmedToken) }
        Task { [weak self] in
            guard let self else { return }
            if let history = try? await self.repository.chatHistory(liveId: self.liveId) {
                self.messages = history
            }
        }
        Task { [weak self] in await self?.loadLiveSettings() }
        Task { [weak self] in await self?.loadModerators() }
        if isHost {
            Task { [weak self] in await self?.loadDedications() }
            Task { [weak self] in await self?.loadJoinRequests() }
        } else {
            Task { [weak self] in await self?.loadAcceptedGuests() }
        }
        await connectRealtime()
    }

    /// Charge les réglages de CE live (dédicaces on/off + prix minimum effectif, invités
    /// on/off) — pour tout le monde (hôte, invité, spectateur), tous doivent voir les mêmes
    /// règles.
    private func loadLiveSettings() async {
        let globalDefault = (try? await repository.dedicationMinPrice()) ?? 10
        if let live = try? await repository.live(id: liveId) {
            liveAllowsDedications = live.allowsDedications
            liveAllowGuests = live.allowGuests
            liveChatEnabled = live.chatEnabled
            dedicationMinPriceCredits = live.dedicationMinPriceCredits ?? globalDefault
        } else {
            dedicationMinPriceCredits = globalDefault
        }
    }

    /// Arrête tout (sortie d'écran) : temps réel + SFU (room principale, ma publication
    /// d'invité éventuelle, et tous les visionnages d'invités actifs).
    public func stop() async {
        // Quitter le live EST une expulsion : si j'étais un invité ACCEPTÉ, ma demande passe
        // "ended" côté serveur — sinon ma case restait visible pour tous (placeholder permanent)
        // et je serais ré-accepté sans nouvelle demande à mon retour. Symétrique de
        // ``leaveStage()``/du retrait par l'hôte.
        if !isHost, isGuestAccepted, let rid = myJoinRequestId {
            try? await repository.cancelJoin(requestId: rid)
        }
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        await media.leave()
        await guestMedia.leave()
        guestViewTasks.values.forEach { $0.cancel() }
        guestViewTasks.removeAll()
        for client in guestClients.values { await client.leave() }
        guestClients.removeAll()
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

    /// Signale ce live avec un motif (modération).
    public func report(reason: ReportReason) async {
        try? await repository.reportLive(liveId: liveId, reason: reason)
    }

    /// Hôte OU modérateur désigné (``canModerate``) : bannit un spectateur (optimiste +
    /// persistant). Il ne peut plus écrire ni rejoindre ; ses messages passés sont masqués
    /// (``visibleMessages``).
    /// - Parameters:
    ///   - userId: spectateur ciblé.
    ///   - reason: motif libre (ex. le message signalé), optionnel.
    public func banUser(userId: String, reason: String?) async {
        bannedUserIds.insert(userId)
        do {
            try await repository.createStreamBan(streamId: liveId, bannedUserId: userId, reason: reason)
        } catch {
            bannedUserIds.remove(userId)
        }
    }

    /// Hôte : démarre la diffusion caméra/micro sur la room déjà rejointe.
    public func startBroadcast() async {
        await media.startBroadcast()
    }

    /// Hôte : coupe/rétablit le micro.
    public func toggleMic() async {
        await media.setMicrophone(enabled: !media.isMicrophoneEnabled)
    }

    /// Hôte : coupe/rétablit la caméra.
    public func toggleCamera() async {
        await media.setCamera(enabled: !media.isCameraEnabled)
    }

    /// Hôte : bascule caméra avant/arrière.
    public func switchCamera() async {
        await media.switchCamera()
    }

    /// Hôte : termine ce live côté backend (arrête aussi la diffusion locale).
    public func endLive() async throws {
        try await repository.endLive(liveId: liveId)
        await media.stopBroadcast()
    }

    /// Retire le cadeau le plus ancien après son animation.
    public func consumeOldestGift() {
        if !giftFeed.isEmpty { giftFeed.removeFirst() }
    }

    // MARK: - Dédicaces

    /// Hôte : active/désactive les dédicaces pour ce live, en direct (visible par tous).
    public func setDedicationsEnabled(_ enabled: Bool) {
        liveAllowsDedications = enabled
        Task { try? await repository.updateLiveSettings(liveId: liveId, allowsDedications: enabled) }
    }

    /// Hôte : fixe le prix minimum d'une dédicace pour CE live (surcharge le défaut global).
    public func setDedicationMinPrice(_ price: Double) {
        guard price > 0 else { return }
        dedicationMinPriceCredits = price
        Task { try? await repository.updateLiveSettings(liveId: liveId, dedicationMinPriceCredits: price) }
    }

    /// Fan : envoie une dédicace (message dédié). `price` est choisi par le fan (≥ prix
    /// minimum effectif — le champ de saisie le clamp déjà, revalidé ici par sécurité).
    /// L'échec (solde insuffisant, etc.) est SIGNALÉ au fan plutôt que silencieux.
    public func dedicate(message: String, price: Double) async {
        let text = message.trimmed
        guard !text.isEmpty else { return }
        let effectivePrice = max(price, dedicationMinPriceCredits)
        do {
            try await repository.sendDedication(liveId: liveId, message: text, priceCredits: effectivePrice)
            dedicationFeedback = "🎤 Dédicace envoyée à l'artiste !"
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Hôte : (re)charge les dédicaces de CE live — séparées en « en attente » (badge +
    /// actions accepter/rejeter) et « acceptées/livrées » (historique, même feuille).
    /// Auto-rafraîchi via temps réel (`dedication:new`/`dedication:update`).
    public func loadDedications() async {
        guard isHost else { return }
        let all = (try? await repository.artistDedications()) ?? []
        let mine = all.filter { $0.concertId == liveId }
        dedications = mine.filter { $0.status == "pending" }
        dedicationHistory = mine.filter { $0.status == "paid" || $0.status == "delivered" }
    }

    /// Hôte : accepte une demande EN ATTENTE — débite le fan MAINTENANT, puis recharge.
    public func acceptDedication(id: String) async {
        do {
            try await repository.acceptDedication(id: id)
            await loadDedications()
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Hôte : rejette une demande EN ATTENTE — aucun débit, puis recharge.
    public func rejectDedication(id: String) async {
        do {
            try await repository.rejectDedication(id: id)
            await loadDedications()
        } catch {
            dedicationFeedback = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Hôte : marque une dédicace ACCEPTÉE comme livrée (interprétée) puis recharge la liste.
    public func deliverDedication(id: String) async {
        try? await repository.deliverDedication(id: id)
        await loadDedications()
    }

    // MARK: - Invités sur scène

    /// Hôte : active/désactive les demandes d'invité pour ce live, en direct.
    public func setGuestsEnabled(_ enabled: Bool) {
        liveAllowGuests = enabled
        Task { try? await repository.updateLiveSettings(liveId: liveId, allowGuests: enabled) }
    }

    /// Spectateur : demande à rejoindre en invité (« lever la main »).
    public func requestJoin() async {
        myJoinRequestId = try? await repository.requestJoin(liveId: liveId)
    }

    /// Spectateur : annule SA demande (avant qu'elle soit traitée).
    public func cancelJoin() async {
        guard let rid = myJoinRequestId else { return }
        try? await repository.cancelJoin(requestId: rid)
        myJoinRequestId = nil
    }

    /// Hôte : (re)charge les demandes en attente + les invités actifs.
    public func loadJoinRequests() async {
        guard isHost else { return }
        joinRequests = (try? await repository.joinRequests(liveId: liveId, status: "pending")) ?? []
        await loadAcceptedGuests()
    }

    /// TOUT LE MONDE (spectateur ou invité) : (re)charge la liste des invités actifs —
    /// nécessaire pour savoir à quelles rooms d'invités s'abonner (``reconcileGuestSubscriptions``).
    /// Contrairement à ``loadJoinRequests()`` (réservé à l'hôte), ne charge pas les demandes
    /// en attente (non actionnables par un simple spectateur).
    private func loadAcceptedGuests() async {
        acceptedGuests = (try? await repository.joinRequests(liveId: liveId, status: "accepted")) ?? []
        await reconcileGuestSubscriptions()
    }

    /// Recalcule mes abonnements de VISIONNAGE aux rooms des invités actifs, hors moi-même :
    /// ouvre une room `live-guest-<liveId>-<userId>` par invité et en extrait la piste vidéo
    /// primaire (exposée par le `LiveRoomClient` lui-même, observable). Ferme celles des
    /// invités qui ne sont plus actifs — c'est ce qui fait disparaître la tuile d'un invité
    /// qui descend ou est retiré, chez TOUT LE MONDE (y compris l'hôte, qui suit désormais la
    /// même logique qu'un spectateur pour voir les invités).
    private func reconcileGuestSubscriptions() async {
        let wanted = Set(acceptedGuests.map(\.userId).filter { $0 != callerId })
        let stale = Set(guestClients.keys).subtracting(wanted)
        for uid in stale {
            guestViewTasks.removeValue(forKey: uid)?.cancel()
            if let client = guestClients.removeValue(forKey: uid) {
                await client.leave()
            }
        }
        let toAdd = wanted.subtracting(guestClients.keys)
        for uid in toAdd {
            let client = LiveRoomClient(tokenService: tokenService)
            guestClients[uid] = client
            let room = "live-guest-\(liveId)-\(uid)"
            guestViewTasks[uid] = Task {
                await client.join(roomName: room, isHost: false, canPublish: false)
            }
        }
    }

    /// Hôte : accepte/refuse une demande EN ATTENTE, puis recharge.
    public func respondJoin(id: String, accept: Bool) async {
        try? await repository.respondJoin(requestId: id, accept: accept)
        await loadJoinRequests()
    }

    /// Hôte : retire un invité déjà accepté, puis recharge.
    public func kickGuest(requestId: String) async {
        try? await repository.kickGuest(requestId: requestId)
        await loadJoinRequests()
    }

    /// Invité accepté : monte sur scène — publie caméra/micro dans MA PROPRE room d'invité
    /// (``guestMedia``), **sans jamais toucher** ``media`` (je reste connecté à la room
    /// principale comme spectateur, je continue donc de voir/entendre l'artiste pendant que
    /// je diffuse). Micro + caméra coupés par défaut à l'entrée en scène : c'est à l'invité
    /// d'activer consciemment ce qu'il veut montrer.
    private func goOnStage() async {
        guard let uid = callerId else { return }
        await guestMedia.join(roomName: "live-guest-\(liveId)-\(uid)", isHost: false, canPublish: true)
        await guestMedia.startBroadcast()
        await guestMedia.setMicrophone(enabled: false)
        await guestMedia.setCamera(enabled: false)
    }

    /// Invité : descend du direct sans le quitter — arrête de publier dans sa room d'invité (sa
    /// tuile disparaît chez tous, via ``reconcileGuestSubscriptions``), clôt sa demande (l'hôte
    /// le retire de la liste). Reste connecté à la room principale (jamais quittée) → continue
    /// de regarder le live sans interruption.
    public func leaveStage() async {
        isGuestAccepted = false
        let rid = myJoinRequestId
        myJoinRequestId = nil
        // `respondJoin` (POST .../respond) est réservé à L'HÔTE côté backend — un invité qui
        // clôt SA PROPRE demande doit passer par l'annulation (DELETE), qui autorise le
        // demandeur lui-même quel que soit le statut courant (pending OU accepted).
        if let rid { try? await repository.cancelJoin(requestId: rid) }
        await guestMedia.leave()
    }

    /// Coupe/rétablit MON micro (hôte : room principale ; invité : sa room dédiée).
    public func toggleGuestMicrophone() async {
        await guestMedia.setMicrophone(enabled: !guestMedia.isMicrophoneEnabled)
    }

    /// Coupe/rétablit MA caméra (invité, sa room dédiée).
    public func toggleGuestCamera() async {
        await guestMedia.setCamera(enabled: !guestMedia.isCameraEnabled)
    }

    /// Hôte : active/désactive le chat pour ce live, en direct — pouvoir EXCLUSIF de l'hôte,
    /// jamais délégué aux modérateurs désignés.
    public func setChatEnabled(_ enabled: Bool) {
        liveChatEnabled = enabled
        Task { try? await repository.updateLiveSettings(liveId: liveId, chatEnabled: enabled) }
    }

    // MARK: - Modérateurs désignés

    /// (Re)charge les modérateurs désignés — appelé au démarrage + sur événement temps réel,
    /// pour TOUT LE MONDE (pas que l'hôte : chacun doit savoir qui d'autre peut bannir).
    public func loadModerators() async {
        moderators = (try? await repository.listEventModerators(liveId: liveId)) ?? []
    }

    /// Hôte : (re)charge les spectateurs connectés (vivier du picker « désigner un modérateur »).
    public func loadViewers() async {
        guard isHost else { return }
        viewers = (try? await repository.listCurrentViewers(liveId: liveId)) ?? []
    }

    /// Hôte : désigne un spectateur modérateur (ban/masquer message — jamais le chat on/off).
    public func appointModerator(userId: String) async {
        try? await repository.appointModerator(liveId: liveId, userId: userId)
        await loadModerators()
    }

    /// Hôte : révoque un modérateur désigné.
    public func revokeModerator(userId: String) async {
        try? await repository.revokeModerator(liveId: liveId, userId: userId)
        await loadModerators()
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
        // Réglages modifiés par l'hôte en direct — tout le monde réagit aussitôt (masque/
        // affiche le bouton de dédicace, etc.). `dedicationMinPriceCredits` absent = pas de
        // surcharge sur ce live → garde le prix effectif déjà chargé (défaut global).
        subscriptions.append(live.onEvent(Realtime.Event.settings, as: LiveSettingsPayload.self) { [weak self] payload in
            guard let self else { return }
            self.liveAllowsDedications = payload.allowsDedications
            self.liveAllowGuests = payload.allowGuests
            if let price = payload.dedicationMinPriceCredits { self.dedicationMinPriceCredits = price }
            if let chatEnabled = payload.chatEnabled { self.liveChatEnabled = chatEnabled }
        })
        // Dédicaces : (a) hôte — badge + liste auto-rafraîchis sans avoir à ouvrir la feuille ;
        // (b) fan concerné — bannière immédiate de la décision de l'hôte (accepté = débité
        // MAINTENANT, rejeté = aucun débit), pour ne jamais laisser croire à un solde qu'il n'a
        // plus.
        subscriptions.append(live.onEvent(Realtime.Event.dedicationNew, as: DedicationEventPayload.self) { [weak self] _ in
            guard let self, self.isHost else { return }
            Task { await self.loadDedications() }
        })
        subscriptions.append(live.onEvent(Realtime.Event.dedicationUpdate, as: DedicationEventPayload.self) { [weak self] payload in
            guard let self else { return }
            if self.isHost { Task { await self.loadDedications() } }
            guard let fanId = payload.fanId, fanId == self.callerId else { return }
            switch payload.status {
            case "paid":
                let credits = payload.priceCredits.map { String(Int($0)) } ?? ""
                self.dedicationFeedback = "🎤 Ta dédicace a été acceptée — \(credits) crédits débités."
            case "rejected":
                self.dedicationFeedback = "Ta dédicace a été refusée — aucun crédit débité."
            case "delivered":
                self.dedicationFeedback = "🎉 Ta dédicace vient d'être interprétée en direct !"
            default:
                break
            }
        })
        // Demandes d'invités : rafraîchir la liste (TOUT LE MONDE — pas que l'hôte — pour que
        // chacun sache à quelles rooms d'invités s'abonner, voir reconcileGuestSubscriptions).
        subscriptions.append(live.onEvent(Realtime.Event.joinNew, as: JoinEventPayload.self) { [weak self] _ in
            guard let self else { return }
            if self.isHost {
                Task { await self.loadJoinRequests() }
            } else {
                Task { await self.loadAcceptedGuests() }
            }
        })
        subscriptions.append(live.onEvent(Realtime.Event.joinUpdate, as: JoinEventPayload.self) { [weak self] payload in
            guard let self else { return }
            if self.isHost {
                Task { await self.loadJoinRequests() }
            } else {
                Task { await self.loadAcceptedGuests() }
            }
            // Spectateur : SA propre demande a changé d'état.
            guard !self.isHost, let userId = payload.userId, userId == self.callerId else { return }
            switch payload.status {
            case "accepted":
                self.isGuestAccepted = true
                Task { await self.goOnStage() }
            case "rejected", "ended", "cancelled":
                // "cancelled" = auto-retrait (leaveStage/stop) ; "ended"/"rejected" = décision
                // de l'hôte.
                self.isGuestAccepted = false
                Task { await self.guestMedia.leave() }
            default:
                break
            }
        })
        // Modération : un modérateur a été désigné/révoqué par l'hôte → recharge pour tous.
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

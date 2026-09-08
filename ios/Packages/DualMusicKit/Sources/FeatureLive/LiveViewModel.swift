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
    /// Confirmation « dédicace envoyée » (ou message d'échec/décision de l'hôte) affichée au fan.
    public private(set) var dedicationFeedback: String?
    public func clearDedicationFeedback() { dedicationFeedback = nil }

    private let liveId: String
    private let roomName: String
    private let callerId: String?
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
    ///   - isHost: vrai pour l'artiste qui diffuse — autorise le bannissement.
    ///   - callerId: id du caller (fan) — sert à cibler la bannière de décision de dédicace.
    public init(
        liveId: String,
        roomName: String,
        media: LiveRoomClient,
        realtime: RealtimeClient,
        repository: LiveRepository,
        isHost: Bool = false,
        callerId: String? = nil
    ) {
        self.liveId = liveId
        self.roomName = roomName
        self.media = media
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
        if isHost {
            Task { [weak self] in await self?.loadDedications() }
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
            dedicationMinPriceCredits = live.dedicationMinPriceCredits ?? globalDefault
        } else {
            dedicationMinPriceCredits = globalDefault
        }
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

    /// Signale ce live avec un motif (modération).
    public func report(reason: ReportReason) async {
        try? await repository.reportLive(liveId: liveId, reason: reason)
    }

    /// Hôte : bannit un spectateur (optimiste + persistant). Il ne peut plus écrire ni
    /// rejoindre ; ses messages passés sont masqués (``visibleMessages``).
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

        await live.connect()
        await chat.connect()
    }
}

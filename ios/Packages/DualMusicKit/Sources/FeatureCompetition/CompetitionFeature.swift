import SwiftUI
import Observation
import LiveKit
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels

/// Message de chat d'une compétition (auteur hydraté par le backend).
///
/// ⚠️ Le backend utilise la clé `message` (colonne DB) pour le contenu et `author` pour
/// l'auteur hydraté — pas `content`/`user` comme Live/Duel/Concert.
public struct CompetitionChatMessage: Decodable, Sendable, Identifiable, Equatable {
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

/// Corps de `POST /competitions/:id/messages`.
struct CompetitionMessageBody: Encodable, Sendable {
    let message: String
}

/// Réponse de `GET /lives/:id/likes` (réutilisée par les compétitions).
private struct CompetitionLikesResponse: Decodable, Sendable {
    let likes: Int
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        likes = c.int(.likes)
    }
    enum CodingKeys: String, CodingKey { case likes }
}

/// Réglage public `vote_config` : `GET /settings/public/vote_config`.
private struct CompetitionVoteConfigSetting: Decodable, Sendable {
    let value: CompetitionVoteConfigSettingValue?
}
private struct CompetitionVoteConfigSettingValue: Decodable, Sendable {
    let pricePerVote: Double
    enum CodingKeys: String, CodingKey { case pricePerVote = "price_per_vote" }
}

/// Réglage public `manual_candidates_config` : ajout manuel de candidat activé ?
private struct ManualCandidatesConfigSetting: Decodable, Sendable {
    let value: ManualCandidatesConfigSettingValue?
}
private struct ManualCandidatesConfigSettingValue: Decodable, Sendable {
    let enabled: Bool?
}

/// Corps de `POST /competitions/:id/candidates/manual`.
struct AddCandidateManuallyBody: Encodable, Sendable {
    let artistId: String
    let pitch: String?
}

/// Corps de `POST /competitions/candidates/:id/review`.
struct CompetitionReviewBody: Encodable, Sendable {
    let approve: Bool
}

/// Corps de `PATCH /competitions/:id` — chat activé/désactivé (dédié, plutôt que le corps
/// COMPLET de ``CreateCompetitionBody`` exigé par ``CompetitionRepository/updateCompetition``).
struct CompetitionChatBody: Encodable, Sendable {
    let chatEnabled: Bool
}

/// Corps de `POST /competitions/candidates/:id/jury-votes`.
struct CompetitionJuryVotesBody: Encodable, Sendable {
    let juryVotes: Int
}

/// Corps de `POST /competitions/:id/performer` — `candidateId` explicitement `null` pour arrêter
/// (encodage manuel : le synthétisé Swift OMET un optionnel `nil` au lieu de l'encoder `null`).
struct CompetitionPerformerBody: Encodable, Sendable {
    let candidateId: String?
    let durationSec: Int

    enum CodingKeys: String, CodingKey { case candidateId, durationSec }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(candidateId, forKey: .candidateId)
        try c.encode(durationSec, forKey: .durationSec)
    }
}

/// Corps de `POST /competitions/:id/focus` — `participantId` explicitement `null` pour libérer
/// (même raison d'encodage manuel que ``CompetitionPerformerBody``).
struct CompetitionFocusBody: Encodable, Sendable {
    let participantId: String?

    enum CodingKeys: String, CodingKey { case participantId }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(participantId, forKey: .participantId)
    }
}

/// Accès REST aux compétitions.
///
/// ⚠️ Contrairement au duel (dont le vote passe par `/wallet/vote`), le vote de compétition
/// a son propre endpoint (`POST /competitions/:id/vote`) — mais reste un **débit atomique**
/// exécuté par une procédure stockée côté serveur.
public struct CompetitionRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Catalogue public des compétitions.
    public func competitions(limit: Int = 50) async throws -> [Competition] {
        try await http.request(
            .get(CompetitionEndpoints.list, query: ["limit": String(limit)]),
            as: [Competition].self
        )
    }

    /// Détail d'une compétition.
    public func competition(id: String) async throws -> Competition {
        try await http.request(.get(CompetitionEndpoints.detail(id)), as: Competition.self)
    }

    /// Candidats avec leurs tallies (base du classement).
    public func candidates(id: String) async throws -> [CompetitionCandidate] {
        try await http.request(.get(CompetitionEndpoints.candidates(id)), as: [CompetitionCandidate].self)
    }

    /// Vote payant pour un candidat (débit atomique + idempotent).
    /// - Parameters:
    ///   - competitionId: compétition concernée.
    ///   - candidateId: candidat visé.
    ///   - credits: nombre **entier** de crédits (contrainte backend).
    public func vote(
        competitionId: String,
        candidateId: String,
        credits: Int,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                CompetitionEndpoints.vote(competitionId),
                body: CompetitionVoteRequest(candidateId: candidateId, credits: credits),
                idempotencyKey: idempotencyKey
            )
        )
    }

    /// Envoie un cadeau à un candidat OU au manager (exactement un destinataire).
    public func sendGift(
        competitionId: String,
        request: CompetitionGiftRequest,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(CompetitionEndpoints.gifts(competitionId), body: request, idempotencyKey: idempotencyKey)
        )
    }

    /// Achète le billet spectateur de la compétition.
    public func buyTicket(competitionId: String, idempotencyKey: String = UUID().uuidString) async throws {
        try await http.send(.post(CompetitionEndpoints.tickets(competitionId), idempotencyKey: idempotencyKey))
    }

    /// Signale cette compétition à la modération.
    ///
    /// ⚠️ Endpoint et forme de requête **différents** de Live/Duel/Concert : table dédiée
    /// `competition_reports`, clé `competitionId` (pas `liveId`), pas de `streamType`.
    public func reportCompetition(competitionId: String, reason: ReportReason) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.reportsCompetition,
                body: ReportCompetitionBody(competitionId: competitionId, reason: reason.rawValue)
            )
        )
    }

    /// Bannit un spectateur ou un candidat (manager uniquement) — table/canal dédiés
    /// `competition_bans` / `competition:banned`, distincts du mécanisme générique
    /// `stream-bans` des trois autres types d'évènement.
    public func createCompetitionBan(competitionId: String, bannedUserId: String, reason: String?) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.competitionBans,
                body: CompetitionBanBody(competitionId: competitionId, bannedUserId: bannedUserId, reason: reason)
            )
        )
    }

    /// Spectateurs/candidats déjà bannis de cette compétition (ids) — amorce l'affichage pour
    /// un arrivant tardif. `GET /moderation/competition-bans` (endpoint dédié, pas le
    /// mécanisme générique `stream-bans`). Best-effort : erreur réseau → liste vide.
    public func listCompetitionBans(competitionId: String) async -> [String] {
        let rows = (try? await http.request(
            .get(ModerationReportEndpoints.competitionBans, query: ["competitionId": competitionId]),
            as: [StreamBanRow].self
        )) ?? []
        return rows.compactMap(\.bannedUserId)
    }

    /// Historique de chat (dernière page) pour amorcer l'overlay.
    public func chatHistory(competitionId: String) async throws -> [CompetitionChatMessage] {
        try await http.request(
            .get(CompetitionEndpoints.messages(competitionId), query: ["limit": "50"]),
            as: [CompetitionChatMessage].self
        )
    }

    /// Poste un message (le backend le diffuse ensuite via Socket.IO).
    public func postMessage(competitionId: String, content: String) async throws {
        try await http.send(.post(CompetitionEndpoints.messages(competitionId), body: CompetitionMessageBody(message: content)))
    }

    /// Spectateurs actuellement connectés (manager uniquement — vivier du picker).
    public func listCurrentViewers(competitionId: String) async throws -> [DisplayProfile] {
        try await http.request(.get(ModerationEndpoints.viewers("competition", competitionId)), as: [DisplayProfile].self)
    }

    /// Modérateurs désignés de cette compétition (manager + jusqu'à ``maxEventModerators``
    /// spectateurs).
    public func listEventModerators(competitionId: String) async throws -> [EventModerator] {
        try await http.request(.get(ModerationEndpoints.moderators("competition", competitionId)), as: [EventModerator].self)
    }

    /// Manager : désigne un spectateur modérateur.
    public func appointModerator(competitionId: String, userId: String) async throws {
        try await http.send(.post(ModerationEndpoints.moderators("competition", competitionId), body: AppointModeratorBody(userId: userId)))
    }

    /// Manager : révoque un modérateur désigné.
    public func revokeModerator(competitionId: String, userId: String) async throws {
        try await http.send(.delete(ModerationEndpoints.revokeModerator("competition", competitionId, userId)))
    }

    // MARK: - Billetterie / gate d'accès programmé

    /// Le caller possède-t-il un billet pour cette compétition ? `GET /competitions/:id/my-ticket`.
    public func ticketInfo(id: String) async -> CompetitionTicketInfo {
        (try? await http.request(.get(CompetitionEndpoints.myTicket(id)), as: CompetitionTicketInfo.self)) ?? CompetitionTicketInfo()
    }

    /// Vrai si le caller est admin (acteur exempté de billet, jamais de l'attente).
    public func amIAdmin() async -> Bool {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.roles.contains(.admin) ?? false
    }

    /// Vrai si le caller a le rôle artiste (éligibilité au bouton Candidater).
    public func amIArtist() async -> Bool {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.roles.contains(.artist) ?? false
    }

    /// Id du caller — sert de `managerId` à la création + à filtrer ses compétitions.
    public func myUserId() async -> String? {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.user.id
    }

    /// Prix d'UN vote en crédits — configuré par l'admin (défaut 1).
    public func votePricePerVote() async -> Double {
        (try? await http.request(.get("/settings/public/vote_config"), as: CompetitionVoteConfigSetting.self))?.value?.pricePerVote ?? 1.0
    }

    // MARK: - J'aime persistés

    /// Compteur de j'aime PERSISTÉ (les compétitions partagent l'endpoint des lives).
    public func likesCount(competitionId: String) async -> Int {
        (try? await http.request(.get(LiveEndpoints.likes(competitionId)), as: CompetitionLikesResponse.self))?.likes ?? 0
    }

    /// Incrémente + persiste le j'aime côté serveur.
    public func likeCompetition(competitionId: String) async {
        try? await http.send(.post(LiveEndpoints.likes(competitionId)))
    }

    // MARK: - Cadeaux : classement des donateurs

    /// Classement des donateurs (`GET /leaderboards/gifts?contextType=competition`).
    public func giftLeaderboard(competitionId: String) async throws -> [CompetitionDonorEntry] {
        try await http.request(
            .get(LeaderboardEndpoints.gifts, query: ["contextType": "competition", "contextId": competitionId]),
            as: [CompetitionDonorEntry].self
        )
    }

    // MARK: - Candidature de l'artiste

    /// Candidatures du caller (artiste), enrichies de leur compétition.
    public func myCandidacies() async throws -> [MyCandidacy] {
        try await http.request(.get(CompetitionEndpoints.candidaciesMine), as: [MyCandidacy].self)
    }

    /// Auto-candidature de l'artiste (distincte de l'ajout manuel par le manager).
    public func apply(competitionId: String, pitch: String?, videoDemoUrl: String?) async throws {
        try await http.send(
            .post(
                CompetitionEndpoints.apply(competitionId),
                body: CompetitionApplyRequest(pitch: pitch?.nilIfBlank, videoDemoUrl: videoDemoUrl?.nilIfBlank)
            )
        )
    }

    // MARK: - Espace MANAGER (organisateur de compétitions)

    /// Compétitions gérées par ce manager.
    public func myCompetitions() async throws -> [Competition] {
        try await http.request(.get(CompetitionEndpoints.mine), as: [Competition].self)
    }

    /// Crée une compétition (le manager s'assigne organisateur). Le backend force `status =
    /// "draft"` à la création, quel que soit le `status` transmis.
    public func createCompetition(_ body: CreateCompetitionBody) async throws {
        try await http.send(.post(CompetitionEndpoints.list, body: body))
    }

    /// Met à jour une compétition existante (édition).
    public func updateCompetition(id: String, body: CreateCompetitionBody) async throws {
        try await http.send(.patch(CompetitionEndpoints.detail(id), body: body))
    }

    /// Publie la compétition (ouvre les votes).
    public func publish(id: String) async throws {
        try await http.send(.post(CompetitionEndpoints.publish(id)))
    }

    /// Finalise le classement (clôture définitive).
    public func finalize(id: String) async throws {
        try await http.send(.post(CompetitionEndpoints.finalize(id)))
    }

    /// Active/désactive le chat de cette compétition pour tous (manager/admin uniquement).
    public func setChatEnabled(id: String, enabled: Bool) async throws {
        try await http.send(.patch(CompetitionEndpoints.detail(id), body: CompetitionChatBody(chatEnabled: enabled)))
    }

    // MARK: - Contrôles manager en direct

    /// Valide/rejette une candidature en attente.
    public func reviewCandidate(candidateId: String, approve: Bool) async throws {
        try await http.send(.post(CompetitionEndpoints.candidateReview(candidateId), body: CompetitionReviewBody(approve: approve)))
    }

    /// L'ajout manuel de candidat est-il activé (réglage admin) ? Désactivé par défaut.
    public func manualCandidatesEnabled() async -> Bool {
        (try? await http.request(.get("/settings/public/manual_candidates_config"), as: ManualCandidatesConfigSetting.self))?.value?.enabled == true
    }

    /// Annuaire public des artistes — pour le picker d'ajout manuel.
    public func artistDirectory() async -> [ArtistDirectoryEntry] {
        (try? await http.request(.get(ArtistEndpoints.list), as: [ArtistDirectoryEntry].self)) ?? []
    }

    /// Ajoute directement un candidat (walk-in, présentiel) sans candidature en ligne —
    /// verrouillé côté serveur par ``manualCandidatesEnabled()``.
    public func addCandidateManually(competitionId: String, artistId: String, pitch: String?) async throws {
        try await http.send(
            .post(CompetitionEndpoints.candidateManual(competitionId), body: AddCandidateManuallyBody(artistId: artistId, pitch: pitch?.nilIfBlank))
        )
    }

    /// Fixe (valeur absolue, pas un incrément) les voix cumulées d'un jury hors ligne pour un
    /// candidat — additionnées aux votes payants + cadeaux dans le classement.
    public func setJuryVotes(candidateId: String, juryVotes: Int) async throws {
        try await http.send(.post(CompetitionEndpoints.candidateJuryVotes(candidateId), body: CompetitionJuryVotesBody(juryVotes: juryVotes)))
    }

    /// Désigne le candidat actuellement mis en avant (ou `nil` pour arrêter).
    public func setPerformer(id: String, candidateId: String?, durationSeconds: Int) async throws {
        try await http.send(.post(CompetitionEndpoints.performer(id), body: CompetitionPerformerBody(candidateId: candidateId, durationSec: durationSeconds)))
    }

    /// Impose (ou libère avec `nil`) la caméra épinglée pour tous.
    public func setFocus(id: String, participantId: String?) async throws {
        try await http.send(.post(CompetitionEndpoints.focus(id), body: CompetitionFocusBody(participantId: participantId)))
    }
}

/// Corps de `POST /moderation/reports/competition`.
struct ReportCompetitionBody: Encodable, Sendable {
    let competitionId: String
    let reason: String
}

/// Corps de `POST /moderation/competition-bans`.
struct CompetitionBanBody: Encodable, Sendable {
    let competitionId: String
    let bannedUserId: String
    let reason: String?
}

/// ViewModel du catalogue de compétitions.
@Observable
@MainActor
public final class CompetitionsViewModel {

    public private(set) var competitions: [Competition] = []
    public private(set) var isLoading = false

    private let repository: CompetitionRepository

    public init(repository: CompetitionRepository) {
        self.repository = repository
    }

    /// Charge le catalogue.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        competitions = (try? await repository.competitions()) ?? []
        isLoading = false
    }
}

/// ViewModel de la room de compétition : classement des candidats + vote payant.
///
/// Choix d'implémentation (identique à Android) : après un vote, on **recharge les candidats
/// depuis le serveur** plutôt que d'incrémenter localement — les tallies font foi côté
/// backend, et on évite tout optimisme sur l'argent.
@Observable
@MainActor
public final class CompetitionRoomViewModel {

    /// Candidats triés par score décroissant (= classement courant).
    /// Client média — UNE SEULE room partagée par tous les publieurs potentiels (manager +
    /// candidats approuvés en mode `"online"`), contrairement à Duel (une room par slot).
    /// ``LiveRoomClient/remoteTiles`` expose les pistes distantes indexées par identité pour
    /// le rendu multi-tuiles ; ``LiveRoomClient/localVideoTrack`` mon propre aperçu si je
    /// publie.
    public let media: LiveRoomClient

    public private(set) var candidates: [CompetitionCandidate] = []
    public private(set) var status: String?
    public private(set) var errorMessage: String?
    public private(set) var competition: Competition?
    public private(set) var messages: [CompetitionChatMessage] = []

    /// Vrai si je peux publier ma caméra : le manager, ou un candidat APPROUVÉ en mode
    /// `"online"` (en mode `"onsite"`, seul l'appareil du manager filme). Recalculé une fois
    /// la compétition ET les candidats chargés — contrairement à Live/Duel/Concert, pas connu
    /// avant (le statut d'approbation d'un candidat n'est disponible qu'après un fetch).
    public private(set) var canPublish = false

    /// Spectateurs/candidats bannis de cette compétition (ids) — leurs messages restent en
    /// mémoire mais sont masqués de l'affichage (``visibleMessages``), pas supprimés.
    public private(set) var bannedUserIds: Set<String> = []
    /// Messages à afficher : ceux d'un banni sont masqués pour tout le monde.
    public var visibleMessages: [CompetitionChatMessage] { messages.filter { !bannedUserIds.contains($0.userId) } }
    /// Vrai si le caller est le manager (organisateur) de cette compétition.
    public var isManager: Bool {
        guard let callerId, let managerId = competition?.managerId else { return false }
        return managerId == callerId
    }

    /// Modérateurs désignés de cette compétition (manager + jusqu'à ``maxEventModerators``
    /// spectateurs) — visible par tous, pour que chacun sache qui d'autre a le pouvoir de
    /// bannir.
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

    private let competitionId: String
    private let callerId: String?
    private let repository: CompetitionRepository
    private let realtime: RealtimeClient
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?

    /// - Parameters:
    ///   - competitionId: identifiant de la compétition.
    ///   - media: client média dédié.
    ///   - repository: lectures + débits.
    ///   - realtime: client Socket.IO (écoute des changements de statut + chat).
    ///   - callerId: id du caller — détermine ``isManager``/``canPublish`` une fois la
    ///     compétition chargée.
    public init(
        competitionId: String,
        media: LiveRoomClient,
        repository: CompetitionRepository,
        realtime: RealtimeClient,
        callerId: String? = nil
    ) {
        self.competitionId = competitionId
        self.media = media
        self.repository = repository
        self.realtime = realtime
        self.callerId = callerId
    }

    /// Démarre : charge le classement + le détail + le chat + écoute le temps réel.
    public func start() async {
        await refresh()
        Task { [weak self] in
            guard let self else { return }
            self.competition = try? await self.repository.competition(id: self.competitionId)
            // Recalculé maintenant que competition ET candidates (chargés par `refresh()`
            // juste avant) sont disponibles.
            let isManagerNow = self.callerId != nil && self.callerId == self.competition?.managerId
            let approvedCandidate = self.competition?.mode == "online" && self.callerId != nil
                && self.candidates.contains { $0.artistId == self.callerId && $0.status == "approved" }
            self.canPublish = isManagerNow || approvedCandidate
            let roomName = self.competition?.liveKitRoom ?? "comp-\(self.competitionId)"
            await self.media.join(roomName: roomName, isHost: isManagerNow, canPublish: self.canPublish)
        }
        Task { [weak self] in
            guard let self else { return }
            if let history = try? await self.repository.chatHistory(competitionId: self.competitionId) {
                self.messages = history
            }
        }
        Task { [weak self] in
            guard let self else { return }
            let banned = await self.repository.listCompetitionBans(competitionId: self.competitionId)
            self.bannedUserIds.formUnion(banned)
        }
        Task { [weak self] in await self?.loadModerators() }

        let live = realtime.session(.live)
        let chat = realtime.session(.chat)
        liveSession = live
        chatSession = chat
        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.competition, id: self.competitionId)
        })
        subscriptions.append(chat.onConnect { [weak self] in
            guard let self else { return }
            chat.join(.competition, id: self.competitionId)
        })
        subscriptions.append(live.onEvent(Realtime.Event.status, as: StatusPayload.self) { [weak self] payload in
            guard let self else { return }
            self.status = payload.status
            // Un changement d'état peut clore les votes → on resynchronise le classement.
            Task { await self.refresh() }
        })
        subscriptions.append(chat.onEvent(Realtime.Event.chatMessage, as: ChatMessagePayload.self) { [weak self] payload in
            self?.messages.append(
                CompetitionChatMessage(id: payload.id, userId: payload.userId, content: payload.content, user: payload.user)
            )
        })
        subscriptions.append(live.onEvent(Realtime.Event.competitionBanned, as: CompetitionBannedPayload.self) { [weak self] payload in
            guard let self, payload.competitionId == nil || payload.competitionId == self.competitionId else { return }
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

    /// Arrête l'écoute temps réel.
    public func stop() async {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
        chatSession?.disconnect()
        await media.leave()
    }

    // MARK: - Diffusion (manager, ou candidat approuvé en mode "online")

    /// Démarre la diffusion caméra + micro (si ``canPublish``).
    public func startBroadcast() async {
        await media.startBroadcast()
    }

    /// Coupe/rétablit MON micro.
    public func toggleMic() async {
        await media.setMicrophone(enabled: !media.isMicrophoneEnabled)
    }

    /// Coupe/rétablit MA caméra.
    public func toggleCamera() async {
        await media.setCamera(enabled: !media.isCameraEnabled)
    }

    /// Bascule caméra avant/arrière.
    public func switchCamera() async {
        await media.switchCamera()
    }

    /// Applique un filtre couleur à MA diffusion.
    public func setColorFilter(id: String, matrix: [Float]?) {
        media.setColorFilter(id: id, matrix: matrix)
    }

    /// Envoie un message de chat.
    public func sendMessage(_ text: String) async {
        let content = text.trimmed
        guard !content.isEmpty else { return }
        try? await repository.postMessage(competitionId: competitionId, content: content)
    }

    /// Manager : bannit un spectateur/candidat (optimiste + persistant).
    /// - Parameters:
    ///   - userId: cible.
    ///   - reason: motif libre (ex. le message signalé), optionnel.
    public func banUser(userId: String, reason: String?) async {
        bannedUserIds.insert(userId)
        do {
            try await repository.createCompetitionBan(competitionId: competitionId, bannedUserId: userId, reason: reason)
        } catch {
            bannedUserIds.remove(userId)
        }
    }

    // MARK: - Modérateurs désignés

    /// (Re)charge les modérateurs désignés — appelé au démarrage + sur événement temps réel,
    /// pour TOUT LE MONDE (pas que le manager : chacun doit savoir qui d'autre peut bannir).
    public func loadModerators() async {
        moderators = (try? await repository.listEventModerators(competitionId: competitionId)) ?? []
    }

    /// Manager : (re)charge les spectateurs connectés (vivier du picker « désigner »).
    public func loadViewers() async {
        guard isManager else { return }
        viewers = (try? await repository.listCurrentViewers(competitionId: competitionId)) ?? []
    }

    /// Manager : désigne un spectateur modérateur (ban/masquer message).
    public func appointModerator(userId: String) async {
        try? await repository.appointModerator(competitionId: competitionId, userId: userId)
        await loadModerators()
    }

    /// Manager : révoque un modérateur désigné.
    public func revokeModerator(userId: String) async {
        try? await repository.revokeModerator(competitionId: competitionId, userId: userId)
        await loadModerators()
    }

    /// Recharge le classement depuis le serveur (source de vérité des tallies).
    public func refresh() async {
        if let list = try? await repository.candidates(id: competitionId) {
            candidates = list.sorted { $0.score > $1.score }
        }
    }

    /// Vote payant pour un candidat, puis resynchronise le classement.
    /// - Parameters:
    ///   - candidateId: candidat visé.
    ///   - credits: montant entier en crédits.
    public func vote(candidateId: String, credits: Int) async {
        do {
            try await repository.vote(competitionId: competitionId, candidateId: candidateId, credits: credits)
            await refresh()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.errVoteFailed
        }
    }

    /// Efface l'erreur affichée.
    public func clearError() { errorMessage = nil }

    /// Signale cette compétition avec un motif (modération).
    public func report(reason: ReportReason) async {
        try? await repository.reportCompetition(competitionId: competitionId, reason: reason)
    }
}

/// Catalogue des compétitions.
@MainActor
public struct CompetitionsListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: CompetitionsViewModel
    private let onOpen: (Competition) -> Void

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'une compétition (room + classement).
    public init(viewModel: CompetitionsViewModel, onOpen: @escaping (Competition) -> Void) {
        self.viewModel = viewModel
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text(s.screenCompetitions)
                .font(DMFont.pageTitle)
                .foregroundStyle(theme.colors.foreground)
                .frame(maxWidth: .infinity)

            if viewModel.isLoading {
                DMLoadingBox()
            } else if viewModel.competitions.isEmpty {
                DMEmptyState(title: s.noCompetitions, subtitle: s.noCompetitionsHint, systemImage: "star")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.competitions) { competition in
                            Button { onOpen(competition) } label: { CompetitionRow(competition: competition) }
                                .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }
}

/// Carte d'une compétition : titre, période, statut, récompense.
private struct CompetitionRow: View {
    @Environment(\.dmTheme) private var theme
    let competition: Competition

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(competition.title)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let start = isoDay(competition.startAt) {
                        Text(start).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(competition.status.capitalizedFirst)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                    if competition.rewardAmount > 0 {
                        Text("🏆 \(formatAmount(competition.rewardAmount))")
                            .font(DMFont.caption).bold()
                            .foregroundStyle(theme.colors.accent)
                    }
                }
            }
        }
    }
}

/// Room de compétition : classement en direct + vote payant par candidat.
@MainActor
public struct CompetitionRoomView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: CompetitionRoomViewModel
    private let voteCredits: Int

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: CompetitionChatMessage?
    @State private var showModeratorsSheet = false
    @State private var showFilterSheet = false

    /// - Parameters:
    ///   - viewModel: état + actions.
    ///   - voteCredits: montant (crédits entiers) d'un vote rapide.
    public init(viewModel: CompetitionRoomViewModel, voteCredits: Int = 10) {
        self.viewModel = viewModel
        self.voteCredits = voteCredits
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            HStack {
                Text(s.ranking)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                if viewModel.canModerate {
                    Button {
                        showModeratorsSheet = true
                        if viewModel.isManager { Task { await viewModel.loadViewers() } }
                    } label: {
                        Image(systemName: "person.2.fill")
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(s.moderators))
                }
                Button { showReport = true } label: {
                    Image(systemName: "flag.fill")
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))
            }

            if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

            // Vidéo multi-tuiles : moi (si je publie) + les autres publieurs actifs (manager +
            // candidats approuvés en mode "online"). Absente si personne ne diffuse (mode
            // "onsite" sans manager connecté, par ex.).
            if viewModel.canPublish || viewModel.media.localVideoTrack != nil || !viewModel.media.remoteTiles.isEmpty {
                videoGrid
                if viewModel.canPublish { hostControls }
            }

            if viewModel.candidates.isEmpty {
                DMEmptyState(title: s.noCandidates, subtitle: s.noCandidatesHint, systemImage: "star")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(Array(viewModel.candidates.enumerated()), id: \.element.id) { index, candidate in
                            CandidateRow(rank: index + 1, candidate: candidate, voteCredits: voteCredits) {
                                Task { await viewModel.vote(candidateId: candidate.id, credits: voteCredits) }
                            }
                        }
                    }
                }
            }

            chatOverlay
            messageBar
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        .refreshable { await viewModel.refresh() }
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        .sheet(isPresented: $showFilterSheet) { filterSheet }
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
        // Confirmation de bannissement (manager uniquement — tap sur un auteur de message).
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

    /// Les 6 derniers messages visibles. Le manager peut bannir l'auteur d'un message en
    /// tapant sur son nom — jamais lui-même.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(viewModel.visibleMessages.suffix(6)) { message in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(message.authorName)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                        .onTapGesture {
                            guard viewModel.canModerate, message.userId != viewModel.competition?.managerId else { return }
                            banTarget = message
                        }
                    Text(message.content)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.foreground)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Saisie de message, sous le chat.
    private var messageBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(s.saySomething, text: $draft)
                .textFieldStyle(.plain)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(theme.colors.card, in: Capsule())
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
        }
    }

    /// Envoie le brouillon puis vide le champ.
    private func send() {
        let text = draft
        draft = ""
        Task { await viewModel.sendMessage(text) }
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

    /// Feuille : modérateurs désignés (révocables par le manager) + désignation d'un
    /// spectateur connecté (manager uniquement). Visible aussi des modérateurs eux-mêmes, en
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

            if viewModel.isManager {
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

    /// Ligne d'un modérateur désigné — révocable par le manager seulement.
    private func moderatorRow(_ moderator: EventModerator) -> some View {
        HStack {
            Text(moderator.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
            Spacer()
            if viewModel.isManager {
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

    /// Picker de désignation (manager uniquement) : spectateurs connectés, hors modérateurs
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

    /// Grille des publieurs actifs : moi (aperçu local, si je publie) puis les autres, triés
    /// par identité pour un ordre stable entre deux rafraîchissements.
    private var videoGrid: some View {
        let remote = viewModel.media.remoteTiles.sorted { $0.key < $1.key }
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 110))], spacing: theme.spacing.sm) {
            if let local = viewModel.media.localVideoTrack {
                SwiftUIVideoView(local, layoutMode: .fill)
                    .aspectRatio(1, contentMode: .fill)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            ForEach(remote, id: \.key) { _, track in
                SwiftUIVideoView(track, layoutMode: .fill)
                    .aspectRatio(1, contentMode: .fill)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
    }

    /// Contrôles du publieur (manager ou candidat approuvé) : démarrer la diffusion, puis
    /// mic/caméra/bascule/filtre une fois lancée.
    private var hostControls: some View {
        Group {
            if !viewModel.media.isCameraEnabled, !viewModel.media.isMicrophoneEnabled {
                Button {
                    Task { await viewModel.startBroadcast() }
                } label: {
                    Text(s.startLive)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(.white)
                        .padding(.horizontal, theme.spacing.md)
                        .padding(.vertical, theme.spacing.sm)
                        .background(theme.colors.accent, in: Capsule())
                }
                .buttonStyle(.plain)
            } else {
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
                    controlButton("camera.filters") {
                        showFilterSheet = true
                    }
                }
            }
        }
    }

    private func controlButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .foregroundStyle(theme.colors.foreground)
                .padding(theme.spacing.sm)
                .background(theme.colors.card, in: Circle())
        }
        .buttonStyle(.plain)
    }

    /// Feuille : grille des filtres couleur (voir ``VideoFilterPresets/all``).
    private var filterSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.colorFilters).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 84))], spacing: theme.spacing.md) {
                    ForEach(VideoFilterPresets.all) { preset in
                        Button {
                            viewModel.setColorFilter(id: preset.id, matrix: preset.matrix)
                        } label: {
                            VStack(spacing: theme.spacing.xs) {
                                Text(preset.emoji).font(.system(size: 28))
                                Text(filterLabel(preset.id))
                                    .font(DMFont.caption)
                                    .foregroundStyle(theme.colors.foreground)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(theme.spacing.sm)
                            .background(
                                viewModel.media.activeFilterId == preset.id ? theme.colors.accent.opacity(0.25) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Libellé localisé d'un filtre couleur.
    private func filterLabel(_ id: String) -> String {
        switch id {
        case "beauty": return s.filterBeauty
        case "smooth": return s.filterSmooth
        case "glow": return s.filterGlow
        case "warm": return s.filterWarm
        case "cool": return s.filterCool
        case "vivid": return s.filterVivid
        case "vintage": return s.filterVintage
        case "noir": return s.filterNoir
        case "studio": return s.filterStudio
        case "neon": return s.filterNeon
        case "dream": return s.filterDream
        default: return s.filterNone
        }
    }
}

/// Ligne de classement : rang, artiste, score, bouton de vote.
private struct CandidateRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let rank: Int
    let candidate: CompetitionCandidate
    let voteCredits: Int
    let onVote: () -> Void

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(medal(rank)) \(candidate.artist?.displayName ?? s.artistSingular)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Text("\(Int(candidate.score)) pts")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                DMButton("\(s.vote) (\(voteCredits))", action: onVote)
                    .frame(width: 130)
            }
        }
    }
}

/// Médaille pour le podium, numéro sinon (même règle que les classements Android).
/// - Parameter rank: rang 1-based.
func medal(_ rank: Int) -> String {
    switch rank {
    case 1: return "🥇"
    case 2: return "🥈"
    case 3: return "🥉"
    default: return "\(rank)."
    }
}

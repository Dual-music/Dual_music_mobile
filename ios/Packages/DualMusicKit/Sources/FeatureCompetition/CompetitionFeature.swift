import SwiftUI
import Observation
import LiveKit
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels
import FeatureGiftShop
import FeatureSponsor
import FeatureWallet

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

/// Vainqueur annoncé (célébration plein écran synchronisée pour tous les spectateurs).
public struct CompetitionWinner: Sendable, Equatable {
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
public struct CompetitionFloatingReaction: Identifiable, Sendable, Equatable {
    public let id: Int
    public let emoji: String
}

/// Cadeau reçu en direct (carte glissante — seule animation, parité concert/live).
public struct CompetitionGift: Identifiable, Sendable, Equatable {
    public let id: Int
    public let fromUserId: String?
    public let fromUserName: String?
    public let name: String?
    public let image: String?
    public let value: Double
}

/// Candidat actuellement mis en avant par le manager (id + fin du créneau, ISO, pour un chrono).
public struct CompetitionPerformer: Sendable, Equatable {
    public let performerId: String?
    public let endsAt: String?

    public init(performerId: String?, endsAt: String?) {
        self.performerId = performerId
        self.endsAt = endsAt
    }

    public var isActive: Bool { performerId != nil }
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
    /// Vrai si JE suis banni de ce direct → l'UI bloque ma saisie et affiche une barrière.
    public var iAmBanned: Bool {
        guard let callerId else { return false }
        return bannedUserIds.contains(callerId)
    }
    /// Vrai si le caller est un ACTEUR (manager, admin, ou candidat publieur) — exempté du
    /// billet ``ScheduledAccessGate`` (jamais exempté de l'attente de l'heure programmée). Un
    /// candidat approuvé ne doit jamais se voir réclamer de billet pour SA propre compétition.
    public var isActor: Bool { isManager || isAdmin || canPublish }

    // MARK: - Billetterie / gate d'accès programmé

    public private(set) var hasTicket = false
    public private(set) var isAdmin = false
    /// Chat activé/désactivé par le manager — suivi séparément de ``competition`` (qui n'a pas
    /// d'initialiseur memberwise, seulement `init(from:)`) pour ne jamais avoir à le reconstruire.
    public private(set) var chatEnabled = true

    // MARK: - Likes / réactions emoji

    public private(set) var likes = 0
    public private(set) var emojiFeed: [CompetitionFloatingReaction] = []
    private var emojiCounter = 0

    // MARK: - Cadeaux : boutique + classement

    public private(set) var topDonor: CompetitionDonorEntry?
    public private(set) var leaderboard: [CompetitionDonorEntry] = []
    public private(set) var giftCatalog: [VirtualGift] = []
    public private(set) var inventory: [InventoryItem] = []
    public private(set) var giftReceived: String?
    public func clearGiftReceived() { giftReceived = nil }
    /// Fil des cadeaux reçus (carte glissante, seule animation — parité concert/live).
    public private(set) var giftFeed: [CompetitionGift] = []
    private var giftCounter = 0

    // MARK: - Présence + vote

    public private(set) var viewerCount = 0
    /// Prix d'UN vote en crédits (défaut 1 tant que non chargé).
    public private(set) var votePrice = 1

    // MARK: - Contrôles manager (organisateur)

    /// Candidats coupés d'AUTORITÉ par le manager (hard-mute) — ids utilisateur.
    public private(set) var mutedArtists: Set<String> = []
    /// Caméra épinglée par le manager (identité LiveKit) — focus imposé, synchronisé.
    public private(set) var forcedFocusId: String?
    /// Manager : masque les autres cases pour TOUS (diffusion éphémère, distincte du focus).
    public private(set) var forcedHideOthers = false
    /// Vainqueur annoncé (broadcast) → célébration plein écran ; `nil` = arrêtée.
    public private(set) var winnerInfo: CompetitionWinner?
    /// Candidat actuellement mis en avant par le manager (id + fin du créneau, pour un chrono).
    public private(set) var performer = CompetitionPerformer(performerId: nil, endsAt: nil)

    /// Pilotage de l'enregistrement (LiveKit Egress) de cette compétition — bouton manager.
    public let recordingCtl: RecordingHolder
    /// Diffusion de pub sponsor pendant cette compétition — déclenchée par le manager.
    public let sponsorAd: SponsorAdHolder

    private let competitionId: String
    private let callerId: String?
    private let repository: CompetitionRepository
    private let realtime: RealtimeClient
    private let wallet: WalletRepository
    private let giftShop: GiftShopRepository
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?
    private var chatSession: NamespaceSession?

    /// - Parameters:
    ///   - competitionId: identifiant de la compétition.
    ///   - media: client média dédié.
    ///   - repository: lectures + débits.
    ///   - realtime: client Socket.IO (écoute des changements de statut + chat).
    ///   - recording: accès REST du pilotage d'enregistrement (LiveKit Egress).
    ///   - sponsorAds: accès REST de la diffusion de pub sponsor.
    ///   - callerId: id du caller — détermine ``isManager``/``canPublish`` une fois la
    ///     compétition chargée.
    public init(
        competitionId: String,
        media: LiveRoomClient,
        repository: CompetitionRepository,
        realtime: RealtimeClient,
        wallet: WalletRepository,
        giftShop: GiftShopRepository,
        recording: RecordingRepository,
        sponsorAds: SponsorAdRepository,
        callerId: String? = nil
    ) {
        self.competitionId = competitionId
        self.media = media
        self.repository = repository
        self.realtime = realtime
        self.wallet = wallet
        self.giftShop = giftShop
        self.recordingCtl = RecordingHolder(sourceType: "competition", sourceId: competitionId, repo: recording)
        self.sponsorAd = SponsorAdHolder(eventType: "competition", eventId: competitionId, repo: sponsorAds)
        self.callerId = callerId
    }

    /// Démarre : charge le classement + le détail + le chat + écoute le temps réel.
    public func start() async {
        await refresh()
        Task { [weak self] in
            guard let self else { return }
            let comp = try? await self.repository.competition(id: self.competitionId)
            self.competition = comp
            self.forcedFocusId = comp?.forcedFocusParticipantId
            self.chatEnabled = comp?.chatEnabled ?? true
            // Recalculé maintenant que competition ET candidates (chargés par `refresh()`
            // juste avant) sont disponibles.
            let isManagerNow = self.callerId != nil && self.callerId == comp?.managerId
            let approvedCandidate = comp?.mode == "online" && self.callerId != nil
                && self.candidates.contains { $0.artistId == self.callerId && $0.status == "approved" }
            self.canPublish = isManagerNow || approvedCandidate
            self.isAdmin = await self.repository.amIAdmin()
            // Billetterie : prix EFFECTIF nul si la compétition n'est pas payante au public
            // (parité `ScheduledAccessGate` : `ticketPrice = isPublicPaid ? viewerTicketPrice : 0`).
            let isActorNow = isManagerNow || self.isAdmin || self.canPublish
            if comp?.isPublicPaid == true, !isActorNow {
                self.hasTicket = await self.repository.ticketInfo(id: self.competitionId).hasTicket
            }
            let roomName = comp?.liveKitRoom ?? "comp-\(self.competitionId)"
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
        Task { [weak self] in
            guard let self else { return }
            self.likes = await self.repository.likesCount(competitionId: self.competitionId)
        }
        Task { [weak self] in
            guard let self else { return }
            let price = await self.repository.votePricePerVote()
            self.votePrice = max(1, Int(price))
        }
        Task { [weak self] in await self?.loadGiftCatalog() }
        Task { [weak self] in await self?.loadInventory() }
        Task { [weak self] in await self?.refreshGiftLeaderboard() }
        recordingCtl.startPolling()

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
        // Réglage chat basculé par le manager — même room pour tous les spectateurs.
        subscriptions.append(live.onEvent(Realtime.Event.settings, as: EventSettingsPayload.self) { [weak self] payload in
            guard let self, payload.competitionId == nil || payload.competitionId == self.competitionId else { return }
            if let enabled = payload.chatEnabled { self.chatEnabled = enabled }
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
        // Présence (spectateurs).
        subscriptions.append(live.onEvent(Realtime.Event.presence, as: PresencePayload.self) { [weak self] payload in
            self?.viewerCount = payload.count
        })
        // Performeur désigné par le manager → chrono de créneau.
        subscriptions.append(live.onEvent(Realtime.Event.performer, as: PerformerPayload.self) { [weak self] payload in
            guard let self else { return }
            if let pid = payload.performerId {
                let endsAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(TimeInterval(payload.durationSec)))
                self.performer = CompetitionPerformer(performerId: pid, endsAt: endsAt)
            } else {
                self.performer = CompetitionPerformer(performerId: nil, endsAt: nil)
            }
        })
        // Focus caméra imposé par le manager → tous mettent cette identité en avant.
        subscriptions.append(live.onEvent(Realtime.Event.focus, as: FocusPayload.self) { [weak self] payload in
            self?.forcedFocusId = payload.participantId
        })
        // Relais broadcast éphémères (emojis, mute, vainqueur, likes, masquage forcé).
        subscriptions.append(live.onEvent(Realtime.Event.broadcast, as: BroadcastEnvelope.self) { [weak self] envelope in
            guard let self else { return }
            switch envelope.event {
            case "emoji_reaction":
                if let emoji = envelope.payload?.emoji { self.pushEmoji(emoji) }
            case "like":
                if let count = envelope.payload?.count, count > self.likes { self.likes = count }
            case "FORCE_MUTE":
                if let artistId = envelope.payload?.artistId { self.applyMuteState(artistId, muted: true) }
            case "FORCE_UNMUTE":
                if let artistId = envelope.payload?.artistId { self.applyMuteState(artistId, muted: false) }
            case "HIDE_OTHERS":
                self.forcedHideOthers = true
            case "SHOW_OTHERS":
                self.forcedHideOthers = false
            case "winner_announced":
                if let p = envelope.payload {
                    self.winnerInfo = CompetitionWinner(name: p.name ?? "Vainqueur", avatar: p.avatar, votes: p.votes ?? 0, percent: p.percent ?? 0)
                }
            case "winner_stopped":
                self.winnerInfo = nil
            default:
                break
            }
        })
        // Cadeaux : carte glissante (fil) + resynchronise classement + bannière « reçu ».
        subscriptions.append(live.onEvent(Realtime.Event.gift, as: GiftPayload.self) { [weak self] payload in
            guard let self else { return }
            Task { [weak self] in
                guard let self else { return }
                let list = await self.refreshGiftLeaderboard()
                let senderName = list.first { $0.userId == payload.fromUserId }?.displayName
                self.giftCounter += 1
                self.giftFeed.append(
                    CompetitionGift(
                        id: self.giftCounter,
                        fromUserId: payload.fromUserId,
                        fromUserName: senderName,
                        name: payload.giftName,
                        image: payload.giftImage,
                        value: payload.value
                    )
                )
            }
            Task { await self.refresh() }
            if let toUserId = payload.toUserId, toUserId == self.callerId {
                self.giftReceived = "🎁 Vous avez reçu un cadeau (\(Int(payload.value)) crédits) !"
            }
        })
        // Modération : un modérateur a été désigné/révoqué par le manager → recharge pour tous.
        subscriptions.append(live.onEvent(Realtime.Event.moderatorAppointed, as: EventModeratorPayload.self) { [weak self] _ in
            Task { await self?.loadModerators() }
        })
        // Pub sponsor (start/stop) diffusée à toute la room.
        subscriptions.append(live.onEvent(Realtime.Event.sponsorAd, as: SponsorAdPayload.self) { [weak self] payload in
            self?.sponsorAd.onEvent(payload)
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

    // MARK: - Billetterie

    /// Achète le billet spectateur (accès programmé/payant). Débit atomique + idempotent.
    public func buyTicket() async -> Result<Void, Error> {
        do {
            try await repository.buyTicket(competitionId: competitionId)
            hasTicket = true
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    // MARK: - Likes / réactions emoji

    /// « J'aime » : incrément local + compteur partagé (broadcast) + persistance + réaction cœur.
    public func sendLike() {
        likes += 1
        liveSession?.broadcast(channel: "competition-likes-\(competitionId)", event: "like", payload: ["count": likes])
        sendReaction("❤️")
        Task { await repository.likeCompetition(competitionId: competitionId) }
    }

    /// Envoie une réaction emoji : effet local + relais aux autres membres de la room.
    public func sendReaction(_ emoji: String) {
        pushEmoji(emoji)
        liveSession?.broadcast(channel: "competition-emojis-\(competitionId)", event: "emoji_reaction", payload: ["emoji": emoji])
    }

    private func pushEmoji(_ emoji: String) {
        emojiCounter += 1
        emojiFeed = (emojiFeed + [CompetitionFloatingReaction(id: emojiCounter, emoji: emoji)]).suffix(12).map { $0 }
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

    /// Recharge le classement des donateurs + la bulle top-donateur.
    public func loadGiftLeaderboard() async {
        await refreshGiftLeaderboard()
    }

    /// Recharge le classement des donateurs + la bulle top-donateur.
    @discardableResult
    private func refreshGiftLeaderboard() async -> [CompetitionDonorEntry] {
        let list = (try? await repository.giftLeaderboard(competitionId: competitionId)) ?? []
        leaderboard = list
        topDonor = list.first
        return list
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

    /// Offre un cadeau à un candidat (alimente son score). Débit atomique côté serveur ; on
    /// resynchronise le classement et on recharge l'inventaire (quantité restante).
    public func sendGift(candidateId: String, giftId: String, credits: Int) async {
        do {
            try await repository.sendGift(
                competitionId: competitionId,
                request: CompetitionGiftRequest(candidateId: candidateId, giftId: giftId, credits: credits)
            )
            await refresh()
            await loadInventory()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Offre un cadeau directement au manager (organisateur) plutôt qu'à un candidat.
    public func sendGiftToManager(giftId: String, credits: Int) async {
        guard let managerId = competition?.managerId else { return }
        do {
            try await repository.sendGift(
                competitionId: competitionId,
                request: CompetitionGiftRequest(recipientUserId: managerId, giftId: giftId, credits: credits)
            )
            await loadInventory()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Retire le cadeau le plus ancien après son animation.
    public func consumeOldestGift() {
        if !giftFeed.isEmpty { giftFeed.removeFirst() }
    }

    // MARK: - Contrôles MANAGER (organisateur)

    /// Active/désactive le chat pour tous (optimiste + persistant).
    public func toggleChat(_ enabled: Bool) async {
        chatEnabled = enabled
        do {
            try await repository.setChatEnabled(id: competitionId, enabled: enabled)
        } catch {
            chatEnabled = !enabled
        }
    }

    /// Manager : coupe/réactive d'AUTORITÉ le micro d'un candidat (hard-mute), diffusé à tous.
    public func toggleMuteArtist(_ artistId: String) {
        let shouldMute = !mutedArtists.contains(artistId)
        applyMuteState(artistId, muted: shouldMute)
        liveSession?.broadcast(
            channel: "competition-mute-\(competitionId)",
            event: shouldMute ? "FORCE_MUTE" : "FORCE_UNMUTE",
            payload: ["artistId": artistId]
        )
    }

    /// Applique l'état de coupure d'un candidat : ensemble partagé + hard-mute réel de MON
    /// micro si c'est moi. Le mute ne concerne QUE le micro, jamais la caméra.
    private func applyMuteState(_ artistId: String, muted: Bool) {
        if muted { mutedArtists.insert(artistId) } else { mutedArtists.remove(artistId) }
        if artistId == callerId {
            Task { await media.setMicrophone(enabled: !muted) }
        }
    }

    /// Manager : masque les autres cases pour TOUS (diffusion éphémère, distincte du focus).
    public func toggleHideOthers() {
        forcedHideOthers.toggle()
        liveSession?.broadcast(
            channel: "competition-hide-others-\(competitionId)",
            event: forcedHideOthers ? "HIDE_OTHERS" : "SHOW_OTHERS",
            payload: [:]
        )
    }

    /// Manager : annonce le VAINQUEUR (candidat en tête, approuvés uniquement) sans clôturer.
    /// Célébration plein écran chez tous via broadcast.
    public func announceWinnerAuto() {
        let ranked = candidates.filter { $0.status == "approved" }
        let pool = ranked.isEmpty ? candidates : ranked
        guard let top = pool.max(by: { $0.score < $1.score }) else { return }
        let total = pool.reduce(0) { $0 + $1.score }
        let percent = total > 0 ? Int((top.score / total) * 100) : 100
        let w = CompetitionWinner(name: top.artist?.displayName ?? "Vainqueur", avatar: top.artist?.avatarURL, votes: Int(top.score), percent: percent)
        winnerInfo = w
        liveSession?.broadcast(
            channel: "competition-winner-\(competitionId)",
            event: "winner_announced",
            payload: ["name": w.name, "avatar": w.avatar ?? "", "votes": w.votes, "percent": w.percent]
        )
    }

    /// Manager : arrête la célébration du vainqueur pour tous (ne clôture PAS le direct).
    public func stopWinnerAnnouncement() {
        winnerInfo = nil
        liveSession?.broadcast(channel: "competition-winner-\(competitionId)", event: "winner_stopped", payload: [:])
    }

    /// Valide/rejette une candidature puis recharge la liste.
    public func reviewCandidate(candidateId: String, approve: Bool) async {
        do {
            try await repository.reviewCandidate(candidateId: candidateId, approve: approve)
            await refresh()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Fixe les voix de jury d'un candidat puis recharge le classement.
    public func setJuryVotes(candidateId: String, juryVotes: Int) async {
        do {
            try await repository.setJuryVotes(candidateId: candidateId, juryVotes: juryVotes)
            await refresh()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Publie la compétition (ouvre les votes).
    public func publish() async {
        do {
            try await repository.publish(id: competitionId)
            await refresh()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Désigne le candidat actuellement mis en avant (ou `nil` pour arrêter).
    public func setPerformer(candidateId: String?, durationSeconds: Int) async {
        try? await repository.setPerformer(id: competitionId, candidateId: candidateId, durationSeconds: durationSeconds)
    }

    /// Finalise le classement (clôture définitive).
    public func finalize() async {
        do {
            try await repository.finalize(id: competitionId)
            await refresh()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    /// Manager : impose (ou libère avec `nil`) la caméra épinglée pour tous.
    public func setFocus(_ participantId: String?) {
        forcedFocusId = participantId
        Task { try? await repository.setFocus(id: competitionId, participantId: participantId) }
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

    private let onLeave: () -> Void

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: CompetitionChatMessage?
    @State private var showModeratorsSheet = false
    @State private var showFilterSheet = false
    @State private var showSettingsSheet = false
    @State private var showGiftSheet = false
    @State private var showLeaderboardSheet = false
    @State private var showReactionBar = false
    @State private var showCancelRecordingConfirm = false
    @State private var recordingErrorMessage: String?
    @State private var showSponsorAdPicker = false
    @State private var localFocus: String?

    /// - Parameters:
    ///   - viewModel: état + actions.
    ///   - voteCredits: montant (crédits entiers) d'un vote rapide.
    ///   - onLeave: retour au catalogue (gate d'accès, barrière de bannissement).
    public init(viewModel: CompetitionRoomViewModel, voteCredits: Int = 10, onLeave: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.voteCredits = voteCredits
        self.onLeave = onLeave
    }

    /// Tuiles actives : moi (si je publie, sous MON identité) + les distants — triées pour un
    /// ordre stable entre deux rafraîchissements.
    private var allTiles: [(id: String, track: VideoTrack)] {
        var tiles = viewModel.media.remoteTiles.map { (id: $0.key, track: $0.value) }
        if let local = viewModel.media.localVideoTrack {
            tiles.append((id: viewModel.media.localIdentity ?? "local", track: local))
        }
        return tiles.sorted { $0.id < $1.id }
    }

    /// Tuile principale : focus imposé par le manager > focus local (tap) > première tuile.
    private var mainTile: (id: String, track: VideoTrack)? {
        let tiles = allTiles
        if let forced = viewModel.forcedFocusId, let match = tiles.first(where: { $0.id == forced }) { return match }
        if let local = localFocus, let match = tiles.first(where: { $0.id == local }) { return match }
        return tiles.first
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            HStack {
                Text(s.ranking)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                ShareLink(item: s.shareLiveText) {
                    Image(systemName: "square.and.arrow.up")
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                HStack(spacing: 4) {
                    Image(systemName: "eye.fill")
                    Text("\(viewModel.viewerCount)")
                }
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.mutedForeground)
                if viewModel.isManager {
                    Button { showSettingsSheet = true } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "slider.horizontal.3")
                                .foregroundStyle(theme.colors.accent)
                            RecordingRailBadge(active: viewModel.recordingCtl.active, paused: viewModel.recordingCtl.paused)
                                .offset(x: 6, y: -6)
                        }
                    }
                    .buttonStyle(.plain)
                }
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

            // Vidéo : tuile principale (focus imposé > focus local > première) + vignettes des
            // autres publieurs actifs (manager + candidats approuvés en mode "online"). Absente
            // si personne ne diffuse (mode "onsite" sans manager connecté, par ex.).
            if viewModel.canPublish || mainTile != nil {
                videoGrid
                if viewModel.canPublish { hostControls }
            }

            if let performerId = viewModel.performer.performerId, viewModel.performer.endsAt != nil {
                let name = viewModel.candidates.first { $0.artistId == performerId }?.artist?.displayName
                Text("🎤 \(name ?? s.artistSingular)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
            }

            if viewModel.candidates.isEmpty {
                DMEmptyState(title: s.noCandidates, subtitle: s.noCandidatesHint, systemImage: "star")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(Array(viewModel.candidates.enumerated()), id: \.element.id) { index, candidate in
                            CandidateRow(
                                rank: index + 1,
                                candidate: candidate,
                                voteCredits: voteCredits,
                                isManager: viewModel.isManager,
                                isMuted: viewModel.mutedArtists.contains(candidate.artistId),
                                isPerforming: viewModel.performer.performerId == candidate.artistId,
                                onVote: { Task { await viewModel.vote(candidateId: candidate.id, credits: voteCredits) } },
                                onGift: { showGiftSheet = true },
                                onApprove: { Task { await viewModel.reviewCandidate(candidateId: candidate.id, approve: true) } },
                                onReject: { Task { await viewModel.reviewCandidate(candidateId: candidate.id, approve: false) } },
                                onToggleMute: { viewModel.toggleMuteArtist(candidate.artistId) },
                                onTogglePerformer: {
                                    Task { await viewModel.setPerformer(candidateId: viewModel.performer.performerId == candidate.artistId ? nil : candidate.artistId, durationSeconds: 120) }
                                },
                                onJuryVotes: { votes in Task { await viewModel.setJuryVotes(candidateId: candidate.id, juryVotes: votes) } }
                            )
                        }
                    }
                }
            }

            if showReactionBar { reactionBar }
            chatOverlay
            bottomBar
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .overlay {
            if let winner = viewModel.winnerInfo { winnerCelebration(winner) }
        }
        .overlay {
            if viewModel.iAmBanned { bannedGate }
        }
        // Pub sponsor : overlay vidéo plein écran pour tous quand une pub est active — le
        // déclencheur est dans la feuille de réglages (voir `settingsSheet`), pas un bouton ici.
        .overlay {
            SponsorAdLayer(
                activeAd: viewModel.sponsorAd.activeAd,
                canTrigger: viewModel.isManager,
                ads: viewModel.sponsorAd.ads,
                busy: viewModel.sponsorAd.busy,
                onLoadAds: { viewModel.sponsorAd.loadAds() },
                onPlay: { viewModel.sponsorAd.play(adVideoId: $0) },
                onStop: { viewModel.sponsorAd.stop() },
                showTriggerButton: false
            )
        }
        // Gate d'accès programmé (parité web `ScheduledAccessGate`) : rendu en dernier →
        // toujours au-dessus (même de la barrière de bannissement).
        .overlay {
            ScheduledAccessGateView(
                type: "competition",
                scheduledAtIso: viewModel.competition?.startAt,
                status: viewModel.status,
                isActor: viewModel.isActor,
                hasTicket: viewModel.hasTicket,
                ticketPrice: viewModel.competition?.isPublicPaid == true ? (viewModel.competition?.viewerTicketPrice ?? 0) : 0,
                onPurchase: { await viewModel.buyTicket() },
                onDismiss: onLeave
            )
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        .refreshable { await viewModel.refresh() }
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        .sheet(isPresented: $showFilterSheet) { filterSheet }
        .sheet(isPresented: $showSettingsSheet) { settingsSheet }
        .sheet(isPresented: $showGiftSheet) { giftSheet }
        .sheet(isPresented: $showLeaderboardSheet) { leaderboardSheet }
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

    /// Barre du bas : saisie de message + likes/réactions + cadeau + vote + classement.
    private var bottomBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(viewModel.chatEnabled ? s.saySomething : s.chatDisabled, text: $draft)
                .textFieldStyle(.plain)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(theme.colors.card, in: Capsule())
                .submitLabel(.send)
                .disabled(!viewModel.chatEnabled || viewModel.iAmBanned)
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(theme.colors.accent, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(draft.trimmed.isEmpty || !viewModel.chatEnabled || viewModel.iAmBanned)
            Button { viewModel.sendLike() } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "heart.fill")
                        .foregroundStyle(Color(red: 1, green: 0.3, blue: 0.42))
                        .padding(theme.spacing.sm)
                        .background(theme.colors.card, in: Circle())
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
            Button { showReactionBar.toggle() } label: {
                Image(systemName: "face.smiling.fill")
                    .foregroundStyle(theme.colors.foreground)
                    .padding(theme.spacing.sm)
                    .background(theme.colors.card, in: Circle())
            }
            .buttonStyle(.plain)
            if !viewModel.candidates.isEmpty {
                Button { showGiftSheet = true } label: {
                    Image(systemName: "gift.fill")
                        .foregroundStyle(.white)
                        .padding(theme.spacing.sm)
                        .background(theme.gradients.primary, in: Circle())
                }
                .buttonStyle(.plain)
            }
            Button {
                showLeaderboardSheet = true
                Task { await viewModel.loadGiftLeaderboard() }
            } label: {
                Image(systemName: "trophy.fill")
                    .foregroundStyle(Color(red: 1, green: 0.76, blue: 0.03))
                    .padding(theme.spacing.sm)
                    .background(theme.colors.card, in: Circle())
            }
            .buttonStyle(.plain)
        }
    }

    /// Barre d'emojis réactions (togglée par le bouton emoji de ``bottomBar``).
    private var reactionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.sm) {
                ForEach(Self.reactionEmojis, id: \.self) { emoji in
                    Button {
                        viewModel.sendReaction(emoji)
                    } label: {
                        Text(emoji)
                            .padding(theme.spacing.sm)
                            .background(theme.colors.card, in: Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private static let reactionEmojis = ["🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂"]

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
    /// Tuile principale (focus) + vignettes des autres publieurs. Tap sur une vignette = focus
    /// local ; épingle (manager) = focus imposé à tous, synchronisé.
    private var videoGrid: some View {
        let tiles = allTiles
        let main = mainTile
        let thumbs = tiles.filter { $0.id != main?.id }
        return VStack(alignment: .leading, spacing: theme.spacing.xs) {
            if let main {
                SwiftUIVideoView(main.track, layoutMode: .fill)
                    .aspectRatio(16 / 10, contentMode: .fill)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            if !thumbs.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: theme.spacing.xs) {
                        ForEach(thumbs, id: \.id) { tile in
                            ZStack(alignment: .topTrailing) {
                                SwiftUIVideoView(tile.track, layoutMode: .fill)
                                    .frame(width: 84, height: 110)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    .onTapGesture { localFocus = tile.id }
                                if viewModel.isManager {
                                    Button { viewModel.setFocus(tile.id) } label: {
                                        Image(systemName: "pin.fill")
                                            .font(.system(size: 11))
                                            .foregroundStyle(.white)
                                            .padding(4)
                                            .background(.black.opacity(0.5), in: Circle())
                                    }
                                    .buttonStyle(.plain)
                                    .padding(3)
                                }
                            }
                        }
                    }
                }
            }
            if viewModel.isManager, viewModel.forcedFocusId != nil {
                Button {
                    viewModel.setFocus(nil)
                } label: {
                    Label(s.stopAction, systemImage: "pin.slash.fill")
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                }
                .buttonStyle(.plain)
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

    // MARK: - Réglages manager

    /// Feuille manager : caméra/micro/flip/pause, chat on/off, masquage forcé, fin.
    private var settingsSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Text("🎛️ " + s.preferences).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            if viewModel.canPublish {
                settingsRow(viewModel.media.isCameraEnabled ? "video.fill" : "video.slash.fill", viewModel.media.isCameraEnabled ? "Caméra activée" : "Caméra coupée") {
                    Task { await viewModel.toggleCamera() }
                }
                settingsRow("arrow.triangle.2.circlepath.camera.fill", "Retourner la caméra") {
                    Task { await viewModel.switchCamera() }
                }
                settingsRow(viewModel.media.isMicrophoneEnabled ? "mic.fill" : "mic.slash.fill", viewModel.media.isMicrophoneEnabled ? "Micro activé" : "Micro coupé") {
                    Task { await viewModel.toggleMic() }
                }
            }
            HStack {
                Text(viewModel.chatEnabled ? s.chatEnabledOn : s.chatEnabledOff)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { viewModel.chatEnabled },
                    set: { enabled in Task { await viewModel.toggleChat(enabled) } }
                ))
                .labelsHidden()
            }
            if !viewModel.media.remoteTiles.isEmpty || viewModel.media.localVideoTrack != nil {
                settingsRow(viewModel.forcedHideOthers ? "eye.fill" : "eye.slash.fill", viewModel.forcedHideOthers ? "Réafficher les autres cases" : "Masquer les autres cases") {
                    viewModel.toggleHideOthers()
                }
            }
            DMButton(s.endLive, style: .destructive) {
                showSettingsSheet = false
                Task { await viewModel.finalize() }
            }

            Divider()
            Text("🔴 \(s.recording)").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
            RecordingSessionControls(
                mode: viewModel.recordingCtl.mode,
                active: viewModel.recordingCtl.active,
                paused: viewModel.recordingCtl.paused,
                finalizing: viewModel.recordingCtl.finalizing,
                accumulatedSeconds: viewModel.recordingCtl.accumulatedSeconds,
                runStartedAt: viewModel.recordingCtl.runStartedAt,
                busy: viewModel.recordingCtl.busy,
                onStart: { viewModel.recordingCtl.start(onError: { recordingErrorMessage = $0 }) },
                onPause: { viewModel.recordingCtl.pause(onError: { recordingErrorMessage = $0 }) },
                onResume: { viewModel.recordingCtl.resume(onError: { recordingErrorMessage = $0 }) },
                onCancel: { showCancelRecordingConfirm = true },
                onSave: { viewModel.recordingCtl.save(onError: { recordingErrorMessage = $0 }) }
            )
            if let recordingErrorMessage {
                Text(recordingErrorMessage).font(DMFont.caption).foregroundStyle(theme.colors.destructive)
            }

            Divider()
            Text("📢 \(s.sponsorStartAd)").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
            if viewModel.sponsorAd.activeAd != nil {
                DMButton(s.sponsorStopAd, style: .destructive, isEnabled: !viewModel.sponsorAd.busy) { viewModel.sponsorAd.stop() }
            } else {
                DMButton(s.sponsorStartAd, style: .secondary, isEnabled: !viewModel.sponsorAd.busy) {
                    viewModel.sponsorAd.loadAds()
                    showSponsorAdPicker = true
                }
            }
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium, .large])
        .dmScreenBackground()
        .confirmationDialog(s.cancel, isPresented: $showCancelRecordingConfirm, titleVisibility: .visible) {
            Button(s.cancel, role: .destructive) {
                viewModel.recordingCtl.cancel(onError: { recordingErrorMessage = $0 })
                showCancelRecordingConfirm = false
            }
        }
        .confirmationDialog(s.sponsorStartAd, isPresented: $showSponsorAdPicker, titleVisibility: .visible) {
            if viewModel.sponsorAd.ads.isEmpty {
                Button(s.sponsorNoAds) {}.disabled(true)
            } else {
                ForEach(viewModel.sponsorAd.ads) { ad in
                    Button("\(ad.title) · \(ad.durationSeconds)s") { viewModel.sponsorAd.play(adVideoId: ad.id) }
                }
            }
        }
    }

    private func settingsRow(_ systemImage: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage).foregroundStyle(theme.colors.accent)
                Text(label).foregroundStyle(theme.colors.foreground)
                Spacer()
            }
        }
        .buttonStyle(.plain)
        .padding(.vertical, theme.spacing.xs)
    }

    // MARK: - Cadeaux

    /// Feuille : envoyer un cadeau à un candidat (ou au manager) depuis l'inventaire, ou en
    /// acheter un dans la boutique.
    private var giftSheet: some View {
        CompetitionGiftSendSheet(
            candidates: viewModel.candidates,
            managerName: viewModel.isManager ? nil : "Manager",
            inventory: viewModel.inventory,
            catalog: viewModel.giftCatalog,
            onSendToCandidate: { candidateId, giftId, credits in
                Task { await viewModel.sendGift(candidateId: candidateId, giftId: giftId, credits: credits); showGiftSheet = false }
            },
            onSendToManager: { giftId, credits in
                Task { await viewModel.sendGiftToManager(giftId: giftId, credits: credits); showGiftSheet = false }
            },
            onPurchase: { giftId in Task { await viewModel.purchaseGift(giftId: giftId) } }
        )
        .task { await viewModel.loadGiftCatalog(); await viewModel.loadInventory() }
    }

    // MARK: - Classement des donateurs

    private var leaderboardSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text("🏆 \(s.donors)").font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            if viewModel.leaderboard.isEmpty {
                Text(s.emptyRanking).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        ForEach(Array(viewModel.leaderboard.enumerated()), id: \.element.id) { index, entry in
                            HStack {
                                Text("\(medal(index + 1)) \(entry.displayName)").foregroundStyle(theme.colors.foreground)
                                Spacer()
                                Text("\(entry.value) \(s.credits)").bold().foregroundStyle(theme.colors.accent)
                            }
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    // MARK: - Célébration du vainqueur

    private func winnerCelebration(_ winner: CompetitionWinner) -> some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(spacing: theme.spacing.md) {
                Text(s.winnerTitle).font(.system(size: 40))
                Text(winner.name).font(DMFont.pageTitle).bold().foregroundStyle(.white)
                Text("\(winner.votes) · \(winner.percent)%")
                    .font(DMFont.body)
                    .foregroundStyle(.white.opacity(0.85))
                if viewModel.isManager {
                    DMButton(s.stopAction) { viewModel.stopWinnerAnnouncement() }
                        .frame(maxWidth: 220)
                }
            }
        }
    }

    // MARK: - Barrière de bannissement

    private var bannedGate: some View {
        ZStack {
            theme.colors.background.ignoresSafeArea()
            VStack(spacing: theme.spacing.lg) {
                Image(systemName: "nosign").font(.system(size: 56)).foregroundStyle(theme.colors.destructive)
                Text(s.banConfirmMessage)
                    .font(DMFont.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(theme.colors.mutedForeground)
                DMButton(s.scheduledBackHome, action: onLeave)
                    .frame(maxWidth: 220)
            }
            .padding(theme.spacing.xl)
        }
    }
}

/// Feuille d'envoi de cadeau : candidat/manager cible + onglets « Mes cadeaux » / « Boutique ».
private struct CompetitionGiftSendSheet: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let candidates: [CompetitionCandidate]
    let managerName: String?
    let inventory: [InventoryItem]
    let catalog: [VirtualGift]
    let onSendToCandidate: (String, String, Int) -> Void
    let onSendToManager: (String, Int) -> Void
    let onPurchase: (String) -> Void

    @State private var targetCandidateId: String?
    @State private var targetIsManager = false
    @State private var showShop = false

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text("🎁 \(s.sendGift)").font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: theme.spacing.xs) {
                    ForEach(candidates) { candidate in
                        pill("🎤 \(candidate.artist?.displayName ?? s.artistSingular)", selected: !targetIsManager && targetCandidateId == candidate.id) {
                            targetCandidateId = candidate.id
                            targetIsManager = false
                        }
                    }
                    if let managerName {
                        pill("🎬 \(managerName)", selected: targetIsManager) {
                            targetIsManager = true
                            targetCandidateId = nil
                        }
                    }
                }
            }

            HStack(spacing: theme.spacing.sm) {
                pill(s.myGifts, selected: !showShop) { showShop = false }
                pill(s.giftShopLabel, selected: showShop) { showShop = true }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    if !showShop {
                        if inventory.isEmpty {
                            Text(s.noGiftsBuyInShop).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                        }
                        ForEach(inventory) { item in
                            Button {
                                let credits = Int(item.price)
                                if targetIsManager { onSendToManager(item.giftId, credits) }
                                else if let cid = targetCandidateId { onSendToCandidate(cid, item.giftId, credits) }
                            } label: {
                                HStack {
                                    Text("\(item.imageURL ?? "🎁")  \(item.name ?? "")").foregroundStyle(theme.colors.foreground)
                                    Spacer()
                                    Text("×\(item.quantity)").bold().foregroundStyle(theme.colors.accent)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    } else {
                        ForEach(catalog) { gift in
                            Button { onPurchase(gift.id) } label: {
                                HStack {
                                    Text("\(gift.emoji ?? "🎁")  \(gift.name)").foregroundStyle(theme.colors.foreground)
                                    Spacer()
                                    Text("\(Int(gift.price)) \(s.credits)").bold().foregroundStyle(theme.colors.accent)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.large])
        .dmScreenBackground()
        .onAppear { if targetCandidateId == nil && !targetIsManager { targetCandidateId = candidates.first?.id } }
    }

    private func pill(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(DMFont.caption).bold()
                .foregroundStyle(selected ? theme.colors.primaryForeground : theme.colors.foreground)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(selected ? theme.colors.accent : theme.colors.card, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Ligne de classement : rang, artiste, score, bouton de vote.
private struct CandidateRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let rank: Int
    let candidate: CompetitionCandidate
    let voteCredits: Int
    let isManager: Bool
    let isMuted: Bool
    let isPerforming: Bool
    let onVote: () -> Void
    let onGift: () -> Void
    let onApprove: () -> Void
    let onReject: () -> Void
    let onToggleMute: () -> Void
    let onTogglePerformer: () -> Void
    let onJuryVotes: (Int) -> Void

    @State private var juryText = ""

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(medal(rank)) \(candidate.artist?.displayName ?? s.artistSingular)")
                            .font(DMFont.body).bold()
                            .foregroundStyle(theme.colors.foreground)
                        Text("\(Int(candidate.score)) pts · \(candidate.status)")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                    Spacer()
                    if candidate.status == "approved" {
                        Button(action: onGift) {
                            Image(systemName: "gift.fill").foregroundStyle(theme.colors.accent)
                        }
                        .buttonStyle(.plain)
                        DMButton("\(s.vote) (\(voteCredits))", action: onVote)
                            .frame(width: 110)
                    }
                }
                if isManager {
                    if candidate.status == "pending" {
                        HStack(spacing: theme.spacing.sm) {
                            DMButton(s.accept, action: onApprove).frame(maxWidth: .infinity)
                            DMButton(s.rejectAction, style: .destructive, action: onReject).frame(maxWidth: .infinity)
                        }
                    } else if candidate.status == "approved" {
                        HStack(spacing: theme.spacing.sm) {
                            Button(action: onTogglePerformer) {
                                Label(isPerforming ? s.stopAction : "🎤", systemImage: isPerforming ? "mic.slash.fill" : "mic.fill")
                                    .font(DMFont.caption).bold()
                                    .foregroundStyle(isPerforming ? theme.colors.destructive : theme.colors.accent)
                            }
                            .buttonStyle(.plain)
                            Button(action: onToggleMute) {
                                Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                                    .foregroundStyle(isMuted ? theme.colors.destructive : theme.colors.mutedForeground)
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            TextField("Jury", text: $juryText)
                                .textFieldStyle(.roundedBorder)
                                .keyboardType(.numberPad)
                                .frame(width: 60)
                            Button("OK") {
                                if let votes = Int(juryText) { onJuryVotes(votes) }
                            }
                            .font(DMFont.caption).bold()
                            .foregroundStyle(theme.colors.primary)
                        }
                    }
                }
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

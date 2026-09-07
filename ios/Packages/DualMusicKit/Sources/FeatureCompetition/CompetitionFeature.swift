import SwiftUI
import Observation
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels

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
    public private(set) var candidates: [CompetitionCandidate] = []
    public private(set) var status: String?
    public private(set) var errorMessage: String?

    private let competitionId: String
    private let repository: CompetitionRepository
    private let realtime: RealtimeClient
    private var subscriptions: [Subscription] = []
    private var liveSession: NamespaceSession?

    /// - Parameters:
    ///   - competitionId: identifiant de la compétition.
    ///   - repository: lectures + débits.
    ///   - realtime: client Socket.IO (écoute des changements de statut).
    public init(competitionId: String, repository: CompetitionRepository, realtime: RealtimeClient) {
        self.competitionId = competitionId
        self.repository = repository
        self.realtime = realtime
    }

    /// Démarre : charge le classement + écoute le temps réel.
    public func start() async {
        await refresh()
        let live = realtime.session(.live)
        liveSession = live
        subscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            live.join(.competition, id: self.competitionId)
        })
        subscriptions.append(live.onEvent(Realtime.Event.status, as: StatusPayload.self) { [weak self] payload in
            guard let self else { return }
            self.status = payload.status
            // Un changement d'état peut clore les votes → on resynchronise le classement.
            Task { await self.refresh() }
        })
        await live.connect()
    }

    /// Arrête l'écoute temps réel.
    public func stop() {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        liveSession?.disconnect()
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

    @State private var showReport = false

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
                Button { showReport = true } label: {
                    Image(systemName: "flag.fill")
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))
            }

            if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

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
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.start() }
        .onDisappear { viewModel.stop() }
        .refreshable { await viewModel.refresh() }
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
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

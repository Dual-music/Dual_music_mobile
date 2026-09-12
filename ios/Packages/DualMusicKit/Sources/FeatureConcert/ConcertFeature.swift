import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Accès REST aux concerts d'artistes.
///
/// Lectures (catalogue, détail, billetterie) et achat de dédicace. L'achat du **billet**
/// est un débit du portefeuille (`POST /wallet/tickets/concert`) — voir `FeatureWallet`.
public struct ConcertRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Catalogue public des concerts d'artistes (approuvés).
    public func concerts(limit: Int = 50) async throws -> [Concert] {
        try await http.request(
            .get(ConcertEndpoints.artistList, query: ["limit": String(limit)]),
            as: [Concert].self
        )
    }

    /// Détail d'un concert.
    public func concert(id: String) async throws -> Concert {
        try await http.request(.get(ConcertEndpoints.artistDetail(id)), as: Concert.self)
    }

    /// Billetterie : prix, places restantes, et si le caller a déjà son billet.
    public func ticketInfo(id: String) async throws -> ConcertTicketInfo {
        try await http.request(.get(ConcertEndpoints.ticketInfo(id)), as: ConcertTicketInfo.self)
    }

    /// Achète une dédicace pour un concert (débit atomique + idempotent).
    /// - Parameters:
    ///   - concertId: concert ciblé.
    ///   - message: texte lu par l'artiste pendant le concert.
    ///   - priceCredits: montant en crédits (obligatoire côté backend).
    ///   - idempotencyKey: clé unique de l'action.
    public func purchaseDedication(
        concertId: String,
        message: String,
        priceCredits: Double,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                ConcertEndpoints.dedicationsPurchase,
                body: DedicationRequest(concertId: concertId, message: message, priceCredits: priceCredits),
                idempotencyKey: idempotencyKey
            )
        )
    }

    /// Signale ce concert à la modération.
    ///
    /// ⚠️ Couche données seulement : il n'existe pas encore d'écran de room/direct pour les
    /// concerts côté iOS (voir `TODO-IOS.md`) — rien n'appelle cette méthode pour l'instant.
    public func reportLive(liveId: String, reason: ReportReason) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.reportsLive,
                body: ReportStreamBody(liveId: liveId, streamType: "concert", reason: reason.rawValue)
            )
        )
    }

    /// Bannit un spectateur (artiste uniquement) : il ne peut plus écrire ni rejoindre.
    public func createStreamBan(streamId: String, bannedUserId: String, reason: String?) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.streamBans,
                body: StreamBanBody(streamId: streamId, streamType: "concert", bannedUserId: bannedUserId, reason: reason)
            )
        )
    }

    /// Spectateurs déjà bannis de ce concert (ids) — amorce l'affichage pour un arrivant
    /// tardif. Best-effort : une erreur réseau donne juste une liste vide.
    public func listStreamBans(concertId: String) async -> [String] {
        let rows = (try? await http.request(
            .get(ModerationReportEndpoints.streamBans, query: ["streamId": concertId, "streamType": "concert"]),
            as: [StreamBanRow].self
        )) ?? []
        return rows.compactMap(\.bannedUserId)
    }

    /// Historique de chat (dernière page) pour amorcer l'overlay.
    public func chatHistory(concertId: String) async throws -> [ConcertChatMessage] {
        try await http.request(
            .get(ConcertEndpoints.messages(concertId), query: ["limit": "50"]),
            as: [ConcertChatMessage].self
        )
    }

    /// Poste un message (le backend le diffuse ensuite via Socket.IO).
    public func postMessage(concertId: String, content: String) async throws {
        try await http.send(.post(ConcertEndpoints.messages(concertId), body: ConcertMessageBody(message: content)))
    }

    /// Envoie un cadeau à l'artiste dans le contexte du concert.
    public func sendGift(concertId: String, giftId: String, toUserId: String) async throws {
        try await http.send(
            .post(
                WalletEndpoints.giftsSend,
                body: SendGiftRequest(giftId: giftId, toUserId: toUserId, concertId: concertId),
                idempotencyKey: "gift-\(concertId)-\(giftId)-\(toUserId)-\(UUID().uuidString)"
            )
        )
    }

    /// Achète le billet spectateur (débit atomique + idempotent) — requis pour regarder un
    /// concert payant, sauf l'artiste lui-même.
    public func buyTicket(concertId: String, idempotencyKey: String = UUID().uuidString) async throws {
        try await http.send(
            .post(WalletEndpoints.ticketConcert, body: BuyConcertTicketBody(concertId: concertId), idempotencyKey: idempotencyKey)
        )
    }

    /// Artiste : démarre la diffusion côté backend (`status: live`) — appelé APRÈS que la
    /// publication caméra/micro a réellement réussi, jamais avant.
    public func goLive(concertId: String) async throws {
        try await http.send(.patch(ConcertEndpoints.artistDetail(concertId), body: ConcertStatusBody(status: "live")))
    }

    /// Artiste : termine le concert côté backend (`status: ended`).
    public func endConcert(concertId: String) async throws {
        try await http.send(.patch(ConcertEndpoints.artistDetail(concertId), body: ConcertStatusBody(status: "ended")))
    }

    /// Spectateurs actuellement connectés (artiste uniquement — vivier du picker).
    public func listCurrentViewers(concertId: String) async throws -> [DisplayProfile] {
        try await http.request(.get(ModerationEndpoints.viewers("concert", concertId)), as: [DisplayProfile].self)
    }

    /// Modérateurs désignés de ce concert (artiste + jusqu'à ``maxEventModerators``
    /// spectateurs).
    public func listEventModerators(concertId: String) async throws -> [EventModerator] {
        try await http.request(.get(ModerationEndpoints.moderators("concert", concertId)), as: [EventModerator].self)
    }

    /// Artiste : désigne un spectateur modérateur.
    public func appointModerator(concertId: String, userId: String) async throws {
        try await http.send(.post(ModerationEndpoints.moderators("concert", concertId), body: AppointModeratorBody(userId: userId)))
    }

    /// Artiste : révoque un modérateur désigné.
    public func revokeModerator(concertId: String, userId: String) async throws {
        try await http.send(.delete(ModerationEndpoints.revokeModerator("concert", concertId, userId)))
    }
}

/// Corps de `POST /concerts/:id/messages`.
struct ConcertMessageBody: Encodable, Sendable {
    let message: String
}

/// Corps de `POST /wallet/tickets/concert`.
struct BuyConcertTicketBody: Encodable, Sendable {
    let concertId: String
}

/// Corps de `PATCH /artist-concerts/:id` — mise à jour de statut (`live`/`ended`).
struct ConcertStatusBody: Encodable, Sendable {
    let status: String
}

/// Corps de `POST /moderation/reports/live` (live/duel/concert, distingués par `streamType`).
struct ReportStreamBody: Encodable, Sendable {
    let liveId: String
    let streamType: String
    let reason: String
}

/// Corps de `POST /moderation/stream-bans` (live/duel/concert, distingués par `streamType`).
struct StreamBanBody: Encodable, Sendable {
    let streamId: String
    let streamType: String
    let bannedUserId: String
    let reason: String?
}

/// ViewModel du catalogue de concerts.
@Observable
@MainActor
public final class ConcertsViewModel {

    public private(set) var concerts: [Concert] = []
    public private(set) var isLoading = false

    private let repository: ConcertRepository

    /// - Parameter repository: lectures REST des concerts d'artistes.
    public init(repository: ConcertRepository) {
        self.repository = repository
    }

    /// Charge le catalogue.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        concerts = (try? await repository.concerts()) ?? []
        isLoading = false
    }
}

/// Catalogue des concerts d'artistes — miroir de `ConcertsListScreen` Android.
@MainActor
public struct ConcertsListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ConcertsViewModel
    private let onOpen: (Concert) -> Void

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'un concert (détail/billetterie).
    public init(viewModel: ConcertsViewModel, onOpen: @escaping (Concert) -> Void = { _ in }) {
        self.viewModel = viewModel
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text(s.screenConcerts)
                .font(DMFont.pageTitle)
                .foregroundStyle(theme.colors.foreground)
                .frame(maxWidth: .infinity)

            if viewModel.isLoading {
                DMLoadingBox()
            } else if viewModel.concerts.isEmpty {
                DMEmptyState(
                    title: s.noConcertsScheduled,
                    subtitle: s.noConcertsScheduledHint,
                    systemImage: "calendar"
                )
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.concerts) { concert in
                            Button { onOpen(concert) } label: { ConcertRow(concert: concert) }
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

/// Carte d'un concert : titre, date, dédicaces, statut et prix du billet.
private struct ConcertRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let concert: Concert

    var body: some View {
        DMCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(concert.title)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let date = isoMinute(concert.scheduledDate) {
                        Text(date).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                    if concert.allowsDedications {
                        Text(s.dedicationsOpen)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.accent)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(statusLabel)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(concert.status == .live ? theme.colors.accent : theme.colors.mutedForeground)
                    if concert.ticketPrice > 0 {
                        Text("\(formatAmount(concert.ticketPrice)) \(s.credits)")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.foreground)
                    } else {
                        Text(s.freeLabel)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                }
            }
        }
    }

    /// Libellé lisible du statut.
    private var statusLabel: String {
        switch concert.status {
        case .live: return s.statusLiveNow
        case .upcoming: return s.statusUpcoming
        case .ended: return s.statusEnded
        case .cancelled: return s.statusCancelled
        default: return concert.status.rawValue.capitalizedFirst
        }
    }
}

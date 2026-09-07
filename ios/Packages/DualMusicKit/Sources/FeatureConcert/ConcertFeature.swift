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
    ///   - idempotencyKey: clé unique de l'action.
    public func purchaseDedication(
        concertId: String,
        message: String,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                ConcertEndpoints.dedicationsPurchase,
                body: DedicationRequest(concertId: concertId, message: message),
                idempotencyKey: idempotencyKey
            )
        )
    }
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

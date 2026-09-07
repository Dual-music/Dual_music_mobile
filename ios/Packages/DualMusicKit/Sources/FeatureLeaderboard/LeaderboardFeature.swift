import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel des classements (artistes, donateurs, saisons périodiques).
///
/// Les trois lectures sont indépendantes et tolérantes : l'échec de l'une n'empêche pas
/// l'affichage des autres.
@Observable
@MainActor
public final class LeaderboardViewModel {

    public private(set) var artists: [LeaderboardEntry] = []
    public private(set) var donors: [LeaderboardEntry] = []
    public private(set) var seasons: [LeaderboardSeason] = []
    public private(set) var isLoading = false

    private let http: HTTPClient

    /// - Parameter http: client HTTP (les endpoints de classement sont de simples lectures).
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Charge les classements + les saisons.
    public func load() async {
        isLoading = true
        artists = await entries(LeaderboardEndpoints.artists)
        donors = await entries(LeaderboardEndpoints.donors)
        seasons = (try? await http.request(.get(LeaderboardEndpoints.seasons), as: [LeaderboardSeason].self)) ?? []
        isLoading = false
    }

    private func entries(_ path: String) async -> [LeaderboardEntry] {
        (try? await http.request(.get(path), as: [LeaderboardEntry].self)) ?? []
    }
}

/// Écran des classements : Artistes / Donateurs / Périodique.
public struct LeaderboardView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: LeaderboardViewModel
    @State private var tab = 0

    /// - Parameter viewModel: source des classements.
    public init(viewModel: LeaderboardViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            DMTabBar(titles: [s.artists, s.donors, s.periodic], selection: $tab)

            if viewModel.isLoading {
                DMLoadingBox()
            } else if tab == 2 {
                seasonList
            } else {
                entryList(tab == 0 ? viewModel.artists : viewModel.donors)
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    @ViewBuilder
    private func entryList(_ list: [LeaderboardEntry]) -> some View {
        if list.isEmpty {
            DMEmptyState(title: s.emptyRanking, subtitle: s.emptyRankingHint, systemImage: "star")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(Array(list.enumerated()), id: \.element.id) { index, entry in
                        EntryRow(rank: index + 1, entry: entry)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var seasonList: some View {
        if viewModel.seasons.isEmpty {
            DMEmptyState(title: s.emptyRanking, subtitle: s.emptyRankingHint, systemImage: "star")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.seasons) { SeasonRow(season: $0) }
                }
            }
        }
    }
}

/// Ligne de classement : rang (médaille au podium), nom, total.
private struct EntryRow: View {
    @Environment(\.dmTheme) private var theme
    let rank: Int
    let entry: LeaderboardEntry

    var body: some View {
        DMCard {
            HStack {
                Text("\(leaderboardMedal(rank))  \(entry.displayName)")
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                Text(formatAmount(entry.total))
                    .font(DMFont.mono)
                    .foregroundStyle(theme.colors.accent)
            }
        }
    }
}

/// Ligne d'une saison (classement périodique) : nom, période, statut, récompense.
private struct SeasonRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let season: LeaderboardSeason

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(season.name)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    let period = [isoDay(season.startDate), isoDay(season.endDate)]
                        .compactMap { $0 }
                        .joined(separator: " → ")
                    if !period.isEmpty {
                        Text(period).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                    if season.isMysteryReward {
                        Text(s.mysteryReward).font(DMFont.caption).foregroundStyle(theme.colors.accent)
                    }
                }
                Spacer()
                Text(season.isActive ? s.seasonActive : s.seasonEnded)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(season.isActive ? theme.colors.primary : theme.colors.mutedForeground)
            }
        }
    }
}

/// Médaille pour le podium, numéro sinon.
/// - Parameter rank: rang 1-based.
func leaderboardMedal(_ rank: Int) -> String {
    switch rank {
    case 1: return "🥇"
    case 2: return "🥈"
    case 3: return "🥉"
    default: return "\(rank)."
    }
}

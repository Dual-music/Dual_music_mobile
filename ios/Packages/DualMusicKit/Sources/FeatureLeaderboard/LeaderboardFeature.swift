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
@MainActor
public struct LeaderboardView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: LeaderboardViewModel
    @State private var tab = 0
    @State private var seasonSub = 0

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

    private var activeSeasons: [LeaderboardSeason] { viewModel.seasons.filter(\.isActive) }
    private var endedSeasons: [LeaderboardSeason] { viewModel.seasons.filter { !$0.isActive } }

    @ViewBuilder
    private var seasonList: some View {
        if viewModel.seasons.isEmpty {
            DMEmptyState(title: s.emptyRanking, subtitle: s.emptyRankingHint, systemImage: "star")
            Spacer()
        } else {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                HStack(spacing: theme.spacing.xs) {
                    seasonPill("\(s.duelTabLive) (\(activeSeasons.count))", selected: seasonSub == 0) { seasonSub = 0 }
                    seasonPill("\(s.seasonEnded) (\(endedSeasons.count))", selected: seasonSub == 1) { seasonSub = 1 }
                }
                let shown = seasonSub == 0 ? activeSeasons : endedSeasons
                if shown.isEmpty {
                    DMEmptyState(title: s.emptyRanking, systemImage: "star")
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: theme.spacing.sm) {
                            ForEach(shown) { SeasonRow(season: $0) }
                        }
                    }
                }
            }
        }
    }

    private func seasonPill(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(DMFont.caption)
                .foregroundStyle(selected ? .white : theme.colors.mutedForeground)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? theme.colors.primary : Color.black.opacity(0.3), in: Capsule())
        }
        .buttonStyle(.plain)
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

/// Ligne d'une saison (classement périodique) : nom, période, statut, récompenses par rang.
private struct SeasonRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let season: LeaderboardSeason

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: theme.spacing.xs) {
                            Text(season.name)
                                .font(DMFont.body).bold()
                                .foregroundStyle(theme.colors.foreground)
                            Text(season.isActive ? s.seasonActive : s.seasonEnded)
                                .font(.system(size: 11)).bold()
                                .foregroundStyle(season.isActive ? theme.colors.primary : theme.colors.mutedForeground)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(season.isActive ? theme.colors.primary.opacity(0.2) : Color.black.opacity(0.3), in: Capsule())
                        }
                        Text(season.type == "donor" ? s.seasonTypeDonor : s.seasonTypeArtist)
                            .font(.system(size: 11))
                            .foregroundStyle(theme.colors.accent)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(Color.black.opacity(0.3), in: Capsule())
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
                }

                if !season.rewards.isEmpty {
                    Text("⭐ \(s.rewardsByRank)")
                        .font(.system(size: 13)).bold()
                        .foregroundStyle(theme.colors.accent)
                    ForEach(season.rewards.sorted { $0.rankPosition < $1.rankPosition }, id: \.rankPosition) { reward in
                        HStack(spacing: theme.spacing.sm) {
                            Text(leaderboardMedal(reward.rankPosition)).font(.system(size: 16))
                            Text("#\(reward.rankPosition)").font(.system(size: 12)).foregroundStyle(theme.colors.mutedForeground)
                            Text(rewardLabel(reward, s.creditsWord)).font(.system(size: 12)).foregroundStyle(theme.colors.foreground)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(Color.black.opacity(0.25), in: RoundedRectangle(cornerRadius: theme.radius.sm))
                    }
                }

                Text(s.noParticipants)
                    .font(.system(size: 12))
                    .foregroundStyle(theme.colors.mutedForeground)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
            }
        }
    }
}

/// Libellé d'une récompense selon son type.
private func rewardLabel(_ r: SeasonReward, _ creditsWord: String) -> String {
    switch r.rewardType {
    case "credits": return "💰 \(Int(r.creditsAmount ?? 0)) \(creditsWord)"
    case "physical": return "📦 \(r.physicalDescription ?? "")"
    default: return "🎁 \(r.physicalDescription ?? "")".trimmed
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

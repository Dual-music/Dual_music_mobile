import SwiftUI
import Observation
import CoreUI
import DomainModels
import FeatureWallet

/// Taille de page des transactions détaillées (parité web/Android).
private let txPageSize = 10

/// ViewModel de l'onglet « Mes revenus » (espace manager/artiste) : revenus groupés par
/// événement, filtre de période, total, et détail dépliable (répartition + transactions
/// paginées). Mêmes endpoints que le web/Android (`RevenueViewModel.kt`).
@Observable
@MainActor
public final class RevenueViewModel {

    /// `day` | `week` | `month` | `all`.
    public private(set) var period = "all"
    public private(set) var events: [RevenueEvent] = []
    public private(set) var totalCredits: Double = 0
    /// Taux €/crédit dérivé du solde (pour l'aperçu fiat) — `0` tant que non résolu.
    public private(set) var perCreditEur: Double = 0
    public private(set) var expandedId: String?
    public private(set) var breakdown: [RevenueBreakdown] = []
    public private(set) var transactions: [EventTransaction] = []
    private var txOffset = 0
    public private(set) var txHasMore = false
    public private(set) var loading = false
    public private(set) var detailLoading = false

    private let wallet: WalletRepository

    /// - Parameter wallet: lectures du portefeuille (revenus/répartition/transactions/solde).
    public init(wallet: WalletRepository) {
        self.wallet = wallet
    }

    /// Change la période et recharge.
    public func setPeriod(_ period: String) {
        self.period = period
        Task { await load() }
    }

    /// Borne ISO `since` correspondant à la période (`nil` = tout).
    private func since(for period: String) -> String? {
        let now = Date()
        switch period {
        case "day": return ISO8601DateFormatter().string(from: Calendar.current.startOfDay(for: now))
        case "week": return ISO8601DateFormatter().string(from: now.addingTimeInterval(-7 * 24 * 3600))
        case "month": return ISO8601DateFormatter().string(from: now.addingTimeInterval(-30 * 24 * 3600))
        default: return nil
        }
    }

    /// Charge les revenus de la période + le taux €/crédit.
    public func load() async {
        loading = true
        expandedId = nil
        let loadedEvents = (try? await wallet.revenues(since: since(for: period))) ?? []
        let bal = try? await wallet.balance()
        events = loadedEvents
        totalCredits = loadedEvents.reduce(0) { $0 + $1.totalReceived }
        perCreditEur = (bal?.balance ?? 0) > 0 ? (bal!.eurValue / bal!.balance) : 0
        loading = false
    }

    /// Déplie/replie un événement ; au dépliage, charge répartition + 1ʳᵉ page de transactions.
    public func toggleEvent(_ sourceId: String) {
        if expandedId == sourceId {
            expandedId = nil
            breakdown = []
            transactions = []
            txOffset = 0
            txHasMore = false
            return
        }
        expandedId = sourceId
        breakdown = []
        transactions = []
        txOffset = 0
        detailLoading = true
        Task {
            let bd = (try? await wallet.revenueBreakdown(sourceId: sourceId)) ?? []
            let tx = (try? await wallet.transactions(sourceId: sourceId, limit: txPageSize, offset: 0)) ?? []
            guard expandedId == sourceId else { return }
            breakdown = bd
            transactions = tx
            txOffset = tx.count
            txHasMore = tx.count >= txPageSize
            detailLoading = false
        }
    }

    /// Charge la page de transactions suivante (bouton « Voir plus »).
    public func loadMoreTx() {
        guard let sourceId = expandedId else { return }
        let offset = txOffset
        Task {
            let next = (try? await wallet.transactions(sourceId: sourceId, limit: txPageSize, offset: offset)) ?? []
            guard expandedId == sourceId else { return }
            transactions += next
            txOffset += next.count
            txHasMore = next.count >= txPageSize
        }
    }
}

/// Onglet « Mes revenus » : total de la période, liste par événement (dépliable), répartition
/// + transactions détaillées. Miroir de `RevenuesTab` (`ManagerSpaceScreen.kt`).
///
/// Export CSV/PDF (Android) simplifié en partage texte natif (`ShareLink`) — écart assumé,
/// documenté dans `docs/PARITE-IOS-RESTANTE.md` (item 2.6).
public struct RevenueView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: RevenueViewModel

    public init(viewModel: RevenueViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.revByEventTitle).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                    Text(s.revByEventSubtitle).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                periodMenu
            }

            DMCard {
                VStack {
                    Text(s.revTotalPeriod).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    Text(formatCredits(viewModel.totalCredits)).font(DMFont.title).foregroundStyle(theme.colors.foreground)
                    Text(formatEuro(viewModel.totalCredits * viewModel.perCreditEur)).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }
                .frame(maxWidth: .infinity)
            }

            if !viewModel.events.isEmpty {
                ShareLink(item: exportText) {
                    Label(s.revExportCsv, systemImage: "square.and.arrow.up")
                        .font(DMFont.caption).bold()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, theme.spacing.sm)
                }
                .buttonStyle(.bordered)
            }

            if viewModel.loading {
                DMLoadingBox()
            } else if viewModel.events.isEmpty {
                DMEmptyState(title: s.revNoRevenue, subtitle: s.revNoRevenueHint, systemImage: "banknote")
            } else {
                ForEach(viewModel.events) { event in
                    revenueEventRow(event)
                }
            }
        }
        .task { await viewModel.load() }
    }

    private var periodMenu: some View {
        Menu {
            Button(s.revPeriodAll) { viewModel.setPeriod("all") }
            Button(s.revPeriodToday) { viewModel.setPeriod("day") }
            Button(s.revPeriod7d) { viewModel.setPeriod("week") }
            Button(s.revPeriod30d) { viewModel.setPeriod("month") }
        } label: {
            Text("\(periodLabel) ▾").font(DMFont.caption).foregroundStyle(theme.colors.foreground)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Color.black.opacity(0.15), in: RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
        }
    }

    private var periodLabel: String {
        switch viewModel.period {
        case "day": return s.revPeriodToday
        case "week": return s.revPeriod7d
        case "month": return s.revPeriod30d
        default: return s.revPeriodAll
        }
    }

    private func revenueEventRow(_ event: RevenueEvent) -> some View {
        let expanded = viewModel.expandedId == event.sourceId
        return DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Button { viewModel.toggleEvent(event.sourceId) } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(sourceLabel(event.sourceType)).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                            HStack(spacing: theme.spacing.sm) {
                                if let day = isoDay(event.lastAt) {
                                    Text(day).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                                }
                                Text("\(event.txCount) \(s.revVersements)").font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                            }
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(formatCredits(event.totalReceived)).font(DMFont.body).bold().foregroundStyle(theme.colors.accent)
                            Text(formatEuro(event.totalReceived * viewModel.perCreditEur)).font(.system(size: 11)).foregroundStyle(theme.colors.mutedForeground)
                        }
                    }
                }
                .buttonStyle(.plain)

                if expanded {
                    if viewModel.detailLoading {
                        DMLoadingBox()
                    } else {
                        ForEach(viewModel.breakdown) { b in
                            HStack {
                                Text(sourceLabel(b.sourceType)).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                                Spacer()
                                Text(formatCredits(b.total)).font(DMFont.caption).foregroundStyle(theme.colors.foreground)
                            }
                        }
                        ForEach(viewModel.transactions) { tx in
                            VStack(alignment: .leading, spacing: 2) {
                                if let minute = isoMinute(tx.createdAt) {
                                    Text(minute).font(.system(size: 11)).foregroundStyle(theme.colors.mutedForeground)
                                }
                                HStack {
                                    Text("\(s.revTotalPaid): \(formatCredits(tx.totalCredits))").font(.system(size: 11)).foregroundStyle(theme.colors.mutedForeground)
                                    Spacer()
                                    Text("\(s.revReceivedYou): \(formatCredits(tx.myCredits))").font(.system(size: 11)).foregroundStyle(theme.colors.accent)
                                }
                            }
                            .padding(.top, theme.spacing.xs)
                        }
                        if viewModel.txHasMore {
                            DMButton(s.revShowMore, style: .secondary) { viewModel.loadMoreTx() }
                        }
                    }
                }
            }
        }
    }

    /// Contenu texte de l'export (parité minimale avec l'export CSV Android, via partage natif).
    private var exportText: String {
        var lines = ["\(s.revByEventTitle) — \(periodLabel)"]
        for e in viewModel.events {
            lines.append("\(sourceLabel(e.sourceType))\t\(e.txCount)\t\(formatCredits(e.totalReceived, withUnit: false))")
        }
        lines.append("\(s.revTotalPeriod)\t\(formatCredits(viewModel.totalCredits, withUnit: false))")
        return lines.joined(separator: "\n")
    }

    private func sourceLabel(_ sourceType: String) -> String {
        switch sourceType {
        case "duel_ticket": return s.srcDuelTicket
        case "duel_replay": return s.srcDuelReplay
        case "concert_ticket": return s.srcConcertTicket
        case "concert_replay": return s.srcConcertReplay
        case "gift_concert": return s.srcGiftConcert
        case "gift_duel": return s.srcGiftDuel
        case "gift_live": return s.srcGiftLive
        case "vote": return s.srcVote
        default: return sourceType
        }
    }
}

/// « Espace Manager/Artiste » — 2 onglets (Mes revenus / Retrait), miroir simplifié de
/// `ManagerSpaceScreen` Android (3 onglets : Revenus/Retrait/Historique — l'historique reste
/// intégré à l'onglet Retrait côté iOS plutôt qu'un 3ᵉ onglet séparé, ``WithdrawalView``
/// l'affichant déjà nativement en bas de formulaire).
public struct ManagerSpaceView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let revenueViewModel: RevenueViewModel
    private let withdrawalViewModel: WithdrawalViewModel

    @State private var tab = 0

    public init(revenueViewModel: RevenueViewModel, withdrawalViewModel: WithdrawalViewModel) {
        self.revenueViewModel = revenueViewModel
        self.withdrawalViewModel = withdrawalViewModel
    }

    /// `WithdrawalView` a déjà son propre `ScrollView` interne (formulaire + historique) —
    /// n'y ajouter un second niveau de scroll créerait une ambiguïté de hauteur. Seul l'onglet
    /// Revenus (simple `VStack`, sans scroll propre) est donc enveloppé ici.
    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            HStack(spacing: theme.spacing.xs) {
                tabPill(s.revTabRevenues, tab == 0) { tab = 0 }
                tabPill(s.revTabWithdraw, tab == 1) { tab = 1 }
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, theme.spacing.md)

            if tab == 0 {
                ScrollView { RevenueView(viewModel: revenueViewModel).padding(theme.spacing.lg) }
            } else {
                WithdrawalView(viewModel: withdrawalViewModel)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
    }

    private func tabPill(_ text: String, _ selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 13)).bold()
                .foregroundStyle(selected ? Color.white : theme.colors.mutedForeground)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? theme.colors.primary : Color.black.opacity(0.3), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

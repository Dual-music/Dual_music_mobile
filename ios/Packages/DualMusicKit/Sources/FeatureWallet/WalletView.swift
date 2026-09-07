import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel du portefeuille.
///
/// Charge le solde et les trois historiques (achats / dépenses / revenus) en une passe.
/// Aucun calcul d'argent ici : tous les montants proviennent du backend.
/// Miroir de `WalletViewModel` Android.
@Observable
@MainActor
public final class WalletViewModel {

    public private(set) var balance = WalletBalance()
    public private(set) var purchases: [CreditPurchase] = []
    public private(set) var spending: [SpendItem] = []
    public private(set) var revenues: [RevenueEvent] = []
    public private(set) var isLoading = false
    public private(set) var errorMessage: String?

    private let repository: WalletRepository

    /// - Parameter repository: accès aux endpoints `/wallet/…`.
    public init(repository: WalletRepository) {
        self.repository = repository
    }

    /// Charge (ou recharge) solde + historiques. Idempotent : ignoré si déjà en cours.
    ///
    /// Les trois historiques sont indépendants : leur échec ne doit pas masquer le solde,
    /// d'où le repli sur liste vide (même stratégie qu'Android).
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        do {
            balance = try await repository.balance()
            purchases = (try? await repository.purchases()) ?? []
            spending = (try? await repository.spending()) ?? []
            revenues = (try? await repository.revenues()) ?? []
        } catch {
            errorMessage = Self.friendlyMessage(error)
        }
        isLoading = false
    }

    /// Traduit une erreur technique en message affichable.
    static func friendlyMessage(_ error: Error) -> String {
        let s = AppStrings.current
        guard let api = error as? APIError else { return s.errWalletLoadFailed }
        if api.isAuthExpired { return s.errSessionExpired }
        if api.isRetriable { return s.errUnstableConnection }
        return api.message
    }
}

/// Écran portefeuille : solde + historiques (achats de crédits / dépenses / revenus).
///
/// Tous les montants proviennent du backend (procédures atomiques) — aucun calcul d'argent
/// n'est refait côté mobile. Miroir de `WalletScreen` Android.
@MainActor
public struct WalletView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: WalletViewModel
    private let onOpenRecharge: () -> Void
    private let canEarn: Bool

    @State private var tab = 0

    /// - Parameters:
    ///   - viewModel: source d'état (solde + historiques).
    ///   - canEarn: affiche l'onglet « Revenus » (artiste/manager/admin uniquement).
    ///   - onOpenRecharge: ouvre l'écran de recharge de crédits.
    public init(viewModel: WalletViewModel, canEarn: Bool = false, onOpenRecharge: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.canEarn = canEarn
        self.onOpenRecharge = onOpenRecharge
    }

    public var body: some View {
        VStack(spacing: theme.spacing.lg) {
            // --- Solde ---
            DMCard {
                VStack(spacing: theme.spacing.sm) {
                    CreditPill(credits: viewModel.balance.balance)
                    Text(formatEuro(viewModel.balance.eurValue))
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                .frame(maxWidth: .infinity)
            }

            DMButton(s.rechargeCredits, action: onOpenRecharge)

            if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

            DMTabBar(titles: tabTitles, selection: $tab)

            if viewModel.isLoading {
                DMLoadingBox()
            } else {
                historyList
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    /// Onglets affichés : « Revenus » n'apparaît que pour ceux qui peuvent en générer.
    private var tabTitles: [String] {
        canEarn ? [s.creditPurchases, s.walletExpenses, s.walletIncome] : [s.creditPurchases, s.walletExpenses]
    }

    @ViewBuilder
    private var historyList: some View {
        ScrollView {
            LazyVStack(spacing: theme.spacing.sm) {
                switch tab {
                case 0:
                    if viewModel.purchases.isEmpty {
                        Text(s.noPurchases)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    ForEach(viewModel.purchases) { PurchaseRow(item: $0) }
                case 1:
                    ForEach(viewModel.spending) { SpendRow(item: $0) }
                default:
                    ForEach(viewModel.revenues) { RevenueRow(item: $0) }
                }
            }
        }
    }
}

// MARK: - Lignes d'historique

/// Ligne d'historique d'un achat de crédits (recharge Mobile Money / carte).
private struct PurchaseRow: View {
    @Environment(\.dmTheme) private var theme
    let item: CreditPurchase

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.paymentMethod ?? "Mobile Money")
                        .foregroundStyle(theme.colors.foreground)
                    Text([isoDay(item.createdAt), item.status].compactMap { $0 }.joined(separator: " · "))
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                Text("+\(formatAmount(item.creditsAmount))")
                    .font(DMFont.mono)
                    .foregroundStyle(theme.colors.primary)
            }
        }
    }
}

/// Ligne d'historique de dépense (crédits sortants).
private struct SpendRow: View {
    @Environment(\.dmTheme) private var theme
    let item: SpendItem

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(sourceLabel(item.sourceType))
                        .foregroundStyle(theme.colors.foreground)
                    if let day = isoDay(item.createdAt) {
                        Text(day).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                Text("-\(formatAmount(item.totalCredits))")
                    .font(DMFont.mono)
                    .foregroundStyle(theme.colors.destructive)
            }
        }
    }
}

/// Ligne d'historique de revenu (crédits entrants, agrégés par événement).
private struct RevenueRow: View {
    @Environment(\.dmTheme) private var theme
    let item: RevenueEvent

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(sourceLabel(item.sourceType))
                        .foregroundStyle(theme.colors.foreground)
                    Text("\(item.txCount) transaction(s)")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                Text("+\(formatAmount(item.totalReceived))")
                    .font(DMFont.mono)
                    .foregroundStyle(theme.colors.primary)
            }
        }
    }
}

/// Traduit le `source_type` backend en libellé lisible (même table qu'Android).
/// - Parameter sourceType: type de source renvoyé par l'API.
private func sourceLabel(_ sourceType: String) -> String {
    switch sourceType {
    case "vote": return "Vote"
    case "gift_duel", "gift_live", "gift_concert", "gift_competition": return "Cadeau"
    case "duel_ticket": return "Ticket duel"
    case "concert_ticket": return "Ticket concert"
    case "duel_replay", "concert_replay": return "Replay"
    default: return sourceType.capitalizedFirst
    }
}

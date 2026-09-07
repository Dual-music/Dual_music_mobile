import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels
#if canImport(UIKit)
import UIKit // presse-papiers
#endif

/// ViewModel du parrainage : code + filleuls + réclamation des récompenses.
@Observable
@MainActor
public final class ReferralViewModel {

    public private(set) var data = MyReferrals()
    public private(set) var message: String?

    private let http: HTTPClient

    /// - Parameter http: client HTTP.
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Charge le parrainage du caller.
    public func load() async {
        if let value = try? await http.request(.get(ReferralEndpoints.me), as: MyReferrals.self) {
            data = value
        }
    }

    /// Réclame la récompense d'un parrainage complété, puis recharge.
    /// - Parameter id: identifiant du parrainage.
    public func claim(id: String) async {
        try? await http.send(.post(ReferralEndpoints.claim(id), idempotencyKey: UUID().uuidString))
        await load()
    }

    /// Copie le code de parrainage dans le presse-papiers.
    public func copyCode() {
        guard let code = data.referralCode else { return }
        #if canImport(UIKit)
        UIPasteboard.general.string = code
        #endif
        message = AppStrings.current.codeCopied
    }
}

/// Écran de parrainage : code partageable, récompenses en attente, liste des filleuls.
public struct ReferralView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ReferralViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: ReferralViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            // Code + copie + partage natif.
            DMCard {
                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    Text(s.yourReferralCode)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                    Text(viewModel.data.referralCode ?? "—")
                        .font(.system(.title2, design: .monospaced).weight(.bold))
                        .foregroundStyle(theme.colors.primaryGlow)

                    if let code = viewModel.data.referralCode {
                        HStack(spacing: theme.spacing.sm) {
                            DMButton(s.copyCode, style: .outline) { viewModel.copyCode() }
                            ShareLink(item: "\(s.shareReferralTitle) : \(code)") {
                                Text(s.menuReferral)
                                    .font(DMFont.button)
                                    .foregroundStyle(theme.colors.secondaryForeground)
                                    .frame(maxWidth: .infinity, minHeight: 52)
                                    .background(theme.colors.secondary)
                                    .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                            }
                        }
                    }
                    if let message = viewModel.message { DMMessage(message) }
                }
            }

            // Récompenses en attente.
            if viewModel.data.stats.pendingRewardCredits > 0 {
                DMCard {
                    Text("🎁 \(formatAmount(viewModel.data.stats.pendingRewardCredits)) \(s.pendingRewards)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.accent)
                }
            }

            Text("\(s.yourReferrals) (\(viewModel.data.referrals.count))")
                .font(DMFont.body)
                .foregroundStyle(theme.colors.foreground)

            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.data.referrals) { item in
                        ReferralRow(item: item, rewardCredits: viewModel.data.stats.rewardCredits) {
                            Task { await viewModel.claim(id: item.id) }
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

/// Ligne d'un filleul : nom + bouton « Réclamer » si la récompense est disponible.
private struct ReferralRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let item: ReferralItem
    let rewardCredits: Double
    let onClaim: () -> Void

    var body: some View {
        DMCard {
            HStack {
                Text(item.referred?.displayName ?? s.referee)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                if item.rewardClaimed {
                    Text(s.claimed)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                } else {
                    DMButton("\(s.claim) \(Int(rewardCredits))", action: onClaim)
                        .frame(width: 150)
                }
            }
        }
    }
}

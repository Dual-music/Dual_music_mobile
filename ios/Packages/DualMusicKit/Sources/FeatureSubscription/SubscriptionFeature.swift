import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel des abonnements : offres + statut courant + souscription par carte (Stripe).
///
/// L'achat ouvre une page de paiement hébergée ; le compte est mis à jour côté serveur
/// après le paiement (webhook Stripe). Miroir de `SubscriptionViewModel` Android.
///
/// > Note conformité App Store : si l'abonnement débloque du contenu **consommé dans
/// > l'app**, Apple impose l'achat in-app. Le paiement web reste acceptable pour un service
/// > également vendu hors app — voir `docs/RELEASE-IOS.md` avant soumission.
@Observable
@MainActor
public final class SubscriptionViewModel {

    public private(set) var plans: [SubscriptionPlan] = []
    public private(set) var current = MySubscription()
    public private(set) var isLoading = false
    public private(set) var message: String?
    /// URL de paiement à ouvrir (consommée par la vue puis remise à `nil`).
    public private(set) var checkoutURL: URL?

    private let http: HTTPClient

    /// - Parameter http: client HTTP.
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Charge offres + abonnement courant.
    public func load() async {
        plans = (try? await http.request(.get(SubscriptionEndpoints.plans), as: [SubscriptionPlan].self)) ?? []
        current = (try? await http.request(.get(SubscriptionEndpoints.me), as: MySubscription.self)) ?? MySubscription()
    }

    /// Achète un abonnement (`pro`/`premium`) : ouvre l'URL de paiement hébergée.
    /// - Parameter plan: identifiant de l'offre.
    public func subscribe(plan: String) async {
        isLoading = true
        message = nil
        defer { isLoading = false }
        do {
            let response: StripeCheckoutResponse = try await http.request(
                .post(PaymentEndpoints.stripeSubscription, body: StripeSubscriptionRequest(plan: plan)),
                as: StripeCheckoutResponse.self
            )
            checkoutURL = URL(string: response.url)
            message = AppStrings.current.openingPayment
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.errSubscriptionUnavailable
        }
    }

    /// À appeler après avoir ouvert l'URL de paiement.
    public func consumeCheckoutURL() { checkoutURL = nil }
}

/// Écran des abonnements Pro/Premium.
public struct SubscriptionView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    @Environment(\.openURL) private var openURL

    private let viewModel: SubscriptionViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: SubscriptionViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                if viewModel.current.isActive {
                    DMCard {
                        Text("\(s.subscriptionActive) \(viewModel.current.subscriptionType ?? "")")
                            .font(DMFont.body).bold()
                            .foregroundStyle(theme.colors.primary)
                    }
                }

                if let message = viewModel.message { DMMessage(message) }

                ForEach(viewModel.plans) { plan in
                    PlanCard(plan: plan, isEnabled: !viewModel.isLoading) {
                        let tier = (plan.tier ?? plan.name ?? "pro").lowercased()
                        Task { await viewModel.subscribe(plan: tier) }
                    }
                }

                Text(s.stripeSubscriptionHint)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .onChange(of: viewModel.checkoutURL) { _, url in
            guard let url else { return }
            openURL(url)
            viewModel.consumeCheckoutURL()
        }
    }
}

/// Carte d'une offre : nom, prix, description + bouton d'abonnement.
private struct PlanCard: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let plan: SubscriptionPlan
    let isEnabled: Bool
    let onSubscribe: () -> Void

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                HStack {
                    Text(plan.name ?? plan.tier ?? s.offer)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Spacer()
                    Text(formatCredits(plan.price))
                        .font(DMFont.mono)
                        .foregroundStyle(theme.colors.accent)
                }
                if let description = plan.description {
                    Text(description)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                DMButton(s.subscribeByCard, isEnabled: isEnabled, action: onSubscribe)
            }
        }
    }
}

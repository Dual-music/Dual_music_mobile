import SwiftUI
import Observation
import StoreKit
import CoreNetwork
import CoreUI
import DomainModels

/// Erreur locale : une transaction StoreKit dont la signature n'a pas pu être vérifiée
/// (JWS invalide) — ne devrait jamais survenir hors jailbreak/appareil compromis.
private struct StoreKitVerificationError: Error {}

/// ViewModel de la recharge de crédits par **StoreKit** (achats intégrés Apple).
///
/// ⚠️ iOS uniquement : Apple impose que toute monnaie virtuelle consommée dans l'app passe
/// par StoreKit (règle 3.1.1) — CinetPay/Stripe restent la voie sur le web et Android
/// (`RechargeScreen.kt`, code Kotlin séparé, inchangé), mais ne peuvent pas cohabiter avec
/// StoreKit dans CET écran iOS. Contrairement à CinetPay (le serveur initie le paiement,
/// webhook crédite ensuite), le flux est inversé ici : le client achète directement via
/// `Product.purchase()`, puis envoie l'id de transaction au serveur pour règlement — celui-ci
/// revérifie TOUJOURS auprès d'Apple (App Store Server API) et lit le nombre de crédits dans
/// SON PROPRE catalogue (jamais le client) avant de créditer, voir
/// `POST /payments/apple/verify` (backend, `payments.service.js#verifyAppleCredits`).
@Observable
@MainActor
public final class RechargeViewModel {

    /// Paliers de crédits disponibles (achats consommables), triés par prix croissant.
    public private(set) var products: [Product] = []
    public private(set) var isLoading = false
    /// Id du produit en cours d'achat — désactive son bouton pendant la transaction.
    public private(set) var purchasingProductId: String?
    public private(set) var message: String?
    public private(set) var messageIsError = false

    private let http: HTTPClient
    private var updatesTask: Task<Void, Never>?

    /// Identifiants App Store Connect des paliers — DOIVENT exister tels quels côté Apple
    /// (Fonctionnalités de l'app → Achats intégrés → Consommable) et rester en phase avec
    /// le catalogue serveur (`appleIAPProducts.js`). Paliers de test, prix/paliers définitifs
    /// à trancher par l'équipe avant publication — modifier cette liste suffit.
    private static let productIDs = [
        "com.dualmusic.app.credits.tier1",
        "com.dualmusic.app.credits.tier2",
        "com.dualmusic.app.credits.tier3",
        "com.dualmusic.app.credits.tier4",
        "com.dualmusic.app.credits.tier5",
    ]

    /// - Parameter http: client HTTP applicatif.
    public init(http: HTTPClient) {
        self.http = http
        // Écoute permanente des transactions (StoreKit peut en redélivrer une hors du flux
        // d'achat direct — ex. app tuée juste après paiement) : démarrée une fois pour toute
        // la session, jamais arrêtée — c'est le fonctionnement voulu par Apple pour ce
        // listener (pas lié au cycle de vie d'un écran).
        updatesTask = Task { [weak self] in await self?.observeTransactionUpdates() }
    }

    /// Charge le catalogue de paliers depuis l'App Store.
    public func load() async {
        guard products.isEmpty else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let fetched = try await Product.products(for: Self.productIDs)
            products = fetched.sorted { $0.price < $1.price }
        } catch {
            message = AppStrings.current.errRechargeFailed
            messageIsError = true
        }
    }

    /// Achète un palier, puis règle la transaction côté serveur.
    public func purchase(_ product: Product) async {
        purchasingProductId = product.id
        message = nil
        defer { purchasingProductId = nil }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                let transaction = try Self.checkVerified(verification)
                await settle(transaction)
            case .userCancelled:
                break
            case .pending:
                message = AppStrings.current.purchasePending
                messageIsError = false
            @unknown default:
                break
            }
        } catch {
            message = AppStrings.current.errRechargeFailed
            messageIsError = true
        }
    }

    /// Règle une transaction StoreKit vérifiée : le serveur revérifie auprès d'Apple et
    /// crédite. `finish()` seulement APRÈS un règlement réussi — sinon StoreKit la
    /// reproposera à la prochaine occasion (voulu : un achat déjà payé ne doit jamais se
    /// perdre à cause d'un appel réseau raté).
    private func settle(_ transaction: Transaction) async {
        do {
            let response = try await http.request(
                .post(
                    PaymentEndpoints.appleVerify,
                    body: VerifyAppleTransactionRequest(transactionId: String(transaction.id)),
                    idempotencyKey: String(transaction.id)
                ),
                as: VerifyAppleTransactionResponse.self
            )
            await transaction.finish()
            message = "🎉 \(Int(response.credits)) \(AppStrings.current.creditsAdded)"
            messageIsError = false
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.errRechargeFailed
            messageIsError = true
        }
    }

    /// Écoute les transactions terminées hors du flux d'achat direct.
    private func observeTransactionUpdates() async {
        for await update in Transaction.updates {
            guard let transaction = try? Self.checkVerified(update) else { continue }
            await settle(transaction)
        }
    }

    private static func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified: throw StoreKitVerificationError()
        case .verified(let safe): return safe
        }
    }
}

/// Corps de `POST /payments/apple/verify`.
private struct VerifyAppleTransactionRequest: Encodable, Sendable {
    let transactionId: String
}

/// Réponse de `POST /payments/apple/verify`.
private struct VerifyAppleTransactionResponse: Decodable, Sendable {
    let credits: Double
    let already: Bool
}

/// Écran de recharge : liste des paliers StoreKit, achat en un tap. Le compte est crédité
/// automatiquement après règlement server-to-server (pas de webhook à attendre ici,
/// contrairement à CinetPay — la réponse de `/payments/apple/verify` est immédiate).
@MainActor
public struct RechargeView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: RechargeViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: RechargeViewModel) {
        self._viewModel = Bindable(viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.rechargeCredits)
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)
                Text(s.chooseCreditsPack)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)

                if let message = viewModel.message {
                    DMMessage(message, kind: viewModel.messageIsError ? .error : .info)
                }

                if viewModel.isLoading {
                    DMLoadingBox()
                } else if viewModel.products.isEmpty {
                    DMEmptyState(title: s.noCreditPacksAvailable, systemImage: "creditcard")
                } else {
                    VStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.products) { product in
                            productRow(product)
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }

    /// Une ligne de palier : nom/description du produit + bouton d'achat au prix localisé
    /// (`displayPrice` — formaté par StoreKit dans la devise du compte App Store du caller).
    private func productRow(_ product: Product) -> some View {
        DMCard {
            HStack(spacing: theme.spacing.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(product.displayName.isEmpty ? product.id : product.displayName)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if !product.description.isEmpty {
                        Text(product.description)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                DMButton(
                    viewModel.purchasingProductId == product.id ? s.purchasing : "\(s.buyAction) \(product.displayPrice)",
                    isLoading: viewModel.purchasingProductId == product.id,
                    isEnabled: viewModel.purchasingProductId == nil
                ) {
                    Task { await viewModel.purchase(product) }
                }
                .frame(maxWidth: 170)
            }
        }
    }
}

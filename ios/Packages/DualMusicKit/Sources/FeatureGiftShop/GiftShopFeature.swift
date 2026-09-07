import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Accès REST à la boutique de cadeaux.
///
/// Catalogue + inventaire + achat (débit atomique du solde). L'achat est **idempotent**.
public struct GiftShopRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Catalogue des cadeaux virtuels achetables.
    public func catalog() async throws -> [VirtualGift] {
        try await http.request(.get(GiftEndpoints.catalog), as: [VirtualGift].self)
    }

    /// Inventaire possédé par le caller.
    public func inventory() async throws -> [InventoryItem] {
        try await http.request(.get(GiftEndpoints.inventory), as: [InventoryItem].self)
    }

    /// Solde du portefeuille en crédits ; `0` en cas d'échec (l'écran reste utilisable).
    public func balance() async -> Double {
        let wallet = try? await http.request(.get(WalletEndpoints.balance), as: WalletBalance.self)
        return wallet?.balance ?? 0
    }

    /// Achète des cadeaux dans l'inventaire (débit du solde).
    /// - Returns: `true` si l'achat a réussi.
    public func purchase(giftId: String, quantity: Int = 1, idempotencyKey: String = UUID().uuidString) async -> Bool {
        do {
            try await http.send(
                .post(
                    GiftEndpoints.purchase,
                    body: PurchaseGiftRequest(giftId: giftId, quantity: quantity),
                    idempotencyKey: idempotencyKey
                )
            )
            return true
        } catch {
            return false
        }
    }
}

/// ViewModel de la boutique de cadeaux.
@Observable
@MainActor
public final class GiftShopViewModel {

    public private(set) var catalog: [VirtualGift] = []
    public private(set) var inventory: [InventoryItem] = []
    public private(set) var balance: Double = 0
    public private(set) var isLoading = false
    public private(set) var message: String?

    private let repository: GiftShopRepository

    /// - Parameter repository: catalogue + inventaire + achat.
    public init(repository: GiftShopRepository) {
        self.repository = repository
    }

    /// Quantité possédée par identifiant de cadeau (badge « ×N »).
    public var ownedByGift: [String: Int] {
        Dictionary(inventory.map { ($0.giftId, $0.quantity) }, uniquingKeysWith: { _, last in last })
    }

    /// Charge catalogue + inventaire + solde.
    public func load() async {
        isLoading = true
        catalog = (try? await repository.catalog()) ?? []
        inventory = (try? await repository.inventory()) ?? []
        balance = await repository.balance()
        isLoading = false
    }

    /// Achète un cadeau, puis rafraîchit l'inventaire et le solde.
    /// - Parameter gift: cadeau du catalogue.
    public func buy(_ gift: VirtualGift) async {
        if await repository.purchase(giftId: gift.id, quantity: 1) {
            inventory = (try? await repository.inventory()) ?? inventory
            balance = await repository.balance()
            message = "✅ \(gift.name) \(AppStrings.current.purchased)"
        } else {
            message = AppStrings.current.errPurchaseFailed
        }
    }
}

/// Boutique de cadeaux : solde en tête + grille du catalogue (visuel, nom, prix, achat)
/// avec un badge « ×N » sur les cadeaux déjà possédés.
public struct GiftShopView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: GiftShopViewModel
    private let onOpenRecharge: () -> Void

    private let columns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]

    /// - Parameters:
    ///   - viewModel: état + actions.
    ///   - onOpenRecharge: ouvre l'écran de recharge de crédits.
    public init(viewModel: GiftShopViewModel, onOpenRecharge: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.onOpenRecharge = onOpenRecharge
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.sm) {
                balanceCard

                Text(s.giftShopHint)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if let message = viewModel.message { DMMessage(message) }
                if viewModel.isLoading { DMLoadingBox().frame(height: 60) }

                if !viewModel.isLoading && viewModel.catalog.isEmpty {
                    DMEmptyState(title: s.emptyShop, subtitle: s.emptyShopHint, systemImage: "gift")
                }

                LazyVGrid(columns: columns, spacing: theme.spacing.sm) {
                    ForEach(viewModel.catalog) { gift in
                        GiftCard(gift: gift, owned: viewModel.ownedByGift[gift.id] ?? 0) {
                            Task { await viewModel.buy(gift) }
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    /// Solde en haut + accès à la recharge (comme sur le web).
    private var balanceCard: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.myBalance)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                    Text(formatCredits(viewModel.balance))
                        .font(.system(.title2, design: .rounded).weight(.bold))
                        .foregroundStyle(theme.colors.foreground)
                }
                Spacer()
                DMButton(s.recharge, action: onOpenRecharge).frame(width: 140)
            }
        }
    }
}

/// Carte d'un cadeau : vignette carrée dégradée + emoji, nom, prix, bouton Acheter.
private struct GiftCard: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let gift: VirtualGift
    let owned: Int
    let onBuy: () -> Void

    var body: some View {
        DMCard(padded: false) {
            VStack(spacing: 0) {
                ZStack(alignment: .topTrailing) {
                    ZStack {
                        Rectangle().fill(theme.gradients.primary)
                        visual
                    }
                    .aspectRatio(1, contentMode: .fill)

                    if owned > 0 {
                        Text("×\(owned)")
                            .font(DMFont.caption).bold()
                            .foregroundStyle(theme.colors.accentForeground)
                            .padding(.horizontal, theme.spacing.sm)
                            .padding(.vertical, 2)
                            .background(theme.colors.accent, in: Capsule())
                            .padding(theme.spacing.sm)
                    }
                }

                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    Text(gift.name)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                        .lineLimit(1)
                    Text(formatCredits(gift.price))
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                    DMButton(s.buy, action: onBuy)
                }
                .padding(theme.spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /// Visuel du cadeau : le backend range en général un **emoji** dans `image_url` (rendu
    /// en grand, comme le web) ; si c'est exceptionnellement une URL, on charge l'image.
    @ViewBuilder
    private var visual: some View {
        let raw = gift.imageURL?.nilIfBlank ?? gift.emoji
        if let raw, raw.hasPrefix("http") {
            DMRemoteImage(url: raw, fallback: "🎁")
        } else {
            Text(raw ?? "🎁").font(.system(size: 56))
        }
    }
}

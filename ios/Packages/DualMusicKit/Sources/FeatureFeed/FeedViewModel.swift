import Foundation
import Observation
import CoreNetwork
import CoreLiveMedia
import DomainModels

/// Charge le feed des lives (`GET /lives`).
///
/// Le backend expose un curseur dans `meta.pagination`, mais la liste des lives actifs est
/// courte et volatile : comme sur Android, on pagine par `page` incrémentale, ce qui reste
/// exact et évite un curseur périmé quand un live se termine entre deux pages.
public struct FeedRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Récupère une page de lives actifs (les plus récents d'abord).
    /// - Parameter page: numéro de page (1-based).
    public func lives(page: Int = 1) async throws -> [Live] {
        try await http.request(
            .get(LiveEndpoints.list, query: ["status": "live", "limit": "10", "page": String(page)]),
            as: [Live].self
        )
    }
}

/// ViewModel du feed vertical.
///
/// Charge les lives, pagine à l'approche du bas, et **pré-chauffe le jeton LiveKit de la
/// prochaine room** dès que la page active change — pour une entrée-live quasi instantanée.
/// Miroir de `FeedViewModel` Android.
@Observable
@MainActor
public final class FeedViewModel {

    public private(set) var items: [Live] = []
    public private(set) var isLoading = false

    private let repository: FeedRepository
    private let tokenService: LiveKitTokenService

    private var page = 1
    private var hasMore = true
    private var prewarmed: [String: LiveKitToken] = [:]

    /// - Parameters:
    ///   - repository: lecture du catalogue de lives.
    ///   - tokenService: émission des jetons LiveKit (prefetch).
    public init(repository: FeedRepository, tokenService: LiveKitTokenService) {
        self.repository = repository
        self.tokenService = tokenService
    }

    /// Charge la première page (idempotent).
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        if let first = try? await repository.lives(page: 1) {
            items = first
            hasMore = !first.isEmpty
            page = 1
            prewarmAround(index: 0)
        }
        isLoading = false
    }

    /// Appelé quand la page active change (scroll) : prewarm + pagination anticipée.
    /// - Parameter index: index de la cellule active.
    public func onPageChanged(index: Int) {
        prewarmAround(index: index)
        if hasMore, index >= items.count - 3 {
            Task { await loadMore() }
        }
    }

    /// Jeton pré-chauffé d'un live (`nil` si pas encore prêt).
    /// - Parameter liveId: identifiant du live.
    public func prewarmedToken(for liveId: String) -> LiveKitToken? {
        prewarmed[liveId]
    }

    // MARK: - Interne

    private func loadMore() async {
        guard !isLoading else { return }
        isLoading = true
        let next = (try? await repository.lives(page: page + 1)) ?? []
        if next.isEmpty {
            hasMore = false
        } else {
            page += 1
            items.append(contentsOf: next)
        }
        isLoading = false
    }

    /// Pré-chauffe les jetons des rooms `index` et `index + 1` (active + suivante).
    private func prewarmAround(index: Int) {
        for offset in 0...1 {
            let target = index + offset
            guard target >= 0, target < items.count else { continue }
            let item = items[target]
            guard prewarmed[item.id] == nil else { continue }
            Task { [weak self] in
                guard let self else { return }
                if let token = try? await self.tokenService.token(roomName: item.liveKitRoom) {
                    self.prewarmed[item.id] = token
                }
            }
        }
    }
}

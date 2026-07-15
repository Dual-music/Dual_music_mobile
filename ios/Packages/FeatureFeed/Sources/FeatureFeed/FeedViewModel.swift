import Foundation
import Observation
import CoreMedia

/// ViewModel du feed vertical.
///
/// Charge les lives, pagine à l'approche du bas, et **pré-chauffe le jeton LiveKit de la
/// prochaine room** dès que la cellule active change — pour que l'entrée dans le live
/// suivant soit quasi instantanée (latence d'entrée-live < 2s, objectif TikTok).
@Observable
@MainActor
public final class FeedViewModel {

    private let repository: FeedRepository
    private let tokenService: LiveKitTokenService

    public private(set) var items: [LiveItem] = []
    public private(set) var isLoading = false
    public var activeId: String?

    private var nextCursor: String?
    private var hasMore = true
    /// Jetons pré-chauffés par id de live (consommés au démarrage de la cellule active).
    private var prewarmedTokens: [String: LiveKitToken] = [:]

    public init(repository: FeedRepository, tokenService: LiveKitTokenService) {
        self.repository = repository
        self.tokenService = tokenService
    }

    /// Charge la première page et fixe la cellule active initiale.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        do {
            let (items, cursor) = try await repository.lives()
            self.items = items
            self.nextCursor = cursor
            self.hasMore = cursor != nil
            self.activeId = items.first?.id
            prewarmAround(index: 0)
        } catch { /* garder l'écran, réessai possible via pull-to-refresh */ }
        isLoading = false
    }

    /// Charge la page suivante quand l'utilisateur approche de la fin.
    public func loadMoreIfNeeded(currentId: String) async {
        guard hasMore, !isLoading, let idx = items.firstIndex(where: { $0.id == currentId }) else { return }
        guard idx >= items.count - 3 else { return } // seuil : 3 items avant la fin
        isLoading = true
        if let (more, cursor) = try? await repository.lives(cursor: nextCursor) {
            items.append(contentsOf: more)
            nextCursor = cursor
            hasMore = cursor != nil
        }
        isLoading = false
    }

    /// Appelé quand la cellule active change (scroll) : pré-chauffe la room suivante.
    public func onActiveChanged(to id: String) {
        activeId = id
        if let idx = items.firstIndex(where: { $0.id == id }) {
            prewarmAround(index: idx)
            Task { await loadMoreIfNeeded(currentId: id) }
        }
    }

    /// Retourne le jeton pré-chauffé d'un live (nil si non prêt → la cellule le récupérera).
    public func prewarmedToken(for id: String) -> LiveKitToken? { prewarmedTokens[id] }

    // MARK: Prewarm

    /// Pré-chauffe le jeton des rooms i, i+1 (l'active + la suivante).
    private func prewarmAround(index: Int) {
        for offset in 0...1 {
            let i = index + offset
            guard i < items.count else { continue }
            let item = items[i]
            guard prewarmedTokens[item.id] == nil else { continue }
            Task { [weak self] in
                guard let self else { return }
                if let token = try? await self.tokenService.token(roomName: item.liveKitRoom) {
                    self.prewarmedTokens[item.id] = token
                }
            }
        }
    }
}

import Foundation
import Observation
import CoreNetwork
import CoreLiveMedia
import CoreRealtime
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
    /// Nombre de spectateurs en temps réel par live (via présence Socket.IO `/live`) — utilisé
    /// par la vue LISTE (``LivesListView``), pas par le pager vertical.
    public private(set) var presence: [String: Int] = [:]

    private let repository: FeedRepository
    private let tokenService: LiveKitTokenService
    private let realtime: RealtimeClient

    private var page = 1
    private var hasMore = true
    private var prewarmed: [String: LiveKitToken] = [:]
    private var liveSession: NamespaceSession?
    private var presenceSubscriptions: [Subscription] = []
    private var presenceConnected = false

    /// - Parameters:
    ///   - repository: lecture du catalogue de lives.
    ///   - tokenService: émission des jetons LiveKit (prefetch).
    ///   - realtime: client Socket.IO partagé (présence temps réel de la vue liste).
    public init(repository: FeedRepository, tokenService: LiveKitTokenService, realtime: RealtimeClient) {
        self.repository = repository
        self.tokenService = tokenService
        self.realtime = realtime
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

    /// Utilisé par la vue LISTE (``LivesListView``) : charge si besoin puis connecte la
    /// présence temps réel. Sûr car sur la liste aucun live n'est actif — se connecter au
    /// socket `/live` ne casse rien (contrairement au pager vertical qui l'utilise déjà
    /// pour le live actif).
    public func refreshPresence() async {
        if items.isEmpty { await load() }
        guard !presenceConnected else { return }
        presenceConnected = true
        connectPresence()
    }

    /// Rejoint les rooms `/live` de tous les lives affichés (un seul socket partagé) et écoute
    /// l'event `presence` → compteur de spectateurs temps réel par carte, comme le web.
    private func connectPresence() {
        let live = realtime.session(.live)
        liveSession = live
        presenceSubscriptions.append(live.onConnect { [weak self] in
            guard let self else { return }
            for item in self.items { live.join(.live, id: item.id) }
        })
        presenceSubscriptions.append(live.onEvent(Realtime.Event.presence, as: PresencePayload.self) { [weak self] payload in
            guard let self else { return }
            let id: String?
            if let room = payload.room, room.hasPrefix("live:") {
                id = String(room.dropFirst("live:".count))
            } else {
                id = payload.liveId
            }
            guard let id, !id.isEmpty else { return }
            self.presence[id] = payload.count
        })
        Task { await live.connect() }
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

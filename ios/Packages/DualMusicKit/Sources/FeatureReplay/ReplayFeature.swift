import SwiftUI
import AVKit
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Accès REST aux rediffusions.
///
/// Catalogue + vérification d'accès + déblocage (débit atomique via le portefeuille).
public struct ReplayRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Catalogue des replays (publics / débloqués selon l'utilisateur).
    public func replays(limit: Int = 50) async throws -> [ReplayVideo] {
        try await http.request(
            .get(ReplayEndpoints.list, query: ["limit": String(limit)]),
            as: [ReplayVideo].self
        )
    }

    /// Détail d'un replay.
    public func replay(id: String) async throws -> ReplayVideo {
        try await http.request(.get(ReplayEndpoints.detail(id)), as: ReplayVideo.self)
    }

    /// Le caller a-t-il déjà accès à ce replay (gratuit ou débloqué) ?
    public func hasAccess(id: String) async throws -> Bool {
        try await http.request(.get(ReplayEndpoints.access(id)), as: ReplayAccess.self).hasAccess
    }

    /// Enregistre une vue (best-effort, non bloquant côté UI).
    public func registerView(id: String) async {
        try? await http.send(.post(ReplayEndpoints.views(id)))
    }

    /// Débloque un replay premium (débit atomique + idempotent).
    /// - Returns: `true` si le déblocage a réussi.
    public func unlock(id: String, idempotencyKey: String = UUID().uuidString) async -> Bool {
        do {
            try await http.send(
                .post(ReplayEndpoints.unlock, body: UnlockReplayRequest(replayId: id), idempotencyKey: idempotencyKey)
            )
            return true
        } catch {
            return false
        }
    }
}

/// ViewModel du catalogue de replays.
@Observable
@MainActor
public final class ReplaysViewModel {

    public private(set) var replays: [ReplayVideo] = []
    public private(set) var isLoading = false

    private let repository: ReplayRepository

    public init(repository: ReplayRepository) {
        self.repository = repository
    }

    /// Charge le catalogue.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        replays = (try? await repository.replays()) ?? []
        isLoading = false
    }
}

/// ViewModel du lecteur de replay : gère l'accès (gratuit / débloqué / à débloquer).
@Observable
@MainActor
public final class ReplayPlayerViewModel {

    /// Le replay à lire.
    public let replay: ReplayVideo

    /// `nil` = en vérification, `true` = accès accordé, `false` = déblocage requis.
    public private(set) var unlocked: Bool?
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    private let repository: ReplayRepository

    /// - Parameters:
    ///   - replay: replay à lire.
    ///   - repository: accès + déblocage.
    public init(replay: ReplayVideo, repository: ReplayRepository) {
        self.replay = replay
        self.repository = repository
    }

    /// Vérifie l'accès au chargement (un replay gratuit est accordé d'office).
    public func checkAccess() async {
        guard replay.requiresUnlock else {
            unlocked = true
            return
        }
        unlocked = (try? await repository.hasAccess(id: replay.id)) ?? false
    }

    /// Débloque le replay (débit du solde) puis autorise la lecture.
    public func unlock() async {
        isBusy = true
        defer { isBusy = false }
        if await repository.unlock(id: replay.id) {
            unlocked = true
            errorMessage = nil
        } else {
            errorMessage = AppStrings.current.unlockFailed
        }
    }

    /// Enregistre une vue (best-effort).
    public func registerView() async {
        await repository.registerView(id: replay.id)
    }
}

/// Catalogue des rediffusions.
@MainActor
public struct ReplaysListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ReplaysViewModel
    private let onOpen: (ReplayVideo) -> Void

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'un replay (lecteur).
    public init(viewModel: ReplaysViewModel, onOpen: @escaping (ReplayVideo) -> Void) {
        self.viewModel = viewModel
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            if viewModel.isLoading {
                DMLoadingBox()
            } else if viewModel.replays.isEmpty {
                DMEmptyState(title: s.noReplays, subtitle: s.noReplaysHint, systemImage: "play.rectangle")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.replays) { replay in
                            Button { onOpen(replay) } label: { ReplayRow(replay: replay) }
                                .buttonStyle(.plain)
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

/// Carte d'un replay : titre, vues, et badge de prix/gratuité.
private struct ReplayRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let replay: ReplayVideo

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("▶  \(replay.title ?? s.replay)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Text("\(replay.viewsCount) \(s.views)")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                if replay.requiresUnlock {
                    Text("🔒 \(formatAmount(replay.replayPrice)) \(s.credits)")
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                } else {
                    Text(s.free)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
            }
        }
    }
}

/// Lecteur de replay.
///
/// Affiche le paywall si un déblocage est requis, sinon lit la vidéo via `AVPlayer`
/// (décodage matériel, HLS/MP4) — équivalent d'ExoPlayer côté Android.
@MainActor
public struct ReplayPlayerView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ReplayPlayerViewModel

    /// - Parameter viewModel: état d'accès + le replay.
    public init(viewModel: ReplayPlayerViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text(viewModel.replay.title ?? s.replay)
                .font(DMFont.headline)
                .foregroundStyle(theme.colors.foreground)

            switch viewModel.unlocked {
            case nil:
                DMLoadingBox().frame(height: 120)

            case .some(false):
                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        Text(s.replayPremium)
                            .font(DMFont.body)
                            .foregroundStyle(theme.colors.foreground)
                        if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }
                        DMButton(
                            viewModel.isBusy
                                ? s.unlocking
                                : "\(s.unlockFor) \(formatAmount(viewModel.replay.replayPrice)) \(s.credits)",
                            isLoading: viewModel.isBusy,
                            isEnabled: !viewModel.isBusy
                        ) {
                            Task { await viewModel.unlock() }
                        }
                    }
                }

            case .some(true):
                if let urlString = viewModel.replay.videoURL, let url = URL(string: urlString) {
                    VideoPlayer(player: AVPlayer(url: url))
                        .aspectRatio(16 / 9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                        .task { await viewModel.registerView() }
                } else {
                    Text(s.videoUnavailable)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
            }

            Spacer()
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.checkAccess() }
    }
}

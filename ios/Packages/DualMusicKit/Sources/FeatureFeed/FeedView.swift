import SwiftUI
import CoreUI
import DomainModels
import FeatureLive

/// Feed vertical plein écran (style TikTok) : un live par page.
///
/// Seule la page **active** connecte la vidéo (via ``FeatureLive/LiveRoomView``) ; les
/// autres affichent une affiche légère. Le jeton de la prochaine room est pré-chauffé par
/// le ViewModel. Miroir de `FeedScreen` Android (`VerticalPager`), réalisé ici avec le
/// défilement paginé natif d'iOS 17 (`scrollTargetBehavior(.paging)`).
@MainActor
public struct FeedView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: FeedViewModel
    /// Fabrique un ViewModel de live pour un item (injection par la coque applicative).
    private let makeLiveViewModel: (Live) -> LiveViewModel

    @State private var currentId: String?

    /// - Parameters:
    ///   - viewModel: source du feed.
    ///   - makeLiveViewModel: fabrique (mémoïsée) d'un ViewModel de live.
    public init(viewModel: FeedViewModel, makeLiveViewModel: @escaping (Live) -> LiveViewModel) {
        self.viewModel = viewModel
        self.makeLiveViewModel = makeLiveViewModel
    }

    public var body: some View {
        Group {
            if viewModel.items.isEmpty {
                emptyState
            } else {
                pager
            }
        }
        .task { await viewModel.load() }
    }

    /// Aucun live en cours : état vide centré plutôt qu'un écran noir.
    private var emptyState: some View {
        ZStack {
            DMScreenBackground()
            if viewModel.isLoading {
                DMLoadingBox()
            } else {
                DMEmptyState(title: s.noLives, subtitle: s.noLivesHint, systemImage: "play.circle")
            }
        }
    }

    /// Défilement vertical paginé : une cellule = un écran complet.
    private var pager: some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                ForEach(viewModel.items) { item in
                    cell(for: item)
                        .containerRelativeFrame([.horizontal, .vertical])
                        .id(item.id)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $currentId)
        .scrollIndicators(.hidden)
        .ignoresSafeArea()
        .background(Color.black)
        .onAppear { currentId = viewModel.items.first?.id }
        .onChange(of: currentId) { _, newValue in
            guard let newValue, let index = viewModel.items.firstIndex(where: { $0.id == newValue }) else { return }
            viewModel.onPageChanged(index: index)
        }
    }

    /// Cellule active → live connecté ; cellules voisines → affiche statique.
    @ViewBuilder
    private func cell(for item: Live) -> some View {
        if item.id == currentId {
            LiveRoomView(
                viewModel: makeLiveViewModel(item),
                hostUserId: item.artistId,
                quickGiftId: "", // sélection réelle depuis la boutique de cadeaux
                prewarmedToken: viewModel.prewarmedToken(for: item.id)
            )
        } else {
            poster(for: item)
        }
    }

    /// Affiche statique (avant activation) : évite de connecter toutes les vidéos.
    private func poster(for item: Live) -> some View {
        ZStack {
            DMScreenBackground()
            VStack(spacing: theme.spacing.sm) {
                Text(item.artist?.displayName ?? s.live)
                    .font(DMFont.headline)
                    .foregroundStyle(.white)
                if let title = item.title {
                    Text(title)
                        .font(DMFont.caption)
                        .foregroundStyle(.white.opacity(0.8))
                }
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.white)
                    .dmGlow()
            }
        }
    }
}

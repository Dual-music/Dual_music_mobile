import SwiftUI
import CoreUI
import CoreMedia
import FeatureLive

/// Feed vertical plein écran (style TikTok) : un live par page, scroll paginé.
///
/// Seule la cellule **active** connecte la vidéo (via `LiveRoomView`) ; les autres montrent
/// une affiche légère. Le jeton de la prochaine room est pré-chauffé (`FeedViewModel`).
///
/// - Parameter makeLiveViewModel: fabrique un `LiveViewModel` pour un live donné (injection
///   des dépendances media/realtime/repository au niveau app).
public struct FeedView: View {
    @Environment(\.dmTheme) private var theme
    @State private var viewModel: FeedViewModel
    private let makeLiveViewModel: (LiveItem) -> LiveViewModel

    public init(viewModel: FeedViewModel, makeLiveViewModel: @escaping (LiveItem) -> LiveViewModel) {
        _viewModel = State(initialValue: viewModel)
        self.makeLiveViewModel = makeLiveViewModel
    }

    public var body: some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                ForEach(viewModel.items) { item in
                    cell(for: item)
                        .containerRelativeFrame([.horizontal, .vertical]) // une page = plein écran
                        .id(item.id)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)           // snap page-à-page (paging vertical iOS 17)
        .scrollPosition(id: $viewModel.activeId) // suit la page visible
        .ignoresSafeArea()
        .background(.black)
        .onChange(of: viewModel.activeId) { _, newId in
            if let newId { viewModel.onActiveChanged(to: newId) }
        }
        .task { await viewModel.load() }
    }

    /// Cellule d'un live : vidéo live si active, sinon affiche légère.
    @ViewBuilder private func cell(for item: LiveItem) -> some View {
        if item.id == viewModel.activeId {
            LiveRoomView(
                viewModel: makeLiveViewModel(item),
                hostUserId: item.artistId,
                quickGiftId: "", // TODO(feature-gifts): sélecteur de cadeau réel
                prewarmedToken: viewModel.prewarmedToken(for: item.id)
            )
        } else {
            poster(for: item)
        }
    }

    /// Affiche statique (avant activation) : évite de connecter toutes les vidéos.
    private func poster(for item: LiveItem) -> some View {
        ZStack {
            theme.gradients.hero
            VStack(spacing: theme.spacing.sm) {
                Text(item.artist?.displayName ?? "Live")
                    .font(DMFont.headline)
                    .foregroundStyle(.white)
                if let title = item.title {
                    Text(title).font(DMFont.caption).foregroundStyle(.white.opacity(0.8))
                }
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.white)
                    .dmGlow()
            }
        }
    }
}

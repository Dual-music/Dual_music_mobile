import SwiftUI
import AVKit
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Accès REST au contenu : vidéos lifestyle + articles de blog.
///
/// Lecture + interactions légères (like, vue) — toutes best-effort : leur échec ne doit
/// jamais interrompre la lecture.
public struct ContentRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Vidéos lifestyle (les plus récentes).
    public func lifestyle(limit: Int = 50) async throws -> [LifestyleVideo] {
        try await http.request(
            .get(ContentEndpoints.lifestyle, query: ["limit": String(limit)]),
            as: [LifestyleVideo].self
        )
    }

    /// Articles de blog publiés.
    public func blogs(limit: Int = 50) async throws -> [BlogPost] {
        try await http.request(
            .get(ContentEndpoints.blogs, query: ["limit": String(limit)]),
            as: [BlogPost].self
        )
    }

    /// Détail d'un article (contenu complet).
    public func blog(id: String) async throws -> BlogPost {
        try await http.request(.get(ContentEndpoints.blog(id)), as: BlogPost.self)
    }

    /// Bascule le like d'une vidéo (best-effort).
    public func toggleLike(videoId: String) async {
        try? await http.send(.post(ContentEndpoints.lifestyleLikes(videoId)))
    }

    /// Enregistre une vue de vidéo (best-effort).
    public func registerVideoView(videoId: String) async {
        try? await http.send(.post(ContentEndpoints.lifestyleViews(videoId)))
    }
}

/// ViewModel du contenu : vidéos lifestyle + articles de blog.
@Observable
@MainActor
public final class ContentViewModel {

    public private(set) var videos: [LifestyleVideo] = []
    public private(set) var blogs: [BlogPost] = []
    public private(set) var isLoading = false

    private let repository: ContentRepository

    /// - Parameter repository: accès REST au contenu.
    public init(repository: ContentRepository) {
        self.repository = repository
    }

    /// Charge les deux flux de contenu.
    public func load() async {
        isLoading = true
        videos = (try? await repository.lifestyle()) ?? []
        blogs = (try? await repository.blogs()) ?? []
        isLoading = false
    }

    /// Like d'une vidéo (best-effort).
    public func like(videoId: String) async {
        await repository.toggleLike(videoId: videoId)
    }

    /// Enregistre une vue de vidéo.
    public func registerView(videoId: String) async {
        await repository.registerVideoView(videoId: videoId)
    }

    /// Charge le contenu complet d'un article (les listes ne renvoient que l'extrait).
    /// - Parameter id: identifiant de l'article.
    /// - Returns: l'article complet, ou `nil` en cas d'échec.
    public func fullArticle(id: String) async -> BlogPost? {
        try? await repository.blog(id: id)
    }
}

/// Écran de contenu avec deux onglets : **Lifestyle** (vidéos) et **Blog** (articles).
///
/// La navigation vers le lecteur / le détail d'article se fait par `NavigationStack`
/// (l'écran est présenté par la coque, qui fournit déjà l'en-tête de page).
@MainActor
public struct ContentView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ContentViewModel
    @State private var tab = 0
    @State private var openedVideo: LifestyleVideo?
    @State private var openedPost: BlogPost?

    /// - Parameter viewModel: source d'état.
    public init(viewModel: ContentViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            DMTabBar(titles: [s.lifestyle, s.blog], selection: $tab)

            if viewModel.isLoading {
                DMLoadingBox()
            } else if tab == 0 {
                videoList
            } else {
                blogList
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
        .fullScreenCover(item: $openedVideo) { video in
            LifestylePlayerView(video: video, viewModel: viewModel) { openedVideo = nil }
        }
        .sheet(item: $openedPost) { post in
            BlogDetailView(post: post, viewModel: viewModel) { openedPost = nil }
        }
    }

    @ViewBuilder
    private var videoList: some View {
        if viewModel.videos.isEmpty {
            DMEmptyState(title: s.emptyGeneric, systemImage: "film")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.videos) { video in
                        Button { openedVideo = video } label: { VideoRow(video: video) }
                            .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var blogList: some View {
        if viewModel.blogs.isEmpty {
            DMEmptyState(title: s.emptyGeneric, systemImage: "doc.text")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.blogs) { post in
                        Button { openedPost = post } label: { BlogRow(post: post) }
                            .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

/// Carte d'une vidéo lifestyle.
private struct VideoRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let video: LifestyleVideo

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("▶  \(video.title ?? s.video)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let artist = video.artistName {
                        Text(artist).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                Text("❤ \(video.likesCount)")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.accent)
            }
        }
    }
}

/// Carte d'un article de blog.
private struct BlogRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let post: BlogPost

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(post.title ?? s.article)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                if let excerpt = post.excerpt {
                    Text(excerpt)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                        .lineLimit(3)
                }
            }
        }
    }
}

/// Lecteur plein écran d'une vidéo lifestyle (format portrait 9:16, comme le web).
private struct LifestylePlayerView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let video: LifestyleVideo
    let viewModel: ContentViewModel
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: theme.spacing.sm) {
            DMPageHeader(title: video.title ?? s.video, onBack: onBack)

            if let urlString = video.videoURL, let url = URL(string: urlString) {
                VideoPlayer(player: AVPlayer(url: url))
                    .aspectRatio(9 / 16, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                    .task { await viewModel.registerView(videoId: video.id) }

                DMButton("❤ \(s.likeAction)") {
                    Task { await viewModel.like(videoId: video.id) }
                }
                .padding(.horizontal, theme.spacing.lg)
            } else {
                Text(s.videoUnavailable)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }
}

/// Détail d'un article de blog (le contenu complet est chargé à l'ouverture).
private struct BlogDetailView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let post: BlogPost
    let viewModel: ContentViewModel
    let onBack: () -> Void

    @State private var full: BlogPost?

    var body: some View {
        VStack(spacing: 0) {
            DMPageHeader(title: post.title ?? s.article, onBack: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: theme.spacing.md) {
                    if let author = post.authorName {
                        Text("\(s.by) \(author)")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                    // Contenu brut : le rendu HTML riche sera ajouté ultérieurement (parité
                    // avec Android, qui affiche également le texte brut).
                    Text(full?.content ?? post.content ?? post.excerpt ?? "")
                        .font(DMFont.body)
                        .foregroundStyle(theme.colors.foreground)
                }
                .padding(theme.spacing.lg)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { full = await viewModel.fullArticle(id: post.id) }
    }
}

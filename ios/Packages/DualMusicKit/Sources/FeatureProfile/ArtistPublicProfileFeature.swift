import SwiftUI
import Observation
import CoreUI
import DomainModels

/// ViewModel du **profil public d'un artiste**, ouvert depuis un direct/duel/concert/
/// compétition (tap sur son nom). Lit `GET /users/:id` (compte + profil artiste + suivi) et
/// permet de suivre / ne plus suivre. Miroir de `ArtistPublicProfileViewModel` (Android).
@Observable
@MainActor
public final class ArtistPublicProfileViewModel {
    public private(set) var loading = true
    public private(set) var profile: DisplayProfile?
    public private(set) var artistProfile: ArtistProfile?
    public private(set) var followerCount = 0
    public private(set) var isFollowing = false
    public private(set) var error: String?

    private let userId: String
    private let repository: ProfileRepository

    /// - Parameters:
    ///   - userId: id de l'artiste consulté.
    ///   - repository: accès REST profils publics.
    public init(userId: String, repository: ProfileRepository) {
        self.userId = userId
        self.repository = repository
    }

    /// (Re)charge le profil public.
    public func load() async {
        loading = true
        error = nil
        do {
            let r = try await repository.publicProfile(userId: userId)
            profile = r.profile
            artistProfile = r.artistProfile
            followerCount = r.followerCount
            isFollowing = r.isFollowing
            loading = false
        } catch {
            self.error = error.localizedDescription
            loading = false
        }
    }

    /// Suit / ne suit plus (optimiste ; annulé si l'appel échoue).
    public func toggleFollow() async {
        guard !loading else { return }
        let wasFollowing = isFollowing
        let next = !wasFollowing
        isFollowing = next
        followerCount = max(0, followerCount + (next ? 1 : -1))
        do {
            try await repository.setFollow(userId: userId, follow: next)
        } catch {
            isFollowing = wasFollowing
            followerCount = max(0, followerCount + (wasFollowing ? 1 : -1))
        }
    }
}

/// Écran de **profil public d'un artiste** (lecture seule) : couverture, avatar, nom, bio,
/// abonnés, liens sociaux cliquables, et bouton Suivre/Suivi. Présenté en
/// `.fullScreenCover` par-dessus le direct/duel/concert/compétition en cours (le retour ne
/// ferme que cet overlay). Équivalent mobile de `/artist/:id` (web).
@MainActor
public struct ArtistPublicProfileView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private let viewModel: ArtistPublicProfileViewModel

    public init(viewModel: ArtistPublicProfileViewModel) {
        self.viewModel = viewModel
    }

    private var name: String {
        viewModel.artistProfile?.stageName?.nilIfBlank
            ?? viewModel.profile?.displayName
            ?? s.artistSingular
    }
    private var avatar: String? { viewModel.artistProfile?.avatarURL ?? viewModel.profile?.avatarURL }
    private var cover: String? { viewModel.artistProfile?.coverImageURL }
    private var bio: String? { viewModel.artistProfile?.bio?.nilIfBlank ?? viewModel.profile?.bio?.nilIfBlank }
    private var links: [(SocialPlatform, String)] {
        guard let raw = viewModel.artistProfile?.socialLinks else { return [] }
        return SocialPlatform.allCases.compactMap { p in
            guard let url = raw[p.key]?.nilIfBlank else { return nil }
            return (p, url)
        }
    }

    public var body: some View {
        ZStack(alignment: .topLeading) {
            theme.colors.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    coverBanner
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        Text(name)
                            .font(DMFont.pageTitle).bold()
                            .foregroundStyle(theme.colors.foreground)
                        Text("\(viewModel.followerCount) \(s.followers)")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                        DMButton(
                            viewModel.isFollowing ? s.followed : s.follow,
                            style: viewModel.isFollowing ? .outline : .primary,
                            action: { Task { await viewModel.toggleFollow() } }
                        )
                        if let bio, !bio.isEmpty {
                            Text(bio)
                                .font(DMFont.body)
                                .foregroundStyle(theme.colors.foreground.opacity(0.9))
                        }
                        if !links.isEmpty {
                            Text(s.socialLinksLabel)
                                .font(DMFont.body).bold()
                                .foregroundStyle(theme.colors.foreground)
                            ForEach(links, id: \.0) { platform, url in
                                Button {
                                    let fixed = url.hasPrefix("http") ? url : "https://\(url)"
                                    if let parsed = URL(string: fixed) { openURL(parsed) }
                                } label: {
                                    HStack(spacing: theme.spacing.sm) {
                                        Image(systemName: "link")
                                            .foregroundStyle(theme.colors.accent)
                                        Text(platform.label)
                                            .font(DMFont.body).bold()
                                            .foregroundStyle(theme.colors.foreground)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(theme.spacing.md)
                                    .background(theme.colors.muted.opacity(0.4), in: RoundedRectangle(cornerRadius: theme.radius.md))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        if let error = viewModel.error, !viewModel.loading {
                            DMMessage(s.errArtistProfileLoadFailed, kind: .error)
                        }
                    }
                    .padding(theme.spacing.lg)
                }
            }

            Button { dismiss() } label: {
                Image(systemName: "arrow.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(.black.opacity(0.45), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(s.back))
            .padding(theme.spacing.sm)

            if viewModel.loading { DMLoadingBox() }
        }
        .task { await viewModel.load() }
    }

    /// Bannière de couverture (ou dégradé) avec l'avatar qui déborde en bas.
    private var coverBanner: some View {
        ZStack(alignment: .bottomLeading) {
            if let cover, !cover.isEmpty {
                DMRemoteImage(url: cover, fallback: "🎤")
                    .frame(height: 150)
                    .frame(maxWidth: .infinity)
            } else {
                theme.gradients.hero.frame(height: 150).frame(maxWidth: .infinity)
            }
            Color.black.opacity(0.25).frame(height: 150).frame(maxWidth: .infinity)
            ZStack {
                Circle().fill(theme.colors.primary.opacity(0.5)).frame(width: 88, height: 88)
                if let avatar, !avatar.isEmpty {
                    DMRemoteImage(url: avatar, fallback: "🎤")
                        .frame(width: 88, height: 88)
                        .clipShape(Circle())
                } else {
                    Text(name.prefix(1).uppercased())
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
            .offset(x: 16, y: 44)
        }
        .padding(.bottom, 52)
    }
}

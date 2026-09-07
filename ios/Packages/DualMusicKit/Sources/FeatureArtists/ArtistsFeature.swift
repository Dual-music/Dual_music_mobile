import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de l'annuaire des artistes + suivi.
///
/// Le suivi est **optimiste** (bascule immédiate, rollback en cas d'échec serveur) pour que
/// le bouton réponde instantanément — comme sur Android.
@Observable
@MainActor
public final class ArtistsViewModel {

    public private(set) var artists: [ArtistSummary] = []
    /// Ids des artistes suivis (état des boutons).
    public private(set) var following: Set<String> = []
    public private(set) var isLoading = false

    private let http: HTTPClient

    /// - Parameter http: client HTTP.
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Charge l'annuaire + les artistes déjà suivis.
    public func load() async {
        isLoading = true
        artists = (try? await http.request(.get(ArtistEndpoints.list), as: [ArtistSummary].self)) ?? []
        let ids = (try? await http.request(.get(ArtistEndpoints.following), as: [String].self)) ?? []
        following = Set(ids)
        isLoading = false
    }

    /// Suit / ne suit plus un artiste (optimiste puis serveur).
    /// - Parameter artistId: artiste ciblé.
    public func toggleFollow(artistId: String) async {
        let wasFollowing = following.contains(artistId)
        if wasFollowing { following.remove(artistId) } else { following.insert(artistId) }

        do {
            if wasFollowing {
                try await http.send(.delete(ArtistEndpoints.follow(artistId)))
            } else {
                try await http.send(.post(ArtistEndpoints.follow(artistId)))
            }
        } catch {
            // Rollback si le serveur refuse.
            if wasFollowing { following.insert(artistId) } else { following.remove(artistId) }
        }
    }
}

/// Annuaire des artistes avec bouton suivre / ne plus suivre.
@MainActor
public struct ArtistsView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ArtistsViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: ArtistsViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            if viewModel.isLoading {
                DMLoadingBox()
            } else if viewModel.artists.isEmpty {
                DMEmptyState(title: s.noArtists, subtitle: s.noArtistsHint, systemImage: "person")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.artists) { artist in
                            ArtistRow(
                                artist: artist,
                                isFollowing: viewModel.following.contains(artist.id)
                            ) {
                                Task { await viewModel.toggleFollow(artistId: artist.id) }
                            }
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

/// Ligne d'un artiste : avatar, nom, nombre d'abonnés, bouton suivre.
private struct ArtistRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let artist: ArtistSummary
    let isFollowing: Bool
    let onToggle: () -> Void

    var body: some View {
        DMCard {
            HStack(spacing: theme.spacing.md) {
                ZStack {
                    Circle().fill(theme.gradients.primary).frame(width: 44, height: 44)
                    if let url = artist.avatarURL, !url.isEmpty {
                        DMRemoteImage(url: url, fallback: "🎤")
                            .frame(width: 44, height: 44)
                            .clipShape(Circle())
                    } else {
                        Text("🎤")
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(artist.displayName)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Text("\(artist.followersCount) \(s.followers)")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                DMButton(isFollowing ? s.followed : s.follow, style: isFollowing ? .outline : .primary, action: onToggle)
                    .frame(width: 120)
            }
        }
    }
}

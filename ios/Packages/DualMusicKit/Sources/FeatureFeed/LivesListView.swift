import SwiftUI
import CoreUI
import DomainModels

/// Liste (grille) de tous les lives en cours — parité web `/lives` : titre + sous-titre,
/// recherche (nom d'artiste ou titre), et cartes « Regarder ». Alternative au pager plein
/// écran (``FeedView``), utile pour parcourir/rechercher sans lancer de lecture vidéo.
/// Miroir de `LivesListScreen` (Android).
@MainActor
public struct LivesListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    /// Réutilise ``FeedViewModel`` (charge `GET /lives?status=live`), comme Android.
    private let viewModel: FeedViewModel
    private let onOpen: (Live) -> Void

    @State private var search = ""

    /// - Parameters:
    ///   - viewModel: source des lives (partagée avec le pager vertical).
    ///   - onOpen: ouvre le live sélectionné (lecteur plein écran).
    public init(viewModel: FeedViewModel, onOpen: @escaping (Live) -> Void) {
        self.viewModel = viewModel
        self.onOpen = onOpen
    }

    private var filtered: [Live] {
        let q = search.trimmed.lowercased()
        guard !q.isEmpty else { return viewModel.items }
        return viewModel.items.filter {
            ($0.artist?.displayName.lowercased().contains(q) ?? false) || ($0.title?.lowercased().contains(q) ?? false)
        }
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.navLives)
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(theme.colors.primary)
                Text(s.livesSubtitle).foregroundStyle(theme.colors.mutedForeground)

                DMTextField(s.searchPlaceholder, text: $search)

                if filtered.isEmpty {
                    DMEmptyState(title: s.noLivesActive, subtitle: s.noLivesHint, systemImage: "play.circle")
                        .padding(.top, theme.spacing.lg)
                } else {
                    ForEach(filtered) { live in
                        LiveListCard(live: live, viewers: viewModel.presence[live.id] ?? live.viewerCount) { onOpen(live) }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.refreshPresence() }
    }
}

/// Carte d'un live : bandeau dégradé (badge LIVE + spectateurs + avatar) puis infos + « Regarder ».
private struct LiveListCard: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let live: Live
    let viewers: Int
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(spacing: 0) {
                ZStack {
                    LinearGradient(
                        colors: [theme.colors.primary.opacity(0.35), theme.colors.accent.opacity(0.35)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    DMRemoteImage(url: live.artist?.avatarURL, fallback: "🎤")
                        .frame(width: 96, height: 96)
                        .clipShape(Circle())

                    VStack {
                        HStack {
                            Text("🔴 LIVE")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Color(red: 0.937, green: 0.267, blue: 0.267), in: Capsule())
                            Spacer()
                            Text("👥 \(viewers) \(s.spectators)")
                                .font(.system(size: 11))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(.black.opacity(0.5), in: Capsule())
                        }
                        Spacer()
                    }
                    .padding(theme.spacing.sm)
                }
                .frame(height: 190)
                .clipped()

                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    Text(live.artist?.displayName ?? s.artistSingular)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Text(live.title ?? s.liveInProgress)
                        .font(.system(size: 13))
                        .foregroundStyle(theme.colors.mutedForeground)
                    Text(s.watchLive)
                        .font(DMFont.button)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.colors.destructive, in: RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                }
                .padding(theme.spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(theme.colors.card, in: RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

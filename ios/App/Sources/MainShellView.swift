import SwiftUI
import CoreUI
import DomainModels
import FeatureArtists
import FeatureCompetition
import FeatureConcert
import FeatureContent
import FeatureDuel
import FeatureFeed
import FeatureLeaderboard
import FeatureLive
import FeatureNotifications

/// Onglets de la navigation basse (ordre identique à Android).
enum MainTab: Int, CaseIterable, Identifiable {
    case home = 0
    case lives = 1
    case duels = 2
    case concerts = 3
    case competitions = 4

    var id: Int { rawValue }

    /// Symbole SF équivalent à l'icône Material utilisée côté Android.
    var systemImage: String {
        switch self {
        case .home: return "house.fill"
        case .lives: return "play.fill"
        case .duels: return "trophy.fill"
        case .concerts: return "music.note"
        case .competitions: return "chart.bar.fill"
        }
    }

    /// Libellé localisé de l'onglet.
    func title(_ s: DMStrings) -> String {
        switch self {
        case .home: return s.navHome
        case .lives: return s.navLives
        case .duels: return s.navDuels
        case .concerts: return s.navConcerts
        case .competitions: return s.navCompetitions
        }
    }
}

/// Accès rapides de la page d'accueil.
enum HomeDestination: Int, Identifiable {
    case lifestyle = 1
    case ranking = 2
    case artists = 3

    var id: Int { rawValue }
}

/// Coque de l'app connectée : **barre du haut** (hors profil) + **navigation basse** + écran.
///
/// Reproduit exactement la structure d'Android (`MainShell`) :
/// - onglets bas : Accueil · Lives · Duels · Concerts · Compét. ;
/// - le **profil** s'ouvre via l'avatar de la barre du haut et possède sa propre
///   sous-navigation ;
/// - la page Accueil donne 3 accès rapides (Lifestyle / Classement / Artistes) — ces
///   sections ne figurent donc pas dans le menu profil, comme sur le web ;
/// - la cloche ouvre le centre de notifications en pleine page.
@MainActor
struct MainShellView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let container: AppContainer

    @State private var tab: MainTab = .home
    @State private var showProfile = false
    @State private var showNotifications = false
    @State private var homeDestination: HomeDestination?
    @State private var openDuel: Duel?
    @State private var openCompetition: Competition?
    @State private var openConcert: Concert?

    var body: some View {
        VStack(spacing: 0) {
            // Barre du haut masquée dans le profil et sur l'écran de notifications
            // (ces sections ont leur propre en-tête).
            if !showProfile && !showNotifications {
                TopBarView(
                    onOpenNotifications: { showNotifications = true },
                    onOpenProfile: { showProfile = true }
                )
            }

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            BottomBarView(selection: tab) { selected in
                tab = selected
                showProfile = false
                showNotifications = false
                homeDestination = nil
                if selected == .duels { openDuel = nil }
                if selected == .competitions { openCompetition = nil }
                if selected == .concerts { openConcert = nil }
            }
        }
        .background(DMScreenBackground())
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if showNotifications {
            SubScreen(title: s.notifications, onBack: { showNotifications = false }) {
                NotificationsView(viewModel: container.notifications)
            }
        } else if showProfile {
            ProfileSectionView(container: container, onClose: { showProfile = false })
        } else {
            switch tab {
            case .home: homeTab
            case .lives: FeedView(viewModel: container.feed) { container.liveRoom(for: $0) }
            case .duels: duelsTab
            case .concerts: concertsTab
            case .competitions: competitionsTab
            }
        }
    }

    /// Accueil + ses 3 accès rapides en pleine page.
    @ViewBuilder
    private var homeTab: some View {
        switch homeDestination {
        case .lifestyle:
            SubScreen(title: s.lifestyle, onBack: { homeDestination = nil }) {
                ContentView(viewModel: container.content)
            }
        case .ranking:
            SubScreen(title: s.ranking, onBack: { homeDestination = nil }) {
                LeaderboardView(viewModel: container.leaderboard)
            }
        case .artists:
            SubScreen(title: s.artists, onBack: { homeDestination = nil }) {
                ArtistsView(viewModel: container.artists)
            }
        case nil:
            HomeView(
                onOpenLifestyle: { homeDestination = .lifestyle },
                onOpenRanking: { homeDestination = .ranking },
                onOpenArtists: { homeDestination = .artists }
            )
        }
    }

    /// Catalogue de duels → room de duel.
    @ViewBuilder
    private var duelsTab: some View {
        if let duel = openDuel {
            SubScreen(title: s.screenDuels, onBack: { openDuel = nil }) {
                DuelRoomView(viewModel: container.duelRoom(for: duel))
            }
        } else {
            DuelsListView(viewModel: container.duelsList) { openDuel = $0 }
        }
    }

    /// Catalogue de compétitions → room de compétition.
    @ViewBuilder
    private var competitionsTab: some View {
        if let competition = openCompetition {
            SubScreen(title: competition.title, onBack: { openCompetition = nil }) {
                CompetitionRoomView(viewModel: container.competitionRoom(for: competition))
            }
        } else {
            CompetitionsListView(viewModel: container.competitions) { openCompetition = $0 }
        }
    }

    /// Catalogue de concerts → room de concert.
    @ViewBuilder
    private var concertsTab: some View {
        if let concert = openConcert {
            SubScreen(title: concert.title, onBack: { openConcert = nil }) {
                ConcertRoomView(
                    viewModel: container.concertRoom(for: concert),
                    concertTitle: concert.title,
                    hostUserId: concert.artistId,
                    onEnded: { openConcert = nil }
                )
            }
        } else {
            ConcertsListView(viewModel: container.concerts) { openConcert = $0 }
        }
    }
}

/// Enveloppe une sous-vue avec un ``CoreUI/DMPageHeader`` : titre centré + flèche de retour.
///
/// Équivalent du `SubScreen` d'Android.
struct SubScreen<Content: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            DMPageHeader(title: title, onBack: onBack)
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Barre supérieure : logo + notifications + accès profil, sur un dégradé de marque qui
/// remonte derrière la **barre d'état** (comme le `statusBarsPadding` d'Android).
struct TopBarView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let onOpenNotifications: () -> Void
    let onOpenProfile: () -> Void

    var body: some View {
        HStack {
            DMLogo(height: 32)
            Spacer()
            Button(action: onOpenNotifications) {
                Image(systemName: "bell.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text(s.notifications))

            Button(action: onOpenProfile) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text(s.profile))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.xs)
        .background(theme.gradients.topBar.ignoresSafeArea(edges: .top))
    }
}

/// Barre de navigation basse : dégradé violet, items blancs, item actif surligné en rose.
///
/// Implémentée à la main (plutôt qu'un `TabView`) pour reproduire fidèlement le rendu de la
/// `NavigationBar` colorée d'Android, que le style système iOS ne permet pas d'obtenir.
struct BottomBarView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let selection: MainTab
    let onSelect: (MainTab) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTab.allCases) { item in
                Button { onSelect(item) } label: {
                    VStack(spacing: 3) {
                        Image(systemName: item.systemImage)
                            .font(.system(size: 18, weight: .semibold))
                        Text(item.title(s))
                            .font(.system(size: 11, weight: selection == item ? .bold : .regular))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(.white.opacity(selection == item ? 1 : 0.6))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        Capsule()
                            .fill(selection == item ? Color(hex: 0xFF4FA3, alpha: 0.4) : .clear)
                            .padding(.horizontal, 6)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == item ? [.isSelected, .isButton] : .isButton)
            }
        }
        .padding(.top, 6)
        .background(theme.gradients.bottomBar.ignoresSafeArea(edges: .bottom))
    }
}

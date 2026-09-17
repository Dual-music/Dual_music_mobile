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
import FeatureProfile
import FeatureReplay
import FeatureWallet

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
    /// Recharge de crédits ouverte depuis l'icône portefeuille de la barre du haut.
    @State private var showRecharge = false
    /// Nombre de notifications non lues (badge de la cloche) — rafraîchi à chaque ouverture/
    /// fermeture du centre de notifications, comme Android (`LaunchedEffect(notifOpen)`).
    @State private var notifUnread = 0
    @State private var homeDestination: HomeDestination?
    @State private var openDuel: Duel?
    @State private var openCompetition: Competition?
    @State private var openConcert: Concert?
    @State private var openConcertReplay: ReplayVideo?
    /// Live ouvert depuis la liste (``LivesListView``) — lecteur plein écran, spectateur.
    @State private var openLive: Live?
    /// Mon propre live ouvert depuis la liste — j'y entre comme HÔTE, pas spectateur.
    @State private var hostLive: Live?
    /// Événement présélectionné pour le sponsoring (bouton contextuel depuis une liste).
    @State private var sponsorPreselect: (type: String, id: String)?
    /// Profil public d'un artiste (tap sur son nom), superposé au-dessus du direct/duel/
    /// concert/compétition en cours — celui-ci reste en composition, pas de reconnexion.
    @State private var openArtist: IdentifiableID?

    var body: some View {
        VStack(spacing: 0) {
            // Barre du haut masquée dans le profil, la recharge et l'écran de notifications
            // (ces sections ont leur propre en-tête).
            if !showProfile && !showNotifications && !showRecharge {
                TopBarView(
                    onOpenNotifications: { showNotifications = true },
                    onOpenProfile: { showProfile = true },
                    onOpenRecharge: { showRecharge = true },
                    unreadCount: notifUnread
                )
            }

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            BottomBarView(selection: tab) { selected in
                tab = selected
                showProfile = false
                showNotifications = false
                showRecharge = false
                homeDestination = nil
                if selected == .duels { openDuel = nil }
                if selected == .competitions { openCompetition = nil }
                if selected == .concerts { openConcert = nil }
                if selected == .lives { openLive = nil }
            }
        }
        .background(DMScreenBackground())
        .fullScreenCover(item: $openArtist) { artist in
            ArtistPublicProfileView(viewModel: container.artistPublicProfile(userId: artist.id))
        }
        .fullScreenCover(item: $hostLive) { live in
            LiveRoomView(viewModel: container.hostLiveRoom(for: live), hostUserId: live.artistId, onEnded: { hostLive = nil })
        }
        .task { await container.profile.load() }
        .task { notifUnread = await container.unreadNotifications() }
        .onChange(of: showNotifications) { _, _ in
            Task { notifUnread = await container.unreadNotifications() }
        }
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if showRecharge {
            // Recharge de crédits (icône portefeuille de la barre du haut) — parité web.
            SubScreen(title: s.rechargeCredits, onBack: { showRecharge = false }) {
                RechargeView(viewModel: container.recharge)
            }
        } else if showNotifications {
            SubScreen(title: s.notifications, onBack: { showNotifications = false }) {
                NotificationsView(viewModel: container.notifications)
            }
        } else if showProfile {
            ProfileSectionView(
                container: container,
                onClose: { showProfile = false },
                initialSponsorTarget: sponsorPreselect,
                onClearSponsorTarget: { sponsorPreselect = nil }
            )
        } else {
            switch tab {
            case .home: homeTab
            case .lives: livesTab
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

    /// Liste des lives (grille + recherche) → pager plein écran (spectateur) ou, si c'est mon
    /// propre live, entrée en tant qu'HÔTE — parité `tab == 1` de `MainActivity.kt`.
    @ViewBuilder
    private var livesTab: some View {
        if let live = openLive {
            FeedView(
                viewModel: container.feed,
                makeLiveViewModel: { container.liveRoom(for: $0) },
                initialLiveId: live.id,
                onOpenArtist: { openArtist = IdentifiableID($0) }
            )
        } else {
            LivesListView(viewModel: container.feed) { live in
                if let myId = container.profile.me?.user.id, live.artistId == myId {
                    hostLive = live
                } else {
                    openLive = live
                }
            }
        }
    }

    /// Catalogue de duels → room de duel.
    @ViewBuilder
    private var duelsTab: some View {
        if let duel = openDuel {
            SubScreen(title: s.screenDuels, onBack: { openDuel = nil }) {
                DuelRoomView(
                    viewModel: container.duelRoom(for: duel),
                    onLeave: { openDuel = nil },
                    onOpenArtist: { openArtist = IdentifiableID($0) }
                )
            }
        } else {
            DuelsListView(
                viewModel: container.duelsList,
                onOpen: { openDuel = $0 },
                onRequestSponsor: { type, id in sponsorPreselect = (type, id); showProfile = true }
            )
        }
    }

    /// Catalogue de compétitions → room de compétition.
    @ViewBuilder
    private var competitionsTab: some View {
        if let competition = openCompetition {
            SubScreen(title: competition.title, onBack: { openCompetition = nil }) {
                CompetitionRoomView(
                    viewModel: container.competitionRoom(for: competition),
                    onLeave: { openCompetition = nil },
                    onOpenArtist: { openArtist = IdentifiableID($0) }
                )
            }
        } else {
            CompetitionsListView(
                viewModel: container.competitions,
                onOpen: { openCompetition = $0 },
                onRequestSponsor: { type, id in sponsorPreselect = (type, id); showProfile = true }
            )
        }
    }

    /// Catalogue de concerts → room de concert ou lecteur de replay.
    @ViewBuilder
    private var concertsTab: some View {
        if let concert = openConcert {
            SubScreen(title: concert.title, onBack: { openConcert = nil }) {
                ConcertRoomView(
                    viewModel: container.concertRoom(for: concert),
                    concertTitle: concert.title,
                    hostUserId: concert.artistId,
                    artistName: concert.artist?.displayName ?? concert.artistName,
                    onEnded: { openConcert = nil },
                    onOpenArtist: { openArtist = IdentifiableID($0) }
                )
            }
        } else if let replay = openConcertReplay {
            SubScreen(title: replay.title ?? s.replay, onBack: { openConcertReplay = nil }) {
                ReplayPlayerView(viewModel: container.replayPlayer(for: replay))
            }
        } else {
            ConcertsListView(
                viewModel: container.concerts,
                onOpen: { openConcert = $0 },
                onOpenReplay: { openConcertReplay = $0 },
                onRequestSponsor: { type, id in sponsorPreselect = (type, id); showProfile = true }
            )
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
    var onOpenRecharge: () -> Void = {}
    var unreadCount: Int = 0

    var body: some View {
        HStack {
            DMLogo(height: 32)
            Spacer()
            // Recharge (crédits) — ouvre la page de recharge (parité web).
            Button(action: onOpenRecharge) {
                Image(systemName: "creditcard.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text(s.rechargeCredits))

            // Cloche + badge du nombre de non-lus (comme le web).
            Button(action: onOpenNotifications) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                    if unreadCount > 0 {
                        Text(unreadCount > 9 ? "9+" : "\(unreadCount)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 18, height: 18)
                            .background(Color(red: 0.882, green: 0.114, blue: 0.282), in: Circle())
                            .offset(x: -6, y: 6)
                    }
                }
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

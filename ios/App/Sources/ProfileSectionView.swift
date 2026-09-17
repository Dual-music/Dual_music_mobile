import SwiftUI
import CoreUI
import DomainModels
import FeatureArtists
import FeatureCompetition
import FeatureContent
import FeatureCreator
import FeatureDuel
import FeatureGiftShop
import FeatureLive
import FeatureNotifications
import FeatureProfile
import FeatureReferral
import FeatureReplay
import FeatureSponsor
import FeatureSubscription
import FeatureWallet
import FeatureWithdrawal

/// Sous-écrans de la section profil.
///
/// Reprend **exactement** le découpage d'Android (`ProfileSection`), en remplaçant les
/// entiers magiques par un `enum` : même arborescence, même ordre, mêmes titres.
enum ProfileRoute: Hashable {
    /// Profil racine (identité + statistiques).
    case root
    /// Liste de menu « Mon espace » en pleine page.
    case menu
    case wallet
    case recharge
    case notifications
    case withdrawal
    case publicProfile
    case myContent
    case replays
    case giftShop
    case referral
    case subscription
    case sponsor
    case creator
    case editProfile
    case becomeArtist
    case becomeManager
    case preferences
    case following
    case admin
    case myLives
    case managerDuels
    case managerCompetitions
}

/// Section profil (ouverte via l'avatar de la barre du haut) avec sa propre sous-navigation.
///
/// Lifestyle / Classement / Artistes n'y figurent pas : ils sont accessibles depuis
/// l'accueil, comme sur le web.
@MainActor
struct ProfileSectionView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let container: AppContainer
    /// Ferme la section profil et revient à l'onglet courant.
    let onClose: () -> Void

    @State private var route: ProfileRoute = .root
    @State private var managerEnabled = false
    @State private var openedReplay: ReplayVideo?
    /// Live en cours de diffusion (hôte) — plein écran, hors de la sous-navigation profil.
    @State private var broadcastingLive: Live?
    /// Duel géré ouvert depuis « Mes Duels » (manager) — plein écran, hors sous-navigation.
    @State private var openManagedDuel: Duel?
    /// Compétition gérée ouverte depuis « Mes Compétitions » (manager) — plein écran.
    @State private var openManagedCompetition: Competition?

    var body: some View {
        content
            .task {
                managerEnabled = await container.managerRequestsEnabled()
                await container.profile.load()
            }
            // Plein écran, hors de la pile de sous-navigation : quitter la diffusion ne doit
            // pas ramener à un sous-écran profil intermédiaire.
            .fullScreenCover(item: $broadcastingLive) { live in
                LiveRoomView(
                    viewModel: container.hostLiveRoom(for: live),
                    hostUserId: live.artistId,
                    onEnded: { broadcastingLive = nil }
                )
            }
            .fullScreenCover(item: $openManagedDuel) { duel in
                DuelRoomView(viewModel: container.duelRoom(for: duel), onLeave: { openManagedDuel = nil })
            }
            .fullScreenCover(item: $openManagedCompetition) { competition in
                CompetitionRoomView(viewModel: container.competitionRoom(for: competition), onLeave: { openManagedCompetition = nil })
            }
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case .root:
            ProfileView(viewModel: container.profile) { route = .menu }

        case .menu:
            SubScreen(title: s.menuMySpace, onBack: { route = .root }) {
                ProfileMenuView(
                    isPureFan: container.profile.isPureFan,
                    canCreate: container.profile.canCreate,
                    managerEnabled: managerEnabled,
                    isAdmin: container.profile.isAdmin,
                    isArtist: container.profile.isArtist,
                    isManager: container.profile.isManager,
                    onNavigate: { route = $0 },
                    onSignOut: {
                        Task {
                            await container.signOut()
                            onClose()
                        }
                    }
                )
            }

        case .wallet:
            SubScreen(title: s.menuTransactions, onBack: { route = .menu }) {
                WalletView(viewModel: container.wallet, canEarn: container.profile.canCreate) {
                    route = .recharge
                }
            }

        case .recharge:
            SubScreen(title: s.rechargeCredits, onBack: { route = .wallet }) {
                RechargeView(viewModel: container.recharge)
            }

        case .notifications:
            // Préférences d'emails de notification (menu profil) — distinct de la liste
            // in-app, restée accessible via la cloche d'accueil (parité Android : la ligne
            // de menu "Notifications" ouvre les préférences, pas la liste).
            SubScreen(title: s.notifications, onBack: { route = .menu }) {
                NotificationPrefsView(viewModel: container.notificationPrefs)
            }

        case .withdrawal:
            SubScreen(title: s.managerSpace, onBack: { route = .menu }) {
                ManagerSpaceView(revenueViewModel: container.revenue, withdrawalViewModel: container.withdrawal)
            }

        case .publicProfile:
            SubScreen(title: s.publicProfile, onBack: { route = .menu }) {
                PublicProfileEditView(
                    viewModel: container.publicProfileEdit,
                    isArtist: container.profile.isArtist,
                    onSaved: { route = .menu }
                )
            }

        case .myContent:
            // Espace « Mon contenu » créateur (publier une vidéo lifestyle + gérer ses
            // replays). Simplification assumée : même écran/route pour artiste ET manager
            // (comme Android, `isArtist` masque juste la partie lifestyle), avec un unique
            // libellé de menu (« Lifestyle ») — Android affiche « Replays » pour le manager.
            SubScreen(title: s.menuContent, onBack: { route = .menu }) {
                MyContentView(viewModel: container.myContent, isArtist: container.profile.isArtist)
            }

        case .replays:
            replaysRoute

        case .giftShop:
            SubScreen(title: s.menuGiftShop, onBack: { route = .menu }) {
                GiftShopView(viewModel: container.giftShop) { route = .recharge }
            }

        case .referral:
            SubScreen(title: s.menuReferral, onBack: { route = .menu }) {
                ReferralView(viewModel: container.referral)
            }

        case .subscription:
            SubScreen(title: s.menuSubscription, onBack: { route = .menu }) {
                SubscriptionView(viewModel: container.subscription)
            }

        case .sponsor:
            SubScreen(title: s.menuSponsor, onBack: { route = .menu }) {
                SponsorView(viewModel: container.sponsor)
            }

        case .creator:
            SubScreen(title: s.menuCreatorSpace, onBack: { route = .menu }) {
                CreatorView(viewModel: container.creator)
            }

        case .editProfile:
            SubScreen(title: s.menuEditProfile, onBack: { route = .menu }) {
                EditProfileView(viewModel: container.editProfile) { route = .root }
            }

        case .becomeArtist:
            SubScreen(title: s.menuBecomeArtist, onBack: { route = .menu }) {
                BecomeArtistView(viewModel: container.becomeRole)
            }

        case .becomeManager:
            SubScreen(title: s.menuBecomeManager, onBack: { route = .menu }) {
                BecomeManagerView(viewModel: container.becomeRole)
            }

        case .preferences:
            SubScreen(title: s.preferences, onBack: { route = .menu }) {
                PreferencesView(container: container)
            }

        case .following:
            SubScreen(title: s.menuFollowing, onBack: { route = .menu }) {
                FollowedArtistsView(viewModel: container.artists)
            }

        case .admin:
            SubScreen(title: s.menuAdminSpace, onBack: { route = .menu }) {
                AdminView(viewModel: container.admin)
            }

        case .myLives:
            SubScreen(title: s.myLives, onBack: { route = .menu }) {
                if let viewModel = container.myLives() {
                    MyLivesView(viewModel: viewModel) { live in broadcastingLive = live }
                } else {
                    DMLoadingBox()
                }
            }

        case .managerDuels:
            SubScreen(title: s.managedDuels, onBack: { route = .menu }) {
                ManagerDuelsView(viewModel: container.managerDuels) { duel in openManagedDuel = duel }
            }

        case .managerCompetitions:
            SubScreen(title: s.managedCompetitions, onBack: { route = .menu }) {
                ManagerCompetitionsView(viewModel: container.managerCompetitions) { competition in openManagedCompetition = competition }
            }
        }
    }

    /// Sous-navigation des replays : liste → lecteur.
    @ViewBuilder
    private var replaysRoute: some View {
        if let replay = openedReplay {
            SubScreen(title: replay.title ?? s.replay, onBack: { openedReplay = nil }) {
                ReplayPlayerView(viewModel: container.replayPlayer(for: replay))
            }
        } else {
            SubScreen(title: s.menuReplays, onBack: { route = .menu }) {
                ReplaysListView(viewModel: container.replays) { openedReplay = $0 }
            }
        }
    }
}

/// Menu du profil affiché en **pleine page** (comme le menu du web réduit en mobile).
///
/// Chaque item ouvre sa page ; le retour depuis un contenu revient à cette liste, et le
/// retour depuis cette liste revient au profil racine.
/// « Devenir manager » n'apparaît que si l'admin a **ouvert** les candidatures manager.
struct ProfileMenuView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let isPureFan: Bool
    let canCreate: Bool
    let managerEnabled: Bool
    let isAdmin: Bool
    let isArtist: Bool
    let isManager: Bool
    let onNavigate: (ProfileRoute) -> Void
    let onSignOut: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                row("square.grid.2x2.fill", s.menuDashboard) { onNavigate(.root) }
                row("heart.fill", s.menuFollowing) { onNavigate(.following) }
                row("creditcard.circle.fill", s.menuSubscription) { onNavigate(.subscription) }
                row("creditcard.fill", s.menuTransactions) { onNavigate(.wallet) }
                row("gift.fill", s.menuReferral) { onNavigate(.referral) }
                row("megaphone.fill", s.menuSponsor) { onNavigate(.sponsor) }

                // Réservé aux fans : candidatures de rôle (manager selon gating admin).
                if isPureFan {
                    row("person.badge.plus.fill", s.menuBecomeArtist) { onNavigate(.becomeArtist) }
                    if managerEnabled {
                        row("briefcase.fill", s.menuBecomeManager) { onNavigate(.becomeManager) }
                    }
                }

                // Réservé artiste/manager/admin : outils créateur + revenus.
                if canCreate {
                    // Réservé artiste : seul rôle qui héberge des lives.
                    if isArtist {
                        row("video.fill", s.myLives) { onNavigate(.myLives) }
                    }
                    // Réservé manager : crée/gère des duels (jamais de live).
                    if isManager {
                        row("figure.boxing", s.managedDuels) { onNavigate(.managerDuels) }
                    }
                    // Réservé manager : crée/gère des compétitions.
                    if isManager {
                        row("trophy.fill", s.managedCompetitions) { onNavigate(.managerCompetitions) }
                    }
                    row("mic.fill", s.menuArtistProfile) { onNavigate(.publicProfile) }
                    row("star.fill", s.menuCreatorSpace) { onNavigate(.creator) }
                    row("film.fill", s.menuContent) { onNavigate(.myContent) }
                    row("wallet.pass.fill", s.managerSpace) { onNavigate(.withdrawal) }
                    row("play.rectangle.fill", s.menuReplays) { onNavigate(.replays) }
                    row("giftcard.fill", s.menuGiftShop) { onNavigate(.giftShop) }
                }

                // Réservé admin : réglages plateforme + assignation de rôle.
                if isAdmin {
                    row("lock.fill", s.menuAdminSpace) { onNavigate(.admin) }
                }

                row("square.and.pencil", s.menuEditProfile) { onNavigate(.editProfile) }
                row("slider.horizontal.3", s.preferences) { onNavigate(.preferences) }
                row("bell.fill", s.notifications) { onNavigate(.notifications) }

                Divider()
                    .background(theme.colors.border)
                    .padding(.vertical, theme.spacing.xs)

                row("rectangle.portrait.and.arrow.right", s.menuSignOut, tint: theme.colors.destructive) {
                    onSignOut()
                }
            }
            .padding(.vertical, theme.spacing.md)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }

    /// Ligne d'item du menu : icône + libellé, cliquable sur toute la largeur.
    private func row(
        _ systemImage: String,
        _ label: String,
        tint: Color? = nil,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: theme.spacing.md) {
                Image(systemName: systemImage)
                    .font(.system(size: 17))
                    .frame(width: 26)
                Text(label).font(DMFont.body)
                Spacer()
            }
            .foregroundStyle(tint ?? theme.colors.foreground)
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

import Foundation
import Observation
import CoreAuth
import CoreLiveMedia
import CoreNetwork
import CoreRealtime
import CoreUI
import CoreUpload
import DomainModels
import FeatureArtists
import FeatureAuth
import FeatureCompetition
import FeatureConcert
import FeatureContent
import FeatureCreator
import FeatureDuel
import FeatureFeed
import FeatureGiftShop
import FeatureLeaderboard
import FeatureLive
import FeatureNotifications
import FeatureProfile
import FeatureReferral
import FeatureReplay
import FeatureSponsor
import FeatureSubscription
import FeatureWallet
import FeatureWithdrawal

/// Conteneur d'injection de l'application — équivalent Swift de `AppContainer` (Android).
///
/// Construit le graphe complet une seule fois : stockage sécurisé → refresh → client HTTP →
/// repositories, puis les services temps réel (Socket.IO) et média (LiveKit). Aucun
/// framework de DI : le graphe est explicite, lisible et vérifiable à la compilation.
///
/// ## Cycle de vie des ViewModels
/// Les ViewModels d'écran sont **mémoïsés** (créés à la première demande puis réutilisés),
/// ce qui reproduit le comportement de `viewModel { }` côté Compose : revenir sur un écran
/// ne relance pas un chargement complet ni ne perd l'état de formulaire. Les ViewModels
/// « par entité » (room de duel, lecteur de replay) sont mémoïsés **par identifiant**, comme
/// `viewModel(key = id)`.
@MainActor
@Observable
public final class AppContainer {

    // MARK: - Socle

    private let tokenStore: KeychainTokenStore
    private let refresher: AuthRefresher
    let http: HTTPClient
    let realtime: RealtimeClient
    private let mediaUploader: MediaUploader
    private let liveKitTokens: LiveKitTokenService

    /// Thème (clair/sombre/système), persistant.
    let themeController = ThemeController()
    /// Langue de l'interface (FR/EN), persistante.
    let languageController = LanguageController()

    // MARK: - Repositories

    let authRepository: AuthRepository
    private let feedRepository: FeedRepository
    private let liveRepository: LiveRepository
    private let walletRepository: WalletRepository
    private let profileRepository: ProfileRepository
    private let notificationRepository: NotificationRepository
    private let withdrawalRepository: WithdrawalRepository
    private let replayRepository: ReplayRepository
    private let giftShopRepository: GiftShopRepository
    private let contentRepository: ContentRepository
    private let duelRepository: DuelRepository
    private let concertRepository: ConcertRepository
    private let competitionRepository: CompetitionRepository

    // MARK: - Cache de ViewModels
    //
    // `@ObservationIgnored` est **essentiel** ici : ces propriétés sont écrites paresseusement
    // lors du premier accès, qui a souvent lieu pendant l'évaluation d'un `body`. Sans cette
    // annotation, chaque création de ViewModel serait vue par SwiftUI comme une mutation
    // d'état pendant le rendu (« Modifying state during view update ») et provoquerait une
    // passe de rendu supplémentaire. Le cache n'a de toute façon pas à être observé : ce sont
    // les ViewModels eux-mêmes qui le sont.

    @ObservationIgnored private var cachedAuth: AuthViewModel?
    @ObservationIgnored private var cachedFeed: FeedViewModel?
    @ObservationIgnored private var cachedWallet: WalletViewModel?
    @ObservationIgnored private var cachedRecharge: RechargeViewModel?
    @ObservationIgnored private var cachedProfile: ProfileViewModel?
    @ObservationIgnored private var cachedEditProfile: EditProfileViewModel?
    @ObservationIgnored private var cachedBecomeRole: BecomeRoleViewModel?
    @ObservationIgnored private var cachedNotifications: NotificationsViewModel?
    @ObservationIgnored private var cachedWithdrawal: WithdrawalViewModel?
    @ObservationIgnored private var cachedReplays: ReplaysViewModel?
    @ObservationIgnored private var cachedGiftShop: GiftShopViewModel?
    @ObservationIgnored private var cachedLeaderboard: LeaderboardViewModel?
    @ObservationIgnored private var cachedReferral: ReferralViewModel?
    @ObservationIgnored private var cachedSubscription: SubscriptionViewModel?
    @ObservationIgnored private var cachedContent: ContentViewModel?
    @ObservationIgnored private var cachedArtists: ArtistsViewModel?
    @ObservationIgnored private var cachedSponsor: SponsorViewModel?
    @ObservationIgnored private var cachedCreator: CreatorViewModel?
    @ObservationIgnored private var cachedAdmin: AdminViewModel?
    @ObservationIgnored private var cachedDuelsList: DuelsListViewModel?
    @ObservationIgnored private var cachedConcerts: ConcertsViewModel?
    @ObservationIgnored private var cachedCompetitions: CompetitionsViewModel?
    @ObservationIgnored private var duelRooms: [String: DuelViewModel] = [:]
    @ObservationIgnored private var competitionRooms: [String: CompetitionRoomViewModel] = [:]
    @ObservationIgnored private var liveRooms: [String: LiveViewModel] = [:]
    @ObservationIgnored private var replayPlayers: [String: ReplayPlayerViewModel] = [:]

    // MARK: - Construction

    public init() {
        let baseURL = AppConfig.apiBaseURL

        tokenStore = KeychainTokenStore(service: AppConfig.keychainService)
        refresher = AuthRefresher(baseURL: baseURL)
        http = HTTPClient(baseURL: baseURL, tokenStore: tokenStore, refresher: refresher)

        // Le fournisseur de JWT alimente le handshake Socket.IO à chaud (jeton courant).
        let store = tokenStore
        realtime = RealtimeClient(baseURL: baseURL) { await store.accessToken() }

        mediaUploader = MediaUploader(http: http)
        liveKitTokens = LiveKitTokenService(http: http)

        authRepository = AuthRepository(http: http, tokenStore: tokenStore, baseURL: baseURL)
        feedRepository = FeedRepository(http: http)
        liveRepository = LiveRepository(http: http)
        walletRepository = WalletRepository(http: http)
        profileRepository = ProfileRepository(http: http)
        notificationRepository = NotificationRepository(http: http)
        withdrawalRepository = WithdrawalRepository(http: http)
        replayRepository = ReplayRepository(http: http)
        giftShopRepository = GiftShopRepository(http: http)
        contentRepository = ContentRepository(http: http)
        duelRepository = DuelRepository(http: http)
        concertRepository = ConcertRepository(http: http)
        competitionRepository = CompetitionRepository(http: http)

        // Aligne les chaînes globales (accessibles hors SwiftUI) dès le démarrage.
        AppStrings.setLanguage(languageController.language)
    }

    // MARK: - ViewModels mémoïsés

    /// ViewModel d'authentification (partagé par les 3 écrans du tunnel d'inscription).
    var auth: AuthViewModel {
        if let cachedAuth { return cachedAuth }
        let viewModel = AuthViewModel(repository: authRepository)
        viewModel.googleIdTokenProvider = GoogleSignInProvider.makeProvider()
        viewModel.onSignedIn = { [weak self] in
            Task { await self?.registerPushTokenIfAvailable() }
        }
        cachedAuth = viewModel
        return viewModel
    }

    var feed: FeedViewModel {
        if let cachedFeed { return cachedFeed }
        let viewModel = FeedViewModel(repository: feedRepository, tokenService: liveKitTokens)
        cachedFeed = viewModel
        return viewModel
    }

    var wallet: WalletViewModel {
        if let cachedWallet { return cachedWallet }
        let viewModel = WalletViewModel(repository: walletRepository)
        cachedWallet = viewModel
        return viewModel
    }

    var recharge: RechargeViewModel {
        if let cachedRecharge { return cachedRecharge }
        let viewModel = RechargeViewModel(http: http)
        cachedRecharge = viewModel
        return viewModel
    }

    var profile: ProfileViewModel {
        if let cachedProfile { return cachedProfile }
        let viewModel = ProfileViewModel(repository: profileRepository)
        cachedProfile = viewModel
        return viewModel
    }

    var editProfile: EditProfileViewModel {
        if let cachedEditProfile { return cachedEditProfile }
        let viewModel = EditProfileViewModel(repository: profileRepository, uploader: mediaUploader)
        cachedEditProfile = viewModel
        return viewModel
    }

    var becomeRole: BecomeRoleViewModel {
        if let cachedBecomeRole { return cachedBecomeRole }
        let viewModel = BecomeRoleViewModel(repository: profileRepository)
        cachedBecomeRole = viewModel
        return viewModel
    }

    var notifications: NotificationsViewModel {
        if let cachedNotifications { return cachedNotifications }
        let viewModel = NotificationsViewModel(repository: notificationRepository, realtime: realtime)
        cachedNotifications = viewModel
        return viewModel
    }

    var withdrawal: WithdrawalViewModel {
        if let cachedWithdrawal { return cachedWithdrawal }
        let viewModel = WithdrawalViewModel(repository: withdrawalRepository)
        cachedWithdrawal = viewModel
        return viewModel
    }

    var replays: ReplaysViewModel {
        if let cachedReplays { return cachedReplays }
        let viewModel = ReplaysViewModel(repository: replayRepository)
        cachedReplays = viewModel
        return viewModel
    }

    var giftShop: GiftShopViewModel {
        if let cachedGiftShop { return cachedGiftShop }
        let viewModel = GiftShopViewModel(repository: giftShopRepository)
        cachedGiftShop = viewModel
        return viewModel
    }

    var leaderboard: LeaderboardViewModel {
        if let cachedLeaderboard { return cachedLeaderboard }
        let viewModel = LeaderboardViewModel(http: http)
        cachedLeaderboard = viewModel
        return viewModel
    }

    var referral: ReferralViewModel {
        if let cachedReferral { return cachedReferral }
        let viewModel = ReferralViewModel(http: http)
        cachedReferral = viewModel
        return viewModel
    }

    var subscription: SubscriptionViewModel {
        if let cachedSubscription { return cachedSubscription }
        let viewModel = SubscriptionViewModel(http: http)
        cachedSubscription = viewModel
        return viewModel
    }

    var content: ContentViewModel {
        if let cachedContent { return cachedContent }
        let viewModel = ContentViewModel(repository: contentRepository)
        cachedContent = viewModel
        return viewModel
    }

    var artists: ArtistsViewModel {
        if let cachedArtists { return cachedArtists }
        let viewModel = ArtistsViewModel(http: http)
        cachedArtists = viewModel
        return viewModel
    }

    var sponsor: SponsorViewModel {
        if let cachedSponsor { return cachedSponsor }
        let viewModel = SponsorViewModel(http: http, uploader: mediaUploader)
        cachedSponsor = viewModel
        return viewModel
    }

    var creator: CreatorViewModel {
        if let cachedCreator { return cachedCreator }
        let viewModel = CreatorViewModel(http: http, uploader: mediaUploader)
        cachedCreator = viewModel
        return viewModel
    }

    var admin: AdminViewModel {
        if let cachedAdmin { return cachedAdmin }
        let viewModel = AdminViewModel(repository: profileRepository)
        cachedAdmin = viewModel
        return viewModel
    }

    var duelsList: DuelsListViewModel {
        if let cachedDuelsList { return cachedDuelsList }
        let viewModel = DuelsListViewModel(repository: duelRepository)
        cachedDuelsList = viewModel
        return viewModel
    }

    var concerts: ConcertsViewModel {
        if let cachedConcerts { return cachedConcerts }
        let viewModel = ConcertsViewModel(repository: concertRepository)
        cachedConcerts = viewModel
        return viewModel
    }

    var competitions: CompetitionsViewModel {
        if let cachedCompetitions { return cachedCompetitions }
        let viewModel = CompetitionsViewModel(repository: competitionRepository)
        cachedCompetitions = viewModel
        return viewModel
    }

    // MARK: - ViewModels par entité

    /// ViewModel de room de duel — **une connexion SFU par duel ouvert**.
    /// - Parameter duel: duel à ouvrir.
    func duelRoom(for duel: Duel) -> DuelViewModel {
        if let existing = duelRooms[duel.id] { return existing }
        let viewModel = DuelViewModel(
            duelId: duel.id,
            roomName: duel.liveKitRoom,
            media: LiveRoomClient(tokenService: liveKitTokens),
            realtime: realtime,
            repository: duelRepository,
            wallet: walletRepository
        )
        duelRooms[duel.id] = viewModel
        return viewModel
    }

    /// ViewModel de room de compétition (classement + votes).
    func competitionRoom(for competition: Competition) -> CompetitionRoomViewModel {
        if let existing = competitionRooms[competition.id] { return existing }
        let viewModel = CompetitionRoomViewModel(
            competitionId: competition.id,
            repository: competitionRepository,
            realtime: realtime
        )
        competitionRooms[competition.id] = viewModel
        return viewModel
    }

    /// ViewModel d'un live du feed — chaque room a son propre client média.
    func liveRoom(for live: Live) -> LiveViewModel {
        if let existing = liveRooms[live.id] { return existing }
        let viewModel = LiveViewModel(
            liveId: live.id,
            roomName: live.liveKitRoom,
            media: LiveRoomClient(tokenService: liveKitTokens),
            realtime: realtime,
            repository: liveRepository
        )
        liveRooms[live.id] = viewModel
        return viewModel
    }

    /// ViewModel de diffusion pour l'**hôte** d'un live (nouveau ou déjà actif, depuis « Mes
    /// lives ») — pas mémoïsé par `live.id` comme le feed spectateur : une session hôte est
    /// éphémère, recréée à chaque ouverture.
    func hostLiveRoom(for live: Live) -> LiveViewModel {
        LiveViewModel(
            liveId: live.id,
            roomName: live.liveKitRoom,
            media: LiveRoomClient(tokenService: liveKitTokens),
            realtime: realtime,
            repository: liveRepository,
            isHost: true
        )
    }

    /// ViewModel « Mes lives » (hôte), mémoïsé — `nil` tant que le profil n'est pas chargé
    /// (voir `profile.load()`, appelé à l'ouverture de la section profil).
    @ObservationIgnored private var myLivesViewModel: MyLivesViewModel?
    func myLives() -> MyLivesViewModel? {
        if let existing = myLivesViewModel { return existing }
        guard let artistId = profile.me?.user.id else { return nil }
        let viewModel = MyLivesViewModel(artistId: artistId, repository: liveRepository)
        myLivesViewModel = viewModel
        return viewModel
    }

    /// ViewModel de lecture d'un replay (accès + déblocage).
    func replayPlayer(for replay: ReplayVideo) -> ReplayPlayerViewModel {
        if let existing = replayPlayers[replay.id] { return existing }
        let viewModel = ReplayPlayerViewModel(replay: replay, repository: replayRepository)
        replayPlayers[replay.id] = viewModel
        return viewModel
    }

    // MARK: - Opérations transverses

    /// Vrai si l'admin a ouvert les candidatures manager (gating de l'entrée de menu).
    func managerRequestsEnabled() async -> Bool {
        await profileRepository.requestsEnabled(RoleEndpoints.managerRequestsEnabled)
    }

    /// Date (ISO) de suppression programmée du compte, ou `nil` si le compte est actif.
    func accountDeletionScheduledAt() async -> String? {
        try? await profileRepository.me().user.deletionScheduledAt
    }

    /// Programme la suppression du compte (délai de grâce de 20 jours).
    func requestAccountDeletion() async {
        try? await profileRepository.requestAccountDeletion()
    }

    /// Annule la suppression programmée.
    func cancelAccountDeletion() async {
        try? await profileRepository.cancelAccountDeletion()
    }

    /// Enregistre le jeton push de cet appareil auprès du backend (une fois connecté).
    ///
    /// Échec silencieux si le jeton n'est pas encore disponible (autorisation refusée,
    /// APNs indisponible en simulateur…) : la notification in-app continue de fonctionner.
    func registerPushTokenIfAvailable() async {
        guard let token = await PushService.shared.currentToken() else { return }
        try? await authRepository.registerDeviceToken(token)
    }

    /// Déconnexion complète : session serveur, jetons locaux, sockets et caches d'écran.
    ///
    /// Vider les caches est **indispensable** : sans cela, le prochain utilisateur verrait
    /// les données du précédent (solde, notifications…) le temps d'un rechargement.
    func signOut() async {
        await auth.signOut()
        realtime.disconnectAll()
        resetCaches()
    }

    /// Réinitialise tous les ViewModels mémoïsés (hors auth, qui porte l'état de session).
    private func resetCaches() {
        cachedFeed = nil
        cachedWallet = nil
        cachedRecharge = nil
        cachedProfile = nil
        cachedEditProfile = nil
        cachedBecomeRole = nil
        cachedNotifications = nil
        cachedWithdrawal = nil
        cachedReplays = nil
        cachedGiftShop = nil
        cachedLeaderboard = nil
        cachedReferral = nil
        cachedSubscription = nil
        cachedContent = nil
        cachedArtists = nil
        cachedSponsor = nil
        cachedCreator = nil
        cachedAdmin = nil
        cachedDuelsList = nil
        cachedConcerts = nil
        cachedCompetitions = nil
        duelRooms.removeAll()
        competitionRooms.removeAll()
        liveRooms.removeAll()
        replayPlayers.removeAll()
        myLivesViewModel = nil
    }
}

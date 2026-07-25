package com.dualmusic.app

import android.content.Context
import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dualmusic.core.auth.AuthRefresher
import com.dualmusic.core.auth.EncryptedTokenStore
import com.dualmusic.core.media.LiveKitTokenService
import com.dualmusic.core.media.LiveRoomClient
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMPageHeader
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Competition
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.UserRole
import com.dualmusic.domain.model.Live
import com.dualmusic.domain.replay.ReplayVideo
import com.dualmusic.feature.auth.AuthRepository
import com.dualmusic.feature.auth.AuthState
import com.dualmusic.feature.auth.EmailVerifyScreen
import com.dualmusic.feature.auth.AuthViewModel
import com.dualmusic.feature.artists.ArtistsScreen
import com.dualmusic.feature.artists.ArtistsViewModel
import com.dualmusic.feature.creator.CreatorScreen
import com.dualmusic.feature.creator.CreatorViewModel
import com.dualmusic.feature.sponsor.SponsorScreen
import com.dualmusic.feature.sponsor.SponsorViewModel
import com.dualmusic.feature.auth.ProfileCompletionScreen
import com.dualmusic.feature.auth.SignInScreen
import com.dualmusic.feature.competition.CompetitionRepository
import com.dualmusic.feature.competition.CompetitionRoomScreen
import com.dualmusic.feature.competition.CompetitionRoomViewModel
import com.dualmusic.feature.competition.CompetitionsListScreen
import com.dualmusic.feature.competition.CompetitionsViewModel
import com.dualmusic.feature.concert.ConcertRepository
import com.dualmusic.feature.content.ContentRepository
import com.dualmusic.feature.content.ContentScreen
import com.dualmusic.feature.content.ContentViewModel
import com.dualmusic.feature.concert.ConcertsListScreen
import com.dualmusic.feature.concert.ConcertsViewModel
import com.dualmusic.feature.duel.DuelRepository
import com.dualmusic.feature.duel.DuelRoomScreen
import com.dualmusic.feature.duel.DuelViewModel
import com.dualmusic.feature.duel.DuelsListScreen
import com.dualmusic.feature.duel.DuelsListViewModel
import com.dualmusic.feature.feed.FeedRepository
import com.dualmusic.feature.giftshop.GiftShopRepository
import com.dualmusic.feature.giftshop.GiftShopScreen
import com.dualmusic.feature.giftshop.GiftShopViewModel
import com.dualmusic.feature.leaderboard.LeaderboardScreen
import com.dualmusic.feature.leaderboard.LeaderboardViewModel
import com.dualmusic.feature.referral.ReferralScreen
import com.dualmusic.feature.referral.ReferralViewModel
import com.dualmusic.feature.subscription.SubscriptionScreen
import com.dualmusic.feature.subscription.SubscriptionViewModel
import com.dualmusic.feature.feed.FeedScreen
import com.dualmusic.feature.feed.FeedViewModel
import com.dualmusic.feature.live.LiveRepository
import com.dualmusic.feature.live.LiveViewModel
import com.dualmusic.feature.notifications.NotificationRepository
import com.dualmusic.feature.notifications.NotificationsScreen
import com.dualmusic.feature.notifications.NotificationsViewModel
import com.dualmusic.feature.profile.AdminScreen
import com.dualmusic.feature.profile.AdminViewModel
import com.dualmusic.feature.profile.BecomeArtistScreen
import com.dualmusic.feature.profile.BecomeManagerScreen
import com.dualmusic.feature.profile.BecomeRoleViewModel
import com.dualmusic.feature.profile.EditProfileScreen
import com.dualmusic.feature.profile.EditProfileViewModel
import com.dualmusic.feature.profile.ProfileRepository
import com.dualmusic.feature.profile.ProfileScreen
import com.dualmusic.feature.profile.ProfileViewModel
import com.dualmusic.feature.replay.ReplayPlayerScreen
import com.dualmusic.feature.replay.ReplayPlayerViewModel
import com.dualmusic.feature.replay.ReplayRepository
import com.dualmusic.feature.replay.ReplaysListScreen
import com.dualmusic.feature.replay.ReplaysViewModel
import com.dualmusic.feature.wallet.WalletRepository
import com.dualmusic.feature.withdrawal.WithdrawalRepository
import com.dualmusic.feature.withdrawal.WithdrawalScreen
import com.dualmusic.feature.withdrawal.WithdrawalViewModel
import com.dualmusic.feature.wallet.RechargeScreen
import com.dualmusic.feature.wallet.RechargeViewModel
import com.dualmusic.feature.wallet.WalletScreen
import com.dualmusic.feature.wallet.WalletViewModel
import com.dualmusic.domain.notification.NotificationEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * URL de base du backend Dual Music.
 *
 * ⚠️ À ADAPTER selon la cible :
 *  - Émulateur Android : `http://10.0.2.2:4000` (alias de « localhost » du PC).
 *  - Téléphone physique : `http://<IP-LOCALE-DU-PC>:4000` (ex. http://192.168.1.20:4000).
 *    Trouve l'IP avec `ipconfig` sur le PC (IPv4). Le PC et le téléphone doivent être sur
 *    le MÊME réseau Wi-Fi, et le backend doit tourner.
 */

/**
private const val API_BASE_URL = "http://10.0.2.2:4000"
*/

private const val API_BASE_URL = "http://172.17.10.149:4000"



/**
 * Conteneur d'injection minimal (sans framework DI, pour rester simple et lisible).
 * Construit le graphe complet : stockage sécurisé → refresh → client HTTP → repositories,
 * puis les services temps réel (Socket.IO) et média (LiveKit).
 * Un seul exemplaire, porté par l'[MainActivity].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tokenStore = EncryptedTokenStore(appContext)
    private val refresher = AuthRefresher(API_BASE_URL)
    private val api = ApiClient(API_BASE_URL, tokenStore, refresher, appScope)
    // Upload média (presign → PUT → confirm), partagé par sponsor + créateur.
    private val mediaUploader = MediaUploader(api)

    /** Thème (clair/sombre/système), persistant. */
    val themeController = ThemeController(appContext)
    val languageController = LanguageController(appContext)

    /** Repository d'authentification prêt à l'emploi. */
    val authRepository = AuthRepository(api, tokenStore, API_BASE_URL)

    /**
     * Enregistre le jeton FCM de cet appareil auprès du backend (à appeler une fois connecté).
     * L'appel nécessite le Bearer de l'utilisateur ; échec silencieux si FCM/réseau indispo.
     */
    fun registerPushToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                appScope.launch {
                    runCatching {
                        api.request<Unit>(
                            Endpoint.post(NotificationEndpoints.DEVICES, """{"token":"$token"}"""),
                        )
                    }
                }
            }
    }

    // --- Lot feed + live ---
    private val tokenService = LiveKitTokenService(api)
    private val feedRepository = FeedRepository(api)
    private val liveRepository = LiveRepository(api)
    // Le fournisseur de JWT alimente le handshake Socket.IO à chaud (token courant).
    private val realtimeClient = RealtimeClient(API_BASE_URL, jwtProvider = { tokenStore.accessToken() })

    // --- Lot portefeuille ---
    private val walletRepository = WalletRepository(api)

    // --- Lot profil ---
    private val profileRepository = ProfileRepository(api)

    // --- Lot notifications ---
    private val notificationRepository = NotificationRepository(api)

    // --- Lot retrait ---
    private val withdrawalRepository = WithdrawalRepository(api)

    // --- Lot replays ---
    private val replayRepository = ReplayRepository(api)

    // --- Lot boutique cadeaux ---
    private val giftShopRepository = GiftShopRepository(api)

    // --- Lot contenu ---
    private val contentRepository = ContentRepository(api)

    // --- Lot duels ---
    private val duelRepository = DuelRepository(api)

    // --- Lot concerts + compétitions ---
    private val concertRepository = ConcertRepository(api)
    private val competitionRepository = CompetitionRepository(api)

    /** Nouveau ViewModel de feed (liste des lives + prefetch des tokens LiveKit). */
    fun makeFeedViewModel(): FeedViewModel = FeedViewModel(feedRepository, tokenService)

    /** Nouveau ViewModel de portefeuille (solde + historiques). */
    fun makeWalletViewModel(): WalletViewModel = WalletViewModel(walletRepository)

    /** Nouveau ViewModel de recharge de crédits (Mobile Money). */
    fun makeRechargeViewModel(): RechargeViewModel = RechargeViewModel(api)

    /** Nouveau ViewModel de profil (identité + statistiques). */
    fun makeProfileViewModel(): ProfileViewModel = ProfileViewModel(profileRepository)

    /** Vrai si l'admin a ouvert les candidatures manager (gating de l'entrée de menu). */
    suspend fun managerRequestsEnabled(): Boolean =
        runCatching { profileRepository.requestsEnabled(com.dualmusic.domain.role.RoleEndpoints.MANAGER_REQUESTS_ENABLED) }
            .getOrDefault(false)

    /** Date (ISO) de suppression programmée du compte, ou null si actif. */
    suspend fun accountDeletionScheduledAt(): String? =
        runCatching { profileRepository.me().user.deletionScheduledAt }.getOrNull()

    /** Programme la suppression du compte (grâce 20 jours). */
    suspend fun requestAccountDeletion() = profileRepository.requestAccountDeletion()

    /** Annule la suppression programmée. */
    suspend fun cancelAccountDeletion() = profileRepository.cancelAccountDeletion()

    /** Nouveau ViewModel d'édition du profil (avec upload d'avatar). */
    fun makeEditProfileViewModel(): EditProfileViewModel = EditProfileViewModel(profileRepository, mediaUploader)

    /** Nouveau ViewModel des candidatures de rôle (devenir artiste/manager). */
    fun makeBecomeRoleViewModel(): BecomeRoleViewModel = BecomeRoleViewModel(profileRepository)

    /** Nouveau ViewModel du centre de notifications (in-app + temps réel). */
    fun makeNotificationsViewModel(): NotificationsViewModel =
        NotificationsViewModel(notificationRepository, realtimeClient)

    /** Nouveau ViewModel du flux de retrait (PIN + méthodes + demande). */
    fun makeWithdrawalViewModel(): WithdrawalViewModel = WithdrawalViewModel(withdrawalRepository)

    /** Nouveau ViewModel du catalogue de replays. */
    fun makeReplaysViewModel(): ReplaysViewModel = ReplaysViewModel(replayRepository)

    /** Nouveau ViewModel de lecture d'un replay (accès + déblocage). */
    fun makeReplayPlayerViewModel(replay: ReplayVideo): ReplayPlayerViewModel =
        ReplayPlayerViewModel(replay, replayRepository)

    /** Nouveau ViewModel de la boutique de cadeaux (catalogue + inventaire). */
    fun makeGiftShopViewModel(): GiftShopViewModel = GiftShopViewModel(giftShopRepository)

    /** Nouveau ViewModel des classements. */
    fun makeLeaderboardViewModel(): LeaderboardViewModel = LeaderboardViewModel(api)

    /** Nouveau ViewModel du parrainage. */
    fun makeReferralViewModel(): ReferralViewModel = ReferralViewModel(api)

    /** Nouveau ViewModel des abonnements. */
    fun makeSubscriptionViewModel(): SubscriptionViewModel = SubscriptionViewModel(api)

    /** Nouveau ViewModel du contenu (lifestyle + blog). */
    fun makeContentViewModel(): ContentViewModel = ContentViewModel(contentRepository)

    /** Nouveau ViewModel de l'annuaire des artistes. */
    fun makeArtistsViewModel(): ArtistsViewModel = ArtistsViewModel(api)

    /** Nouveau ViewModel du sponsoring. */
    fun makeSponsorViewModel(): SponsorViewModel = SponsorViewModel(api, mediaUploader)

    /** Nouveau ViewModel de l'espace créateur. */
    fun makeCreatorViewModel(): CreatorViewModel = CreatorViewModel(api, mediaUploader)

    /** Nouveau ViewModel de l'espace admin (réglages + assignation de rôle). */
    fun makeAdminViewModel(): AdminViewModel = AdminViewModel(profileRepository)

    /** Nouveau ViewModel du catalogue de duels. */
    fun makeDuelsListViewModel(): DuelsListViewModel = DuelsListViewModel(duelRepository)

    /** Nouveau ViewModel du catalogue de concerts. */
    fun makeConcertsViewModel(): ConcertsViewModel = ConcertsViewModel(concertRepository)

    /** Nouveau ViewModel du catalogue de compétitions. */
    fun makeCompetitionsViewModel(): CompetitionsViewModel = CompetitionsViewModel(competitionRepository)

    /** Nouveau ViewModel de room de compétition (classement + votes). */
    fun makeCompetitionRoomViewModel(competitionId: String): CompetitionRoomViewModel =
        CompetitionRoomViewModel(competitionId, competitionRepository, realtimeClient)

    /**
     * Fabrique un ViewModel de room de duel (une connexion SFU par duel ouvert).
     * Le vote payant est délégué au portefeuille (procédure atomique serveur).
     */
    fun makeDuelViewModel(duel: Duel): DuelViewModel {
        val media = LiveRoomClient(appContext, tokenService, appScope)
        return DuelViewModel(
            duelId = duel.id,
            roomName = duel.roomId ?: "duel:${duel.id}",
            media = media,
            realtime = realtimeClient,
            repository = duelRepository,
            wallet = walletRepository,
        )
    }

    /**
     * Fabrique un ViewModel de live pour un item du feed. Chaque room a son propre
     * [LiveRoomClient] (une connexion SFU par live affiché).
     */
    fun makeLiveViewModel(live: Live): LiveViewModel {
        val media = LiveRoomClient(appContext, tokenService, appScope)
        return LiveViewModel(
            liveId = live.id,
            roomName = live.liveKitRoom,
            media = media,
            realtime = realtimeClient,
            repository = liveRepository,
        )
    }
}

/**
 * Activité principale. Affiche la connexion ; une fois connecté, la coque applicative
 * (feed vertical + portefeuille).
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(this)
        enableEdgeToEdge()
        setContent {
            val themeMode by container.themeController.mode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val language by container.languageController.language.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(
                com.dualmusic.core.ui.i18n.LocalStrings provides com.dualmusic.core.ui.i18n.stringsFor(language),
            ) {
            DualMusicTheme(darkTheme = darkTheme) {
                val vm: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }
                LaunchedEffect(Unit) { vm.bootstrap() }

                val authState by vm.authState.collectAsStateWithLifecycle()

                // Une fois connecté : enregistre le jeton push + demande l'autorisation
                // de notifications (Android 13+).
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* accordée ou non : sans effet bloquant */ }
                LaunchedEffect(authState) {
                    if (authState is AuthState.SignedIn) {
                        container.registerPushToken()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                when (val st = authState) {
                    is AuthState.SignedIn -> MainShell(container, onSignOut = vm::signOut)
                    is AuthState.PendingEmailVerification -> EmailVerifyScreen(viewModel = vm, email = st.email)
                    is AuthState.PendingProfileCompletion -> ProfileCompletionScreen(viewModel = vm)
                    else -> SignInScreen(viewModel = vm, onGoogle = { /* TODO(lot suivant): OAuth Google + deeplink */ })
                }
            }
            }
        }
    }
}

/**
 * Coque de l'app connectée : **barre du haut** (hors profil) + **navigation basse** + écran.
 *
 * Onglets bas : Accueil · Lives · Duels · Concerts · Compét. Le **profil** s'ouvre via
 * l'avatar de la barre du haut ([showProfile]) et possède sa propre sous-navigation
 * ([profileSub]). La page Accueil donne 3 accès rapides (Lifestyle/Classement/Artistes)
 * via [homeOpen] — ces sections ne figurent donc plus dans le profil (comme sur le web).
 */
@Composable
private fun MainShell(container: AppContainer, onSignOut: () -> Unit) {
    // 0=Accueil · 1=Lives · 2=Duels · 3=Concerts · 4=Compét.
    var tab by remember { mutableIntStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }
    // Sous-navigation du profil (0=profil ; voir le when ci-dessous).
    var profileSub by remember { mutableIntStateOf(0) }
    // Accès rapides de l'accueil : 0=aucun · 1=lifestyle · 2=classement · 3=artistes.
    var homeOpen by remember { mutableIntStateOf(0) }
    var openDuel by remember { mutableStateOf<Duel?>(null) }
    var openCompetition by remember { mutableStateOf<Competition?>(null) }

    // Quitte le profil et sélectionne un onglet bas.
    fun goTab(t: Int) { tab = t; showProfile = false }

    Scaffold(
        topBar = {
            // Barre du haut masquée dans le profil (qui a déjà son propre en-tête).
            if (!showProfile) {
                TopBar(
                    onOpenNotifications = { showProfile = true; profileSub = 2 },
                    onOpenProfile = { showProfile = true; profileSub = 0 },
                )
            }
        },
        bottomBar = {
            // Barre basse colorée (dégradé violet) + items en blanc/rose.
            val navS = com.dualmusic.core.ui.i18n.LocalStrings.current
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = Color(0x66FF4FA3),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f),
            )
            Box(
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF2A1257), Color(0xFF3A1D6E))),
                ),
            ) {
                NavigationBar(containerColor = Color.Transparent) {
                    NavigationBarItem(
                        selected = !showProfile && tab == 0,
                        onClick = { goTab(0) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(navS.navHome) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = !showProfile && tab == 1,
                        onClick = { goTab(1) },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        label = { Text(navS.navLives) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = !showProfile && tab == 2,
                        onClick = { goTab(2); openDuel = null },
                        icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                        label = { Text(navS.navDuels) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = !showProfile && tab == 3,
                        onClick = { goTab(3) },
                        icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        label = { Text(navS.navConcerts) },
                        colors = navItemColors,
                    )
                    NavigationBarItem(
                        selected = !showProfile && tab == 4,
                        onClick = { goTab(4); openCompetition = null },
                        icon = { Icon(Icons.Filled.Leaderboard, contentDescription = null) },
                        label = { Text(navS.navCompetitions) },
                        colors = navItemColors,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showProfile) {
                ProfileSection(
                    container = container,
                    sub = profileSub,
                    onSub = { profileSub = it },
                    onSignOut = onSignOut,
                )
            } else when (tab) {
                0 -> when (homeOpen) {
                    1 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.lifestyle, onBack = { homeOpen = 0 }) {
                        ContentScreen(viewModel = viewModel { container.makeContentViewModel() })
                    }
                    2 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.ranking, onBack = { homeOpen = 0 }) {
                        LeaderboardScreen(viewModel = viewModel { container.makeLeaderboardViewModel() })
                    }
                    3 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.artists, onBack = { homeOpen = 0 }) {
                        ArtistsScreen(viewModel = viewModel { container.makeArtistsViewModel() })
                    }
                    else -> HomeScreen(
                        onOpenLifestyle = { homeOpen = 1 },
                        onOpenClassement = { homeOpen = 2 },
                        onOpenArtistes = { homeOpen = 3 },
                    )
                }
                1 -> {
                    val feedVm: FeedViewModel = viewModel { container.makeFeedViewModel() }
                    FeedScreen(viewModel = feedVm, makeLiveViewModel = container::makeLiveViewModel)
                }
                2 -> {
                    val duel = openDuel
                    if (duel == null) {
                        val listVm: DuelsListViewModel = viewModel { container.makeDuelsListViewModel() }
                        DuelsListScreen(viewModel = listVm, onOpen = { openDuel = it })
                    } else {
                        // Clé = id du duel → un ViewModel (et une room SFU) par duel ouvert.
                        val duelVm: DuelViewModel = viewModel(key = duel.id) { container.makeDuelViewModel(duel) }
                        DuelRoomScreen(viewModel = duelVm)
                    }
                }
                3 -> {
                    val concertsVm: ConcertsViewModel = viewModel { container.makeConcertsViewModel() }
                    ConcertsListScreen(viewModel = concertsVm)
                }
                4 -> {
                    val competition = openCompetition
                    if (competition == null) {
                        val listVm: CompetitionsViewModel = viewModel { container.makeCompetitionsViewModel() }
                        CompetitionsListScreen(viewModel = listVm, onOpen = { openCompetition = it })
                    } else {
                        val roomVm: CompetitionRoomViewModel =
                            viewModel(key = competition.id) { container.makeCompetitionRoomViewModel(competition.id) }
                        CompetitionRoomScreen(viewModel = roomVm)
                    }
                }
            }
        }
    }
}

/**
 * Section profil (ouverte via l'avatar de la barre du haut) avec sa propre sous-navigation.
 *
 * Lifestyle / Classement / Artistes n'y figurent plus : ils sont accessibles depuis l'accueil.
 *
 * @param sub sous-écran courant (0 = profil).
 * @param onSub change de sous-écran.
 */
@Composable
private fun ProfileSection(
    container: AppContainer,
    sub: Int,
    onSub: (Int) -> Unit,
    onSignOut: () -> Unit,
) {
    when (sub) {
        1 -> SubScreen(title = "Mon portefeuille", onBack = { onSub(0) }) {
            val walletVm: WalletViewModel = viewModel { container.makeWalletViewModel() }
            WalletScreen(viewModel = walletVm, onOpenRecharge = { onSub(15) })
        }
        15 -> SubScreen(title = "Recharger des crédits", onBack = { onSub(1) }) {
            val rechargeVm: RechargeViewModel = viewModel { container.makeRechargeViewModel() }
            RechargeScreen(viewModel = rechargeVm)
        }
        2 -> SubScreen(title = "Notifications", onBack = { onSub(0) }) {
            val notifVm: NotificationsViewModel = viewModel { container.makeNotificationsViewModel() }
            NotificationsScreen(viewModel = notifVm)
        }
        3 -> SubScreen(title = "Retrait des crédits", onBack = { onSub(0) }) {
            val wdVm: WithdrawalViewModel = viewModel { container.makeWithdrawalViewModel() }
            WithdrawalScreen(viewModel = wdVm)
        }
        4 -> SubScreen(title = "Replays", onBack = { onSub(0) }) {
            // Sous-navigation replays : liste → lecteur.
            var openReplay by remember { mutableStateOf<ReplayVideo?>(null) }
            val r = openReplay
            if (r == null) {
                val listVm: ReplaysViewModel = viewModel { container.makeReplaysViewModel() }
                ReplaysListScreen(viewModel = listVm, onOpen = { openReplay = it })
            } else {
                val playerVm: ReplayPlayerViewModel =
                    viewModel(key = r.id) { container.makeReplayPlayerViewModel(r) }
                Column(modifier = Modifier.fillMaxSize()) {
                    DMButton(
                        "← Liste des replays",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.padding(DualMusicTheme.spacing.sm),
                    ) { openReplay = null }
                    ReplayPlayerScreen(viewModel = playerVm)
                }
            }
        }
        5 -> SubScreen(title = "Boutique de cadeaux", onBack = { onSub(0) }) {
            val shopVm: GiftShopViewModel = viewModel { container.makeGiftShopViewModel() }
            GiftShopScreen(viewModel = shopVm, onOpenRecharge = { onSub(15) })
        }
        7 -> SubScreen(title = "Parrainage", onBack = { onSub(0) }) {
            val refVm: ReferralViewModel = viewModel { container.makeReferralViewModel() }
            ReferralScreen(viewModel = refVm)
        }
        8 -> SubScreen(title = "Abonnements", onBack = { onSub(0) }) {
            val subVm: SubscriptionViewModel = viewModel { container.makeSubscriptionViewModel() }
            SubscriptionScreen(viewModel = subVm)
        }
        11 -> SubScreen(title = "Sponsoring", onBack = { onSub(0) }) {
            val sponsorVm: SponsorViewModel = viewModel { container.makeSponsorViewModel() }
            SponsorScreen(viewModel = sponsorVm)
        }
        12 -> SubScreen(title = "Espace créateur", onBack = { onSub(0) }) {
            val creatorVm: CreatorViewModel = viewModel { container.makeCreatorViewModel() }
            CreatorScreen(viewModel = creatorVm)
        }
        13 -> SubScreen(title = "Modifier le profil", onBack = { onSub(0) }) {
            val editVm: EditProfileViewModel = viewModel { container.makeEditProfileViewModel() }
            EditProfileScreen(viewModel = editVm, onSaved = { onSub(0) })
        }
        14 -> SubScreen(title = "Devenir artiste", onBack = { onSub(0) }) {
            val roleVm: BecomeRoleViewModel = viewModel { container.makeBecomeRoleViewModel() }
            BecomeArtistScreen(viewModel = roleVm)
        }
        16 -> SubScreen(title = "Devenir manager", onBack = { onSub(0) }) {
            val roleVm: BecomeRoleViewModel = viewModel { container.makeBecomeRoleViewModel() }
            BecomeManagerScreen(viewModel = roleVm)
        }
        17 -> SubScreen(title = "Préférences", onBack = { onSub(0) }) {
            val mode by container.themeController.mode.collectAsStateWithLifecycle()
            val lang by container.languageController.language.collectAsStateWithLifecycle()
            var deletionAt by remember { mutableStateOf<String?>(null) }
            var refresh by remember { mutableIntStateOf(0) }
            val prefScope = rememberCoroutineScope()
            LaunchedEffect(refresh) { deletionAt = container.accountDeletionScheduledAt() }
            PreferencesScreen(
                currentMode = mode,
                onSelectMode = { container.themeController.set(it) },
                currentLanguage = lang,
                onSelectLanguage = { container.languageController.set(it) },
                deletionScheduledAt = deletionAt,
                onRequestDeletion = { prefScope.launch { runCatching { container.requestAccountDeletion() }; refresh++ } },
                onCancelDeletion = { prefScope.launch { runCatching { container.cancelAccountDeletion() }; refresh++ } },
            )
        }
        18 -> SubScreen(title = "Suivis", onBack = { onSub(0) }) {
            ArtistsScreen(viewModel = viewModel { container.makeArtistsViewModel() })
        }
        19 -> SubScreen(title = "Espace admin", onBack = { onSub(0) }) {
            AdminScreen(viewModel = viewModel { container.makeAdminViewModel() })
        }
        else -> {
            var showMenu by remember { mutableStateOf(false) }
            var managerEnabled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { managerEnabled = container.managerRequestsEnabled() }

            val profileVm: ProfileViewModel = viewModel { container.makeProfileViewModel() }
            val profileState by profileVm.uiState.collectAsStateWithLifecycle()
            val roles = profileState.me?.roles ?: emptyList()
            val canCreate = roles.any { it == UserRole.ARTIST || it == UserRole.MANAGER || it == UserRole.ADMIN }
            val isAdmin = roles.any { it == UserRole.ADMIN }

            ProfileScreen(viewModel = profileVm, onOpenMenu = { showMenu = true })
            if (showMenu) {
                ProfileMenuSheet(
                    isPureFan = !canCreate,
                    canCreate = canCreate,
                    managerEnabled = managerEnabled,
                    isAdmin = isAdmin,
                    onNavigate = { showMenu = false; onSub(it) },
                    onSignOut = { showMenu = false; onSignOut() },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

/**
 * Enveloppe une sous-vue (portefeuille, notifications…) avec un [DMPageHeader] :
 * titre centré + flèche de retour en haut à gauche.
 *
 * @param title titre affiché, centré.
 * @param onBack action de retour.
 */
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        DMPageHeader(title = title, onBack = onBack)
        content()
    }
}

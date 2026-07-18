package com.dualmusic.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
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
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Competition
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.Live
import com.dualmusic.domain.replay.ReplayVideo
import com.dualmusic.feature.auth.AuthRepository
import com.dualmusic.feature.auth.AuthState
import com.dualmusic.feature.auth.AuthViewModel
import com.dualmusic.feature.artists.ArtistsScreen
import com.dualmusic.feature.artists.ArtistsViewModel
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
import com.dualmusic.feature.wallet.WalletScreen
import com.dualmusic.feature.wallet.WalletViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * URL de base du backend Dual Music.
 *
 * ⚠️ À ADAPTER selon la cible :
 *  - Émulateur Android : `http://10.0.2.2:4000` (alias de « localhost » du PC).
 *  - Téléphone physique : `http://<IP-LOCALE-DU-PC>:4000` (ex. http://192.168.1.20:4000).
 *    Trouve l'IP avec `ipconfig` sur le PC (IPv4). Le PC et le téléphone doivent être sur
 *    le MÊME réseau Wi-Fi, et le backend doit tourner.
 */
private const val API_BASE_URL = "http://10.0.2.2:4000"

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

    /** Repository d'authentification prêt à l'emploi. */
    val authRepository = AuthRepository(api, tokenStore, API_BASE_URL)

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

    /** Nouveau ViewModel de profil (identité + statistiques). */
    fun makeProfileViewModel(): ProfileViewModel = ProfileViewModel(profileRepository)

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
            DualMusicTheme {
                val vm: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }
                LaunchedEffect(Unit) { vm.bootstrap() }

                val authState by vm.authState.collectAsStateWithLifecycle()
                when (authState) {
                    is AuthState.SignedIn -> MainShell(container, onSignOut = vm::signOut)
                    else -> SignInScreen(viewModel = vm, onGoogle = { /* TODO(lot suivant): OAuth Google + deeplink */ })
                }
            }
        }
    }
}

/**
 * Coque de l'app connectée : barre de navigation basse + écran courant.
 * Onglets : **Lives** (feed vertical) et **Portefeuille**. Les autres features
 * (duels, concerts, compétitions, profil) s'ajouteront ici au fil des lots.
 */
@Composable
private fun MainShell(container: AppContainer, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    // Duel actuellement ouvert (null = on affiche le catalogue).
    var openDuel by remember { mutableStateOf<Duel?>(null) }
    // Compétition actuellement ouverte (null = on affiche le catalogue).
    var openCompetition by remember { mutableStateOf<Competition?>(null) }
    val colors = DualMusicTheme.colors

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = colors.card) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("Lives") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; openDuel = null },
                    icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                    label = { Text("Duels") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                    label = { Text("Concerts") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3; openCompetition = null },
                    icon = { Icon(Icons.Filled.Leaderboard, contentDescription = null) },
                    label = { Text("Compét.") },
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Profil") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> {
                    val feedVm: FeedViewModel = viewModel { container.makeFeedViewModel() }
                    FeedScreen(viewModel = feedVm, makeLiveViewModel = container::makeLiveViewModel)
                }
                1 -> {
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
                2 -> {
                    val concertsVm: ConcertsViewModel = viewModel { container.makeConcertsViewModel() }
                    ConcertsListScreen(viewModel = concertsVm)
                }
                3 -> {
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
                else -> {
                    // Onglet Profil ; portefeuille et notifications s'ouvrent par-dessus (retour).
                    // 0 = profil · 1 = portefeuille · 2 = notifications
                    var sub by remember { mutableIntStateOf(0) }
                    when (sub) {
                        1 -> SubScreen(onBack = { sub = 0 }) {
                            val walletVm: WalletViewModel = viewModel { container.makeWalletViewModel() }
                            WalletScreen(viewModel = walletVm)
                        }
                        2 -> SubScreen(onBack = { sub = 0 }) {
                            val notifVm: NotificationsViewModel = viewModel { container.makeNotificationsViewModel() }
                            NotificationsScreen(viewModel = notifVm)
                        }
                        3 -> SubScreen(onBack = { sub = 0 }) {
                            val wdVm: WithdrawalViewModel = viewModel { container.makeWithdrawalViewModel() }
                            WithdrawalScreen(viewModel = wdVm)
                        }
                        4 -> SubScreen(onBack = { sub = 0 }) {
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
                        5 -> SubScreen(onBack = { sub = 0 }) {
                            val shopVm: GiftShopViewModel = viewModel { container.makeGiftShopViewModel() }
                            GiftShopScreen(viewModel = shopVm)
                        }
                        6 -> SubScreen(onBack = { sub = 0 }) {
                            val lbVm: LeaderboardViewModel = viewModel { container.makeLeaderboardViewModel() }
                            LeaderboardScreen(viewModel = lbVm)
                        }
                        7 -> SubScreen(onBack = { sub = 0 }) {
                            val refVm: ReferralViewModel = viewModel { container.makeReferralViewModel() }
                            ReferralScreen(viewModel = refVm)
                        }
                        8 -> SubScreen(onBack = { sub = 0 }) {
                            val subVm: SubscriptionViewModel = viewModel { container.makeSubscriptionViewModel() }
                            SubscriptionScreen(viewModel = subVm)
                        }
                        9 -> SubScreen(onBack = { sub = 0 }) {
                            val contentVm: ContentViewModel = viewModel { container.makeContentViewModel() }
                            ContentScreen(viewModel = contentVm)
                        }
                        10 -> SubScreen(onBack = { sub = 0 }) {
                            val artistsVm: ArtistsViewModel = viewModel { container.makeArtistsViewModel() }
                            ArtistsScreen(viewModel = artistsVm)
                        }
                        else -> {
                            val profileVm: ProfileViewModel = viewModel { container.makeProfileViewModel() }
                            ProfileScreen(
                                viewModel = profileVm,
                                onOpenWallet = { sub = 1 },
                                onOpenWithdrawal = { sub = 3 },
                                onOpenReplays = { sub = 4 },
                                onOpenGiftShop = { sub = 5 },
                                onOpenLeaderboard = { sub = 6 },
                                onOpenReferral = { sub = 7 },
                                onOpenSubscription = { sub = 8 },
                                onOpenContent = { sub = 9 },
                                onOpenArtists = { sub = 10 },
                                onOpenNotifications = { sub = 2 },
                                onSignOut = onSignOut,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enveloppe une sous-vue (portefeuille, notifications) avec une barre « ← Retour ».
 * Évite de dupliquer la logique de retour et de modifier les écrans concernés.
 */
@Composable
private fun SubScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        DMButton(
            "← Retour",
            style = DMButtonStyle.OUTLINE,
            modifier = Modifier.padding(DualMusicTheme.spacing.md),
            onClick = onBack,
        )
        content()
    }
}

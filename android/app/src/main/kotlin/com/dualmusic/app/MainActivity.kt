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
import com.dualmusic.feature.profile.DashboardScreen
import com.dualmusic.feature.profile.DashboardViewModel
import com.dualmusic.feature.profile.EditProfileViewModel
import com.dualmusic.feature.profile.ProfileRepository
import com.dualmusic.feature.profile.ProfileScreen
import com.dualmusic.feature.profile.PublicProfileEditScreen
import com.dualmusic.feature.profile.PublicProfileViewModel
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

// WiFi (débogage sans fil / QR code) : le téléphone atteint le backend du PC via son IP LAN.
// Le PC et le téléphone doivent être sur le MÊME réseau WiFi. Mettre à jour cette IP si elle
// change (DHCP) — la voir avec `ipconfig` (Adresse IPv4). Le backend écoute sur 0.0.0.0:4000.
// En USB : repasser à "http://127.0.0.1:4000" + `adb reverse tcp:4000 tcp:4000`.
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
    val currencyController = CurrencyController(appContext)

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

    /** ViewModel du tableau de bord (identité + statistiques par rôle). */
    fun makeDashboardViewModel(): DashboardViewModel = DashboardViewModel(profileRepository)

    /** ViewModel du profil public créateur (artiste/manager + liens sociaux). */
    fun makePublicProfileViewModel(): PublicProfileViewModel = PublicProfileViewModel(profileRepository, mediaUploader)

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

    /** Table publique des taux de change (pivot USD) — pour le sélecteur de devise. */
    suspend fun exchangeRates(): List<com.dualmusic.domain.settings.ExchangeRate> =
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.settings.SettingsEndpoints.EXCHANGE_RATES),
                kotlinx.serialization.builtins.ListSerializer(com.dualmusic.domain.settings.ExchangeRate.serializer()),
            )
        }.getOrDefault(emptyList())

    /** Charge les préférences visuelles (fuseau + notifications) et alimente le store global. */
    suspend fun loadUiPreferences() {
        runCatching {
            api.request(
                Endpoint.get(com.dualmusic.domain.settings.SettingsEndpoints.UI_PREFERENCES),
                com.dualmusic.domain.settings.UiPreferencesDto.serializer(),
            )
        }.getOrNull()?.let { dto ->
            val cur = com.dualmusic.core.ui.prefs.UiPreferencesStore.current()
            com.dualmusic.core.ui.prefs.UiPreferencesStore.set(
                com.dualmusic.core.ui.prefs.UiPrefs(
                    topDonorMode = dto.topDonorMode?.takeIf { it in setOf("full", "reduced", "off") } ?: cur.topDonorMode,
                    topDonorAnimation = dto.topDonorAnimation?.takeIf { it in setOf("default", "traversing") } ?: cur.topDonorAnimation,
                    reduceAnimations = dto.reduceAnimations ?: cur.reduceAnimations,
                    timezone = dto.timezone?.takeIf { it.isNotBlank() } ?: cur.timezone,
                ),
            )
        }
    }

    /** Met à jour le store immédiatement puis persiste (best-effort) via PUT camelCase. */
    suspend fun saveUiPreferences(prefs: com.dualmusic.core.ui.prefs.UiPrefs) {
        com.dualmusic.core.ui.prefs.UiPreferencesStore.set(prefs)
        runCatching {
            val body = """{"topDonorMode":"${prefs.topDonorMode}","topDonorAnimation":"${prefs.topDonorAnimation}","reduceAnimations":${prefs.reduceAnimations},"timezone":"${prefs.timezone}"}"""
            api.request<Unit>(Endpoint.put(com.dualmusic.domain.settings.SettingsEndpoints.UI_PREFERENCES, body))
        }
    }

    /** Nouveau ViewModel d'édition du profil (avec upload d'avatar). */
    fun makeEditProfileViewModel(): EditProfileViewModel = EditProfileViewModel(profileRepository, mediaUploader)

    /** Nouveau ViewModel des candidatures de rôle (devenir artiste/manager). */
    fun makeBecomeRoleViewModel(): BecomeRoleViewModel = BecomeRoleViewModel(profileRepository)

    /** Nouveau ViewModel du centre de notifications (in-app + temps réel). */
    /** Nouveau ViewModel des préférences email (menu Notifs). */
    fun makeNotificationPrefsViewModel(): com.dualmusic.feature.notifications.NotificationPrefsViewModel =
        com.dualmusic.feature.notifications.NotificationPrefsViewModel(notificationRepository)

    fun makeNotificationsViewModel(): NotificationsViewModel =
        NotificationsViewModel(notificationRepository, realtimeClient)

    /** Nombre de notifications non lues (pour le badge de la cloche). */
    suspend fun unreadNotifications(): Int =
        runCatching { notificationRepository.unreadCount() }.getOrDefault(0)

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

    /** Espace « Mes Duels » du manager (créer/organiser des duels). */
    fun makeManagerDuelsViewModel(): com.dualmusic.feature.duel.ManagerDuelsViewModel =
        com.dualmusic.feature.duel.ManagerDuelsViewModel(duelRepository)

    /** Nouveau ViewModel du catalogue de concerts. */
    fun makeConcertsViewModel(): ConcertsViewModel = ConcertsViewModel(concertRepository)

    /** Nouveau ViewModel du catalogue de compétitions. */
    fun makeCompetitionsViewModel(): CompetitionsViewModel = CompetitionsViewModel(competitionRepository)

    /** Nouveau ViewModel « Mes compétitions » (candidatures de l'artiste). */
    fun makeMyCompetitionsViewModel(): com.dualmusic.feature.competition.MyCompetitionsViewModel =
        com.dualmusic.feature.competition.MyCompetitionsViewModel(competitionRepository)

    /** Espace « Mes compétitions » du manager (créer/gérer des compétitions). */
    fun makeManagerCompetitionsViewModel(): com.dualmusic.feature.competition.ManagerCompetitionsViewModel =
        com.dualmusic.feature.competition.ManagerCompetitionsViewModel(competitionRepository)

    /** Nouveau ViewModel « Mes Lives » (gestion + lancement de lives). */
    fun makeMyLivesViewModel(): com.dualmusic.feature.live.MyLivesViewModel =
        com.dualmusic.feature.live.MyLivesViewModel(api)

    /** Nouveau ViewModel « Contenu » (publier lifestyle + mes vidéos/replays). */
    fun makeMyContentViewModel(): com.dualmusic.feature.content.MyContentViewModel =
        com.dualmusic.feature.content.MyContentViewModel(api, mediaUploader)

    /** Nouveau ViewModel de room de compétition (classement + votes). */
    fun makeCompetitionRoomViewModel(competitionId: String): CompetitionRoomViewModel =
        CompetitionRoomViewModel(competitionId, competitionRepository, realtimeClient)

    /**
     * Fabrique un ViewModel de room de concert (viewer + hôte artiste). L'hôte est déterminé
     * par le VM (fetch `me`). Une connexion SFU par concert ouvert.
     */
    fun makeConcertRoomViewModel(concert: com.dualmusic.domain.model.Concert): com.dualmusic.feature.concert.ConcertRoomViewModel {
        val media = LiveRoomClient(appContext, tokenService, appScope)
        return com.dualmusic.feature.concert.ConcertRoomViewModel(
            concertId = concert.id,
            media = media,
            realtime = realtimeClient,
            repository = concertRepository,
            hostUserId = concert.artistId,
            ticketPrice = concert.ticketPrice,
        )
    }

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

    /**
     * Fabrique un ViewModel de live en mode **hôte** (diffusion) : même overlay riche que
     * le viewer (chat/cadeaux/présence) + publication caméra/micro. Room dédiée.
     */
    fun makeLiveHostViewModel(live: Live): LiveViewModel {
        val media = LiveRoomClient(appContext, tokenService, appScope)
        return LiveViewModel(
            liveId = live.id,
            roomName = live.liveKitRoom,
            media = media,
            realtime = realtimeClient,
            repository = liveRepository,
            isHost = true,
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
            val currency by container.currencyController.currency.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(
                com.dualmusic.core.ui.i18n.LocalStrings provides com.dualmusic.core.ui.i18n.stringsFor(language),
                com.dualmusic.core.ui.currency.LocalCurrency provides currency,
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
                        // Préférences visuelles (fuseau horaire) → store global, appliqué partout.
                        container.loadUiPreferences()
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
/** Valeur spéciale de sous-navigation du profil : la **liste de menu** en pleine page. */
private const val PROFILE_MENU = 100

@Composable
private fun MainShell(container: AppContainer, onSignOut: () -> Unit) {
    // 0=Accueil · 1=Lives · 2=Duels · 3=Concerts · 4=Compét.
    var tab by remember { mutableIntStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }
    // Sous-navigation du profil (0=profil ; PROFILE_MENU=liste ; sinon = contenu d'un item).
    var profileSub by remember { mutableIntStateOf(0) }
    // Accès rapides de l'accueil : 0=aucun · 1=lifestyle · 2=classement · 3=artistes.
    var homeOpen by remember { mutableIntStateOf(0) }
    // Superposition « messages de notifications » (icône cloche de la barre du haut).
    var notifOpen by remember { mutableStateOf(false) }
    // Badge de la cloche : nombre de non-lus, rafraîchi à chaque ouverture/fermeture des notifs.
    var notifUnread by remember { mutableIntStateOf(0) }
    LaunchedEffect(notifOpen) { notifUnread = container.unreadNotifications() }
    var openDuel by remember { mutableStateOf<Duel?>(null) }
    var openCompetition by remember { mutableStateOf<Competition?>(null) }
    var openConcert by remember { mutableStateOf<com.dualmusic.domain.model.Concert?>(null) }
    // Diffusion live (hôte) en plein écran, au-dessus du Scaffold → SANS la barre du bas.
    var broadcastLive by remember { mutableStateOf<Live?>(null) }

    // Quitte le profil et sélectionne un onglet bas (réinitialise les superpositions).
    fun goTab(t: Int) { tab = t; showProfile = false; homeOpen = 0; notifOpen = false }

    // Écran de diffusion plein écran (prioritaire sur tout le reste, pas de nav basse).
    broadcastLive?.let { live ->
        val hostVm: LiveViewModel = viewModel(key = "host-${live.id}") { container.makeLiveHostViewModel(live) }
        com.dualmusic.feature.live.LiveRoomScreen(
            viewModel = hostVm,
            hostUserId = live.artistId,
            quickGiftId = "",
            isHost = true,
            onEndLive = { broadcastLive = null },
            liveTitle = live.title,
            artistName = live.artist?.displayName,
        )
        return
    }

    Scaffold(
        topBar = {
            // Barre du haut masquée dans le profil et sur l'écran de notifications (en-tête propre).
            if (!showProfile && !notifOpen) {
                TopBar(
                    onOpenNotifications = { notifOpen = true },
                    onOpenProfile = { showProfile = true; profileSub = 0 },
                    unreadCount = notifUnread,
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
                        onClick = { goTab(3); openConcert = null },
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
            if (notifOpen) {
                // Messages de notifications (icône cloche de l'accueil) — retour vers l'écran courant.
                SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.notifications, onBack = { notifOpen = false }) {
                    NotificationsScreen(viewModel = viewModel { container.makeNotificationsViewModel() })
                }
            } else if (showProfile) {
                ProfileSection(
                    container = container,
                    sub = profileSub,
                    onSub = { profileSub = it },
                    onSignOut = onSignOut,
                    onStartBroadcast = { broadcastLive = it },
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
                        DuelRoomScreen(viewModel = duelVm, onLeave = { openDuel = null })
                    }
                }
                3 -> {
                    val concert = openConcert
                    if (concert == null) {
                        val concertsVm: ConcertsViewModel = viewModel { container.makeConcertsViewModel() }
                        ConcertsListScreen(viewModel = concertsVm, onOpen = { openConcert = it })
                    } else {
                        // Clé = id du concert → un ViewModel (et une room SFU) par concert ouvert.
                        val roomVm: com.dualmusic.feature.concert.ConcertRoomViewModel =
                            viewModel(key = concert.id) { container.makeConcertRoomViewModel(concert) }
                        com.dualmusic.feature.concert.ConcertRoomScreen(
                            viewModel = roomVm,
                            concertTitle = concert.title,
                            onLeave = { openConcert = null },
                        )
                    }
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
    onStartBroadcast: (Live) -> Unit,
) {
    // Rôles + gating admin, partagés par le profil racine, le dashboard et la page de menu.
    var managerEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { managerEnabled = container.managerRequestsEnabled() }
    val dashboardVm: DashboardViewModel = viewModel { container.makeDashboardViewModel() }
    val dashState by dashboardVm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { dashboardVm.load() }
    val editProfileVm: EditProfileViewModel = viewModel { container.makeEditProfileViewModel() }
    val roles = dashState.me?.roles ?: emptyList()
    val isArtist = roles.any { it == UserRole.ARTIST }
    val isManager = roles.any { it == UserRole.MANAGER }
    val canCreate = isArtist || isManager || roles.any { it == UserRole.ADMIN }
    val isAdmin = roles.any { it == UserRole.ADMIN }

    when (sub) {
        1 -> SubScreen(title = "Mon portefeuille", onBack = { onSub(PROFILE_MENU) }) {
            val walletVm: WalletViewModel = viewModel { container.makeWalletViewModel() }
            WalletScreen(viewModel = walletVm, onOpenRecharge = { onSub(15) }, canEarn = canCreate)
        }
        15 -> SubScreen(title = "Recharger des crédits", onBack = { onSub(1) }) {
            val rechargeVm: RechargeViewModel = viewModel { container.makeRechargeViewModel() }
            RechargeScreen(viewModel = rechargeVm)
        }
        2 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.notifications, onBack = { onSub(PROFILE_MENU) }) {
            com.dualmusic.feature.notifications.NotificationPrefsScreen(
                viewModel = viewModel { container.makeNotificationPrefsViewModel() },
            )
        }
        3 -> SubScreen(title = "Retrait des crédits", onBack = { onSub(PROFILE_MENU) }) {
            val wdVm: WithdrawalViewModel = viewModel { container.makeWithdrawalViewModel() }
            WithdrawalScreen(viewModel = wdVm)
        }
        4 -> SubScreen(title = "Replays", onBack = { onSub(PROFILE_MENU) }) {
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
        5 -> SubScreen(title = "Boutique de cadeaux", onBack = { onSub(PROFILE_MENU) }) {
            val shopVm: GiftShopViewModel = viewModel { container.makeGiftShopViewModel() }
            GiftShopScreen(viewModel = shopVm, onOpenRecharge = { onSub(15) })
        }
        7 -> SubScreen(title = "Parrainage", onBack = { onSub(PROFILE_MENU) }) {
            val refVm: ReferralViewModel = viewModel { container.makeReferralViewModel() }
            ReferralScreen(viewModel = refVm)
        }
        8 -> SubScreen(title = "Abonnements", onBack = { onSub(PROFILE_MENU) }) {
            val subVm: SubscriptionViewModel = viewModel { container.makeSubscriptionViewModel() }
            SubscriptionScreen(viewModel = subVm)
        }
        11 -> SubScreen(title = "Sponsoring", onBack = { onSub(PROFILE_MENU) }) {
            val sponsorVm: SponsorViewModel = viewModel { container.makeSponsorViewModel() }
            SponsorScreen(viewModel = sponsorVm)
        }
        12 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.navDuels, onBack = { onSub(PROFILE_MENU) }) {
            // Manager (non-artiste) : gestion des duels (créer/organiser). Artiste : « demander un duel ».
            if (isManager && !isArtist) {
                com.dualmusic.feature.duel.ManagerDuelsScreen(viewModel = viewModel { container.makeManagerDuelsViewModel() })
            } else {
                val creatorVm: CreatorViewModel = viewModel { container.makeCreatorViewModel() }
                CreatorScreen(viewModel = creatorVm, initialTab = 0)
            }
        }
        23 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.navConcerts, onBack = { onSub(PROFILE_MENU) }) {
            val creatorVm: CreatorViewModel = viewModel { container.makeCreatorViewModel() }
            CreatorScreen(viewModel = creatorVm, initialTab = 1)
        }
        24 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.navLives, onBack = { onSub(PROFILE_MENU) }) {
            com.dualmusic.feature.live.MyLivesScreen(
                viewModel = viewModel { container.makeMyLivesViewModel() },
                onStartBroadcast = onStartBroadcast,
            )
        }
        25 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.menuMyCompetitions, onBack = { onSub(PROFILE_MENU) }) {
            // Manager (non-artiste) : gestion (créer/gérer). Artiste/fan : ses candidatures.
            if (isManager && !isArtist) {
                com.dualmusic.feature.competition.ManagerCompetitionsScreen(
                    viewModel = viewModel { container.makeManagerCompetitionsViewModel() },
                )
            } else {
                com.dualmusic.feature.competition.MyCompetitionsScreen(
                    viewModel = viewModel { container.makeMyCompetitionsViewModel() },
                )
            }
        }
        26 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.menuContent, onBack = { onSub(PROFILE_MENU) }) {
            com.dualmusic.feature.content.MyContentScreen(
                viewModel = viewModel { container.makeMyContentViewModel() },
            )
        }
        20 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.menuDashboard, onBack = { onSub(PROFILE_MENU) }) {
            DashboardScreen(viewModel = dashboardVm, onNavigate = { onSub(it) })
        }
        21 -> SubScreen(title = com.dualmusic.core.ui.i18n.LocalStrings.current.publicProfile, onBack = { onSub(PROFILE_MENU) }) {
            val publicVm: PublicProfileViewModel = viewModel { container.makePublicProfileViewModel() }
            PublicProfileEditScreen(viewModel = publicVm, isArtist = isArtist, onSaved = { onSub(PROFILE_MENU) })
        }
        14 -> SubScreen(title = "Devenir artiste", onBack = { onSub(PROFILE_MENU) }) {
            val roleVm: BecomeRoleViewModel = viewModel { container.makeBecomeRoleViewModel() }
            BecomeArtistScreen(viewModel = roleVm)
        }
        16 -> SubScreen(title = "Devenir manager", onBack = { onSub(PROFILE_MENU) }) {
            val roleVm: BecomeRoleViewModel = viewModel { container.makeBecomeRoleViewModel() }
            BecomeManagerScreen(viewModel = roleVm)
        }
        17 -> SubScreen(title = "Préférences", onBack = { onSub(PROFILE_MENU) }) {
            val mode by container.themeController.mode.collectAsStateWithLifecycle()
            val lang by container.languageController.language.collectAsStateWithLifecycle()
            val currency by container.currencyController.currency.collectAsStateWithLifecycle()
            var rates by remember { mutableStateOf<List<com.dualmusic.domain.settings.ExchangeRate>>(emptyList()) }
            var deletionAt by remember { mutableStateOf<String?>(null) }
            var refresh by remember { mutableIntStateOf(0) }
            val prefScope = rememberCoroutineScope()
            val uiPrefs by com.dualmusic.core.ui.prefs.UiPreferencesStore.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { rates = container.exchangeRates() }
            LaunchedEffect(Unit) { container.loadUiPreferences() }
            LaunchedEffect(refresh) { deletionAt = container.accountDeletionScheduledAt() }
            PreferencesScreen(
                currentMode = mode,
                onSelectMode = { container.themeController.set(it) },
                currentLanguage = lang,
                onSelectLanguage = { container.languageController.set(it) },
                currentCurrency = currency,
                currencyOptions = rates,
                onSelectCurrency = { container.currencyController.set(it) },
                uiPrefs = uiPrefs,
                onUiPrefsChange = { prefScope.launch { container.saveUiPreferences(it) } },
                deletionScheduledAt = deletionAt,
                onRequestDeletion = { prefScope.launch { runCatching { container.requestAccountDeletion() }; refresh++ } },
                onCancelDeletion = { prefScope.launch { runCatching { container.cancelAccountDeletion() }; refresh++ } },
            )
        }
        18 -> SubScreen(title = "Suivis", onBack = { onSub(PROFILE_MENU) }) {
            com.dualmusic.feature.artists.FollowedArtistsScreen(
                viewModel = viewModel { container.makeArtistsViewModel() },
            )
        }
        19 -> SubScreen(title = "Espace admin", onBack = { onSub(PROFILE_MENU) }) {
            AdminScreen(viewModel = viewModel { container.makeAdminViewModel() })
        }
        // Liste de menu en pleine page (retour → profil racine).
        PROFILE_MENU -> SubScreen(
            title = com.dualmusic.core.ui.i18n.LocalStrings.current.menuMySpace,
            onBack = { onSub(0) },
        ) {
            ProfileMenuPage(
                isArtist = isArtist,
                isManager = isManager,
                isPureFan = !canCreate,
                managerEnabled = managerEnabled,
                isAdmin = isAdmin,
                onNavigate = { onSub(it) },
                onSignOut = onSignOut,
            )
        }
        // Profil racine : fiche éditable (infos grisées + crayon + mot de passe replié).
        else -> ProfileScreen(viewModel = editProfileVm, onOpenMenu = { onSub(PROFILE_MENU) })
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

/**
 * Écran générique « section en construction » — utilisé pour les entrées de menu artiste
 * dont l'écran mobile dédié (Lives, Mes compétitions, Contenu) arrive dans un prochain lot.
 */
@Composable
private fun SectionComingSoon() {
    val s = com.dualmusic.core.ui.i18n.LocalStrings.current
    com.dualmusic.core.ui.components.DMEmptyState(
        title = s.comingSoon,
        subtitle = s.comingSoonHint,
        modifier = Modifier.fillMaxSize(),
    )
}

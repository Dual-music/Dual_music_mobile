package com.dualmusic.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.model.Duel
import com.dualmusic.domain.model.Live
import com.dualmusic.feature.auth.AuthRepository
import com.dualmusic.feature.auth.AuthState
import com.dualmusic.feature.auth.AuthViewModel
import com.dualmusic.feature.auth.SignInScreen
import com.dualmusic.feature.duel.DuelRepository
import com.dualmusic.feature.duel.DuelRoomScreen
import com.dualmusic.feature.duel.DuelViewModel
import com.dualmusic.feature.duel.DuelsListScreen
import com.dualmusic.feature.duel.DuelsListViewModel
import com.dualmusic.feature.feed.FeedRepository
import com.dualmusic.feature.feed.FeedScreen
import com.dualmusic.feature.feed.FeedViewModel
import com.dualmusic.feature.live.LiveRepository
import com.dualmusic.feature.live.LiveViewModel
import com.dualmusic.feature.wallet.WalletRepository
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

    // --- Lot duels ---
    private val duelRepository = DuelRepository(api)

    /** Nouveau ViewModel de feed (liste des lives + prefetch des tokens LiveKit). */
    fun makeFeedViewModel(): FeedViewModel = FeedViewModel(feedRepository, tokenService)

    /** Nouveau ViewModel de portefeuille (solde + historiques). */
    fun makeWalletViewModel(): WalletViewModel = WalletViewModel(walletRepository)

    /** Nouveau ViewModel du catalogue de duels. */
    fun makeDuelsListViewModel(): DuelsListViewModel = DuelsListViewModel(duelRepository)

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
                    is AuthState.SignedIn -> MainShell(container)
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
private fun MainShell(container: AppContainer) {
    var tab by remember { mutableIntStateOf(0) }
    // Duel actuellement ouvert (null = on affiche le catalogue).
    var openDuel by remember { mutableStateOf<Duel?>(null) }
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
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Portefeuille") },
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
                else -> {
                    val walletVm: WalletViewModel = viewModel { container.makeWalletViewModel() }
                    WalletScreen(viewModel = walletVm)
                }
            }
        }
    }
}

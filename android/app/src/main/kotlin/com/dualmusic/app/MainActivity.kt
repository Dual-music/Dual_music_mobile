package com.dualmusic.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dualmusic.core.auth.AuthRefresher
import com.dualmusic.core.auth.EncryptedTokenStore
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.feature.auth.AuthRepository
import com.dualmusic.feature.auth.AuthState
import com.dualmusic.feature.auth.AuthViewModel
import com.dualmusic.feature.auth.SignInScreen
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
 *
 * Tant que le backend n'est pas joignable, l'écran s'affiche mais « Se connecter » échoue
 * proprement (message d'erreur) — c'est normal pour ce jalon.
 */
private const val API_BASE_URL = "http://10.0.2.2:4000"

/**
 * Conteneur d'injection minimal (sans framework DI, pour rester simple et lisible).
 * Construit le graphe : stockage sécurisé → refresh → client HTTP → repository d'auth.
 * Un seul exemplaire, porté par l'[MainActivity].
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tokenStore = EncryptedTokenStore(context.applicationContext)
    private val refresher = AuthRefresher(API_BASE_URL)
    private val api = ApiClient(API_BASE_URL, tokenStore, refresher, appScope)

    /** Repository d'authentification prêt à l'emploi pour les ViewModels. */
    val authRepository = AuthRepository(api, tokenStore, API_BASE_URL)
}

/**
 * Activité principale. Affiche l'écran de connexion ; une fois connecté, un écran de
 * confirmation minimal (les vrais écrans — feed, live — arriveront aux lots suivants).
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(this)
        enableEdgeToEdge()
        setContent {
            DualMusicTheme {
                // ViewModel retenu, alimenté par le repository du conteneur.
                val vm: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }

                // Réhydrate la session au démarrage (jeton en stockage sécurisé).
                LaunchedEffect(Unit) { vm.bootstrap() }

                val authState by vm.authState.collectAsStateWithLifecycle()
                when (val state = authState) {
                    is AuthState.SignedIn -> SignedInScreen(email = state.user.email, onSignOut = vm::signOut)
                    else -> SignInScreen(viewModel = vm, onGoogle = { /* TODO(lot suivant): OAuth Google + deeplink */ })
                }
            }
        }
    }
}

/** Écran affiché une fois connecté (placeholder du prochain lot : feed). */
@Composable
private fun SignedInScreen(email: String, onSignOut: () -> Unit) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        Text("✅ Connecté", color = colors.foreground)
        Text(email, color = colors.mutedForeground)
        DMButton("Se déconnecter", style = DMButtonStyle.OUTLINE, onClick = onSignOut)
    }
}

package com.dualmusic.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMLogo
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.model.UserRole
import com.dualmusic.domain.user.UserStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État de l'écran profil. */
data class ProfileUiState(
    val me: MeResponse? = null,
    val stats: UserStats = UserStats(),
    val isLoading: Boolean = false,
)

/**
 * ViewModel du profil : identité + statistiques.
 *
 * @param repository lectures REST du profil.
 */
class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Charge le profil + les statistiques (un échec de stats n'empêche pas l'affichage). */
    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val me = runCatching { repository.me() }.getOrNull()
            val stats = runCatching { repository.stats() }.getOrDefault(UserStats())
            _uiState.value = ProfileUiState(me = me, stats = stats, isLoading = false)
        }
    }
}

/**
 * Écran profil : identité, rôles, statistiques, accès portefeuille + déconnexion.
 *
 * @param viewModel source d'état.
 * @param onOpenWallet ouvre le portefeuille.
 * @param onOpenWithdrawal ouvre le flux de retrait.
 * @param onOpenNotifications ouvre le centre de notifications.
 * @param onSignOut déconnexion (efface la session).
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenWallet: () -> Unit,
    onOpenWithdrawal: () -> Unit,
    onOpenReplays: () -> Unit,
    onOpenGiftShop: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenReferral: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        DMLogo(height = 48.dp)

        // Identité.
        DMCard {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                Text(
                    ui.me?.profile?.displayName ?: ui.me?.user?.email ?: "Profil",
                    color = colors.foreground,
                )
                ui.me?.user?.email?.let { Text(it, color = colors.mutedForeground) }
                if (!ui.me?.roles.isNullOrEmpty()) {
                    Text(ui.me!!.roles.joinToString(" · ") { roleLabel(it) }, color = colors.accent)
                }
            }
        }

        // Statistiques.
        StatSection("Artiste", listOf(
            "Votes reçus" to ui.stats.artistStats.totalVotes.toInt().toString(),
            "Duels gagnés" to "${ui.stats.artistStats.wonDuels}/${ui.stats.artistStats.totalDuels}",
            "Cadeaux reçus" to ui.stats.artistStats.totalGifts.toString(),
        ))
        StatSection("Fan", listOf(
            "Votes émis" to ui.stats.fanStats.totalVotesCast.toInt().toString(),
            "Cadeaux envoyés" to ui.stats.fanStats.totalGiftsSent.toString(),
            "Billets" to ui.stats.fanStats.totalTickets.toString(),
        ))

        DMButton("Mon portefeuille", onClick = onOpenWallet)
        DMButton("Retirer mes crédits", style = DMButtonStyle.SECONDARY, onClick = onOpenWithdrawal)
        DMButton("Replays", style = DMButtonStyle.SECONDARY, onClick = onOpenReplays)
        DMButton("Boutique de cadeaux", style = DMButtonStyle.SECONDARY, onClick = onOpenGiftShop)
        DMButton("Découvrir (lifestyle & blog)", style = DMButtonStyle.SECONDARY, onClick = onOpenContent)
        DMButton("Classements", style = DMButtonStyle.SECONDARY, onClick = onOpenLeaderboard)
        DMButton("Parrainage", style = DMButtonStyle.SECONDARY, onClick = onOpenReferral)
        DMButton("Abonnements", style = DMButtonStyle.SECONDARY, onClick = onOpenSubscription)
        DMButton("Notifications", style = DMButtonStyle.SECONDARY, onClick = onOpenNotifications)
        DMButton("Se déconnecter", style = DMButtonStyle.OUTLINE, onClick = onSignOut)
    }
}

/** Bloc de statistiques (titre + lignes clé/valeur). */
@Composable
private fun StatSection(title: String, rows: List<Pair<String, String>>) {
    val colors = DualMusicTheme.colors
    DMCard {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
            Text(title, color = colors.primaryGlow)
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, color = colors.mutedForeground)
                    Text(value, color = colors.foreground)
                }
            }
        }
    }
}

/** Libellé lisible d'un rôle. */
private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.FAN -> "Fan"
    UserRole.ARTIST -> "Artiste"
    UserRole.MANAGER -> "Manager"
    UserRole.MODERATOR -> "Modérateur"
    UserRole.ADMIN -> "Admin"
}

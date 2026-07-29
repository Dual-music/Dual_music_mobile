package com.dualmusic.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.model.UserRole
import com.dualmusic.domain.user.UserStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État du tableau de bord. */
data class DashboardUiState(
    val me: MeResponse? = null,
    val stats: UserStats = UserStats(),
    val isLoading: Boolean = false,
)

/**
 * ViewModel du tableau de bord : identité + statistiques agrégées (artiste/manager/fan).
 *
 * @param repository lectures REST du profil.
 */
class DashboardViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Charge le profil + les statistiques (un échec de stats n'empêche pas l'affichage). */
    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // `/auth/me` porte les RÔLES (gating du menu artiste/manager). Un échec transitoire
            // (réseau instable en tethering) ne doit PAS retomber silencieusement sur « fan » :
            // on réessaie, et on conserve les rôles déjà chargés si l'appel échoue.
            var me: MeResponse? = null
            for (attempt in 0 until 3) {
                me = runCatching { repository.me() }.getOrNull()
                if (me != null) break
                if (attempt < 2) kotlinx.coroutines.delay(700)
            }
            val stats = runCatching { repository.stats() }.getOrDefault(UserStats())
            _uiState.value = DashboardUiState(
                me = me ?: _uiState.value.me,
                stats = stats,
                isLoading = false,
            )
        }
    }
}

/**
 * **Tableau de bord** : statistiques par rôle présentées en cartes illustrées (icônes + valeurs),
 * un **anneau de progression** pour le taux de victoire (artiste), et des **raccourcis** vers les
 * sections clés. Chaque profil ne voit QUE ses propres recaps (fan / artiste / manager), comme le web.
 *
 * @param viewModel source d'état.
 * @param onNavigate ouvre un sous-écran du profil (indices alignés sur [com.dualmusic.app]).
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onNavigate: (Int) -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    val roles = ui.me?.roles ?: emptyList()
    val isArtist = UserRole.ARTIST in roles
    val isManager = UserRole.MANAGER in roles
    val isAdmin = UserRole.ADMIN in roles
    val isPureFan = !isArtist && !isManager && !isAdmin
    val canCreate = isArtist || isManager || isAdmin

    if (ui.isLoading && ui.me == null) {
        DMLoadingBox(Modifier.fillMaxSize())
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        // Espace Artiste : votes/cadeaux reçus + anneau taux de victoire.
        if (isArtist) {
            val a = ui.stats.artistStats
            StatSection(s.statArtistSpace) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WinRateRing(won = a.wonDuels, total = a.totalDuels, label = s.statWinRate)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                    ) {
                        StatTile(Icons.Filled.HowToVote, a.totalVotes.toInt().toString(), s.statVotesReceived)
                        StatTile(Icons.Filled.CardGiftcard, a.totalGifts.toString(), s.statGiftsReceived)
                        StatTile(Icons.Filled.SportsMartialArts, "${a.wonDuels}/${a.totalDuels}", s.statDuelsWon)
                    }
                }
            }
        }

        // Espace Manager.
        if (isManager) {
            val m = ui.stats.managerStats
            StatSection(s.statManagerSpace) {
                StatGrid(
                    Triple(Icons.Filled.SportsMartialArts, m.totalDuelsManaged.toString(), s.statDuelsManaged),
                    Triple(Icons.Filled.Star, m.activeDuels.toString(), s.statActiveDuels),
                    Triple(Icons.Filled.CardGiftcard, m.totalGiftsReceived.toString(), s.statGiftsReceived),
                )
            }
        }

        // Espace Fan.
        if (isPureFan) {
            val f = ui.stats.fanStats
            StatSection(s.statFanSpace) {
                StatGrid(
                    Triple(Icons.Filled.HowToVote, f.totalVotesCast.toInt().toString(), s.statVotesCast),
                    Triple(Icons.Filled.CardGiftcard, f.totalGiftsSent.toString(), s.statGiftsSent),
                    Triple(Icons.Filled.ConfirmationNumber, f.totalTickets.toString(), s.statTickets),
                )
            }
        }

        // Raccourcis vers les sections clés (dépend du rôle).
        StatSection(s.quickActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                QuickAction(Icons.Filled.AccountBalanceWallet, s.menuTransactions, Modifier.weight(1f)) { onNavigate(1) }
                QuickAction(Icons.Filled.Favorite, s.menuFollowing, Modifier.weight(1f)) { onNavigate(18) }
                if (canCreate) {
                    QuickAction(Icons.Filled.EmojiEvents, s.menuWithdraw, Modifier.weight(1f)) { onNavigate(3) }
                } else {
                    QuickAction(Icons.Filled.CardGiftcard, s.menuReferral, Modifier.weight(1f)) { onNavigate(7) }
                }
            }
            if (canCreate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                ) {
                    QuickAction(Icons.Filled.Star, s.menuCreatorSpace, Modifier.weight(1f)) { onNavigate(12) }
                    QuickAction(Icons.Filled.PlayArrow, s.menuReplays, Modifier.weight(1f)) { onNavigate(4) }
                    QuickAction(Icons.Filled.Redeem, s.menuGiftShop, Modifier.weight(1f)) { onNavigate(5) }
                }
            }
        }
    }
}

/** Section titrée (carte) qui héberge le contenu de stats. */
@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
            Text(title, color = colors.primaryGlow, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Trois tuiles de stats alignées sur une rangée (icône + valeur + libellé). */
@Composable
private fun StatGrid(a: Triple<ImageVector, String, String>, b: Triple<ImageVector, String, String>, c: Triple<ImageVector, String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
    ) {
        StatTile(a.first, a.second, a.third, Modifier.weight(1f))
        StatTile(b.first, b.second, b.third, Modifier.weight(1f))
        StatTile(c.first, c.second, c.third, Modifier.weight(1f))
    }
}

/** Tuile de statistique : pastille d'icône dégradée + valeur en gros + libellé. */
@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DualMusicTheme.gradients.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.foreground, modifier = Modifier.size(22.dp))
        }
        Text(value, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = colors.mutedForeground, fontSize = 12.sp)
    }
}

/** Anneau de progression (Canvas) affichant le taux de victoire won/total. */
@Composable
private fun WinRateRing(won: Int, total: Int, label: String) {
    val colors = DualMusicTheme.colors
    val ratio = if (total > 0) (won.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val track = colors.border
    val progress = colors.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(96.dp).aspectRatio(1f)) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = progress,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text("${(ratio * 100).toInt()}%", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Text(label, color = colors.mutedForeground, fontSize = 12.sp)
    }
}

/** Bouton-raccourci : petite carte icône + libellé, cliquable. */
@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(DualMusicTheme.radii.md))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(vertical = DualMusicTheme.spacing.md, horizontal = DualMusicTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Text(label, color = colors.foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

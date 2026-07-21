package com.dualmusic.feature.profile

import androidx.compose.animation.AnimatedVisibility
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.model.UserRole
import com.dualmusic.domain.user.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
 * Écran profil : identité (photo, nom, email, rôle), statistiques, et un **menu déroulant**
 * (icône hamburger) donnant accès aux sections — filtrées selon le rôle, comme le web mobile.
 *
 * @param viewModel source d'état.
 * @param onOpenWallet ouvre le portefeuille.
 * @param onOpenWithdrawal ouvre le flux de retrait (artiste/manager).
 * @param onOpenNotifications ouvre le centre de notifications.
 * @param onSignOut déconnexion.
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
    onOpenArtists: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenCreator: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    val roles = ui.me?.roles ?: emptyList()
    val isArtist = UserRole.ARTIST in roles
    val isManager = UserRole.MANAGER in roles
    val isAdmin = UserRole.ADMIN in roles
    // Outils créateur + retrait de crédits : réservés artiste/manager/admin (comme le web).
    val canCreate = isArtist || isManager || isAdmin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        // Barre : titre + icône menu (déroule/replie la liste des sections).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mon profil", color = colors.foreground, fontWeight = FontWeight.Bold)
            IconButton(onClick = { menuOpen = !menuOpen }) {
                Icon(
                    imageVector = if (menuOpen) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (menuOpen) "Fermer le menu" else "Ouvrir le menu",
                    tint = colors.foreground,
                )
            }
        }

        // Héro : photo + nom + email + rôle.
        ProfileHero(ui.me, primaryRoleLabel(roles))

        // Statistiques par rôle — comme sur le web, chaque profil ne voit QUE ses propres
        // recaps : un fan ne voit pas les stats artiste/manager, et inversement.
        val roles = ui.me?.roles ?: emptyList()
        val isArtist = UserRole.ARTIST in roles
        val isManager = UserRole.MANAGER in roles
        val isAdmin = UserRole.ADMIN in roles
        val isPureFan = !isArtist && !isManager && !isAdmin

        if (isArtist) {
            StatSection("Espace Artiste", listOf(
                "Votes reçus" to ui.stats.artistStats.totalVotes.toInt().toString(),
                "Duels gagnés" to "${ui.stats.artistStats.wonDuels}/${ui.stats.artistStats.totalDuels}",
                "Cadeaux reçus" to ui.stats.artistStats.totalGifts.toString(),
            ))
        }
        if (isManager) {
            StatSection("Espace Manager", listOf(
                "Duels gérés" to ui.stats.managerStats.totalDuelsManaged.toString(),
                "En cours" to ui.stats.managerStats.activeDuels.toString(),
                "Cadeaux reçus" to ui.stats.managerStats.totalGiftsReceived.toString(),
            ))
        }
        if (isPureFan) {
            StatSection("Espace Fan", listOf(
                "Votes effectués" to ui.stats.fanStats.totalVotesCast.toInt().toString(),
                "Cadeaux envoyés" to ui.stats.fanStats.totalGiftsSent.toString(),
                "Billets achetés" to ui.stats.fanStats.totalTickets.toString(),
            ))
        }

        // Action principale toujours visible.
        DMButton("Mon portefeuille", onClick = onOpenWallet)

        // Menu déroulant (sections), filtré par rôle.
        AnimatedVisibility(visible = menuOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                if (canCreate) {
                    DMButton("Espace créateur", style = DMButtonStyle.SECONDARY, onClick = onOpenCreator)
                    DMButton("Retirer mes crédits", style = DMButtonStyle.SECONDARY, onClick = onOpenWithdrawal)
                }
                DMButton("Sponsoring", style = DMButtonStyle.SECONDARY, onClick = onOpenSponsor)
                DMButton("Replays", style = DMButtonStyle.SECONDARY, onClick = onOpenReplays)
                DMButton("Boutique de cadeaux", style = DMButtonStyle.SECONDARY, onClick = onOpenGiftShop)
                DMButton("Découvrir (lifestyle & blog)", style = DMButtonStyle.SECONDARY, onClick = onOpenContent)
                DMButton("Artistes", style = DMButtonStyle.SECONDARY, onClick = onOpenArtists)
                DMButton("Classements", style = DMButtonStyle.SECONDARY, onClick = onOpenLeaderboard)
                DMButton("Parrainage", style = DMButtonStyle.SECONDARY, onClick = onOpenReferral)
                DMButton("Abonnements", style = DMButtonStyle.SECONDARY, onClick = onOpenSubscription)
                DMButton("Notifications", style = DMButtonStyle.SECONDARY, onClick = onOpenNotifications)
                DMButton("Se déconnecter", style = DMButtonStyle.OUTLINE, onClick = onSignOut)
            }
        }
    }
}

/** Bandeau d'identité : avatar (photo ou initiale), nom, email, rôle principal. */
@Composable
private fun ProfileHero(me: MeResponse?, roleLabel: String) {
    val colors = DualMusicTheme.colors
    val displayName = me?.profile?.displayName ?: me?.user?.email ?: "Profil"
    val avatarUrl = me?.profile?.avatarUrl

    DMCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar : photo distante si disponible, sinon initiale sur pastille dégradée.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DualMusicTheme.gradients.primary),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = rememberRemoteImage(avatarUrl)
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = "Photo de profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        displayName.take(1).uppercase(),
                        color = colors.foreground,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.xs)) {
                Text(displayName, color = colors.foreground, fontWeight = FontWeight.Bold)
                me?.user?.email?.let { Text(it, color = colors.mutedForeground) }
                Text(roleLabel, color = colors.accent, fontWeight = FontWeight.Bold)
            }
        }
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

/**
 * Charge une image distante (avatar) en [ImageBitmap], sans dépendance externe.
 *
 * Téléchargement + décodage sur [Dispatchers.IO] via [produceState] (re-déclenché si l'URL
 * change). Renvoie `null` tant que l'image n'est pas prête ou en cas d'échec (repli initiale).
 * Suffisant pour un unique avatar ; pour du chargement massif, préférer une lib de cache.
 *
 * @param url URL publique de l'image (peut être nulle/vide).
 */
@Composable
private fun rememberRemoteImage(url: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return image
}

/** Libellé du rôle principal (priorité admin > artiste > manager > modérateur > fan). */
private fun primaryRoleLabel(roles: List<UserRole>): String = when {
    UserRole.ADMIN in roles -> "Admin"
    UserRole.ARTIST in roles -> "Artiste"
    UserRole.MANAGER in roles -> "Manager"
    UserRole.MODERATOR in roles -> "Modérateur"
    else -> "Fan"
}

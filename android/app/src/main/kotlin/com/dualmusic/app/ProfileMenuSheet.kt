package com.dualmusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Menu du profil affiché en **pleine page** (comme le menu du web réduit en mobile).
 *
 * Chaque item ouvre sa page (via [onNavigate] avec le n° de sous-écran) ; le retour depuis
 * un contenu revient à cette liste, et le retour depuis cette liste revient au profil (géré
 * par l'écran parent). « Devenir Manager » n'apparaît que si l'admin a **ouvert** les
 * candidatures manager ([managerEnabled]) — sinon l'admin désigne directement les managers.
 *
 * @param managerEnabled affiche l'entrée « Devenir Manager » si vrai.
 * @param onNavigate ouvre le sous-écran ciblé.
 * @param onSignOut déconnexion.
 */
@Composable
fun ProfileMenuPage(
    isPureFan: Boolean,
    canCreate: Boolean,
    managerEnabled: Boolean,
    isAdmin: Boolean = false,
    showPublicProfile: Boolean = false,
    onNavigate: (Int) -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(vertical = DualMusicTheme.spacing.md),
    ) {
        MenuRow(Icons.Filled.Dashboard, s.menuDashboard) { onNavigate(20) }
        MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
        MenuRow(Icons.Filled.CardMembership, s.menuSubscription) { onNavigate(8) }
        MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
        MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
        MenuRow(Icons.Filled.Campaign, s.menuSponsor) { onNavigate(11) }
        // Réservé aux fans : candidatures de rôle (manager selon gating admin).
        if (isPureFan) {
            MenuRow(Icons.Filled.PersonAdd, s.menuBecomeArtist) { onNavigate(14) }
            if (managerEnabled) MenuRow(Icons.Filled.Business, s.menuBecomeManager) { onNavigate(16) }
        }
        // Réservé artiste/manager : édition du profil public (nom de scène, bio, liens sociaux).
        if (showPublicProfile) MenuRow(Icons.Filled.Public, s.publicProfile) { onNavigate(21) }
        // Réservé artiste/manager/admin : outils créateur + revenus.
        if (canCreate) {
            MenuRow(Icons.Filled.Star, s.menuCreatorSpace) { onNavigate(12) }
            MenuRow(Icons.Filled.AccountBalanceWallet, s.menuWithdraw) { onNavigate(3) }
            MenuRow(Icons.Filled.PlayArrow, s.menuReplays) { onNavigate(4) }
            MenuRow(Icons.Filled.Redeem, s.menuGiftShop) { onNavigate(5) }
        }
        // Réservé admin : réglages plateforme + assignation de rôle.
        if (isAdmin) MenuRow(Icons.Filled.Lock, "Espace admin") { onNavigate(19) }
        MenuRow(Icons.Filled.Tune, s.preferences) { onNavigate(17) }
        MenuRow(Icons.Filled.Notifications, s.notifications) { onNavigate(2) }
        HorizontalDivider(modifier = Modifier.padding(vertical = DualMusicTheme.spacing.xs))
        MenuRow(Icons.Filled.Logout, s.menuSignOut, tint = colors.destructive, onClick = onSignOut)
    }
}

/** Ligne d'item du menu : icône + libellé, cliquable. */
@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    val color = tint ?: colors.foreground
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DualMusicTheme.spacing.lg, vertical = DualMusicTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(
            label,
            color = color,
            modifier = Modifier.padding(start = DualMusicTheme.spacing.md),
        )
    }
}

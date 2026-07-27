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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
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
 * Menu du profil affiché en **pleine page**, aligné sur la sidebar profil du **web**
 * (`useProfileTabs`) : l'ordre et les libellés des entrées dépendent du rôle
 * (fan / artiste / manager), afin d'avoir exactement les mêmes sections que sur le web.
 *
 * Chaque item ouvre sa page (via [onNavigate] avec le n° de sous-écran) ; le retour depuis
 * un contenu revient à cette liste, et le retour depuis cette liste revient au profil (géré
 * par l'écran parent). « Devenir Manager » (fan) n'apparaît que si l'admin a **ouvert** les
 * candidatures manager ([managerEnabled]).
 *
 * @param isArtist rôle artiste (menu artiste).
 * @param isManager rôle manager (menu manager).
 * @param isPureFan aucun rôle créateur (menu fan).
 * @param managerEnabled affiche « Devenir Manager » (fan) si vrai.
 * @param isAdmin ajoute l'entrée « Espace admin ».
 * @param onNavigate ouvre le sous-écran ciblé.
 * @param onSignOut déconnexion.
 */
@Composable
fun ProfileMenuPage(
    isArtist: Boolean,
    isManager: Boolean,
    isPureFan: Boolean,
    managerEnabled: Boolean,
    isAdmin: Boolean = false,
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
        when {
            // ----- Menu ARTISTE (même ordre que la sidebar web) -----
            isArtist -> {
                MenuRow(Icons.Filled.Dashboard, s.menuDashboard) { onNavigate(20) }
                MenuRow(Icons.Filled.Mic, s.menuArtistProfile) { onNavigate(21) }
                MenuRow(Icons.Filled.SportsMartialArts, s.navDuels) { onNavigate(12) }
                MenuRow(Icons.Filled.MusicNote, s.navConcerts) { onNavigate(23) }
                MenuRow(Icons.Filled.Videocam, s.navLives) { onNavigate(24) }
                MenuRow(Icons.Filled.EmojiEvents, s.menuMyCompetitions) { onNavigate(25) }
                MenuRow(Icons.Filled.Movie, s.menuContent) { onNavigate(26) }
                MenuRow(Icons.Filled.AccountBalanceWallet, s.menuEarnings) { onNavigate(3) }
                MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
                MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
                MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
            }
            // ----- Menu MANAGER -----
            isManager -> {
                MenuRow(Icons.Filled.Dashboard, s.menuDashboard) { onNavigate(20) }
                MenuRow(Icons.Filled.SportsMartialArts, s.navDuels) { onNavigate(12) }
                MenuRow(Icons.Filled.EmojiEvents, s.menuMyCompetitions) { onNavigate(25) }
                MenuRow(Icons.Filled.Mic, s.menuArtistProfile) { onNavigate(21) }
                MenuRow(Icons.Filled.AccountBalanceWallet, s.menuEarnings) { onNavigate(3) }
                MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
                MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
                MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
            }
            // ----- Menu FAN -----
            else -> {
                MenuRow(Icons.Filled.Dashboard, s.menuDashboard) { onNavigate(20) }
                MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
                MenuRow(Icons.Filled.CardMembership, s.menuSubscription) { onNavigate(8) }
                MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
                MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
                MenuRow(Icons.Filled.Campaign, s.menuSponsor) { onNavigate(11) }
                if (isPureFan) {
                    MenuRow(Icons.Filled.PersonAdd, s.menuBecomeArtist) { onNavigate(14) }
                    if (managerEnabled) MenuRow(Icons.Filled.Business, s.menuBecomeManager) { onNavigate(16) }
                }
            }
        }

        // ----- Commun à tous les rôles (fin de liste, comme le web) -----
        MenuRow(Icons.Filled.Tune, s.preferences) { onNavigate(17) }
        MenuRow(Icons.Filled.Notifications, s.notifications) { onNavigate(2) }
        if (isAdmin) MenuRow(Icons.Filled.Lock, "Espace admin") { onNavigate(19) }
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

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Invitations de duel reçues, en attente d'acceptation — badge sur l'item "Duels" (artiste). */
    pendingDuelInvites: Int = 0,
    /** Candidats en attente de revue (toutes compétitions gérées) — badge sur l'item "Compétitions" (manager). */
    pendingCandidates: Int = 0,
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
                MenuRow(Icons.Filled.SportsMartialArts, s.navDuels, badge = pendingDuelInvites) { onNavigate(12) }
                MenuRow(Icons.Filled.MusicNote, s.navConcerts) { onNavigate(23) }
                MenuRow(Icons.Filled.Videocam, s.navLives) { onNavigate(24) }
                // Badge de revue manager même ici : un artiste AUSSI manager entre dans cette
                // branche (isArtist prioritaire ci-dessus) mais l'entrée ouvre bien
                // ManagerCompetitionsScreen (voir ProfileSection, sub=25) — pendingCandidates
                // reste à 0 pour un non-manager (chargement gated sur isManager).
                MenuRow(Icons.Filled.EmojiEvents, s.menuMyCompetitions, badge = pendingCandidates) { onNavigate(25) }
                MenuRow(Icons.Filled.Movie, s.menuContent) { onNavigate(26) }
                MenuRow(Icons.Filled.AccountBalanceWallet, s.menuEarnings) { onNavigate(3) }
                MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
                MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
                MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
                MenuRow(Icons.Filled.Campaign, s.menuSponsor) { onNavigate(11) }
            }
            // ----- Menu MANAGER -----
            isManager -> {
                MenuRow(Icons.Filled.Dashboard, s.menuDashboard) { onNavigate(20) }
                MenuRow(Icons.Filled.SportsMartialArts, s.navDuels) { onNavigate(12) }
                MenuRow(Icons.Filled.EmojiEvents, s.menuMyCompetitions, badge = pendingCandidates) { onNavigate(25) }
                MenuRow(Icons.Filled.Mic, s.menuArtistProfile) { onNavigate(21) }
                // Gestion des replays de duels/compétitions gérés (prix, publication,
                // téléchargement, remplacement) — même écran que côté artiste, filtré sur les
                // replays dont ce manager est propriétaire (créateur).
                MenuRow(Icons.Filled.Movie, s.menuReplays) { onNavigate(26) }
                MenuRow(Icons.Filled.AccountBalanceWallet, s.menuEarnings) { onNavigate(3) }
                MenuRow(Icons.Filled.Favorite, s.menuFollowing) { onNavigate(18) }
                MenuRow(Icons.Filled.CreditCard, s.menuTransactions) { onNavigate(1) }
                MenuRow(Icons.Filled.CardGiftcard, s.menuReferral) { onNavigate(7) }
                MenuRow(Icons.Filled.Campaign, s.menuSponsor) { onNavigate(11) }
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

/**
 * Ligne d'item du menu : icône + libellé, cliquable.
 * @param badge compte affiché en pastille à droite (ex. invitations en attente) — masqué si ≤ 0.
 *   Chargé par l'écran appelant au montage (pas seulement à l'ouverture de cet item), pour être
 *   visible immédiatement.
 */
@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color? = null, badge: Int = 0, onClick: () -> Unit) {
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
            modifier = Modifier.padding(start = DualMusicTheme.spacing.md).weight(1f),
        )
        if (badge > 0) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .background(colors.destructive, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}

package com.dualmusic.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Menu du profil en **pop-up** (feuille modale) — réplique le menu du web réduit en mobile.
 *
 * Chaque item navigue vers sa page (via [onNavigate] avec le n° de sous-écran) puis ferme la
 * feuille. « Devenir Manager » n'apparaît que si l'admin a **ouvert** les candidatures
 * manager ([managerEnabled]) — sinon l'admin désigne directement les managers.
 *
 * @param managerEnabled affiche l'entrée « Devenir Manager » si vrai.
 * @param onNavigate ouvre le sous-écran ciblé.
 * @param onSignOut déconnexion.
 * @param onDismiss ferme la feuille.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    isPureFan: Boolean,
    canCreate: Boolean,
    managerEnabled: Boolean,
    onNavigate: (Int) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.card) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Mon espace",
                color = colors.mutedForeground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = DualMusicTheme.spacing.lg, vertical = DualMusicTheme.spacing.sm),
            )
            MenuRow(Icons.Filled.Dashboard, "Dashboard") { onNavigate(0) }
            MenuRow(Icons.Filled.Favorite, "Suivis") { onNavigate(18) }
            MenuRow(Icons.Filled.CardMembership, "Abonnement") { onNavigate(8) }
            MenuRow(Icons.Filled.CreditCard, "Mes transactions") { onNavigate(1) }
            MenuRow(Icons.Filled.CardGiftcard, "Programme de Parrainage") { onNavigate(7) }
            MenuRow(Icons.Filled.Campaign, "Sponsor") { onNavigate(11) }
            // Réservé aux fans : candidatures de rôle (manager selon gating admin).
            if (isPureFan) {
                MenuRow(Icons.Filled.PersonAdd, "Devenir Artiste") { onNavigate(14) }
                if (managerEnabled) MenuRow(Icons.Filled.Business, "Devenir Manager") { onNavigate(16) }
            }
            // Réservé artiste/manager/admin : outils créateur + revenus.
            if (canCreate) {
                MenuRow(Icons.Filled.Star, "Espace créateur") { onNavigate(12) }
                MenuRow(Icons.Filled.AccountBalanceWallet, "Retirer mes crédits") { onNavigate(3) }
                MenuRow(Icons.Filled.PlayArrow, "Replays") { onNavigate(4) }
                MenuRow(Icons.Filled.Redeem, "Boutique de cadeaux") { onNavigate(5) }
            }
            MenuRow(Icons.Filled.Edit, "Modifier le profil") { onNavigate(13) }
            MenuRow(Icons.Filled.Tune, "Préférences") { onNavigate(17) }
            MenuRow(Icons.Filled.Notifications, "Notifs") { onNavigate(2) }
            HorizontalDivider(modifier = Modifier.padding(vertical = DualMusicTheme.spacing.xs))
            MenuRow(Icons.Filled.Logout, "Déconnexion", tint = colors.destructive, onClick = onSignOut)
        }
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

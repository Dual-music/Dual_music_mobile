package com.dualmusic.core.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * En-tête de DIRECT partagé par les 3 types d'événement (duel / concert / compétition) —
 * mêmes icônes et même disposition que le web (`MobileStreamOverlay`) :
 *  - à gauche : badge **LIVE** rouge + label de l'événement, compteur de **spectateurs**
 *    (icône œil) et compteur de **likes** (icône cœur) ;
 *  - à droite : **partager**, **signaler** (optionnel), **fermer**.
 *
 * Icônes Material (parité visuelle avec les icônes lucide du web) au lieu des emojis.
 *
 * @param eventLabel libellé court affiché à côté du badge LIVE (ex. "DUEL", "CONCERT").
 * @param viewerCount nombre de spectateurs en direct.
 * @param likes total de likes (masqué si 0).
 * @param onShare partage natif (null → bouton masqué).
 * @param onReport signalement (null → bouton masqué).
 * @param onClose fermeture du direct (null → bouton masqué).
 */
@Composable
fun LiveHeader(
    eventLabel: String,
    viewerCount: Int,
    likes: Int,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    // Optionnels (parité web) : nom du diffuseur + état micro/caméra + bouton participants.
    mediaLabel: String? = null,
    micOn: Boolean? = null,
    camOn: Boolean? = null,
    onParticipants: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Badge LIVE rouge (point + texte).
            Row(
                modifier = Modifier
                    .background(Color(0xFFDC2626), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            if (eventLabel.isNotBlank()) {
                Box(
                    Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(eventLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            StatPill(icon = Icons.Filled.Visibility, text = compact(viewerCount))
            if (likes > 0) StatPill(icon = Icons.Filled.Favorite, text = compact(likes), tint = Color(0xFFFF4D6D))
            // Diffuseur : nom + état micro/caméra (vert = actif, rouge = coupé) — parité web.
            if (!mediaLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(mediaLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    micOn?.let {
                        Icon(if (it) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = if (it) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(14.dp))
                    }
                    camOn?.let {
                        Icon(if (it) Icons.Filled.Videocam else Icons.Filled.VideocamOff, contentDescription = null, tint = if (it) Color(0xFF22C55E) else Color(0xFFFF4D6D), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            onShare?.let { IconPill(icon = Icons.Filled.Share, contentDescription = "Partager", onClick = it) }
            onParticipants?.let { IconPill(icon = Icons.Filled.Groups, contentDescription = "Participants", onClick = it) }
            onReport?.let { IconPill(icon = Icons.Outlined.Flag, contentDescription = "Signaler", onClick = it) }
            onClose?.let { IconPill(icon = Icons.Filled.Close, contentDescription = "Fermer", onClick = it) }
        }
    }
}

/** Pastille translucide « icône + valeur » (spectateurs, likes). */
@Composable
private fun StatPill(icon: ImageVector, text: String, tint: Color = Color.White) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bouton d'action circulaire translucide (partage / signaler / fermer). */
@Composable
private fun IconPill(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/** Formatage compact d'un compteur (1200 → "1.2k"). */
private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
    else -> n.toString()
}

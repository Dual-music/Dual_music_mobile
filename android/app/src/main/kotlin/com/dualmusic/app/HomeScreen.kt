package com.dualmusic.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.DMLogo
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme

/** Dégradé de marque (violet → rose) réutilisé pour le titre et les accents. */
private val BrandGradient = Brush.linearGradient(listOf(Color(0xFFB07CFF), Color(0xFFFF4FA3)))

/**
 * Barre supérieure de l'app (hors profil) : logo + notifications + accès profil.
 *
 * Fond en **dégradé de marque** qui remonte derrière la **barre d'état** (via
 * [statusBarsPadding] appliqué APRÈS le fond) : la ligne système (heure, réseau, batterie)
 * est ainsi colorée, et le contenu de la barre descend sous elle (pas de superposition).
 *
 * @param onOpenNotifications ouvre le centre de notifications.
 * @param onOpenProfile ouvre la section profil.
 */
@Composable
fun TopBar(onOpenNotifications: () -> Unit, onOpenProfile: () -> Unit, unreadCount: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF3A1D6E), Color(0xFF6D28D9), Color(0xFF9D2FB0))),
            )
            .statusBarsPadding()
            .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DMLogo(height = 32.dp)
        Box(modifier = Modifier.weight(1f))
        // Cloche + badge du nombre de non-lus (comme le web).
        Box {
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 2.dp)
                        .size(18.dp)
                        .background(Color(0xFFE11D48), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
        }
        IconButton(onClick = onOpenProfile) {
            Icon(Icons.Filled.Person, contentDescription = "Profil", tint = Color.White)
        }
    }
}

/**
 * Page d'accueil : bannière plein écran (image de fond du web) avec **logo au-dessus du
 * titre** (dégradé), un court slogan, et **3 accès rapides** sous forme de grosses icônes
 * animées (Lifestyle, Classement, Artistes) alignées en bas. Tient sans défilement.
 *
 * @param onOpenLifestyle ouvre la page Lifestyle (vidéos + blog).
 * @param onOpenClassement ouvre les classements.
 * @param onOpenArtistes ouvre l'annuaire des artistes.
 */
@Composable
fun HomeScreen(
    onOpenLifestyle: () -> Unit,
    onOpenClassement: () -> Unit,
    onOpenArtistes: () -> Unit,
) {
    val s = LocalStrings.current
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Voile dégradé pour la lisibilité.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xCC0B0614), Color(0x880B0614), Color(0xF20B0614)))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DualMusicTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Même image que l'accueil web (icône « duel »), mise en valeur dans un cadre
            // moderne : bord en dégradé de marque, halo coloré et légère pulsation.
            val heroPulse = rememberInfiniteTransition(label = "hero")
            val heroScale by heroPulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
                label = "heroScale",
            )
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .scale(heroScale)
                    .shadow(28.dp, RoundedCornerShape(38.dp), spotColor = Color(0xFFB07CFF), ambientColor = Color(0xFFFF4FA3))
                    .clip(RoundedCornerShape(38.dp))
                    .background(BrandGradient)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(35.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1C1033), Color(0xFF120A24)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.duel_icon),
                    contentDescription = "Dual Music",
                    modifier = Modifier.size(112.dp),
                )
            }
            Text(
                s.homeTitle,
                style = TextStyle(brush = BrandGradient),
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DualMusicTheme.spacing.lg),
            )
            Text(
                s.homeSubtitle,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = DualMusicTheme.spacing.sm),
            )

            Spacer(Modifier.weight(1f))

            // 3 accès rapides, grosses icônes animées, bien espacées.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                HomeAccess(s.lifestyle, Icons.Filled.Movie, pulseMs = 1100, onClick = onOpenLifestyle)
                HomeAccess(s.ranking, Icons.Filled.EmojiEvents, pulseMs = 1400, onClick = onOpenClassement)
                HomeAccess(s.artists, Icons.Filled.Mic, pulseMs = 1700, onClick = onOpenArtistes)
            }

            Spacer(Modifier.height(DualMusicTheme.spacing.xxl))
        }
    }
}

/**
 * Accès rapide de l'accueil : pastille circulaire en dégradé de marque, icône blanche,
 * **pulsation** continue (durée [pulseMs] décalée par accès pour un effet vivant), + libellé.
 */
@Composable
private fun HomeAccess(label: String, icon: ImageVector, pulseMs: Int, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = label)
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(tween(pulseMs), RepeatMode.Reverse),
        label = "scale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(scale)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(BrandGradient)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = DualMusicTheme.spacing.sm)
                .width(88.dp),
            textAlign = TextAlign.Center,
        )
    }
}

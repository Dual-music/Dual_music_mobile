package com.dualmusic.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMLogo
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Barre supérieure de l'app (hors profil) : logo à gauche, notifications + accès profil à
 * droite — reprend la barre du web en version mobile. Masquée dans la section profil (qui
 * possède déjà son propre en-tête).
 *
 * @param onOpenNotifications ouvre le centre de notifications.
 * @param onOpenProfile ouvre la section profil.
 */
@Composable
fun TopBar(onOpenNotifications: () -> Unit, onOpenProfile: () -> Unit) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = DualMusicTheme.spacing.md, vertical = DualMusicTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DMLogo(height = 32.dp)
        Box(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenNotifications) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = colors.foreground)
        }
        IconButton(onClick = onOpenProfile) {
            Icon(Icons.Filled.Person, contentDescription = "Profil", tint = colors.primaryGlow)
        }
    }
}

/**
 * Page d'accueil : bannière plein écran (même image de fond que le web) avec le logo, le
 * slogan et **3 accès rapides** — Lifestyle, Classement, Artistes (à la place des boutons
 * « Commencer / Voir les duels » du web).
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Image de fond (bannière web) + voile dégradé pour la lisibilité du texte.
        Image(
            painter = painterResource(id = R.drawable.hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC0B0614), Color(0x990B0614), Color(0xE60B0614)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DualMusicTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DMLogo(height = 72.dp)
            Text(
                "Participez aux duels musicaux en direct",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DualMusicTheme.spacing.lg),
            )
            Text(
                "Votez pour vos artistes préférés, offrez des cadeaux virtuels et vivez l'expérience ultime de la compétition musicale.",
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DualMusicTheme.spacing.sm),
            )

            Column(
                modifier = Modifier
                    .padding(top = DualMusicTheme.spacing.xl)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
            ) {
                DMButton("🎬  Lifestyle", modifier = Modifier.fillMaxWidth(), onClick = onOpenLifestyle)
                DMButton("🏆  Classement", style = DMButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), onClick = onOpenClassement)
                DMButton("🎤  Artistes", style = DMButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), onClick = onOpenArtistes)
            }
        }
    }
}

package com.dualmusic.feature.profile

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.creator.SocialPlatform

/**
 * Écran de **profil public d'un artiste** (lecture seule) ouvert depuis un direct quand un
 * spectateur clique sur le nom de l'artiste : couverture, avatar, nom, bio, abonnés, liens
 * sociaux cliquables, et bouton **Suivre / Suivi**. Équivalent mobile de `/artist/:id` (web).
 */
@Composable
fun ArtistPublicProfileScreen(
    viewModel: ArtistPublicProfileViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val context = LocalContext.current

    // Le bouton RETOUR système ferme l'overlay profil (revient au direct), pas le direct.
    BackHandler(onBack = onBack)

    val name = state.artistProfile?.stageName?.takeIf { it.isNotBlank() }
        ?: state.profile?.displayName ?: "Artiste"
    val avatar = state.artistProfile?.avatarUrl ?: state.profile?.avatarUrl
    val cover = state.artistProfile?.coverImageUrl
    val bio = state.artistProfile?.bio?.takeIf { it.isNotBlank() } ?: state.profile?.bio?.takeIf { it.isNotBlank() }
    val links = state.artistProfile?.socialLinks.orEmpty().filterValues { it.isNotBlank() }

    Box(
        Modifier.fillMaxSize().background(colors.background)
            // Capte tous les taps → le direct EN DESSOUS ne reçoit rien (overlay opaque).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
        ) {
            // Bannière de couverture (ou dégradé) avec l'avatar qui déborde en bas.
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                if (!cover.isNullOrBlank()) {
                    DMRemoteImage(url = cover, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(DualMusicTheme.gradients.hero))
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                // Avatar circulaire, chevauchant le bas de la couverture.
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp).offset(y = 44.dp)
                        .size(88.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatar.isNullOrBlank()) {
                        DMRemoteImage(url = avatar, contentDescription = name, modifier = Modifier.fillMaxSize(), fallbackEmoji = "🎤")
                    } else {
                        Text(name.take(1).uppercase(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(52.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "${state.followerCount} abonné" + if (state.followerCount > 1) "s" else "",
                    color = colors.mutedForeground, fontSize = 13.sp,
                )
                DMButton(
                    if (state.isFollowing) "✓ Suivi" else "Suivre",
                    style = if (state.isFollowing) DMButtonStyle.OUTLINE else DMButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.toggleFollow() },
                )
                if (!bio.isNullOrBlank()) {
                    Text(bio, color = colors.foreground.copy(alpha = 0.9f), fontSize = 14.sp)
                }
                if (links.isNotEmpty()) {
                    Text("Réseaux", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    links.forEach { (key, url) ->
                        val label = SocialPlatform.values().find { it.key == key }?.label ?: key.replaceFirstChar { it.uppercase() }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(colors.muted.copy(alpha = 0.4f))
                                .clickable {
                                    runCatching {
                                        val fixed = if (url.startsWith("http")) url else "https://$url"
                                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fixed)))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Text(label, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                if (state.error != null && !state.loading) {
                    Text("Impossible de charger le profil.", color = colors.destructive, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Bouton retour flottant (au-dessus de la couverture).
        Box(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
                .size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        if (state.loading) DMLoadingBox(Modifier.align(Alignment.Center))
    }
}

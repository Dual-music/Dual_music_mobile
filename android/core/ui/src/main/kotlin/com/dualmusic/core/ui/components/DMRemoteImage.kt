package com.dualmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Image distante avec cache mémoire + disque (Coil) — mise en cache par URL : une fois chargée,
 * une image redonne un affichage INSTANTANÉ partout où elle réapparaît (autre écran, retour en
 * arrière, item de liste qui se recompose au scroll), sans re-télécharger ni re-décoder.
 *
 * Avant ce composant s'appuyait sur un chargeur maison sans aucun cache (nouveau fetch réseau à
 * CHAQUE recomposition) — d'où des avatars lents à apparaître et un flash de repli ([fallbackEmoji])
 * visible à chaque fois, même pour une image déjà vue quelques secondes plus tôt.
 *
 * @param url URL publique de l'image (peut être nulle/vide).
 * @param contentDescription description accessibilité.
 * @param modifier modificateur (taille/forme — appliquer `clip` à l'appelant si besoin).
 * @param fallbackEmoji emoji affiché si l'URL est vide ou le chargement échoue.
 */
@Composable
fun DMRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackEmoji: String = "🎁",
) {
    val colors = DualMusicTheme.colors
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(fallbackEmoji, color = colors.foreground, textAlign = TextAlign.Center)
        }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = contentDescription,
        modifier = modifier,
    ) {
        val state = painter.state
        when (state) {
            is coil.compose.AsyncImagePainter.State.Error -> {
                // Échec réel (URL invalide, réseau indisponible) : repli visible, comme avant.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(fallbackEmoji, color = colors.foreground, textAlign = TextAlign.Center)
                }
            }
            is coil.compose.AsyncImagePainter.State.Loading,
            is coil.compose.AsyncImagePainter.State.Empty -> {
                // Premier téléchargement seulement (les suivants sont servis par le cache Coil,
                // donc instantanés) — fond neutre plutôt qu'une icône qui apparaît puis disparaît.
                Box(modifier = Modifier.fillMaxSize().background(colors.muted))
            }
            else -> SubcomposeAsyncImageContent(contentScale = ContentScale.Crop)
        }
    }
}

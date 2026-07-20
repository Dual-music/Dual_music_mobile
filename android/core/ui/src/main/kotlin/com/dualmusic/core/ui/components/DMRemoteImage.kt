package com.dualmusic.core.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import com.dualmusic.core.ui.theme.DualMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Image distante autonome (sans dépendance de type Coil/Glide).
 *
 * Télécharge + décode l'image sur [Dispatchers.IO] via [produceState] (re-déclenché si l'URL
 * change). Tant qu'elle n'est pas prête — ou si l'URL est vide/en échec — affiche le [fallback]
 * (par défaut un emoji centré). Suffisant pour des vignettes (avatars, cadeaux) ; pour des
 * listes très volumineuses, préférer une lib avec cache mémoire/disque.
 *
 * @param url URL publique de l'image (peut être nulle/vide).
 * @param contentDescription description accessibilité.
 * @param modifier modificateur (taille/forme — appliquer `clip` à l'appelant si besoin).
 * @param fallbackEmoji emoji affiché en repli.
 */
@Composable
fun DMRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackEmoji: String = "🎁",
) {
    val colors = DualMusicTheme.colors
    val image = rememberRemoteImageBitmap(url)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(fallbackEmoji, color = colors.foreground, textAlign = TextAlign.Center)
        }
    }
}

/** Charge une image distante en [ImageBitmap] (null tant qu'indisponible / en cas d'échec). */
@Composable
fun rememberRemoteImageBitmap(url: String?): ImageBitmap? {
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

package com.dualmusic.core.upload

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durée d'une vidéo (`content://` Uri) en secondes, bornée à [1, 600] (limite backend sponsor).
 *
 * Lecture des métadonnées sur [Dispatchers.IO] via [MediaMetadataRetriever]. Renvoie `null`
 * si la durée est indisponible (l'appelant retombe alors sur une valeur par défaut).
 *
 * @param context contexte pour ouvrir le média.
 * @param uri Uri de la vidéo.
 * @return durée en secondes (1..600) ou `null`.
 */
suspend fun videoDurationSeconds(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        ms?.let { (it / 1000L).toInt().coerceIn(1, 600) }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

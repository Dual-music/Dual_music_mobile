package com.dualmusic.core.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichier local sélectionné par l'utilisateur, prêt pour l'upload.
 *
 * @property bytes contenu brut (chargé en mémoire — voir la garde de taille à la lecture).
 * @property filename nom d'origine (sert à déduire l'extension côté serveur).
 * @property contentType type MIME (ex. `image/jpeg`, `video/mp4`).
 */
class LocalMedia(
    val bytes: ByteArray,
    val filename: String,
    val contentType: String,
) {
    /** Taille en octets. */
    val size: Long get() = bytes.size.toLong()

    /** `image` si le MIME commence par `image/`, `video` s'il commence par `video/`, sinon `null`. */
    val mediaKind: String? get() = when {
        contentType.startsWith("image/") -> "image"
        contentType.startsWith("video/") -> "video"
        else -> null
    }
}

/**
 * Lit un `content://` Uri (issu du sélecteur système) en [LocalMedia].
 *
 * Lecture sur [Dispatchers.IO]. Une garde de taille évite les `OutOfMemoryError` : les octets
 * sont chargés en RAM (le stockage objet ne permet pas de streamer trivialement le PUT signé).
 *
 * @param context contexte pour accéder au `ContentResolver`.
 * @param uri Uri de contenu retourné par le picker.
 * @param maxBytes plafond de sécurité (défaut 100 Mo) — au-delà, on lève [MediaTooLargeException].
 * @return le média lu.
 * @throws MediaTooLargeException si le fichier dépasse [maxBytes].
 * @throws IllegalStateException si l'Uri est illisible.
 */
suspend fun readLocalMedia(
    context: Context,
    uri: Uri,
    maxBytes: Long = 100L * 1024 * 1024,
): LocalMedia = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val contentType = resolver.getType(uri) ?: "application/octet-stream"

    // Nom + taille déclarée via OpenableColumns (best-effort).
    var filename = "upload"
    var declaredSize = -1L
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) filename = cursor.getString(nameIdx)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
        }
    }
    if (declaredSize in 1..Long.MAX_VALUE && declaredSize > maxBytes) {
        throw MediaTooLargeException(declaredSize, maxBytes)
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Impossible de lire le fichier sélectionné.")
    if (bytes.size.toLong() > maxBytes) throw MediaTooLargeException(bytes.size.toLong(), maxBytes)

    LocalMedia(bytes = bytes, filename = filename, contentType = contentType)
}

/** Fichier trop volumineux pour l'upload en mémoire. */
class MediaTooLargeException(val actual: Long, val max: Long) :
    Exception("Fichier trop volumineux (${actual / (1024 * 1024)} Mo, max ${max / (1024 * 1024)} Mo).")

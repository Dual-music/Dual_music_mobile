package com.dualmusic.domain.upload

import kotlinx.serialization.Serializable

/**
 * Contrat d'upload média — flux « presign → PUT direct → confirm » (compatible S3/GCS/Supabase Storage).
 *
 * Étapes (identiques au web) :
 *  1. [PresignRequest] → `POST /uploads/presign` → [PresignResult] (URL signée + clé + URL publique).
 *  2. PUT brut des octets vers [PresignResult.uploadUrl] (hors API : pas de Bearer, pas de préfixe /api/v1).
 *  3. [ConfirmRequest] → `POST /uploads/confirm` → [ConfirmResult] (validation magic-bytes + scan serveur).
 *  4. URL finale = [ConfirmResult.publicUrl] ?? [ConfirmResult.url] ?? [PresignResult.publicUrl].
 */

/** Catégories de média acceptées par le backend (détermine le bucket + les contraintes mime/taille). */
object UploadCategory {
    const val AVATAR = "avatar"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val LIFESTYLE = "lifestyle"
    const val REPLAY = "replay"
    const val SPONSOR = "sponsor"
    const val ATTACHMENT = "attachment"
}

/** Corps de `POST /uploads/presign` (camelCase). */
@Serializable
data class PresignRequest(
    val category: String,
    val filename: String,
    val contentType: String,
    val size: Long,
)

/** Réponse de `POST /uploads/presign` — URL signée pour le PUT + identifiants de la ressource. */
@Serializable
data class PresignResult(
    /** URL signée vers laquelle envoyer les octets en PUT (absolue, hors API). */
    val uploadUrl: String,
    /** Clé de stockage (à renvoyer à `/uploads/confirm`). */
    val key: String,
    /** URL publique finale (si le bucket est public). */
    val publicUrl: String? = null,
)

/** Corps de `POST /uploads/confirm` — valide l'objet uploadé côté serveur. */
@Serializable
data class ConfirmRequest(val key: String)

/** Réponse de `POST /uploads/confirm` — URL exploitable du média confirmé. */
@Serializable
data class ConfirmResult(
    val url: String? = null,
    val publicUrl: String? = null,
)

/** Chemins REST de l'upload (source unique, partagée). */
object UploadEndpoints {
    const val PRESIGN = "/uploads/presign"
    const val CONFIRM = "/uploads/confirm"
}

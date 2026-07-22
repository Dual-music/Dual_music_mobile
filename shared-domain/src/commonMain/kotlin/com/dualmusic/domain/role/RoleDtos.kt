package com.dualmusic.domain.role

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Demandes de promotion de rôle (fan → artiste / manager) et réglages associés.
 *
 * ⚠️ Corps de requête en **camelCase** (Joi `stripUnknown` côté backend).
 */

/** Corps de `POST /artists/requests` — candidature artiste. */
@Serializable
data class ApplyArtistRequest(
    val description: String,
    val socialLinks: Map<String, String> = emptyMap(),
    val justificationDocumentUrl: String? = null,
)

/** Corps de `POST /managers/requests` — candidature manager. */
@Serializable
data class ApplyManagerRequest(
    val bio: String,
    val experience: String,
)

/** Élément de `GET /artists|managers/requests/me` (statut de ma candidature). */
@Serializable
data class RoleRequest(
    val id: String,
    /** `pending` | `approved` | `rejected`. */
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

/** Valeur d'un réglage `*_requests_enabled` (`GET /settings/public/:key` → `value`). */
@Serializable
data class EnabledFlag(val enabled: Boolean = true)

/** Réponse de `GET /settings/public/:key` — `{ key, value }`. */
@Serializable
data class PublicSetting(
    val key: String? = null,
    val value: EnabledFlag? = null,
)

/** Chemins REST des demandes de rôle + réglages publics. */
object RoleEndpoints {
    const val ARTIST_APPLY = "/artists/requests"
    const val ARTIST_MINE = "/artists/requests/me"
    const val MANAGER_APPLY = "/managers/requests"
    const val MANAGER_MINE = "/managers/requests/me"

    /** Réglage public autorisant les candidatures (`artist_requests_enabled` / `manager_requests_enabled`). */
    fun publicSetting(key: String) = "/settings/public/$key"

    const val ARTIST_REQUESTS_ENABLED = "artist_requests_enabled"
    const val MANAGER_REQUESTS_ENABLED = "manager_requests_enabled"
}

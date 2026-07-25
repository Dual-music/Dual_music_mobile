package com.dualmusic.domain.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Utilisateur retourné par la recherche admin (`GET /admin/users/search?q=`).
 * `id` = identifiant utilisateur (= id du profil), utilisable comme `userId` pour assigner un rôle.
 */
@Serializable
data class AdminUser(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
) {
    val label: String get() = fullName?.takeIf { it.isNotBlank() } ?: email ?: id.take(8)
}

/** Corps de `POST /admin/roles` (assigner) et `DELETE /admin/roles` (révoquer). */
@Serializable
data class AssignRoleRequest(val userId: String, val role: String)

/** Valeur d'un réglage booléen (`{ enabled }`). */
@Serializable
data class SettingEnabled(val enabled: Boolean)

/** Corps de `PUT /admin/settings/:key` — le backend attend `{ value: <any> }`. */
@Serializable
data class SettingBody(val value: SettingEnabled)

/** Chemins REST de l'espace admin (source unique, partagée). */
object AdminEndpoints {
    /** Recherche d'utilisateurs par nom/email (`?q=`). */
    const val USERS_SEARCH = "/admin/users/search"

    /** Assignation (POST) / révocation (DELETE) d'un rôle. */
    const val ROLES = "/admin/roles"

    /** Réglage plateforme (clé/valeur). */
    fun setting(key: String) = "/admin/settings/$key"
}

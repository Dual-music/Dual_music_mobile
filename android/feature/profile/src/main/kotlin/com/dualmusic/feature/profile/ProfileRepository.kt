package com.dualmusic.feature.profile

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.admin.AdminEndpoints
import com.dualmusic.domain.admin.AdminUser
import com.dualmusic.domain.admin.AssignRoleRequest
import com.dualmusic.domain.admin.SettingBody
import com.dualmusic.domain.admin.SettingEnabled
import com.dualmusic.domain.auth.AuthEndpoints
import com.dualmusic.domain.auth.ChangePasswordRequest
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.creator.ArtistProfile
import com.dualmusic.domain.creator.CreatorEndpoints
import com.dualmusic.domain.creator.ManagerProfile
import com.dualmusic.domain.creator.PublicProfileResponse
import com.dualmusic.domain.creator.UpdateArtistProfileRequest
import com.dualmusic.domain.creator.UpdateManagerProfileRequest
import com.dualmusic.domain.role.ApplyArtistRequest
import com.dualmusic.domain.role.ApplyManagerRequest
import com.dualmusic.domain.role.PublicSetting
import com.dualmusic.domain.role.RoleEndpoints
import com.dualmusic.domain.role.RoleRequest
import com.dualmusic.domain.user.UpdateProfileRequest
import com.dualmusic.domain.user.UserEndpoints
import com.dualmusic.domain.user.UserStats
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Accès REST au profil : lecture (identité + stats), **édition** (`PATCH /users/me`) et
 * **demandes de rôle** (devenir artiste/manager) avec leur gating public.
 */
class ProfileRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Profil + rôles du caller. */
    suspend fun me(): MeResponse = api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer())

    /** Statistiques agrégées (artiste/manager/fan). */
    suspend fun stats(): UserStats = api.request(Endpoint.get(UserEndpoints.MY_STATS), UserStats.serializer())

    /** Met à jour le profil (nom, bio, avatar, pays, numéro). */
    suspend fun updateProfile(request: UpdateProfileRequest) {
        val body = json.encodeToString(UpdateProfileRequest.serializer(), request)
        api.request<Unit>(Endpoint.patch(UserEndpoints.UPDATE_ME, body))
    }

    /** Programme la suppression du compte (grâce 20 jours). */
    suspend fun requestAccountDeletion() {
        api.request<Unit>(Endpoint.post(UserEndpoints.ME_DELETION))
    }

    /** Annule une suppression de compte programmée. */
    suspend fun cancelAccountDeletion() {
        api.request<Unit>(Endpoint.delete(UserEndpoints.ME_DELETION))
    }

    // --- Profils publics créateurs (artiste / manager) ---

    /**
     * Profil public artiste du caller. Lu via `GET /users/:id` (pas de `GET /artists/me`
     * côté backend) — renvoie `null` si l'utilisateur n'a pas encore de profil artiste.
     */
    suspend fun myArtistProfile(userId: String): ArtistProfile? =
        api.request(Endpoint.get(CreatorEndpoints.publicProfile(userId)), PublicProfileResponse.serializer()).artistProfile

    /** Met à jour le profil public artiste (`PATCH /artists/me`). */
    suspend fun updateArtistProfile(request: UpdateArtistProfileRequest) {
        val body = json.encodeToString(UpdateArtistProfileRequest.serializer(), request)
        api.request<Unit>(Endpoint.patch(CreatorEndpoints.ARTIST_ME, body))
    }

    /** Profil public manager du caller (`GET /managers/me`). */
    suspend fun myManagerProfile(): ManagerProfile =
        api.request(Endpoint.get(CreatorEndpoints.MANAGER_ME), ManagerProfile.serializer())

    /** Met à jour le profil public manager (`PATCH /managers/me`). */
    suspend fun updateManagerProfile(request: UpdateManagerProfileRequest) {
        val body = json.encodeToString(UpdateManagerProfileRequest.serializer(), request)
        api.request<Unit>(Endpoint.patch(CreatorEndpoints.MANAGER_ME, body))
    }

    /** Change le mot de passe (utilisateur connecté). */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val body = json.encodeToString(
            ChangePasswordRequest.serializer(),
            ChangePasswordRequest(currentPassword = currentPassword.ifBlank { null }, newPassword = newPassword),
        )
        api.request<Unit>(Endpoint.post(AuthEndpoints.PASSWORD_CHANGE, body))
    }

    /** Candidature artiste. */
    suspend fun applyArtist(request: ApplyArtistRequest) {
        val body = json.encodeToString(ApplyArtistRequest.serializer(), request)
        api.request<Unit>(Endpoint.post(RoleEndpoints.ARTIST_APPLY, body))
    }

    /** Candidature manager. */
    suspend fun applyManager(request: ApplyManagerRequest) {
        val body = json.encodeToString(ApplyManagerRequest.serializer(), request)
        api.request<Unit>(Endpoint.post(RoleEndpoints.MANAGER_APPLY, body))
    }

    /** Mes candidatures artiste (pour connaître un éventuel statut en attente). */
    suspend fun myArtistRequests(): List<RoleRequest> =
        api.request(Endpoint.get(RoleEndpoints.ARTIST_MINE), ListSerializer(RoleRequest.serializer()))

    /** Mes candidatures manager. */
    suspend fun myManagerRequests(): List<RoleRequest> =
        api.request(Endpoint.get(RoleEndpoints.MANAGER_MINE), ListSerializer(RoleRequest.serializer()))

    /**
     * Vrai si les candidatures pour ce rôle sont ouvertes (réglage admin). Par défaut ouvert
     * si le réglage est absent/illisible.
     */
    suspend fun requestsEnabled(key: String): Boolean = settingEnabled(key, default = true)

    /** Lit un flag public `{enabled}` avec une valeur par défaut si absent/illisible. */
    suspend fun settingEnabled(key: String, default: Boolean): Boolean =
        runCatching {
            api.request(Endpoint.get(RoleEndpoints.publicSetting(key)), PublicSetting.serializer())
                .value?.enabled ?: default
        }.getOrDefault(default)

    // --- Admin ---

    /** Recherche d'utilisateurs (nom/email) — réservé admin. */
    suspend fun adminSearchUsers(query: String): List<AdminUser> =
        api.request(
            Endpoint.get(AdminEndpoints.USERS_SEARCH, mapOf("q" to query)),
            ListSerializer(AdminUser.serializer()),
        )

    /** Assigne un rôle (`fan`/`artist`/`manager`/`moderator`/`admin`) à un utilisateur. */
    suspend fun adminAssignRole(userId: String, role: String) {
        val body = json.encodeToString(AssignRoleRequest.serializer(), AssignRoleRequest(userId, role))
        api.request<Unit>(Endpoint.post(AdminEndpoints.ROLES, body))
    }

    /** Révoque un rôle d'un utilisateur. */
    suspend fun adminRevokeRole(userId: String, role: String) {
        val body = json.encodeToString(AssignRoleRequest.serializer(), AssignRoleRequest(userId, role))
        api.request<Unit>(Endpoint.delete(AdminEndpoints.ROLES, body))
    }

    /** Ouvre/ferme les candidatures d'un rôle (`manager_requests_enabled`, `artist_requests_enabled`). */
    suspend fun adminSetRequestsEnabled(key: String, enabled: Boolean) {
        val body = json.encodeToString(SettingBody.serializer(), SettingBody(SettingEnabled(enabled)))
        api.request<Unit>(Endpoint.put(AdminEndpoints.setting(key), body))
    }
}

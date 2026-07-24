package com.dualmusic.feature.profile

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.auth.AuthEndpoints
import com.dualmusic.domain.auth.ChangePasswordRequest
import com.dualmusic.domain.auth.MeResponse
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
    suspend fun requestsEnabled(key: String): Boolean =
        runCatching {
            api.request(Endpoint.get(RoleEndpoints.publicSetting(key)), PublicSetting.serializer())
                .value?.enabled ?: true
        }.getOrDefault(true)
}

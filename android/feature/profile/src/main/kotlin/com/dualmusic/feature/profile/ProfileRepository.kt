package com.dualmusic.feature.profile

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.user.UserEndpoints
import com.dualmusic.domain.user.UserStats

/**
 * Accès REST au profil et aux statistiques du caller.
 *
 * Lecture seule : identité (`/auth/me`) et statistiques (`/users/me/stats`).
 */
class ProfileRepository(private val api: ApiClient) {

    /** Profil + rôles du caller. */
    suspend fun me(): MeResponse = api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer())

    /** Statistiques agrégées (artiste/manager/fan). */
    suspend fun stats(): UserStats = api.request(Endpoint.get(UserEndpoints.MY_STATS), UserStats.serializer())
}

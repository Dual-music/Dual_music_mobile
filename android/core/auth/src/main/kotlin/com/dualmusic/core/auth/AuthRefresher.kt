package com.dualmusic.core.auth

import com.dualmusic.core.network.RefreshedTokens
import com.dualmusic.core.network.TokenRefresher
import com.dualmusic.domain.api.ApiResponse
import com.dualmusic.domain.auth.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Exécute le refresh de session (`POST /auth/refresh`).
 *
 * Implémente `core:network.TokenRefresher`. Utilise un client Ktor **dédié** (et non
 * `ApiClient`) pour éviter la dépendance circulaire : `ApiClient` requiert un
 * `TokenRefresher`.
 *
 * @param baseUrl URL publique SANS `/api/v1`.
 */
class AuthRefresher(
    private val baseUrl: String,
    private val http: HttpClient = HttpClient(OkHttp),
) : TokenRefresher {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun refresh(refreshToken: String): RefreshedTokens {
        val url = baseUrl.trimEnd('/') + "/api/v1/auth/refresh"
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        if (response.status.value !in 200..299) {
            error("refresh_failed_${response.status.value}") // → core:network efface la session
        }
        val env = json.decodeFromString(
            ApiResponse.serializer(AuthSession.serializer()),
            response.bodyAsText(),
        )
        val session = env.data ?: error("refresh_no_data")
        return RefreshedTokens(access = session.accessToken, refresh = session.refreshToken)
    }
}

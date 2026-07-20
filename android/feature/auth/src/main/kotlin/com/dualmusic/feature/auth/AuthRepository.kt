package com.dualmusic.feature.auth

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.network.TokenStore
import com.dualmusic.domain.auth.AuthEndpoints
import com.dualmusic.domain.auth.AuthSession
import com.dualmusic.domain.auth.ForgotPasswordRequest
import com.dualmusic.domain.auth.LoginRequest
import com.dualmusic.domain.auth.ResetPasswordRequest
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.auth.RegisterRequest
import com.dualmusic.domain.auth.VerifyOtpRequest
import kotlinx.serialization.json.Json

/**
 * Accès aux opérations d'authentification `/auth/…`.
 *
 * Orchestre [ApiClient] (REST) et [TokenStore] (persistance). Après login/register,
 * persiste la session ; après logout, l'efface.
 *
 * @param baseUrl URL publique SANS `/api/v1` (pour l'URL de démarrage Google).
 */
class AuthRepository(
    private val api: ApiClient,
    private val tokenStore: TokenStore,
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Connexion email + mot de passe ; persiste la session. */
    suspend fun login(email: String, password: String): AuthSession {
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
        val session: AuthSession = api.request(Endpoint.post(AuthEndpoints.LOGIN, body).copy(anonymous = true))
        tokenStore.setTokens(session.accessToken, session.refreshToken)
        return session
    }

    /** Inscription ; persiste la session. */
    suspend fun register(request: RegisterRequest): AuthSession {
        val body = json.encodeToString(RegisterRequest.serializer(), request)
        val session: AuthSession = api.request(Endpoint.post(AuthEndpoints.REGISTER, body).copy(anonymous = true))
        tokenStore.setTokens(session.accessToken, session.refreshToken)
        return session
    }

    /** Utilisateur courant (`/auth/me`) — réhydratation au démarrage. */
    suspend fun me(): MeResponse = api.request(Endpoint.get(AuthEndpoints.ME))

    /** Déconnexion : invalide le refresh côté serveur puis efface le stockage local. */
    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        runCatching {
            api.request<Unit>(Endpoint.post(AuthEndpoints.LOGOUT, """{"refreshToken":${refresh?.let { "\"$it\"" } ?: "null"}}"""))
        }
        tokenStore.clear()
    }

    /** Envoie un OTP au numéro de l'utilisateur connecté. */
    suspend fun sendPhoneOtp() {
        api.request<Unit>(Endpoint.post(AuthEndpoints.OTP_PHONE_SEND))
    }

    /** Vérifie le code OTP (4–8 chiffres). */
    suspend fun verifyPhoneOtp(code: String) {
        val body = json.encodeToString(VerifyOtpRequest.serializer(), VerifyOtpRequest(code))
        api.request<Unit>(Endpoint.post(AuthEndpoints.OTP_PHONE_VERIFY, body))
    }

    /** (Ré)envoie le code de vérification par email au compte connecté. */
    suspend fun sendEmailOtp() {
        api.request<Unit>(Endpoint.post(AuthEndpoints.OTP_EMAIL_SEND))
    }

    /** Vérifie le code email → marque l'email comme vérifié côté serveur. */
    suspend fun verifyEmailOtp(code: String) {
        val body = json.encodeToString(VerifyOtpRequest.serializer(), VerifyOtpRequest(code))
        api.request<Unit>(Endpoint.post(AuthEndpoints.OTP_EMAIL_VERIFY, body))
    }

    /** Demande un email de réinitialisation (renvoie toujours OK, ne révèle rien). */
    suspend fun forgotPassword(email: String) {
        val body = json.encodeToString(ForgotPasswordRequest.serializer(), ForgotPasswordRequest(email.trim()))
        api.request<Unit>(Endpoint.post(AuthEndpoints.PASSWORD_FORGOT, body).copy(anonymous = true))
    }

    /** Réinitialise le mot de passe avec le code reçu par email. */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val body = json.encodeToString(
            ResetPasswordRequest.serializer(),
            ResetPasswordRequest(email.trim(), code.trim(), newPassword),
        )
        api.request<Unit>(Endpoint.post(AuthEndpoints.PASSWORD_RESET, body).copy(anonymous = true))
    }

    /**
     * URL de démarrage OAuth Google (redirect web) — à ouvrir dans un **Custom Tab**.
     * ⚠️ Retour via deep link à gérer ; ou endpoint natif idToken à ajouter côté backend
     * (voir DELIVERY-03).
     */
    fun googleStartUrl(): String = baseUrl.trimEnd('/') + "/api/v1" + AuthEndpoints.OAUTH_GOOGLE_START
}

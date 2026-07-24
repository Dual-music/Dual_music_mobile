package com.dualmusic.domain.auth

import com.dualmusic.domain.model.DisplayProfile
import com.dualmusic.domain.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs d'authentification — partagés iOS/Android, alignés sur le backend `/auth/…`.
 *
 * Rappel du modèle réel (≠ « phone passwordless » du doc initial) :
 *  - Auth primaire = **email + mot de passe** (`/auth/login`, `/auth/register`).
 *  - `/auth/refresh` échange le refresh token.
 *  - OTP téléphone = **vérification post-login** du numéro (`/auth/otp/phone/…`), authentifié.
 *  - Google = flux **redirect web** (`/auth/oauth/google`) — voir note « Google natif ».
 */

/** Utilisateur authentifié (`AuthSession.user` / `/auth/me`.user). */
@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val phone: String? = null,
    @SerialName("phoneVerified") val phoneVerified: Boolean = false,
    @SerialName("emailVerified") val emailVerified: Boolean = false,
    @SerialName("isBanned") val isBanned: Boolean = false,
    /** Date de suppression programmée (ISO) si le compte est en délai de grâce, sinon null. */
    val deletionScheduledAt: String? = null,
)

/** Session renvoyée par login/register/refresh. */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
    val profile: DisplayProfile? = null,
    val roles: List<UserRole> = emptyList(),
    val expiresIn: Int = 0,
    val tokenType: String = "Bearer",
) {
    /** Vrai si l'utilisateur possède le rôle admin. */
    val isAdmin: Boolean get() = roles.contains(UserRole.ADMIN)
}

/** Réponse de `GET /auth/me`. */
@Serializable
data class MeResponse(
    val user: AuthUser,
    val profile: DisplayProfile? = null,
    val roles: List<UserRole> = emptyList(),
)

/** Corps de `POST /auth/login`. */
@Serializable
data class LoginRequest(val email: String, val password: String)

/** Corps de `POST /auth/register`. */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String? = null,
    val phone: String? = null,
    val countryCode: String? = null,
    val phoneCountryCode: String? = null,
    val referralCode: String? = null,
)

/** Corps de `POST /auth/refresh`. */
@Serializable
data class RefreshRequest(val refreshToken: String)

/** Corps de `POST /auth/otp/phone/verify` et `/auth/otp/email/verify`. */
@Serializable
data class VerifyOtpRequest(val code: String)

/** Corps de `POST /auth/password/forgot`. */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** Corps de `POST /auth/password/change` (utilisateur connecté). */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String? = null,
    val newPassword: String,
)

/** Corps de `POST /auth/password/reset` — code reçu par email + nouveau mot de passe. */
@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)

/**
 * Corps proposé pour l'échange natif Google (`POST /auth/oauth/google/native`).
 * ⚠️ Endpoint à AJOUTER côté backend (voir DELIVERY-03) — le backend n'expose
 * aujourd'hui que le flux redirect web. En attendant, l'app utilise le redirect web.
 */
@Serializable
data class GoogleNativeRequest(val idToken: String)

/** Chemins REST d'authentification (source unique, partagée). */
object AuthEndpoints {
    const val LOGIN = "/auth/login"
    const val REGISTER = "/auth/register"
    const val REFRESH = "/auth/refresh"
    const val LOGOUT = "/auth/logout"
    const val ME = "/auth/me"
    const val OTP_PHONE_SEND = "/auth/otp/phone/send"
    const val OTP_PHONE_VERIFY = "/auth/otp/phone/verify"
    const val OTP_EMAIL_SEND = "/auth/otp/email/send"
    const val OTP_EMAIL_VERIFY = "/auth/otp/email/verify"
    const val PASSWORD_FORGOT = "/auth/password/forgot"
    const val PASSWORD_RESET = "/auth/password/reset"
    const val PASSWORD_CHANGE = "/auth/password/change"
    const val OAUTH_GOOGLE_START = "/auth/oauth/google"
    /** Proposé (à créer côté backend) pour l'auth Google native mobile. */
    const val OAUTH_GOOGLE_NATIVE = "/auth/oauth/google/native"
}

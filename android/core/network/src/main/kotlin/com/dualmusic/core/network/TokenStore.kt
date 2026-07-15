package com.dualmusic.core.network

/**
 * Fournisseur/persistance des jetons d'authentification.
 *
 * Implémenté par `core-auth` (EncryptedSharedPreferences + StrongBox). Isolé en interface
 * pour découpler le réseau du stockage sécurisé et faciliter les tests.
 */
interface TokenStore {
    /** Jeton d'accès (JWT ~15 min), ou null si non connecté. */
    suspend fun accessToken(): String?

    /** Jeton de refresh (~30 j), ou null. */
    suspend fun refreshToken(): String?

    /** Persiste la paire après un refresh réussi. */
    suspend fun setTokens(access: String, refresh: String)

    /** Efface les jetons (déconnexion / refresh échoué). */
    suspend fun clear()
}

/** Nouvelle paire de jetons issue d'un refresh. */
data class RefreshedTokens(val access: String, val refresh: String)

/**
 * Exécuteur du refresh (`POST /auth/refresh`), fourni par `core-auth` pour éviter une
 * dépendance circulaire (le réseau n'a pas connaissance des routes d'auth).
 */
interface TokenRefresher {
    /** Échange le refresh token contre une nouvelle paire. Lève si invalide. */
    suspend fun refresh(refreshToken: String): RefreshedTokens
}

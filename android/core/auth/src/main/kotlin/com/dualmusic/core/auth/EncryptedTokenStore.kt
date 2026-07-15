package com.dualmusic.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dualmusic.core.network.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stockage sécurisé des jetons JWT via **EncryptedSharedPreferences**.
 *
 * Implémente `core:network.TokenStore`. La clé maître utilise **StrongBox** (enclave
 * matérielle) quand l'appareil le supporte, avec repli AES-256-GCM sinon. Les accès I/O
 * sont déportés sur `Dispatchers.IO`.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true) // enclave matérielle si dispo
            .build()

        EncryptedSharedPreferences.create(
            context,
            "dm_secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun accessToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_ACCESS, null) }

    override suspend fun refreshToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_REFRESH, null) }

    override suspend fun setTokens(access: String, refresh: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}

package com.dualmusic.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dualmusic.core.network.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stockage sécurisé des jetons JWT via **EncryptedSharedPreferences**.
 *
 * Implémente `core:network.TokenStore`. La clé maître utilise **StrongBox** (enclave
 * matérielle) quand l'appareil le supporte, avec repli AES-256-GCM logiciel sinon.
 *
 * ⚠️ Robustesse indispensable : sur de nombreux appareils (notamment Transsion/Infinix,
 * entrée de gamme), StrongBox est absent ou bogué → `EncryptedSharedPreferences.create()`
 * lève `StrongBoxUnavailableException` / `KeyStoreException`. Un fichier chiffré avec une
 * clé Keystore qui a changé (réinstallation, restauration) devient aussi illisible. Sans
 * garde-fou, la 1re écriture (au login) plante et l'utilisateur voit « Une erreur est
 * survenue ». On dégrade donc proprement : StrongBox → logiciel → purge+recréation →
 * dernier repli en clair (jetons courts) pour ne jamais bloquer la connexion.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences =
        runCatching { createEncrypted(strongBox = true) }
            // StrongBox indisponible/bogué → réessaie sans enclave matérielle.
            .recoverCatching { createEncrypted(strongBox = false) }
            // Fichier chiffré illisible (clé Keystore changée/corrompue) → purge + recrée.
            .recoverCatching {
                appContext.deleteSharedPreferences(PREFS_NAME)
                createEncrypted(strongBox = false)
            }
            // Dernier repli : prefs standard, pour ne jamais empêcher la connexion.
            .getOrElse { appContext.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE) }

    private fun createEncrypted(strongBox: Boolean): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .apply { if (strongBox) setRequestStrongBoxBacked(true) }
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
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
        const val PREFS_NAME = "dm_secure_tokens"
        const val PREFS_FALLBACK = "dm_tokens_fallback"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}

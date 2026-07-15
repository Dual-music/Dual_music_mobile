package com.dualmusic.core.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Garde biométrique (empreinte / visage) pour les actions sensibles.
 *
 * Utilisée avant un **retrait** ou un **changement de PIN wallet**. S'ajoute par-dessus le
 * PIN de retrait 6 chiffres déjà vérifié côté backend.
 */
class BiometricGate(private val activity: FragmentActivity) {

    sealed class BiometricError(message: String) : Throwable(message) {
        object Unavailable : BiometricError("Biométrie indisponible")
        object Failed : BiometricError("Authentification échouée")
        object Cancelled : BiometricError("Annulé")
    }

    /** Vrai si une biométrie forte est configurée sur l'appareil. */
    fun isAvailable(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Demande une authentification biométrique.
     * @param title titre du prompt (ex. « Confirmer le retrait »).
     * @param subtitle sous-titre optionnel.
     * @throws BiometricError selon l'issue.
     */
    suspend fun authenticate(title: String, subtitle: String? = null) {
        if (!isAvailable()) throw BiometricError.Unavailable

        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!cont.isActive) return
                    val cancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    cont.resumeWithException(if (cancel) BiometricError.Cancelled else BiometricError.Failed)
                }
                override fun onAuthenticationFailed() { /* tentative rejetée : le prompt reste ouvert */ }
            })

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { subtitle?.let { setSubtitle(it) } }
                .setNegativeButtonText("Annuler")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            prompt.authenticate(info)
        }
    }
}

package com.dualmusic.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Web client ID Firebase (type 3) = `serverClientId`. C'est l'**audience** de l'ID token,
 * vérifiée côté backend (`/auth/oauth/google/native`). Doit correspondre au projet Firebase
 * `dual-music`.
 */
private const val WEB_CLIENT_ID =
    "663210356103-ujh7o2g4tgrpc84e1fj5fg5o0lijqnft.apps.googleusercontent.com"

/**
 * Ouvre le sélecteur de compte Google (Credential Manager) et renvoie l'**ID token** signé
 * par Google, à envoyer au backend pour la connexion native.
 *
 * `setFilterByAuthorizedAccounts(false)` : propose tous les comptes de l'appareil (pas
 * seulement ceux déjà autorisés), pour une première connexion fluide.
 *
 * @param context contexte d'Activity (fourni par `LocalContext` dans l'écran).
 * @return l'ID token Google.
 * @throws androidx.credentials.exceptions.GetCredentialException si l'utilisateur annule
 *   ou si aucun compte n'est disponible.
 * @throws IllegalStateException si la crédential retournée n'est pas un ID token Google.
 */
suspend fun requestGoogleIdToken(context: Context): String {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = response.credential
    check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Type de crédential inattendu : ${credential.type}"
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

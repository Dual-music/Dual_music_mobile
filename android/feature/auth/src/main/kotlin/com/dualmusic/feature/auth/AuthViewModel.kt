package com.dualmusic.feature.auth

import com.dualmusic.domain.geo.Country
import com.dualmusic.domain.geo.Countries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.api.DomainError
import com.dualmusic.domain.auth.AuthUser
import com.dualmusic.domain.auth.RegisterRequest
import com.dualmusic.domain.user.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État global de session. */
sealed interface AuthState {
    data object Loading : AuthState          // réhydratation au démarrage
    data object SignedOut : AuthState
    /** Inscrit mais email non vérifié : on propose l'écran de saisie du code. */
    data class PendingEmailVerification(val user: AuthUser, val email: String) : AuthState
    /** Email vérifié : on demande les informations de profil (nom, pays, numéro). */
    data class PendingProfileCompletion(val user: AuthUser) : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** Écran actif de la zone d'authentification. */
enum class AuthMode { LOGIN, REGISTER, FORGOT, RESET }

/** État de l'écran auth (connexion / inscription / mot de passe oublié / reset). */
data class SignInUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val phone: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val country: Country = Countries.DEFAULT,
    val referralCode: String = "",
    val acceptTerms: Boolean = false,
    // Reset de mot de passe.
    val resetCode: String = "",
    val newPassword: String = "",
    // Vérification email (écran dédié).
    val verifyCode: String = "",
    /** Instant ISO d'expiration du code en cours (10 min côté serveur) — pilote le chrono affiché. */
    val codeExpiresAt: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    // Erreurs rattachées à un champ précis (bordure rouge) plutôt que le seul message générique
    // `error` en bas de formulaire.
    /** Email déjà utilisé (409, inscription) — message affiché sous le champ email. */
    val emailFieldError: String? = null,
    /** Identifiants invalides (401, connexion) — les DEUX champs sont marqués en rouge (on ne
     *  sait pas lequel est fautif) ; le message complet reste affiché en bas du formulaire. */
    val credentialsInvalid: Boolean = false,
    // Réglage admin (`google_signin_config`), désactivé par défaut — voir `AuthViewModel.init`.
    val googleSigninEnabled: Boolean = false,
) {
    /** Le formulaire courant est-il soumettable ? (règles = celles du backend). */
    val canSubmit: Boolean
        get() = !isSubmitting && when (mode) {
            AuthMode.LOGIN -> email.contains("@") && password.length >= 8
            // Étape 1 : email + mot de passe + confirmation + acceptation obligatoire des
            // documents légaux. Les autres infos (nom, pays, numéro) sont demandées APRÈS
            // validation du code email.
            AuthMode.REGISTER ->
                email.contains("@") && password.length >= 8 && password == confirmPassword && acceptTerms
            AuthMode.FORGOT -> email.contains("@")
            AuthMode.RESET -> email.contains("@") && resetCode.length in 4..8 && newPassword.length >= 8
        }
}

/**
 * ViewModel de l'authentification (MVI léger).
 *
 * Gère la connexion, l'inscription (mêmes champs que le web), le mot de passe oublié
 * (demande + reset par code email) et la vérification de l'email par code.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    init {
        // Réglage admin, désactivé par défaut — le bouton Google reste masqué tant que ce
        // chargement n'a pas répondu `true` (jamais affiché "par erreur" en cas d'échec réseau).
        viewModelScope.launch {
            val enabled = runCatching { repository.googleSigninEnabled() }.getOrDefault(false)
            _uiState.update { it.copy(googleSigninEnabled = enabled) }
        }
    }

    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, error = null, emailFieldError = null, credentialsInvalid = false) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v, error = null, credentialsInvalid = false) }
    fun onConfirmPasswordChange(v: String) = _uiState.update { it.copy(confirmPassword = v, error = null) }
    fun onFullNameChange(v: String) = _uiState.update { it.copy(fullName = v, error = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phone = v, error = null) }
    fun onBirthDateChange(v: String) = _uiState.update { it.copy(birthDate = v, error = null) }
    fun onGenderChange(v: String) = _uiState.update { it.copy(gender = v, error = null) }
    fun onReferralChange(v: String) = _uiState.update { it.copy(referralCode = v.uppercase(), error = null) }
    fun onCountrySelected(country: Country) = _uiState.update { it.copy(country = country, error = null) }
    fun onAcceptTermsChange(v: Boolean) = _uiState.update { it.copy(acceptTerms = v, error = null) }
    fun onResetCodeChange(v: String) = _uiState.update { it.copy(resetCode = v.filter { c -> c.isDigit() }, error = null) }
    fun onNewPasswordChange(v: String) = _uiState.update { it.copy(newPassword = v, error = null) }
    fun onVerifyCodeChange(v: String) = _uiState.update { it.copy(verifyCode = v.filter { c -> c.isDigit() }, error = null) }

    /** Instant ISO 10 minutes dans le futur — durée de vie d'un code OTP (`config.otp.ttlSeconds` côté backend). */
    private fun otpExpiryIso(): String = java.time.Instant.now().plusSeconds(600).toString()

    /** Change de mode (connexion/inscription/oublié/reset), en nettoyant messages. */
    fun setMode(mode: AuthMode) = _uiState.update { it.copy(mode = mode, error = null, info = null) }

    /** Connexion via ID token Google (obtenu par Credential Manager côté écran). */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching { repository.loginWithGoogle(idToken) }
                .onSuccess { _authState.value = AuthState.SignedIn(it.user) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: com.dualmusic.core.ui.i18n.appStrings.errGoogleSignInFailed) } }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    /** Remonte une erreur du sélecteur Google (annulation, pas de compte…). */
    fun onGoogleError(message: String?) = _uiState.update { it.copy(error = message) }

    /**
     * Réhydrate la session au lancement (via le refresh token persisté).
     *
     * Un compte dont l'email n'est pas encore vérifié (inscription interrompue avant la saisie
     * du code, ou app fermée/rouverte pendant ce temps) revient sur l'écran de vérification —
     * avec la possibilité de renvoyer le code — plutôt que d'entrer directement dans l'app
     * (la vérification n'est plus contournable, voir `skipVerification` retiré).
     */
    fun bootstrap() {
        viewModelScope.launch {
            _authState.value = runCatching { repository.me() }
                .fold(
                    onSuccess = {
                        if (it.user.emailVerified) AuthState.SignedIn(it.user)
                        else AuthState.PendingEmailVerification(it.user, it.user.email)
                    },
                    onFailure = { AuthState.SignedOut },
                )
        }
    }

    /** Soumet le formulaire courant selon le [SignInUiState.mode]. */
    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, info = null, emailFieldError = null, credentialsInvalid = false) }
            when (s.mode) {
                AuthMode.LOGIN -> runCatching { repository.login(s.email.trim(), s.password) }
                    .onSuccess { _authState.value = AuthState.SignedIn(it.user) }
                    .onFailure { setError(it) }

                // Étape 1 : on ne crée le compte qu'avec email + mot de passe. Le backend
                // envoie automatiquement le code de vérification par email.
                AuthMode.REGISTER -> runCatching {
                    repository.register(RegisterRequest(email = s.email.trim(), password = s.password))
                }.onSuccess {
                    _authState.value = AuthState.PendingEmailVerification(it.user, s.email.trim())
                    _uiState.update { st -> st.copy(codeExpiresAt = otpExpiryIso()) }
                }.onFailure { setError(it) }

                AuthMode.FORGOT -> runCatching { repository.forgotPassword(s.email.trim()) }
                    .onSuccess {
                        _uiState.update {
                            it.copy(mode = AuthMode.RESET, info = "Si un compte existe, un code a été envoyé par email.")
                        }
                    }
                    .onFailure { setError(it) }

                AuthMode.RESET -> runCatching {
                    repository.resetPassword(s.email.trim(), s.resetCode, s.newPassword)
                }.onSuccess {
                    _uiState.update {
                        it.copy(mode = AuthMode.LOGIN, password = "", info = "Mot de passe réinitialisé. Connecte-toi.")
                    }
                }.onFailure { setError(it) }
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    /**
     * Vérifie le code email. Un compte qui a déjà un profil complet (cas d'un compte existant
     * dont l'email n'avait simplement jamais été vérifié — ex. ancien contournement retiré)
     * entre directement dans l'app plutôt que de repasser par la saisie du profil.
     */
    fun verifyEmail() {
        if (_authState.value !is AuthState.PendingEmailVerification) return
        val code = _uiState.value.verifyCode
        if (code.length !in 4..8) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                repository.verifyEmailOtp(code)
                repository.me()
            }.onSuccess { me ->
                _authState.value = if (!me.profile?.fullName.isNullOrBlank()) {
                    AuthState.SignedIn(me.user)
                } else {
                    AuthState.PendingProfileCompletion(me.user)
                }
            }.onFailure { setError(it) }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    /**
     * Étape 3 : enregistre les informations de profil (nom, pays, numéro) puis entre dans
     * l'app. Le numéro est optionnel ; le nom est requis.
     */
    fun completeProfile() {
        val pending = _authState.value as? AuthState.PendingProfileCompletion ?: return
        val s = _uiState.value
        if (s.fullName.isBlank()) {
            _uiState.update { it.copy(error = com.dualmusic.core.ui.i18n.appStrings.errNameRequired) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                repository.updateProfile(
                    UpdateProfileRequest(
                        fullName = s.fullName.trim(),
                        countryCode = s.country.code,
                        phone = s.phone.trim().ifBlank { null },
                        phoneCountryCode = s.country.dial,
                        birthDate = s.birthDate.trim().ifBlank { null },
                        gender = s.gender.ifBlank { null },
                    ),
                )
            }.onSuccess { _authState.value = AuthState.SignedIn(pending.user) }
                .onFailure { setError(it) }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    /** Passe la saisie des infos de profil (pourra les compléter plus tard). */
    fun skipProfile() {
        val pending = _authState.value as? AuthState.PendingProfileCompletion ?: return
        _authState.value = AuthState.SignedIn(pending.user)
    }

    /** (Ré)envoie le code de vérification email. */
    fun resendEmailCode() {
        viewModelScope.launch {
            runCatching { repository.sendEmailOtp() }
                .onSuccess { _uiState.update { it.copy(info = "Nouveau code envoyé.", codeExpiresAt = otpExpiryIso()) } }
                .onFailure { setError(it) }
        }
    }

    /** Déconnexion. */
    fun signOut() {
        viewModelScope.launch {
            repository.logout()
            _authState.value = AuthState.SignedOut
            _uiState.value = SignInUiState()
        }
    }

    private fun setError(t: Throwable) {
        val message = friendlyMessage(t)
        val is401 = t is DomainError && t.httpStatus == 401
        val is409 = t is DomainError && t.httpStatus == 409
        _uiState.update {
            it.copy(
                error = message,
                emailFieldError = if (is409) message else null,
                credentialsInvalid = is401,
            )
        }
    }

    /** Traduit une erreur backend en message utilisateur. */
    private fun friendlyMessage(t: Throwable): String = when {
        t is DomainError && t.httpStatus == 401 -> "Email ou mot de passe incorrect."
        t is DomainError && t.httpStatus == 409 -> "Cet email est déjà utilisé."
        t is DomainError && (t.code == "OTP_INVALID" || t.code == "OTP_EXPIRED") -> "Code invalide ou expiré."
        t is DomainError && t.code == "VALIDATION_ERROR" -> "Champs invalides (email valide + mot de passe ≥ 8 caractères)."
        // Erreur réseau (httpStatus 0) : message déjà précis (timeout, hors-ligne…), voir
        // ApiClient#humanizeNetworkException — ne pas l'écraser par un texte générique.
        t is DomainError && t.code == com.dualmusic.domain.api.ErrorCode.NETWORK -> t.message
        t is DomainError && t.isRetriable -> "Le serveur rencontre un problème. Réessaie dans un instant."
        t is DomainError -> t.message
        else -> "Une erreur est survenue. Réessaie."
    }
}

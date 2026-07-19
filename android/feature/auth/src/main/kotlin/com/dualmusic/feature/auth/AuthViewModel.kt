package com.dualmusic.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.api.DomainError
import com.dualmusic.domain.auth.AuthUser
import com.dualmusic.domain.auth.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État global de session. */
sealed interface AuthState {
    data object Loading : AuthState          // réhydratation au démarrage
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** État de l'écran de connexion / inscription. */
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    /** `true` = mode inscription ; `false` = mode connexion. */
    val isRegister: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    /** Le formulaire est-il soumettable ? (activation du bouton — mdp ≥ 8, email avec `@`). */
    val canSubmit: Boolean get() = !isSubmitting && email.contains("@") && password.length >= 8
}

/**
 * ViewModel de l'authentification (MVI léger).
 *
 * Expose [authState] (session) et [uiState] (écran de connexion) en [StateFlow]. Les
 * écrans Compose collectent ces flux ; les intents passent par les méthodes publiques.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value, error = null) }

    /** Bascule entre connexion et inscription (réinitialise l'erreur). */
    fun toggleMode() = _uiState.update { it.copy(isRegister = !it.isRegister, error = null) }

    /** Réhydrate la session au lancement (via le refresh token persisté). */
    fun bootstrap() {
        viewModelScope.launch {
            _authState.value = runCatching { AuthState.SignedIn(repository.me().user) }
                .getOrDefault(AuthState.SignedOut)
        }
    }

    /** Soumet le formulaire : connexion ou inscription selon [SignInUiState.isRegister]. */
    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = if (state.isRegister) {
                runCatching {
                    repository.register(
                        RegisterRequest(
                            email = state.email.trim(),
                            password = state.password,
                            fullName = state.fullName.trim().ifBlank { null },
                        ),
                    )
                }
            } else {
                runCatching { repository.login(state.email.trim(), state.password) }
            }
            result
                .onSuccess { _authState.value = AuthState.SignedIn(it.user) }
                .onFailure { _uiState.update { s -> s.copy(error = friendlyMessage(it)) } }
            _uiState.update { it.copy(isSubmitting = false) }
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

    /** Traduit une erreur backend en message utilisateur. */
    private fun friendlyMessage(t: Throwable): String = when {
        t is DomainError && t.httpStatus == 401 -> "Email ou mot de passe incorrect."
        t is DomainError && t.httpStatus == 409 -> "Cet email est déjà utilisé."
        t is DomainError && t.code == "VALIDATION_ERROR" -> "Identifiants invalides (email valide + mot de passe ≥ 8 caractères)."
        t is DomainError && t.isRetriable -> "Connexion instable. Réessaie."
        t is DomainError -> t.message
        else -> "Une erreur est survenue. Réessaie."
    }
}

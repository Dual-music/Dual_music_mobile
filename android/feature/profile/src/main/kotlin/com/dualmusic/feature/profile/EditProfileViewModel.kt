package com.dualmusic.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.domain.geo.Countries
import com.dualmusic.domain.geo.Country
import com.dualmusic.domain.upload.UploadCategory
import com.dualmusic.domain.user.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la page profil éditable (infos perso + mot de passe). */
data class EditProfileUiState(
    val fullName: String = "",
    val bio: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val country: Country = Countries.DEFAULT,
    val phone: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val changingPassword: Boolean = false,
    val message: String? = null,
    val passwordMessage: String? = null,
    /** Date ISO de suppression programmée (délai de grâce), ou null si le compte est actif. */
    val deletionScheduledAt: String? = null,
)

/**
 * ViewModel de la page profil éditable : préremplit depuis `/auth/me`, gère l'upload d'avatar,
 * l'enregistrement (`PATCH /users/me` — nom, bio, pays, numéro) et le **changement de mot de
 * passe** (`POST /auth/password/change`). Piloté par [com.dualmusic.feature.profile.ProfileScreen].
 *
 * @param repository lectures + écriture profil + mot de passe.
 * @param uploader upload de l'avatar (catégorie `avatar`).
 */
class EditProfileViewModel(
    private val repository: ProfileRepository,
    private val uploader: MediaUploader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    /** (Re)charge le formulaire avec le profil courant — sert aussi à annuler une édition. */
    fun load() {
        viewModelScope.launch {
            val me = runCatching { repository.me() }.getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    fullName = me.profile?.fullName ?: "",
                    bio = me.profile?.bio ?: "",
                    email = me.user.email,
                    avatarUrl = me.profile?.avatarUrl,
                    country = Countries.byCode(me.profile?.countryCode),
                    phone = me.profile?.phone ?: "",
                    currentPassword = "",
                    newPassword = "",
                    message = null,
                    passwordMessage = null,
                    deletionScheduledAt = me.user.deletionScheduledAt,
                )
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(fullName = v, message = null) }
    fun onBioChange(v: String) = _uiState.update { it.copy(bio = v, message = null) }
    fun onCountrySelected(c: Country) = _uiState.update { it.copy(country = c, message = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phone = v.filter { c -> c.isDigit() }, message = null) }
    fun onCurrentPasswordChange(v: String) = _uiState.update { it.copy(currentPassword = v, passwordMessage = null) }
    fun onNewPasswordChange(v: String) = _uiState.update { it.copy(newPassword = v, passwordMessage = null) }
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

    /**
     * Upload l'avatar sélectionné puis l'enregistre IMMÉDIATEMENT (`PATCH /users/me`, ce seul
     * champ — `explicitNulls = false` sur le serializer garantit qu'aucun autre champ du profil
     * n'est touché) : la photo doit être prise en compte dès le téléversement, sans dépendre du
     * bouton « Enregistrer » du formulaire (qui pouvait ne jamais être pressé).
     */
    fun uploadAvatar(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.AVATAR) }
                .onSuccess { url ->
                    _uiState.update { it.copy(uploading = false, avatarUrl = url) }
                    runCatching { repository.updateProfile(UpdateProfileRequest(avatarUrl = url)) }
                        .onFailure { e -> _uiState.update { it.copy(message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) } }
                }
                .onFailure { e -> _uiState.update { it.copy(uploading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) } }
        }
    }

    /** Enregistre le profil (nom, bio, pays, numéro), puis exécute [onDone] en cas de succès. */
    fun save(onDone: () -> Unit) {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching {
                repository.updateProfile(
                    UpdateProfileRequest(
                        fullName = s.fullName.trim().ifBlank { null },
                        bio = s.bio.trim().ifBlank { null },
                        avatarUrl = s.avatarUrl,
                        countryCode = s.country.code,
                        phone = s.phone.trim().ifBlank { null },
                        phoneCountryCode = s.country.dial,
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(saving = false, message = com.dualmusic.core.ui.i18n.appStrings.profileUpdated) }
                onDone()
            }.onFailure { e -> _uiState.update { it.copy(saving = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.saveFailed) } }
        }
    }

    /** Programme la suppression du compte (délai de grâce 20 jours), puis recharge l'état. */
    fun requestAccountDeletion() {
        viewModelScope.launch {
            runCatching { repository.requestAccountDeletion() }
            load()
        }
    }

    /** Annule une suppression programmée, puis recharge l'état. */
    fun cancelAccountDeletion() {
        viewModelScope.launch {
            runCatching { repository.cancelAccountDeletion() }
            load()
        }
    }

    /** Change le mot de passe (nouveau ≥ 8 caractères). */
    fun changePassword() {
        val s = _uiState.value
        if (s.newPassword.length < 8) {
            _uiState.update { it.copy(passwordMessage = com.dualmusic.core.ui.i18n.appStrings.newPasswordTooShort) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(changingPassword = true, passwordMessage = null) }
            runCatching { repository.changePassword(s.currentPassword, s.newPassword) }
                .onSuccess {
                    _uiState.update {
                        it.copy(changingPassword = false, currentPassword = "", newPassword = "", passwordMessage = com.dualmusic.core.ui.i18n.appStrings.passwordChanged)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(changingPassword = false, passwordMessage = e.message ?: com.dualmusic.core.ui.i18n.appStrings.changeFailed) } }
        }
    }
}

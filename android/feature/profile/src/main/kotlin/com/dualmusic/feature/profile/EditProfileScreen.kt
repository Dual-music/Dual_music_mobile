package com.dualmusic.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.geo.Countries
import com.dualmusic.domain.geo.Country
import com.dualmusic.domain.upload.UploadCategory
import com.dualmusic.domain.user.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran d'édition du profil. */
data class EditProfileUiState(
    val fullName: String = "",
    val bio: String = "",
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
)

/**
 * ViewModel d'édition du profil : préremplit depuis `/auth/me`, gère l'upload d'avatar,
 * l'enregistrement (`PATCH /users/me` — nom, bio, pays, numéro) et le **changement de mot
 * de passe** (`POST /auth/password/change`).
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

    /** Préremplit le formulaire avec le profil courant. */
    fun load() {
        viewModelScope.launch {
            val me = runCatching { repository.me() }.getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    fullName = me.profile?.fullName ?: "",
                    avatarUrl = me.profile?.avatarUrl,
                    country = Countries.byCode(me.profile?.countryCode),
                    phone = me.profile?.phone ?: "",
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

    /** Upload l'avatar sélectionné et mémorise son URL. */
    fun uploadAvatar(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.AVATAR) }
                .onSuccess { url -> _uiState.update { it.copy(uploading = false, avatarUrl = url) } }
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

/**
 * Écran d'édition du profil : avatar, nom, bio, **pays**, **numéro**, et **mot de passe**.
 *
 * @param viewModel source d'état.
 * @param onSaved appelé après un enregistrement réussi (retour au profil).
 */
@Composable
fun EditProfileScreen(viewModel: EditProfileViewModel, onSaved: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 5L * 1024 * 1024) }
                    .onSuccess { viewModel.uploadAvatar(it) }
                    .onFailure { viewModel.setMessage(it.message ?: strings.fileUnreadable) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar (photo ou initiale) + bouton de changement.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(DualMusicTheme.gradients.primary),
            contentAlignment = Alignment.Center,
        ) {
            val url = ui.avatarUrl
            if (!url.isNullOrBlank()) {
                DMRemoteImage(url = url, contentDescription = strings.avatar, modifier = Modifier.size(96.dp).clip(CircleShape))
            } else {
                Text(ui.fullName.take(1).uppercase().ifBlank { "?" }, color = colors.foreground, fontWeight = FontWeight.Bold)
            }
        }
        DMButton(
            if (ui.uploading) strings.uploading else strings.changePhoto,
            style = DMButtonStyle.SECONDARY,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )

        // --- Informations ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.fullName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(strings.fullName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text(strings.bio) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(strings.country, color = colors.mutedForeground)
                CountryDropdown(ui.country, viewModel::onCountrySelected)

                OutlinedTextField(
                    value = ui.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text(strings.phoneNumber) },
                    prefix = { Text("${ui.country.dial} ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                ui.message?.let { Text(it, color = colors.primaryGlow) }
                DMButton(
                    if (ui.saving) strings.saving else strings.save,
                    enabled = !ui.saving && !ui.uploading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.save(onSaved) },
                )
            }
        }

        // --- Changement de mot de passe ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                Text(strings.changePassword, color = colors.foreground, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = ui.currentPassword,
                    onValueChange = viewModel::onCurrentPasswordChange,
                    label = { Text(strings.currentPassword) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = { Text(strings.newPassword) },
                    supportingText = { Text(strings.atLeast8) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ui.passwordMessage?.let { Text(it, color = colors.primaryGlow) }
                DMButton(
                    if (ui.changingPassword) strings.changing else strings.changePassword,
                    style = DMButtonStyle.SECONDARY,
                    enabled = !ui.changingPassword,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::changePassword,
                )
            }
        }
    }
}

/** Sélecteur de pays (menu déroulant). */
@Composable
private fun CountryDropdown(current: Country, onSelect: (Country) -> Unit) {
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
                .clickable { expanded = true }
                .padding(DualMusicTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${current.name} (${current.dial})", color = colors.foreground)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.mutedForeground)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Countries.ALL.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.name} (${c.dial})") },
                    onClick = { onSelect(c); expanded = false },
                )
            }
        }
    }
}

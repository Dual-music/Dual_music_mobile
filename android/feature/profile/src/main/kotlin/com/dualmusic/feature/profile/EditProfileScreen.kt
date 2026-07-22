package com.dualmusic.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
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
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel d'édition du profil : préremplit depuis `/auth/me`, gère l'upload d'avatar et
 * l'enregistrement (`PATCH /users/me`).
 *
 * @param repository lectures + écriture profil.
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
                it.copy(fullName = me.profile?.fullName ?: "", avatarUrl = me.profile?.avatarUrl)
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(fullName = v, message = null) }
    fun onBioChange(v: String) = _uiState.update { it.copy(bio = v, message = null) }
    fun setMessage(text: String?) = _uiState.update { it.copy(message = text) }

    /** Upload l'avatar sélectionné et mémorise son URL. */
    fun uploadAvatar(media: com.dualmusic.core.upload.LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.AVATAR) }
                .onSuccess { url -> _uiState.update { it.copy(uploading = false, avatarUrl = url) } }
                .onFailure { e -> _uiState.update { it.copy(uploading = false, message = e.message ?: "Échec de l'upload.") } }
        }
    }

    /** Enregistre le profil, puis exécute [onDone] en cas de succès. */
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
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(saving = false, message = "✅ Profil mis à jour.") }
                onDone()
            }.onFailure { e -> _uiState.update { it.copy(saving = false, message = e.message ?: "Enregistrement impossible.") } }
        }
    }
}

/**
 * Écran d'édition du profil : avatar (upload), nom, bio.
 *
 * @param viewModel source d'état.
 * @param onSaved appelé après un enregistrement réussi (retour au profil).
 */
@Composable
fun EditProfileScreen(viewModel: EditProfileViewModel, onSaved: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 5L * 1024 * 1024) }
                    .onSuccess { viewModel.uploadAvatar(it) }
                    .onFailure { viewModel.setMessage(it.message ?: "Fichier illisible.") }
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
                DMRemoteImage(url = url, contentDescription = "Avatar", modifier = Modifier.size(96.dp).clip(CircleShape))
            } else {
                Text(ui.fullName.take(1).uppercase().ifBlank { "?" }, color = colors.foreground, fontWeight = FontWeight.Bold)
            }
        }
        DMButton(
            if (ui.uploading) "Upload…" else "Changer la photo",
            style = DMButtonStyle.SECONDARY,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )

        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.fullName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nom complet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ui.message?.let { Text(it, color = colors.primaryGlow) }
                DMButton(
                    if (ui.saving) "Enregistrement…" else "Enregistrer",
                    enabled = !ui.saving && !ui.uploading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.save(onSaved) },
                )
            }
        }
    }
}

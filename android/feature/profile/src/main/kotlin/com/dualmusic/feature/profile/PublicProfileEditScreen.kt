package com.dualmusic.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.LocalMedia
import com.dualmusic.core.upload.MediaUploader
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.creator.SocialPlatform
import com.dualmusic.domain.creator.UpdateArtistProfileRequest
import com.dualmusic.domain.creator.UpdateManagerProfileRequest
import com.dualmusic.domain.upload.UploadCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'éditeur de profil public (artiste ou manager). */
data class PublicProfileUiState(
    val isArtist: Boolean = true,
    /** Nom de scène (artiste) ou nom affiché (manager). */
    val name: String = "",
    val bio: String = "",
    val experience: String = "",
    val coverUrl: String? = null,
    val isPublic: Boolean = true,
    val social: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
) {
    /** Plateformes sociales à proposer selon le rôle. */
    val platforms: List<SocialPlatform> get() = if (isArtist) SocialPlatform.ARTIST else SocialPlatform.MANAGER
}

/**
 * ViewModel du **profil public créateur** : charge le profil artiste (`GET /users/:id`) ou
 * manager (`GET /managers/me`), et enregistre les champs éditables + les **liens sociaux**
 * (`PATCH /artists/me` ou `PATCH /managers/me`).
 *
 * @param repository lectures + écritures profils créateurs.
 * @param uploader upload de l'image de couverture (catégorie `image`).
 */
class PublicProfileViewModel(
    private val repository: ProfileRepository,
    private val uploader: MediaUploader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    /** Charge le profil public correspondant au rôle. */
    fun load(isArtist: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, isArtist = isArtist) }
            if (isArtist) {
                val me = runCatching { repository.me() }.getOrNull()
                val ap = me?.user?.id?.let { id -> runCatching { repository.myArtistProfile(id) }.getOrNull() }
                _uiState.update {
                    it.copy(
                        loading = false,
                        name = ap?.stageName ?: me?.profile?.stageName ?: "",
                        bio = ap?.bio ?: "",
                        coverUrl = ap?.coverImageUrl,
                        isPublic = ap?.isPublic ?: true,
                        social = ap?.socialLinks ?: emptyMap(),
                    )
                }
            } else {
                val mp = runCatching { repository.myManagerProfile() }.getOrNull()
                _uiState.update {
                    it.copy(
                        loading = false,
                        name = mp?.displayName ?: "",
                        bio = mp?.bio ?: "",
                        experience = mp?.experience ?: "",
                        coverUrl = mp?.coverImageUrl,
                        isPublic = mp?.isPublic ?: true,
                        social = mp?.socialLinks ?: emptyMap(),
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, message = null) }
    fun onBioChange(v: String) = _uiState.update { it.copy(bio = v, message = null) }
    fun onExperienceChange(v: String) = _uiState.update { it.copy(experience = v, message = null) }
    fun onPublicToggle(v: Boolean) = _uiState.update { it.copy(isPublic = v, message = null) }
    fun onSocialChange(key: String, value: String) =
        _uiState.update { it.copy(social = it.social.toMutableMap().apply { put(key, value) }, message = null) }

    /** Upload l'image de couverture et mémorise son URL. */
    fun uploadCover(media: LocalMedia) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            runCatching { uploader.upload(media, UploadCategory.IMAGE) }
                .onSuccess { url -> _uiState.update { it.copy(uploading = false, coverUrl = url) } }
                .onFailure { e -> _uiState.update { it.copy(uploading = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.uploadFailed) } }
        }
    }

    /** Enregistre le profil public (champs + liens sociaux non vides). */
    fun save(onDone: () -> Unit) {
        val s = _uiState.value
        val cleanedSocial = s.social.filterValues { it.isNotBlank() }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            runCatching {
                if (s.isArtist) {
                    repository.updateArtistProfile(
                        UpdateArtistProfileRequest(
                            stageName = s.name.trim().ifBlank { null },
                            bio = s.bio.trim().ifBlank { null },
                            coverImageUrl = s.coverUrl,
                            isPublic = s.isPublic,
                            socialLinks = cleanedSocial,
                        ),
                    )
                } else {
                    repository.updateManagerProfile(
                        UpdateManagerProfileRequest(
                            displayName = s.name.trim().ifBlank { null },
                            bio = s.bio.trim().ifBlank { null },
                            experience = s.experience.trim().ifBlank { null },
                            coverImageUrl = s.coverUrl,
                            isPublic = s.isPublic,
                            socialLinks = cleanedSocial,
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(saving = false, message = com.dualmusic.core.ui.i18n.appStrings.profileUpdated) }
                onDone()
            }.onFailure { e -> _uiState.update { it.copy(saving = false, message = e.message ?: com.dualmusic.core.ui.i18n.appStrings.saveFailed) } }
        }
    }
}

/**
 * Éditeur du **profil public** artiste/manager : image de couverture, nom de scène / nom
 * affiché, bio (+ expérience pour le manager), **liens de réseaux sociaux** (URL complètes),
 * et visibilité publique.
 *
 * @param viewModel source d'état.
 * @param isArtist vrai pour le profil artiste, faux pour le profil manager.
 * @param onSaved appelé après un enregistrement réussi.
 */
@Composable
fun PublicProfileEditScreen(viewModel: PublicProfileViewModel, isArtist: Boolean, onSaved: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(isArtist) { viewModel.load(isArtist) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readLocalMedia(context, uri, maxBytes = 5L * 1024 * 1024) }
                    .onSuccess { viewModel.uploadCover(it) }
                    .onFailure { /* échec de lecture — ignoré (rare) */ }
            }
        }
    }

    if (ui.loading) {
        DMLoadingBox(Modifier.fillMaxSize())
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        // Image de couverture (16:9) + bouton de changement.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                .background(colors.card),
            contentAlignment = Alignment.Center,
        ) {
            val cover = ui.coverUrl
            if (!cover.isNullOrBlank()) {
                DMRemoteImage(url = cover, contentDescription = strings.coverImage, modifier = Modifier.fillMaxSize())
            } else {
                Text(strings.coverImage, color = colors.mutedForeground)
            }
        }
        DMButton(
            if (ui.uploading) strings.uploading else strings.changeCover,
            style = DMButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )

        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(if (isArtist) strings.stageName else strings.fullName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text(strings.bio) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isArtist) {
                    OutlinedTextField(
                        value = ui.experience,
                        onValueChange = viewModel::onExperienceChange,
                        label = { Text(strings.managerExpLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Liens sociaux.
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                Text(strings.socialLinks, color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(strings.socialLinksHint, color = colors.mutedForeground)
                ui.platforms.forEach { p ->
                    OutlinedTextField(
                        value = ui.social[p.key] ?: "",
                        onValueChange = { viewModel.onSocialChange(p.key, it) },
                        label = { Text(p.label) },
                        placeholder = { Text(p.hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Visibilité publique.
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.makeProfilePublic, color = colors.foreground)
                Switch(checked = ui.isPublic, onCheckedChange = viewModel::onPublicToggle)
            }
        }

        ui.message?.let { Text(it, color = colors.primaryGlow) }
        DMButton(
            if (ui.saving) strings.saving else strings.save,
            enabled = !ui.saving && !ui.uploading,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.save(onSaved) },
        )
    }
}

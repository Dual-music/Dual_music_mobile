package com.dualmusic.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.role.ApplyArtistRequest
import com.dualmusic.domain.role.ApplyManagerRequest
import com.dualmusic.domain.role.RoleEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran « Devenir artiste/manager ». */
data class BecomeRoleUiState(
    val artistEnabled: Boolean = true,
    val managerEnabled: Boolean = true,
    val artistPending: Boolean = false,
    val managerPending: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel des candidatures de rôle. Charge le gating (réglages admin) + le statut de mes
 * candidatures ; soumet les demandes.
 */
class BecomeRoleViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BecomeRoleUiState())
    val uiState: StateFlow<BecomeRoleUiState> = _uiState.asStateFlow()

    /** Charge les réglages d'ouverture + mes candidatures en attente. */
    fun load() {
        viewModelScope.launch {
            val artistEnabled = repository.requestsEnabled(RoleEndpoints.ARTIST_REQUESTS_ENABLED)
            val managerEnabled = repository.requestsEnabled(RoleEndpoints.MANAGER_REQUESTS_ENABLED)
            val artistPending = runCatching { repository.myArtistRequests() }.getOrDefault(emptyList())
                .any { it.status == "pending" }
            val managerPending = runCatching { repository.myManagerRequests() }.getOrDefault(emptyList())
                .any { it.status == "pending" }
            _uiState.update {
                it.copy(
                    artistEnabled = artistEnabled,
                    managerEnabled = managerEnabled,
                    artistPending = artistPending,
                    managerPending = managerPending,
                )
            }
        }
    }

    /** Soumet une candidature artiste (description ≥ 10 caractères) — mêmes champs que le web. */
    fun applyArtist(description: String, documentUrl: String, socialLinks: Map<String, String>) {
        if (description.trim().length < 10) {
            _uiState.update { it.copy(message = "Décris ton projet (au moins 10 caractères).") }
            return
        }
        submit {
            repository.applyArtist(
                ApplyArtistRequest(
                    description = description.trim(),
                    socialLinks = socialLinks.filterValues { it.isNotBlank() },
                    justificationDocumentUrl = documentUrl.trim().ifBlank { null },
                ),
            )
        }
    }

    /** Soumet une candidature manager (bio + expérience requises). */
    fun applyManager(bio: String, experience: String) {
        if (bio.trim().length < 10 || experience.trim().length < 5) {
            _uiState.update { it.copy(message = "Renseigne ta bio et ton expérience.") }
            return
        }
        submit { repository.applyManager(ApplyManagerRequest(bio = bio.trim(), experience = experience.trim())) }
    }

    private fun submit(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, message = null) }
            runCatching { action() }
                .onSuccess {
                    _uiState.update { it.copy(submitting = false, message = "✅ Demande envoyée — en attente de validation.") }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, message = e.message ?: "Envoi impossible.") } }
        }
    }
}

/**
 * Écran dédié **« Devenir artiste »** (réservé aux fans) — formulaire séparé, comme le web.
 * Ouvert, fermé (réglage admin) ou déjà en attente.
 *
 * @param viewModel source d'état (partagée avec l'écran manager).
 */
@Composable
fun BecomeArtistScreen(viewModel: BecomeRoleViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    LaunchedEffect(Unit) { viewModel.load() }

    var description by remember { mutableStateOf("") }
    var documentUrl by remember { mutableStateOf("") }
    var instagram by remember { mutableStateOf("") }
    var tiktok by remember { mutableStateOf("") }
    var youtube by remember { mutableStateOf("") }
    var twitter by remember { mutableStateOf("") }
    var facebook by remember { mutableStateOf("") }
    var spotify by remember { mutableStateOf("") }

    RoleColumn {
        ui.message?.let { Text(it, color = colors.primaryGlow) }
        RoleCard(title = "Devenir artiste", enabled = ui.artistEnabled, pending = ui.artistPending) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Présente ton projet musical *") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = documentUrl,
                onValueChange = { documentUrl = it },
                label = { Text("Lien d'un document justificatif (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Réseaux sociaux (optionnel)", color = colors.mutedForeground)
            SocialField("Instagram", instagram) { instagram = it }
            SocialField("TikTok", tiktok) { tiktok = it }
            SocialField("YouTube", youtube) { youtube = it }
            SocialField("X (Twitter)", twitter) { twitter = it }
            SocialField("Facebook", facebook) { facebook = it }
            SocialField("Spotify", spotify) { spotify = it }
            DMButton(
                if (ui.submitting) "Envoi…" else "Envoyer ma candidature",
                enabled = !ui.submitting,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.applyArtist(
                        description = description,
                        documentUrl = documentUrl,
                        socialLinks = mapOf(
                            "instagram" to instagram,
                            "tiktok" to tiktok,
                            "youtube" to youtube,
                            "twitter" to twitter,
                            "facebook" to facebook,
                            "spotify" to spotify,
                        ),
                    )
                },
            )
        }
    }
}

/** Champ de lien d'un réseau social. */
@Composable
private fun SocialField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Écran dédié **« Devenir manager »** (réservé aux fans) — formulaire séparé, comme le web.
 * ⚠️ N'apparaît côté profil que si l'admin a ouvert les candidatures manager.
 *
 * @param viewModel source d'état (partagée avec l'écran artiste).
 */
@Composable
fun BecomeManagerScreen(viewModel: BecomeRoleViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    LaunchedEffect(Unit) { viewModel.load() }

    var bio by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    RoleColumn {
        ui.message?.let { Text(it, color = colors.primaryGlow) }
        RoleCard(title = "Devenir manager", enabled = ui.managerEnabled, pending = ui.managerPending) {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = experience,
                onValueChange = { experience = it },
                label = { Text("Expérience") },
                modifier = Modifier.fillMaxWidth(),
            )
            DMButton(
                if (ui.submitting) "Envoi…" else "Envoyer ma candidature",
                enabled = !ui.submitting,
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.applyManager(bio, experience) },
            )
        }
    }
}

/** Conteneur commun (fond dégradé + scroll + padding) des écrans de candidature. */
@Composable
private fun RoleColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
        content = content,
    )
}

/**
 * Carte d'une candidature de rôle. Affiche le formulaire ([form]) si ouvert et non déjà en
 * attente ; sinon un message (en attente de validation, ou candidatures fermées par l'admin).
 */
@Composable
private fun RoleCard(
    title: String,
    enabled: Boolean,
    pending: Boolean,
    form: @Composable () -> Unit,
) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold)
            when {
                pending -> Text("⏳ Ta demande est en attente de validation.", color = colors.mutedForeground)
                !enabled -> Text(
                    "Les candidatures sont actuellement fermées. L'administrateur désigne directement les promotions.",
                    color = colors.mutedForeground,
                )
                else -> form()
            }
        }
    }
}

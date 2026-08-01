package com.dualmusic.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.admin.AdminUser
import com.dualmusic.domain.role.RoleEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'espace admin. */
data class AdminUiState(
    val managerEnabled: Boolean = true,
    val artistEnabled: Boolean = true,
    /** Autorise les managers à créer des duels eux-mêmes (défaut faux : l'admin assigne). */
    val duelCreationEnabled: Boolean = false,
    val query: String = "",
    val users: List<AdminUser> = emptyList(),
    val searching: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel de l'espace admin : bascule l'ouverture des candidatures (artiste/manager) et
 * assigne/révoque directement un rôle à un utilisateur (recherche par nom/email).
 *
 * @param repository accès REST (méthodes `admin*`).
 */
class AdminViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    /** Charge l'état courant des deux réglages d'ouverture des candidatures. */
    fun load() {
        viewModelScope.launch {
            val manager = repository.requestsEnabled(RoleEndpoints.MANAGER_REQUESTS_ENABLED)
            val artist = repository.requestsEnabled(RoleEndpoints.ARTIST_REQUESTS_ENABLED)
            val duelCreate = repository.settingEnabled(RoleEndpoints.MANAGER_DUEL_CREATION, default = false)
            _uiState.update { it.copy(managerEnabled = manager, artistEnabled = artist, duelCreationEnabled = duelCreate) }
        }
    }

    fun onQueryChange(v: String) = _uiState.update { it.copy(query = v, message = null) }

    /** Ouvre/ferme les candidatures « Devenir manager ». */
    fun toggleManager(enabled: Boolean) = setRequests(RoleEndpoints.MANAGER_REQUESTS_ENABLED, enabled) {
        _uiState.update { it.copy(managerEnabled = enabled) }
    }

    /** Ouvre/ferme les candidatures « Devenir artiste ». */
    fun toggleArtist(enabled: Boolean) = setRequests(RoleEndpoints.ARTIST_REQUESTS_ENABLED, enabled) {
        _uiState.update { it.copy(artistEnabled = enabled) }
    }

    /** Autorise/interdit aux managers de créer des duels (sinon assignés par l'admin). */
    fun toggleDuelCreation(enabled: Boolean) = setRequests(RoleEndpoints.MANAGER_DUEL_CREATION, enabled) {
        _uiState.update { it.copy(duelCreationEnabled = enabled) }
    }

    private fun setRequests(key: String, enabled: Boolean, onOk: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.adminSetRequestsEnabled(key, enabled) }
                .onSuccess { onOk() }
                .onFailure { e -> _uiState.update { it.copy(message = e.message ?: "Modification impossible.") } }
        }
    }

    /** Recherche des utilisateurs par nom/email. */
    fun search() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, message = null) }
            runCatching { repository.adminSearchUsers(q) }
                .onSuccess { list -> _uiState.update { it.copy(searching = false, users = list) } }
                .onFailure { e -> _uiState.update { it.copy(searching = false, message = e.message ?: "Recherche impossible.") } }
        }
    }

    /** Assigne un rôle à un utilisateur. */
    fun assign(userId: String, role: String) {
        viewModelScope.launch {
            runCatching { repository.adminAssignRole(userId, role) }
                .onSuccess { _uiState.update { it.copy(message = "✅ Rôle « $role » assigné.") } }
                .onFailure { e -> _uiState.update { it.copy(message = e.message ?: "Assignation impossible.") } }
        }
    }

    /** Révoque un rôle d'un utilisateur. */
    fun revoke(userId: String, role: String) {
        viewModelScope.launch {
            runCatching { repository.adminRevokeRole(userId, role) }
                .onSuccess { _uiState.update { it.copy(message = "Rôle « $role » révoqué.") } }
                .onFailure { e -> _uiState.update { it.copy(message = e.message ?: "Révocation impossible.") } }
        }
    }
}

/**
 * Espace admin : ouverture des candidatures + assignation directe de rôle.
 *
 * @param viewModel source d'état.
 */
@Composable
fun AdminScreen(viewModel: AdminViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Espace admin", color = colors.foreground, fontWeight = FontWeight.Bold)
        ui.message?.let { Text(it, color = colors.primaryGlow) }

        // Ouverture des candidatures.
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text("Candidatures de rôle", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(
                    "Fermé : les fans ne voient plus le formulaire ; tu assignes le rôle manuellement ci-dessous.",
                    color = colors.mutedForeground,
                )
                AdminToggle("Demandes « Devenir artiste »", ui.artistEnabled) { viewModel.toggleArtist(it) }
                AdminToggle("Demandes « Devenir manager »", ui.managerEnabled) { viewModel.toggleManager(it) }
                AdminToggle("Création de duels par les managers", ui.duelCreationEnabled) { viewModel.toggleDuelCreation(it) }
            }
        }

        // Assignation directe de rôle.
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text("Assigner un rôle", color = colors.foreground, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Rechercher (nom ou email)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DMButton(
                    if (ui.searching) "Recherche…" else "Rechercher",
                    style = DMButtonStyle.SECONDARY,
                    onClick = viewModel::search,
                )
            }
        }

        ui.users.forEach { user ->
            AdminUserRow(
                user = user,
                onAssign = { role -> viewModel.assign(user.id, role) },
                onRevoke = { role -> viewModel.revoke(user.id, role) },
            )
        }
    }
}

/** Interrupteur libellé + Switch. */
@Composable
private fun AdminToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.foreground)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ligne d'un utilisateur trouvé : assigner artiste/manager (ou révoquer). */
@Composable
private fun AdminUserRow(user: AdminUser, onAssign: (String) -> Unit, onRevoke: (String) -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(user.label, color = colors.foreground, fontWeight = FontWeight.Bold)
            user.email?.let { Text(it, color = colors.mutedForeground) }
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                DMButton("+ Artiste", modifier = Modifier.weight(1f), onClick = { onAssign("artist") })
                DMButton("+ Manager", style = DMButtonStyle.SECONDARY, modifier = Modifier.weight(1f), onClick = { onAssign("manager") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                DMButton("– Artiste", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { onRevoke("artist") })
                DMButton("– Manager", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = { onRevoke("manager") })
            }
        }
    }
}

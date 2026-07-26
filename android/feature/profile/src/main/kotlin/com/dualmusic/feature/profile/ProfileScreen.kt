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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.core.upload.readLocalMedia
import com.dualmusic.domain.geo.Countries
import com.dualmusic.domain.geo.Country
import kotlinx.coroutines.launch

/**
 * Page **Profil** : reprend la mise en page de l'ancienne page « Modifier » (photo centrée
 * appréciée) mais présentée comme une **fiche en lecture seule** par défaut — tous les champs
 * sont grisés. Une **icône crayon** bascule en mode édition : seuls les champs modifiables
 * deviennent manipulables, avec Enregistrer / Annuler. Le **mot de passe** est masqué par
 * défaut dans une section repliée qu'on déroule pour le changer.
 *
 * Remplace l'écran d'édition séparé (les infos sont désormais directement sur le profil).
 *
 * @param viewModel source d'état (chargement `/auth/me`, upload avatar, save, mot de passe).
 * @param onOpenMenu ouvre le menu du profil (sections + déconnexion).
 */
@Composable
fun ProfileScreen(viewModel: EditProfileViewModel, onOpenMenu: () -> Unit) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var passwordExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
        // Barre : titre + crayon (édition) + menu.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.profileTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (editing) { viewModel.load(); editing = false } else editing = true
                }) {
                    Icon(
                        if (editing) Icons.Filled.Close else Icons.Filled.Edit,
                        contentDescription = strings.edit,
                        tint = if (editing) colors.mutedForeground else colors.accent,
                    )
                }
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = colors.foreground)
                }
            }
        }

        // Avatar centré (photo ou initiale).
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
        if (editing) {
            DMButton(
                if (ui.uploading) strings.uploading else strings.changePhoto,
                style = DMButtonStyle.SECONDARY,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
        // Email (toujours en lecture seule).
        if (ui.email.isNotBlank()) Text(ui.email, color = colors.mutedForeground)

        // --- Informations (grisées hors édition) ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                OutlinedTextField(
                    value = ui.fullName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(strings.fullName) },
                    enabled = editing,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text(strings.bio) },
                    enabled = editing,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(strings.country, color = colors.mutedForeground)
                CountryDropdown(ui.country, viewModel::onCountrySelected, enabled = editing)
                OutlinedTextField(
                    value = ui.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text(strings.phoneNumber) },
                    prefix = { Text("${ui.country.dial} ") },
                    enabled = editing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                ui.message?.let { Text(it, color = colors.primaryGlow) }

                if (editing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm),
                    ) {
                        DMButton(
                            strings.cancel,
                            style = DMButtonStyle.OUTLINE,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.load(); editing = false },
                        )
                        DMButton(
                            if (ui.saving) strings.saving else strings.save,
                            enabled = !ui.saving && !ui.uploading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.save { editing = false } },
                        )
                    }
                }
            }
        }

        // --- Mot de passe (replié par défaut) ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { passwordExpanded = !passwordExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.mutedForeground)
                    Column(modifier = Modifier.weight(1f).padding(start = DualMusicTheme.spacing.md)) {
                        Text(strings.changePassword, color = colors.foreground, fontWeight = FontWeight.Bold)
                        if (!passwordExpanded) Text(strings.changePasswordSectionHint, color = colors.mutedForeground)
                    }
                    Icon(
                        if (passwordExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.mutedForeground,
                    )
                }

                if (passwordExpanded) {
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

        // --- Zone sensible : suppression de compte (délai de grâce 20 jours) ---
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text(strings.deleteAccount, color = colors.destructive, fontWeight = FontWeight.Bold)
                val scheduled = ui.deletionScheduledAt
                if (scheduled != null) {
                    Text("${strings.accountDeletionScheduledOn} ${scheduled.take(10)}. ${strings.deletionCanStillCancel}", color = colors.destructive)
                    DMButton(
                        strings.cancelDeletion,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::cancelAccountDeletion,
                    )
                } else if (!confirmDelete) {
                    DMButton(
                        strings.deleteAccount,
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDelete = true },
                    )
                } else {
                    Text(strings.deleteAccountGrace, color = colors.mutedForeground)
                    Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                        DMButton(
                            strings.cancel,
                            style = DMButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmDelete = false },
                        )
                        DMButton(
                            strings.confirm,
                            style = DMButtonStyle.OUTLINE,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmDelete = false; viewModel.requestAccountDeletion() },
                        )
                    }
                }
            }
        }
    }
}

/** Sélecteur de pays (menu déroulant) — désactivé (grisé, non cliquable) hors édition. */
@Composable
internal fun CountryDropdown(current: Country, onSelect: (Country) -> Unit, enabled: Boolean = true) {
    val colors = DualMusicTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val textColor = if (enabled) colors.foreground else colors.mutedForeground
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DualMusicTheme.radii.md))
                .border(1.dp, colors.border, RoundedCornerShape(DualMusicTheme.radii.md))
                .then(if (enabled) Modifier.clickable { expanded = true } else Modifier)
                .padding(DualMusicTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${current.name} (${current.dial})", color = textColor)
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

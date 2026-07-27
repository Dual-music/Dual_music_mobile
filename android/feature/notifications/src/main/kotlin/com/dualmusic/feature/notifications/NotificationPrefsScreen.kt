package com.dualmusic.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.notification.NotificationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel des préférences d'emails de notification (`GET/PUT /notifications/preferences`).
 *
 * @param repository accès REST notifications.
 */
class NotificationPrefsViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _prefs = MutableStateFlow(NotificationPreferences())
    val prefs: StateFlow<NotificationPreferences> = _prefs.asStateFlow()

    /** Charge les préférences courantes. */
    fun load() {
        viewModelScope.launch {
            _prefs.value = runCatching { repository.emailPreferences() }.getOrDefault(NotificationPreferences())
        }
    }

    /** Applique une nouvelle valeur (optimiste) et persiste côté serveur. */
    fun update(newPrefs: NotificationPreferences) {
        _prefs.value = newPrefs
        viewModelScope.launch {
            runCatching { repository.updateEmailPreferences(newPrefs) }.onSuccess { _prefs.value = it }
        }
    }
}

/**
 * Écran « Notifs » du menu profil — préférences d'emails par catégorie (comme le web).
 * La liste in-app reste accessible via la cloche d'accueil.
 *
 * @param viewModel source d'état.
 */
@Composable
fun NotificationPrefsScreen(viewModel: NotificationPrefsViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .verticalScroll(rememberScrollState())
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.emailNotifs, color = colors.foreground, fontWeight = FontWeight.Bold)
        Text(s.emailNotifsHint, color = colors.mutedForeground)

        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                PrefRow(s.navConcerts, prefs.emailConcerts) { viewModel.update(prefs.copy(emailConcerts = it)) }
                PrefRow(s.navDuels, prefs.emailDuels) { viewModel.update(prefs.copy(emailDuels = it)) }
                PrefRow(s.navLives, prefs.emailLives) { viewModel.update(prefs.copy(emailLives = it)) }
                PrefRow(s.notifGifts, prefs.emailGifts) { viewModel.update(prefs.copy(emailGifts = it)) }
                PrefRow(s.notifVotes, prefs.emailVotes) { viewModel.update(prefs.copy(emailVotes = it)) }
                PrefRow(s.notifRequests, prefs.emailRequests) { viewModel.update(prefs.copy(emailRequests = it)) }
                PrefRow(s.notifAssignments, prefs.emailAssignments) { viewModel.update(prefs.copy(emailAssignments = it)) }
                // Emails système : requis, non désactivable.
                PrefRow("${s.notifSystem} · ${s.notifRequired}", enabled = false, checked = true) {}
            }
        }
    }
}

/** Ligne d'une préférence : libellé + interrupteur. */
@Composable
private fun PrefRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DualMusicTheme.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.foreground)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

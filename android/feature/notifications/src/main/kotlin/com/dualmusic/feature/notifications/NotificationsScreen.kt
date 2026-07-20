package com.dualmusic.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualmusic.core.realtime.NamespaceSession
import com.dualmusic.core.realtime.RealtimeClient
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.notification.AppNotification
import com.dualmusic.domain.realtime.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel du centre de notifications in-app.
 *
 * Charge l'historique (REST) puis écoute le namespace Socket.IO `/notifications` : chaque
 * nouvelle notification poussée au user courant est ajoutée en tête de liste.
 *
 * @param repository accès REST aux notifications.
 * @param realtime client Socket.IO partagé.
 */
class NotificationsViewModel(
    private val repository: NotificationRepository,
    private val realtime: RealtimeClient,
) : ViewModel() {

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    private var session: NamespaceSession? = null

    /** Charge l'historique + connecte le temps réel. */
    fun start() {
        viewModelScope.launch {
            runCatching { repository.list() }.getOrNull()?.let { _items.value = it }
        }
        val notif = realtime.session(Realtime.Namespace.NOTIFICATIONS).also { session = it }
        viewModelScope.launch {
            // Pas de room à rejoindre : le handshake JWT identifie le user ; le serveur
            // pousse ses notifications directement.
            notif.on(Realtime.RealtimeEvent.NOTIFICATION, AppNotification.serializer()) { n ->
                _items.update { listOf(n) + it }
            }
            notif.connect()
        }
    }

    /** Coupe le temps réel. */
    fun stop() { session?.disconnect() }

    /** Marque une notification lue (optimiste + serveur). */
    fun markRead(id: String) {
        _items.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
        viewModelScope.launch { runCatching { repository.markRead(id) } }
    }

    /** Marque tout lu (optimiste + serveur). */
    fun markAllRead() {
        _items.update { list -> list.map { it.copy(read = true) } }
        viewModelScope.launch { runCatching { repository.markAllRead() } }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

/**
 * Centre de notifications in-app.
 *
 * @param viewModel source d'état.
 */
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notifications", color = colors.foreground, fontWeight = FontWeight.Bold)
            if (items.any { !it.read }) {
                DMButton("Tout lu", style = DMButtonStyle.OUTLINE, onClick = viewModel::markAllRead)
            }
        }

        if (items.isEmpty()) {
            DMEmptyState(
                title = "Aucune notification",
                subtitle = "Tes alertes apparaîtront ici.",
                icon = Icons.Filled.Notifications,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(items) { notif ->
                NotificationRow(notif) { viewModel.markRead(notif.id) }
            }
        }
    }
}

/** Ligne de notification (point non-lu + titre + message). */
@Composable
private fun NotificationRow(notif: AppNotification, onClick: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pastille non-lu.
            if (!notif.read) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
            Column(modifier = Modifier.padding(start = DualMusicTheme.spacing.sm)) {
                notif.title?.let {
                    Text(
                        it,
                        color = colors.foreground,
                        fontWeight = if (notif.read) FontWeight.Normal else FontWeight.Bold,
                    )
                }
                notif.message?.let { Text(it, color = colors.mutedForeground) }
            }
        }
    }
}

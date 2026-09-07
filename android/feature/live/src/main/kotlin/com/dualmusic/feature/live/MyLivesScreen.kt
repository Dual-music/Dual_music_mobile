package com.dualmusic.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.components.DMLoadingBox
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.model.EventStatus
import com.dualmusic.domain.model.Live
import com.dualmusic.domain.user.UserEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * ViewModel de « Mes Lives » : liste les lives de l'artiste (`GET /lives?artistId=me`),
 * lance un live (`POST /lives {title}`) et termine le live actif (`POST /lives/:id/end`).
 * Mêmes endpoints que le web (ArtistLivesManager).
 *
 * @param api client HTTP.
 */
class MyLivesViewModel(private val api: ApiClient) : ViewModel() {

    private val json = Json { explicitNulls = false }
    private var myUserId: String? = null

    private val _lives = MutableStateFlow<List<Live>>(emptyList())
    val lives: StateFlow<List<Live>> = _lives.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Live tout juste créé, à ouvrir en diffusion (consommé par l'écran). */
    private val _pendingBroadcast = MutableStateFlow<Live?>(null)
    val pendingBroadcast: StateFlow<Live?> = _pendingBroadcast.asStateFlow()

    /** Charge l'id du caller + ses lives. */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            myUserId = runCatching { api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer()) }
                .getOrNull()?.user?.id
            _lives.value = myUserId?.let { uid ->
                runCatching {
                    api.request(
                        Endpoint.get("/lives", query = mapOf("artistId" to uid)),
                        ListSerializer(Live.serializer()),
                    )
                }.getOrDefault(emptyList())
            } ?: emptyList()
            _isLoading.value = false
        }
    }

    /**
     * Lance un live (titre optionnel), puis recharge.
     * @param allowsDedications dédicaces activées pour ce live.
     * @param dedicationMinPriceCredits prix minimum propre à ce live (`null` = défaut global).
     * @param allowGuests demandes d'invité (« lever la main ») activées pour ce live.
     */
    fun createLive(
        title: String,
        allowsDedications: Boolean = true,
        dedicationMinPriceCredits: Double? = null,
        allowGuests: Boolean = true,
    ) {
        viewModelScope.launch {
            _message.value = null
            val safe = title.ifBlank { "Live" }
            val price = dedicationMinPriceCredits?.let { ""","dedicationMinPriceCredits":$it""" } ?: ""
            val body = "{\"title\":" + json.encodeToString(String.serializer(), safe) +
                ",\"allowsDedications\":$allowsDedications,\"allowGuests\":$allowGuests$price}"
            runCatching { api.request(Endpoint.post("/lives", body), Live.serializer()) }
                .onSuccess { created -> _pendingBroadcast.value = created; load() }
                .onFailure { e -> _message.value = e.message }
        }
    }

    /** Réinitialise le live en attente une fois la diffusion ouverte. */
    fun consumePending() {
        _pendingBroadcast.value = null
    }

    /** Termine le live actif, puis recharge. */
    fun endLive(id: String) {
        viewModelScope.launch {
            runCatching { api.request<Unit>(Endpoint.post("/lives/$id/end")) }.onSuccess { load() }
        }
    }
}

/**
 * Écran « Mes Lives » — lancer un live, voir/terminer le live actif, et l'historique
 * des lives terminés. Équivalent mobile de ArtistLivesManager du web.
 *
 * @param viewModel source d'état.
 */
@Composable
fun MyLivesScreen(
    viewModel: MyLivesViewModel,
    onStartBroadcast: (Live) -> Unit,
) {
    val lives by viewModel.lives.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pending by viewModel.pendingBroadcast.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    var title by remember { mutableStateOf("") }
    var allowsDedications by remember { mutableStateOf(true) }
    var allowGuests by remember { mutableStateOf(true) }
    var minPriceText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }
    // Ouvre la diffusion plein écran dès qu'un live vient d'être créé.
    LaunchedEffect(pending) {
        pending?.let { onStartBroadcast(it); viewModel.consumePending() }
    }

    val active = lives.firstOrNull { it.status == EventStatus.LIVE }
    val past = lives.filter { it.status == EventStatus.ENDED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        message?.let { Text(it, color = colors.destructive) }

        // Lancer un live.
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                Text(s.myLives, color = colors.foreground, fontWeight = FontWeight.Bold)
                Text(s.myLivesHint, color = colors.mutedForeground)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(s.liveTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Réglages du live (modifiables aussi en direct depuis les contrôles ⚙️).
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton(
                        "Dédicaces ${if (allowsDedications) "ON" else "OFF"}",
                        style = if (allowsDedications) DMButtonStyle.PRIMARY else DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = { allowsDedications = !allowsDedications },
                    )
                    DMButton(
                        "Invités ${if (allowGuests) "ON" else "OFF"}",
                        style = if (allowGuests) DMButtonStyle.PRIMARY else DMButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = { allowGuests = !allowGuests },
                    )
                }
                if (allowsDedications) {
                    OutlinedTextField(
                        value = minPriceText,
                        onValueChange = { v -> minPriceText = v.filter { it.isDigit() } },
                        label = { Text("Prix minimum dédicace (vide = défaut)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DMButton(
                    s.startLive,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = active == null && !isLoading,
                    onClick = {
                        viewModel.createLive(title, allowsDedications, minPriceText.toDoubleOrNull(), allowGuests)
                        title = ""; minPriceText = ""
                    },
                )
            }
        }

        // Live actif (terminable).
        active?.let { live ->
            DMCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).clickable { onStartBroadcast(live) }) {
                        Text("🔴 ${live.title ?: s.liveActive}", color = colors.primary, fontWeight = FontWeight.Bold)
                        Text("${live.viewerCount} 👁", color = colors.mutedForeground)
                    }
                    // « Terminer » compact (icône) — ne masque plus le titre.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.destructive, CircleShape)
                            .clickable { viewModel.endLive(live.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = s.endLive,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Historique des lives terminés.
        when {
            isLoading -> DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
            past.isEmpty() && active == null -> DMEmptyState(
                title = s.noLives,
                subtitle = s.noLivesHint,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(past) { live -> PastLiveRow(live) }
            }
        }
    }
}

/** Ligne d'un live terminé : titre + nombre de spectateurs. */
@Composable
private fun PastLiveRow(live: Live) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(live.title ?: "Live", color = colors.foreground, fontWeight = FontWeight.Bold)
            Text("${live.viewerCount} 👁", color = colors.mutedForeground)
        }
    }
}

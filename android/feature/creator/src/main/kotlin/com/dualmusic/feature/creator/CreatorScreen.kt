package com.dualmusic.feature.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.auth.MeResponse
import com.dualmusic.domain.creator.CreatorEndpoints
import com.dualmusic.domain.creator.DuelRequestItem
import com.dualmusic.domain.creator.RespondDuelRequest
import com.dualmusic.domain.model.Concert
import com.dualmusic.domain.user.UserEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** État de l'espace créateur. */
data class CreatorUiState(
    val myUserId: String? = null,
    val duelRequests: List<DuelRequestItem> = emptyList(),
    val concerts: List<Concert> = emptyList(),
)

/**
 * ViewModel des outils créateur : défis de duel (répondre) + mes concerts.
 *
 * On charge l'id du caller (`/auth/me`) pour distinguer les défis REÇUS (répondables) des
 * défis ÉMIS. La réponse à un défi est validée/autorisée côté serveur.
 *
 * @param api client HTTP.
 */
class CreatorViewModel(private val api: ApiClient) : ViewModel() {

    private val json = Json { explicitNulls = false }

    private val _uiState = MutableStateFlow(CreatorUiState())
    val uiState: StateFlow<CreatorUiState> = _uiState.asStateFlow()

    /** Charge l'id du caller + les défis + les concerts. */
    fun load() {
        viewModelScope.launch {
            val me = runCatching { api.request(Endpoint.get(UserEndpoints.ME), MeResponse.serializer()) }.getOrNull()
            val requests = runCatching {
                api.request(Endpoint.get(CreatorEndpoints.DUEL_REQUESTS_MINE), ListSerializer(DuelRequestItem.serializer()))
            }.getOrDefault(emptyList())
            val concerts = runCatching {
                api.request(Endpoint.get(CreatorEndpoints.MY_CONCERTS), ListSerializer(Concert.serializer()))
            }.getOrDefault(emptyList())
            _uiState.value = CreatorUiState(myUserId = me?.user?.id, duelRequests = requests, concerts = concerts)
        }
    }

    /** Répond à un défi reçu (accepter/refuser), puis recharge. */
    fun respond(id: String, accept: Boolean) {
        viewModelScope.launch {
            val body = json.encodeToString(RespondDuelRequest.serializer(), RespondDuelRequest(accept))
            runCatching { api.request<Unit>(Endpoint.post(CreatorEndpoints.duelRespond(id), body)) }
                .onSuccess { load() }
        }
    }
}

/**
 * Espace créateur : deux onglets — Défis de duel (répondre) et Mes concerts.
 *
 * @param viewModel source d'état.
 */
@Composable
fun CreatorScreen(viewModel: CreatorViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text("Espace créateur", color = colors.foreground, fontWeight = FontWeight.Bold)
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = colors.foreground) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Défis") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mes concerts") })
        }

        if (tab == 0) {
            if (ui.duelRequests.isEmpty()) Text("Aucun défi.", color = colors.mutedForeground)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(ui.duelRequests) { req ->
                    DuelRequestRow(
                        request = req,
                        // Répondable si je suis le destinataire et que c'est en attente.
                        canRespond = req.opponentId == ui.myUserId && req.status == "pending",
                        onAccept = { viewModel.respond(req.id, true) },
                        onDecline = { viewModel.respond(req.id, false) },
                    )
                }
            }
        } else {
            if (ui.concerts.isEmpty()) Text("Aucun concert.", color = colors.mutedForeground)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                items(ui.concerts) { concert -> ConcertRow(concert) }
            }
        }
    }
}

/** Ligne d'un défi de duel : accepter/refuser si reçu, sinon statut. */
@Composable
private fun DuelRequestRow(
    request: DuelRequestItem,
    canRespond: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(request.message ?: "Défi de duel", color = colors.foreground, fontWeight = FontWeight.Bold)
            request.proposedDate?.let { Text("Proposé : ${it.take(16)}", color = colors.mutedForeground) }
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                    DMButton("Accepter", modifier = Modifier.weight(1f), onClick = onAccept)
                    DMButton("Refuser", style = DMButtonStyle.OUTLINE, modifier = Modifier.weight(1f), onClick = onDecline)
                }
            } else {
                Text(statusLabel(request.status), color = colors.mutedForeground)
            }
        }
    }
}

/** Ligne d'un concert de l'artiste. */
@Composable
private fun ConcertRow(concert: Concert) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(concert.title, color = colors.foreground, fontWeight = FontWeight.Bold)
                concert.scheduledDate?.let { Text(it.take(16), color = colors.mutedForeground) }
            }
            Text(concert.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = colors.mutedForeground)
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "En attente"
    "accepted" -> "Accepté"
    "declined" -> "Refusé"
    else -> status
}

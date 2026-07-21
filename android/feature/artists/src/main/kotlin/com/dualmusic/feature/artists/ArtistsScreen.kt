package com.dualmusic.feature.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.dualmusic.core.ui.components.DMEmptyState
import com.dualmusic.core.ui.theme.DualMusicTheme
import com.dualmusic.domain.artist.ArtistEndpoints
import com.dualmusic.domain.artist.ArtistSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * ViewModel de l'annuaire des artistes + suivi.
 *
 * @param api client HTTP.
 */
class ArtistsViewModel(private val api: ApiClient) : ViewModel() {

    private val _artists = MutableStateFlow<List<ArtistSummary>>(emptyList())
    val artists: StateFlow<List<ArtistSummary>> = _artists.asStateFlow()

    /** Ids des artistes suivis (pour l'état des boutons). */
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Charge l'annuaire + les artistes déjà suivis. */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _artists.value = runCatching {
                api.request(Endpoint.get(ArtistEndpoints.LIST), ListSerializer(ArtistSummary.serializer()))
            }.getOrDefault(emptyList())
            _following.value = runCatching {
                api.request(Endpoint.get(ArtistEndpoints.FOLLOWING), ListSerializer(String.serializer())).toSet()
            }.getOrDefault(emptySet())
            _isLoading.value = false
        }
    }

    /** Suit / ne suit plus un artiste (optimiste + serveur). */
    fun toggleFollow(artistId: String) {
        val isFollowing = artistId in _following.value
        // Optimiste.
        _following.update { if (isFollowing) it - artistId else it + artistId }
        viewModelScope.launch {
            runCatching {
                if (isFollowing) {
                    // DELETE avec corps vide (Endpoint accepte le body null).
                    api.request<Unit>(Endpoint.delete(ArtistEndpoints.follow(artistId)))
                } else {
                    api.request<Unit>(Endpoint.post(ArtistEndpoints.follow(artistId)))
                }
            }.onFailure {
                // Rollback si échec.
                _following.update { if (isFollowing) it + artistId else it - artistId }
            }
        }
    }
}

/**
 * Annuaire des artistes avec bouton suivre/ne plus suivre.
 *
 * @param viewModel source d'état.
 */
@Composable
fun ArtistsScreen(viewModel: ArtistsViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        if (isLoading) CircularProgressIndicator(color = colors.primary)
        if (!isLoading && artists.isEmpty()) {
            DMEmptyState(
                title = "Aucun artiste",
                subtitle = "Les artistes de la plateforme apparaîtront ici.",
                icon = Icons.Filled.Person,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            items(artists) { artist ->
                ArtistRow(
                    artist = artist,
                    isFollowing = artist.id in following,
                    onToggle = { viewModel.toggleFollow(artist.id) },
                )
            }
        }
    }
}

/** Ligne d'un artiste : nom, followers, bouton suivre. */
@Composable
private fun ArtistRow(artist: ArtistSummary, isFollowing: Boolean, onToggle: () -> Unit) {
    val colors = DualMusicTheme.colors
    DMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("🎤  ${artist.displayName}", color = colors.foreground, fontWeight = FontWeight.Bold)
                Text("${artist.followersCount} abonnés", color = colors.mutedForeground)
            }
            DMButton(
                if (isFollowing) "Suivi" else "Suivre",
                style = if (isFollowing) DMButtonStyle.OUTLINE else DMButtonStyle.PRIMARY,
                onClick = onToggle,
            )
        }
    }
}

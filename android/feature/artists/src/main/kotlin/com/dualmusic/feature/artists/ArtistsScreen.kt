package com.dualmusic.feature.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dualmusic.core.ui.components.DMRemoteImage
import com.dualmusic.core.ui.i18n.LocalStrings
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

    /** Ids UTILISATEUR des artistes suivis (clé de suivi = `/users/:userId/follow`). */
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

    /** Suit / ne suit plus un artiste (optimiste + serveur). Clé = id UTILISATEUR. */
    fun toggleFollow(userId: String) {
        val isFollowing = userId in _following.value
        _following.update { if (isFollowing) it - userId else it + userId }
        viewModelScope.launch {
            runCatching {
                if (isFollowing) api.request<Unit>(Endpoint.delete(ArtistEndpoints.follow(userId)))
                else api.request<Unit>(Endpoint.post(ArtistEndpoints.follow(userId)))
            }.onFailure {
                _following.update { if (isFollowing) it + userId else it - userId }
            }
        }
    }
}

/**
 * Page publique « Découvrez nos artistes » — parité web : titre + sous-titre + recherche +
 * cartes (avatar, nom, bio, abonnés) avec bouton Suivre / Suivi.
 */
@Composable
fun ArtistsScreen(viewModel: ArtistsViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current

    LaunchedEffect(Unit) { viewModel.load() }

    var search by remember { mutableStateOf("") }
    val q = search.trim().lowercase()
    val filtered = if (q.isEmpty()) artists else artists.filter { it.displayName.lowercase().contains(q) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md),
    ) {
        Text(s.discoverArtists, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(s.discoverArtistsDesc, color = colors.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(s.searchArtist) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isLoading) DMLoadingBox(Modifier.fillMaxWidth().weight(1f))
        if (!isLoading && filtered.isEmpty()) {
            DMEmptyState(title = s.noArtists, subtitle = s.noArtistsHint, icon = Icons.Filled.Person, modifier = Modifier.weight(1f))
        } else if (!isLoading) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.md), modifier = Modifier.weight(1f)) {
                items(filtered) { artist ->
                    ArtistCard(
                        artist = artist,
                        isFollowing = artist.opponentUserId in following,
                        onToggle = { viewModel.toggleFollow(artist.opponentUserId) },
                    )
                }
            }
        }
    }
}

/** Carte d'un artiste : grande image + nom + bio + abonnés + bouton Suivre. */
@Composable
private fun ArtistCard(artist: ArtistSummary, isFollowing: Boolean, onToggle: () -> Unit) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    DMCard(modifier = Modifier.fillMaxWidth(), padded = false) {
        DMRemoteImage(
            url = artist.avatarUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            fallbackEmoji = "🎤",
        )
        Column(modifier = Modifier.fillMaxWidth().padding(DualMusicTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
            Text(artist.displayName, color = colors.foreground, fontWeight = FontWeight.Bold)
            artist.bio?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.mutedForeground, fontSize = 13.sp, maxLines = 2) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("👥 ${artist.followersCount} ${s.followers}", color = colors.mutedForeground, fontSize = 12.sp)
                DMButton(
                    if (isFollowing) s.followed else s.follow,
                    style = if (isFollowing) DMButtonStyle.OUTLINE else DMButtonStyle.PRIMARY,
                    onClick = onToggle,
                )
            }
        }
    }
}

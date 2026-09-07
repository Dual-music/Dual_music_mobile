package com.dualmusic.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.domain.creator.ArtistProfile
import com.dualmusic.domain.model.DisplayProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État de l'écran de **profil public d'un artiste** (consulté par un spectateur). */
data class ArtistPublicProfileState(
    val loading: Boolean = true,
    val profile: DisplayProfile? = null,
    val artistProfile: ArtistProfile? = null,
    val followerCount: Int = 0,
    val isFollowing: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel du profil public d'un artiste, ouvert depuis un direct (clic sur son nom).
 * Lit `GET /users/:id` (compte + profil artiste + suivi) et permet de suivre / ne plus suivre.
 */
class ArtistPublicProfileViewModel(
    private val userId: String,
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistPublicProfileState())
    val state: StateFlow<ArtistPublicProfileState> = _state.asStateFlow()

    init { load() }

    /** (Re)charge le profil public. */
    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.publicProfile(userId) }
                .onSuccess { r ->
                    _state.value = ArtistPublicProfileState(
                        loading = false,
                        profile = r.profile,
                        artistProfile = r.artistProfile,
                        followerCount = r.followerCount,
                        isFollowing = r.isFollowing,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    /** Suit / ne suit plus (optimiste ; annulé si l'appel échoue). */
    fun toggleFollow() {
        val cur = _state.value
        if (cur.loading) return
        val next = !cur.isFollowing
        _state.value = cur.copy(
            isFollowing = next,
            followerCount = (cur.followerCount + if (next) 1 else -1).coerceAtLeast(0),
        )
        viewModelScope.launch {
            runCatching { repository.setFollow(userId, next) }
                .onFailure { _state.value = _state.value.copy(isFollowing = cur.isFollowing, followerCount = cur.followerCount) }
        }
    }
}

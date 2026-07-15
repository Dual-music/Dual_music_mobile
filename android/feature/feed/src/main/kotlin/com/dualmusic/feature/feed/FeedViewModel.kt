package com.dualmusic.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualmusic.core.media.LiveKitTokenService
import com.dualmusic.domain.media.LiveKitToken
import com.dualmusic.domain.model.Live
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel du feed vertical.
 *
 * Charge les lives, pagine à l'approche du bas, et **pré-chauffe le jeton LiveKit de la
 * prochaine room** dès que la page active change — pour une entrée-live quasi instantanée.
 */
class FeedViewModel(
    private val repository: FeedRepository,
    private val tokenService: LiveKitTokenService,
) : ViewModel() {

    private val _items = MutableStateFlow<List<Live>>(emptyList())
    val items: StateFlow<List<Live>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var page = 1
    private var hasMore = true
    private val prewarmed = mutableMapOf<String, LiveKitToken>()

    /** Charge la première page. */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.lives(page = 1) }.getOrNull()?.let {
                _items.value = it
                hasMore = it.isNotEmpty()
                page = 1
                prewarmAround(0)
            }
            _isLoading.value = false
        }
    }

    /** Appelé quand la page active change (scroll) : prewarm + pagination. */
    fun onPageChanged(index: Int) {
        prewarmAround(index)
        if (hasMore && index >= _items.value.size - 3) loadMore()
    }

    /** Jeton pré-chauffé d'un live (null si non prêt). */
    fun prewarmedToken(liveId: String): LiveKitToken? = prewarmed[liveId]

    private fun loadMore() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val next = runCatching { repository.lives(page = page + 1) }.getOrNull().orEmpty()
            if (next.isEmpty()) hasMore = false else { page += 1; _items.update { it + next } }
            _isLoading.value = false
        }
    }

    /** Pré-chauffe les jetons des rooms i et i+1 (active + suivante). */
    private fun prewarmAround(index: Int) {
        val list = _items.value
        for (offset in 0..1) {
            val item = list.getOrNull(index + offset) ?: continue
            if (prewarmed.containsKey(item.id)) continue
            viewModelScope.launch {
                runCatching { tokenService.token(roomName = item.liveKitRoom) }
                    .getOrNull()?.let { prewarmed[item.id] = it }
            }
        }
    }
}

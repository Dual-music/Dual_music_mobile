package com.dualmusic.feature.sponsor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * État + action d'enregistrement d'une room, réutilisable par les ViewModels live/duel/
 * concert/compétition (comme [SponsorAdHolder]). L'UI n'affiche le bouton que si le mode
 * admin est `manual` ; un indicateur « REC » suffit en `auto`.
 *
 * @param sourceType 'live' | 'duel' | 'concert' | 'competition'.
 * @param sourceId identifiant de l'événement.
 * @param repo accès REST (status/start/stop).
 * @param scope portée du VM (viewModelScope).
 */
class RecordingHolder(
    private val sourceType: String,
    private val sourceId: String,
    private val repo: RecordingRepository,
    private val scope: CoroutineScope,
) {
    /** Mode admin : off | auto | manual. */
    private val _mode = MutableStateFlow("off")
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** Un enregistrement est en cours. */
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Verrou anti double-clic. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Rafraîchit l'état depuis le serveur (mode + en cours). */
    fun refresh() {
        scope.launch {
            runCatching { repo.status(sourceType, sourceId) }.getOrNull()?.let {
                _mode.value = it.mode
                _active.value = it.active
            }
        }
    }

    /** Démarre / arrête l'enregistrement (mode manual). */
    fun toggle() {
        scope.launch {
            _busy.value = true
            val result = runCatching {
                if (_active.value) repo.stop(sourceType, sourceId) else repo.start(sourceType, sourceId)
            }.getOrNull()
            if (result != null) {
                _mode.value = result.mode
                _active.value = result.active
            }
            _busy.value = false
        }
    }
}

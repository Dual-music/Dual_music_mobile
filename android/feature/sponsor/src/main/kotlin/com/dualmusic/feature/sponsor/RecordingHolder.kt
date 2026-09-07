package com.dualmusic.feature.sponsor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * État + actions d'enregistrement d'une room, réutilisable par les ViewModels live/duel/
 * concert/compétition (comme [SponsorAdHolder]). L'UI n'affiche les contrôles que si le mode
 * admin est `manual` ; un indicateur « REC » suffit en `auto`.
 *
 * L'egress LiveKit n'a pas de vraie pause : [pause] arrête le segment en cours, [resume] en
 * démarre un nouveau sous la même session (le serveur les recolle en UNE vidéo à [save]). Le
 * chrono ([accumulatedSeconds] + temps écoulé depuis [runStartedAt] quand [active]) est géré
 * côté UI (voir `RecordingHostButton`).
 *
 * @param sourceType 'live' | 'duel' | 'concert' | 'competition'.
 * @param sourceId identifiant de l'événement.
 * @param repo accès REST (status/start/pause/resume/cancel/stop).
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

    /** Un segment est en cours d'enregistrement. */
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** En pause (session ouverte, rien n'enregistre actuellement). */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Sauvegarde en cours (dernier segment en clôture / recollage ffmpeg). */
    private val _finalizing = MutableStateFlow(false)
    val finalizing: StateFlow<Boolean> = _finalizing.asStateFlow()

    /**
     * Un enregistrement récent a échoué côté serveur (segment jamais démarré, aucun segment
     * récupérable à la sauvegarde…) — reste vrai ~2 min (voir recording.service.js), le temps
     * qu'un `LaunchedEffect(failed)` côté écran l'affiche une fois puis se stabilise.
     */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Détail technique du dernier échec (voir [failed]). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Durée cumulée des segments déjà clos (secondes). */
    private val _accumulatedSeconds = MutableStateFlow(0)
    val accumulatedSeconds: StateFlow<Int> = _accumulatedSeconds.asStateFlow()

    /** Horodatage ISO de départ du segment en cours (null si rien n'est actif). */
    private val _runStartedAt = MutableStateFlow<String?>(null)
    val runStartedAt: StateFlow<String?> = _runStartedAt.asStateFlow()

    /** Verrou anti double-clic. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Sonde périodiquement l'état côté serveur (parité web, qui fait pareil toutes les 6 s) —
     * sans ça, un dénouement qui arrive APRÈS la réponse immédiate d'une action (ex. le dernier
     * segment échoue au webhook egress, bien après que [save] ait déjà répondu « stopping ») n'est
     * jamais repris côté client : l'écran restait bloqué sur « Finalisation… » indéfiniment alors
     * que le serveur avait déjà résolu la session (constaté en conditions réelles). Auto-stoppée
     * quand [scope] (viewModelScope) est annulée — pas besoin d'arrêt explicite.
     */
    fun startPolling(intervalMs: Long = 6000) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (true) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    private fun apply(s: com.dualmusic.domain.recording.RecordingStatus) {
        _mode.value = s.mode
        _active.value = s.active
        _paused.value = s.paused
        _finalizing.value = s.finalizing
        _failed.value = s.failed
        _error.value = s.error
        _accumulatedSeconds.value = s.accumulatedSeconds
        _runStartedAt.value = s.runStartedAt
    }

    /** Rafraîchit l'état depuis le serveur (mode + session en cours + chrono). */
    fun refresh() {
        scope.launch {
            runCatching { repo.status(sourceType, sourceId) }.getOrNull()?.let { apply(it) }
        }
    }

    private fun run(action: suspend () -> com.dualmusic.domain.recording.RecordingStatus, onError: ((String) -> Unit)? = null) {
        scope.launch {
            _busy.value = true
            val result = runCatching { action() }
            result.getOrNull()?.let { apply(it) }
            if (result.isFailure) {
                onError?.invoke(result.exceptionOrNull()?.message ?: "error")
                // Auto-resynchronisation : un échec ici peut survenir APRÈS que le serveur ait
                // déjà changé d'état (ex. l'egress a fini de s'arrêter tout seul entre-temps) —
                // sans ce refresh, l'état local reste bloqué sur l'ancienne valeur et TOUS les
                // boutons suivants semblent « ne rien faire » puisqu'ils ne correspondent plus
                // à l'état réel côté serveur.
                refresh()
            }
            _busy.value = false
        }
    }

    /** Démarre l'enregistrement (mode manual). */
    fun start(onError: ((String) -> Unit)? = null) = run({ repo.start(sourceType, sourceId) }, onError)

    /** Met en pause l'enregistrement en cours. */
    fun pause(onError: ((String) -> Unit)? = null) = run({ repo.pause(sourceType, sourceId) }, onError)

    /** Reprend un enregistrement en pause. */
    fun resume(onError: ((String) -> Unit)? = null) = run({ repo.resume(sourceType, sourceId) }, onError)

    /** Annule tout l'enregistrement en cours — aucun replay n'est créé. */
    fun cancel(onError: ((String) -> Unit)? = null) = run({ repo.cancel(sourceType, sourceId) }, onError)

    /** Sauvegarde l'enregistrement (segments recollés en un seul fichier). */
    fun save(onError: ((String) -> Unit)? = null) = run({ repo.stop(sourceType, sourceId) }, onError)

    /**
     * Démarre/sauvegarde en un seul geste (parité duel/live/compétition — UI simple à bouton
     * unique, sans pause/annuler). Conservé pour ces écrans ; le concert utilise directement
     * [start]/[pause]/[resume]/[cancel]/[save] pour ses contrôles complets.
     */
    fun toggle() {
        if (_active.value || _paused.value) save() else start()
    }
}

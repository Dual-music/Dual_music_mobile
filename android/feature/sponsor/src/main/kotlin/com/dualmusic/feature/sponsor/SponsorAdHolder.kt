package com.dualmusic.feature.sponsor

import com.dualmusic.domain.realtime.SponsorAdPayload
import com.dualmusic.domain.realtime.SponsorAdVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * État + actions de **diffusion de pub sponsor** pour une room d'événement, réutilisable par
 * les ViewModels live/duel/concert/compétition (leur évite de dupliquer la logique).
 *
 * Le VM se contente de : (1) créer un holder, (2) relayer l'event temps réel `sponsor:ad`
 * via [onEvent] depuis son listener `/live` (déjà connecté et joint à la room), (3) exposer
 * ce holder à l'écran. La pub active est pilotée par le serveur (start/stop) → tous les
 * spectateurs se synchronisent.
 *
 * @param eventType type de room côté serveur : "live" | "duel" | "concert" | "competition".
 * @param eventId identifiant de l'événement.
 * @param repo accès REST (liste + play/stop).
 * @param scope portée du VM (viewModelScope).
 */
class SponsorAdHolder(
    private val eventType: String,
    private val eventId: String,
    private val repo: SponsorAdRepository,
    private val scope: CoroutineScope,
) {
    /** Pub actuellement diffusée (overlay vidéo), ou `null`. */
    private val _activeAd = MutableStateFlow<SponsorAdVideo?>(null)
    val activeAd: StateFlow<SponsorAdVideo?> = _activeAd.asStateFlow()

    /** Id de la diffusion en cours (pour l'arrêter). */
    private val _playId = MutableStateFlow<String?>(null)

    /** Catalogue des pubs éligibles (chargé à la demande par l'hôte). */
    private val _ads = MutableStateFlow<List<SponsorAdVideo>>(emptyList())
    val ads: StateFlow<List<SponsorAdVideo>> = _ads.asStateFlow()

    /** Verrou anti double-clic pendant un play/stop. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** À appeler depuis le listener temps réel `sponsor:ad` du VM. */
    fun onEvent(p: SponsorAdPayload) {
        if (p.action == "start") {
            _activeAd.value = p.ad
            _playId.value = p.playId
        } else {
            _activeAd.value = null
            _playId.value = null
        }
    }

    /** Charge les pubs éligibles (hôte : avant d'ouvrir le sélecteur). */
    fun loadAds() {
        scope.launch {
            runCatching { repo.listAds(eventType, eventId) }.getOrNull()?.let { _ads.value = it }
        }
    }

    /** Lance la diffusion d'une pub (hôte). L'état sera confirmé par l'event serveur. */
    fun play(adVideoId: String) {
        scope.launch {
            _busy.value = true
            runCatching { repo.play(eventType, eventId, adVideoId) }
                .onSuccess { play ->
                    _activeAd.value = _ads.value.firstOrNull { it.id == adVideoId }
                    _playId.value = play.id
                }
            _busy.value = false
        }
    }

    /** Arrête la diffusion en cours (hôte). */
    fun stop() {
        val id = _playId.value
        scope.launch {
            _busy.value = true
            if (id != null) runCatching { repo.stop(id) }
            _activeAd.value = null
            _playId.value = null
            _busy.value = false
        }
    }
}

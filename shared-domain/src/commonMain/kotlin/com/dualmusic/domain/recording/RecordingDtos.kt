package com.dualmusic.domain.recording

import kotlinx.serialization.Serializable

/**
 * État d'enregistrement serveur (LiveKit Egress) d'un direct.
 *
 * L'egress LiveKit n'a pas de vraie pause : « Pause » arrête le segment en cours, « Reprendre »
 * en démarre un nouveau sous la même session (recollés en un seul fichier à la sauvegarde). Le
 * chrono côté client s'obtient en additionnant [accumulatedSeconds] (segments déjà clos) au
 * temps écoulé depuis [runStartedAt] quand [active] est vrai.
 *
 * @property mode 'off' (aucun) | 'auto' (auto au passage en live) | 'manual' (l'hôte lance).
 * @property active un segment est en cours d'enregistrement.
 * @property paused en pause (segment arrêté, session ouverte).
 * @property finalizing sauvegarde en cours (dernier segment en clôture / recollage ffmpeg).
 * @property failed un enregistrement récent a échoué (segment jamais démarré, aucun segment
 *   récupérable à la sauvegarde…) — signalé une fois, [error] porte le détail technique.
 * @property accumulatedSeconds durée cumulée des segments déjà clos.
 * @property runStartedAt horodatage ISO de départ du segment en cours (null si pas actif).
 */
@Serializable
data class RecordingStatus(
    val mode: String = "off",
    val active: Boolean = false,
    val paused: Boolean = false,
    val finalizing: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null,
    val accumulatedSeconds: Int = 0,
    val runStartedAt: String? = null,
)

/** Chemins REST du pilotage d'enregistrement (source unique). */
object RecordingEndpoints {
    const val STATUS = "/recordings/status"
    const val START = "/recordings/start"
    const val PAUSE = "/recordings/pause"
    const val RESUME = "/recordings/resume"
    const val CANCEL = "/recordings/cancel"
    const val STOP = "/recordings/stop"
}

package com.dualmusic.domain.recording

import kotlinx.serialization.Serializable

/**
 * État d'enregistrement serveur (LiveKit Egress) d'un direct.
 *
 * @property mode 'off' (aucun) | 'auto' (auto au passage en live) | 'manual' (l'hôte lance).
 * @property active un enregistrement est en cours.
 */
@Serializable
data class RecordingStatus(
    val mode: String = "off",
    val active: Boolean = false,
)

/** Chemins REST du pilotage d'enregistrement (source unique). */
object RecordingEndpoints {
    const val STATUS = "/recordings/status"
    const val START = "/recordings/start"
    const val STOP = "/recordings/stop"
}

package com.dualmusic.domain.realtime

import kotlinx.serialization.Serializable

/**
 * Enveloppe du relais de broadcast éphémère (`{channel, event, payload}`), partagée par les
 * overlays temps réel (duel, concert, compétition). Le canal live définit sa propre variante
 * locale pour les besoins invités ; ici on couvre le cas commun des réactions emojis.
 */
@Serializable
data class BroadcastEnvelope(
    val channel: String? = null,
    val event: String? = null,
    val payload: BroadcastPayload? = null,
)

/** Charge utile du relais broadcast — `emoji_reaction` → emoji ; `like` → compteur partagé ;
 *  `focus` → slot épinglé par le manager (`artist1`/`artist2`/`manager`, ou null = libéré). */
@Serializable
data class BroadcastPayload(
    val emoji: String? = null,
    val count: Int? = null,
    val slot: String? = null,
    // `winner_announced` : vainqueur annoncé par le manager (célébration plein écran synchronisée).
    val name: String? = null,
    val avatar: String? = null,
    val votes: Int? = null,
)

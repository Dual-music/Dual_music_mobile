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
    val percent: Int? = null,
    // `FORCE_MUTE`/`FORCE_UNMUTE` : id utilisateur de l'artiste coupé/réactivé d'autorité par le manager.
    val artistId: String? = null,
    // `media-state` (compétition multi-cam) : état micro/caméra d'un publieur, indexé par son
    // identité LiveKit (= userId), pour afficher les badges des tuiles chez tous les spectateurs.
    val identity: String? = null,
    val isMicOn: Boolean? = null,
    val isCameraOn: Boolean? = null,
    val isStreaming: Boolean? = null,
)

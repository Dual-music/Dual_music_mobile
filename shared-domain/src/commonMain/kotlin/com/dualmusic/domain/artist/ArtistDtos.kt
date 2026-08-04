package com.dualmusic.domain.artist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Résumé d'artiste pour l'annuaire (`GET /artists`). */
@Serializable
data class ArtistSummary(
    val id: String,
    // Id de l'UTILISATEUR (≠ id du profil artiste) : clé pour défier un adversaire et s'exclure soi-même.
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("followers_count") val followersCount: Int = 0,
) {
    /** Nom à afficher : nom de scène sinon nom complet. */
    val displayName: String
        get() = stageName?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: "Artiste"

    /** Id utilisateur de l'artiste (repli sur `id` si absent). */
    val opponentUserId: String get() = userId ?: id
}

/** Chemins REST de l'annuaire artistes / suivi (source unique, partagée). */
object ArtistEndpoints {
    /** Annuaire public des artistes. */
    const val LIST = "/artists"
    /** Ids des artistes suivis par le caller. */
    const val FOLLOWING = "/users/me/following"
    /** Suivre / ne plus suivre un artiste. */
    fun follow(id: String) = "/users/$id/follow"
}

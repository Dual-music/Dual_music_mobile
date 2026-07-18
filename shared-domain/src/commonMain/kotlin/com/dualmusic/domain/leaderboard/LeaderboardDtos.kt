package com.dualmusic.domain.leaderboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Entrée de classement (artiste ou donateur).
 *
 * Le backend enrichit chaque entrée avec le profil d'affichage + un total agrégé
 * (votes/cadeaux reçus pour les artistes, crédits offerts pour les donateurs).
 */
@Serializable
data class LeaderboardEntry(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val total: Double = 0.0,
) {
    /** Nom à afficher : nom de scène sinon nom complet sinon repli. */
    val displayName: String
        get() = stageName?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: "Utilisateur"
}

/** Chemins REST des classements (source unique, partagée). */
object LeaderboardEndpoints {
    /** Classement des artistes (all-time), par votes/cadeaux reçus. */
    const val ARTISTS = "/leaderboards/artists"
    /** Classement des donateurs (all-time), par crédits offerts. */
    const val DONORS = "/leaderboards/donors"
    /** Gagnants de saisons. */
    const val WINNERS = "/leaderboards/winners"
}

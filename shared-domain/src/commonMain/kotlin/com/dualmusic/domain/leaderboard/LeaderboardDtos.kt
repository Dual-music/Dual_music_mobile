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
    /** All-time artists/donors renvoient `name` + `score` (≠ saisons live qui ont full/stage_name). */
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("stage_name") val stageName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val total: Double = 0.0,
    val score: Double = 0.0,
) {
    /** Nom à afficher : nom de scène sinon nom complet sinon `name` sinon repli. */
    val displayName: String
        get() = stageName?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "Utilisateur"

    /** Valeur agrégée (points/crédits) : `total` sinon `score`. */
    val value: Double get() = if (total != 0.0) total else score
}

/** Récompense d'une saison, par rang. */
@Serializable
data class SeasonReward(
    @SerialName("rank_position") val rankPosition: Int = 0,
    @SerialName("reward_type") val rewardType: String? = null,
    @SerialName("credits_amount") val creditsAmount: Double? = null,
    @SerialName("physical_description") val physicalDescription: String? = null,
)

/**
 * Saison de classement (onglet « Périodique » du web) — `GET /leaderboards/seasons`.
 */
@Serializable
data class LeaderboardSeason(
    val id: String,
    val name: String,
    val type: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_mystery_reward") val isMysteryReward: Boolean = false,
    val rewards: List<SeasonReward> = emptyList(),
)

/** Chemins REST des classements (source unique, partagée). */
object LeaderboardEndpoints {
    /** Classement des artistes (all-time), par votes/cadeaux reçus. */
    const val ARTISTS = "/leaderboards/artists"
    /** Classement des donateurs (all-time), par crédits offerts. */
    const val DONORS = "/leaderboards/donors"
    /** Saisons (classement périodique). */
    const val SEASONS = "/leaderboards/seasons"
    /** Gagnants de saisons. */
    const val WINNERS = "/leaderboards/winners"
}

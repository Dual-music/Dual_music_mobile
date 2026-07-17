package com.dualmusic.domain.user

import kotlinx.serialization.Serializable

/**
 * Statistiques de l'utilisateur (`GET /users/me/stats`).
 *
 * Regroupe trois facettes selon les rôles : artiste, manager, fan. Toutes les valeurs
 * sont calculées côté serveur.
 */
@Serializable
data class UserStats(
    val artistStats: ArtistStats = ArtistStats(),
    val managerStats: ManagerStats = ManagerStats(),
    val fanStats: FanStats = FanStats(),
)

/** Facette artiste : votes/cadeaux reçus, duels joués/gagnés. */
@Serializable
data class ArtistStats(
    val totalVotes: Double = 0.0,
    val totalGifts: Int = 0,
    val totalDuels: Int = 0,
    val wonDuels: Int = 0,
)

/** Facette manager : duels gérés, en cours, cadeaux reçus. */
@Serializable
data class ManagerStats(
    val totalDuelsManaged: Int = 0,
    val activeDuels: Int = 0,
    val totalGiftsReceived: Int = 0,
)

/** Facette fan : votes émis, cadeaux envoyés, billets achetés. */
@Serializable
data class FanStats(
    val totalVotesCast: Double = 0.0,
    val totalGiftsSent: Int = 0,
    val totalTickets: Int = 0,
)

/** Chemins REST du profil / utilisateur. */
object UserEndpoints {
    /** Profil + rôles du caller (réhydratation). */
    const val ME = "/auth/me"

    /** Statistiques agrégées du caller. */
    const val MY_STATS = "/users/me/stats"

    /** Mise à jour du profil du caller. */
    const val UPDATE_ME = "/users/me"

    /** Badges d'un utilisateur. */
    fun badges(id: String) = "/users/$id/badges"
}

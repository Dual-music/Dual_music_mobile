package com.dualmusic.domain.duel

/**
 * Chemins REST des duels (source unique, partagée iOS/Android).
 *
 * Rappel : le **vote payant** n'est PAS ici — c'est une opération de portefeuille
 * (`POST /wallet/vote`, procédure atomique). Ce catalogue ne couvre que le duel lui-même
 * (catalogue, détail, tallies, chat).
 */
object DuelEndpoints {
    /** Liste des duels. Filtres possibles : `status`, `artistId`, `managerId`, pagination. */
    const val LIST = "/duels"

    /** Tallies de votes en masse : `?ids=a,b,c` → [com.dualmusic.domain.model.DuelVoteTotal]. */
    const val VOTES_BATCH = "/duels/votes/batch"

    /** Détail d'un duel (artistes hydratés + voteTotals). */
    fun detail(id: String) = "/duels/$id"

    /** Tallies de votes d'un duel : `[{artist_id, total}]`. */
    fun votes(id: String) = "/duels/$id/votes"

    /** Historique de chat du duel (GET) / envoi d'un message (POST). */
    fun messages(id: String) = "/duels/$id/messages"

    /** Billet du caller pour ce duel : `{hasTicket, count}`. */
    fun myTicket(id: String) = "/duels/$id/my-ticket"
}

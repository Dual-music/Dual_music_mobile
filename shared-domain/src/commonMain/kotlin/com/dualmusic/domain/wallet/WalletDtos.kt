package com.dualmusic.domain.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs du portefeuille — alignés sur le backend `/wallet/…`.
 *
 * Rappel économie : le solde est en CRÉDITS (1 crédit = 0,50 € — invariant métier).
 * Les DÉBITS (vote, cadeau, ticket, déblocage replay) passent par des procédures
 * stockées atomiques côté serveur : le mobile n'affiche que des aperçus et laisse le
 * backend faire foi sur les montants.
 */

/** Réponse de `GET /wallet` — solde courant + contre-valeur euro calculée serveur. */
@Serializable
data class WalletBalance(
    val balance: Double = 0.0,
    /** Contre-valeur en euros calculée par le backend (fait foi). */
    val eurValue: Double = 0.0,
)

/**
 * Élément de `GET /wallet/revenues` — revenus agrégés PAR ÉVÉNEMENT source.
 * (Ce que l'utilisateur a GAGNÉ : artiste/manager.)
 */
@Serializable
data class RevenueEvent(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_type") val sourceType: String,
    /** Total de crédits reçus par le caller sur cet événement. */
    @SerialName("total_received") val totalReceived: Double = 0.0,
    @SerialName("tx_count") val txCount: Int = 0,
    @SerialName("last_at") val lastAt: String? = null,
)

/**
 * Élément de `GET /wallet/spending` — sorties du caller (ce qu'il a DÉPENSÉ :
 * cadeaux envoyés, votes, tickets…). Provient de `revenue_distributions` (payer_id).
 */
@Serializable
data class SpendItem(
    val id: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String? = null,
    /** Montant total débité pour cette opération. */
    @SerialName("total_credits") val totalCredits: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Élément de `GET /wallet/revenues/breakdown` — total par type de source. */
@Serializable
data class RevenueBreakdown(
    @SerialName("source_type") val sourceType: String,
    val total: Double = 0.0,
)

/** Corps de `POST /wallet/vote` — vote payant pour un artiste dans un duel. */
@Serializable
data class VoteRequest(
    val duelId: String,
    val artistId: String,
    val amount: Double,
)

/** Corps de `POST /wallet/gifts/send` — envoi d'un cadeau de l'inventaire. */
@Serializable
data class SendGiftRequest(
    val giftId: String,
    val toUserId: String,
    val duelId: String? = null,
    val liveId: String? = null,
    val concertId: String? = null,
)

/** Corps de `POST /wallet/gifts/purchase` — achat de cadeaux dans l'inventaire. */
@Serializable
data class PurchaseGiftRequest(
    val giftId: String,
    val quantity: Int = 1,
)

/** Chemins REST du portefeuille (source unique, partagée iOS/Android). */
object WalletEndpoints {
    /** Solde : la racine du routeur wallet. */
    const val BALANCE = "/wallet"
    const val REVENUES = "/wallet/revenues"
    const val SPENDING = "/wallet/spending"
    const val REVENUES_BREAKDOWN = "/wallet/revenues/breakdown"
    const val TRANSACTIONS = "/wallet/transactions"
    const val VOTE = "/wallet/vote"
    const val GIFTS_PURCHASE = "/wallet/gifts/purchase"
    const val GIFTS_SEND = "/wallet/gifts/send"
    const val TICKET_DUEL = "/wallet/tickets/duel"
    const val TICKET_CONCERT = "/wallet/tickets/concert"
    const val REPLAY_UNLOCK = "/wallet/replays/unlock"
}

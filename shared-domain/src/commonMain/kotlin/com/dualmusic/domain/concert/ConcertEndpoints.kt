package com.dualmusic.domain.concert

import kotlinx.serialization.Serializable

/**
 * Chemins REST des concerts.
 *
 * Deux familles côté backend :
 *  - `/artist-concerts` — concerts créés par les artistes (le catalogue public courant) ;
 *  - `/concerts` — routes transverses : billetterie (`ticket-info`), dédicaces, rappels.
 *
 * ⚠️ L'ACHAT d'un billet est un débit : `POST /wallet/tickets/concert` (feature:wallet).
 */
object ConcertEndpoints {
    /** Catalogue public des concerts d'artistes. */
    const val ARTIST_LIST = "/artist-concerts"

    /** Concerts du caller (espace artiste). */
    const val ARTIST_MINE = "/artist-concerts/me"

    /** Dédicaces achetées par le caller (fan). */
    const val DEDICATIONS_MINE = "/concerts/dedications/me"

    /** Dédicaces reçues par le caller (artiste). */
    const val DEDICATIONS_ARTIST_MINE = "/concerts/dedications/artist/me"

    /** Achat d'une dédicace (débit idempotent). */
    const val DEDICATIONS_PURCHASE = "/concerts/dedications"

    /** Détail d'un concert d'artiste. */
    fun artistDetail(id: String) = "/artist-concerts/$id"

    /** Infos billetterie d'un concert : prix, places restantes, billet du caller. */
    fun ticketInfo(id: String) = "/concerts/$id/ticket-info"

    /** Rappel du caller pour un concert (GET/PUT/DELETE). */
    fun reminder(id: String) = "/concerts/$id/reminder"
}

/**
 * Infos de billetterie d'un concert (`GET /concerts/:id/ticket-info`).
 *
 * @property price prix du billet en crédits.
 * @property hasTicket vrai si le caller possède déjà un billet.
 * @property soldOut vrai si la jauge est atteinte.
 */
@Serializable
data class ConcertTicketInfo(
    val price: Double = 0.0,
    val hasTicket: Boolean = false,
    val soldOut: Boolean = false,
    val remaining: Int? = null,
)

/** Corps d'achat d'une dédicace (`POST /concerts/dedications`). */
@Serializable
data class DedicationRequest(
    val concertId: String,
    val concertType: String = "artist_concert",
    val message: String,
)

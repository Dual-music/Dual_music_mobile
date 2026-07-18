package com.dualmusic.domain.sponsor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Palier tarifaire de sponsoring (`sponsor_price_tiers`) — prix par durée de média. */
@Serializable
data class SponsorTier(
    val id: String,
    val label: String? = null,
    @SerialName("min_seconds") val minSeconds: Int = 0,
    @SerialName("max_seconds") val maxSeconds: Int = 0,
    @SerialName("price_credits") val priceCredits: Double = 0.0,
)

/** Demande de sponsoring du caller (`sponsor_requests`). */
@Serializable
data class SponsorRequest(
    val id: String,
    @SerialName("event_type") val eventType: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_duration_seconds") val mediaDurationSeconds: Int? = null,
    /** `pending` | `approved` | `rejected`. */
    val status: String = "pending",
    @SerialName("price_credits") val priceCredits: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Vrai si la demande est approuvée et en attente de paiement. */
    val payable: Boolean get() = status == "approved"
}

/**
 * Corps de `POST /sponsors/requests` — création d'une demande de sponsoring.
 *
 * ⚠️ camelCase STRICT : le backend (Joi `stripUnknown`) supprime silencieusement tout champ
 * hors DTO. Ne jamais annoter en snake_case ici.
 *
 * Le prix n'est PAS envoyé : il est calculé serveur à partir de [mediaDurationSeconds]
 * (barème par palier). [eventType] ∈ `duel|concert|artist_concert|competition`,
 * [mediaType] ∈ `image|video`, durée 1..600 s, [mediaUrl] = URL publique issue de l'upload.
 */
@Serializable
data class CreateSponsorRequest(
    val eventType: String,
    val eventId: String,
    val mediaType: String,
    val mediaUrl: String,
    val mediaDurationSeconds: Int,
    val description: String? = null,
)

/** Chemins REST du sponsoring (source unique, partagée). */
object SponsorEndpoints {
    const val TIERS = "/sponsors/tiers"
    const val MY_REQUESTS = "/sponsors/requests/me"
    const val CREATE = "/sponsors/requests"
    fun pay(id: String) = "/sponsors/requests/$id/pay"
}

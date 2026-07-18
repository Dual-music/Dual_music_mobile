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

/** Chemins REST du sponsoring (source unique, partagée). */
object SponsorEndpoints {
    const val TIERS = "/sponsors/tiers"
    const val MY_REQUESTS = "/sponsors/requests/me"
    fun pay(id: String) = "/sponsors/requests/$id/pay"
}

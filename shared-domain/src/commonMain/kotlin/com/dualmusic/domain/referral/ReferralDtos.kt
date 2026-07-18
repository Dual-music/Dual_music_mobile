package com.dualmusic.domain.referral

import com.dualmusic.domain.model.DisplayProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Une personne parrainée (`referrals`). */
@Serializable
data class ReferralItem(
    val id: String,
    @SerialName("referred_id") val referredId: String? = null,
    @SerialName("reward_claimed") val rewardClaimed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    /** Profil d'affichage du filleul (hydraté serveur). */
    val referred: DisplayProfile? = null,
)

/** Statistiques de parrainage. */
@Serializable
data class ReferralStats(
    val total: Int = 0,
    val completed: Int = 0,
    @SerialName("pendingRewardCredits") val pendingRewardCredits: Double = 0.0,
    @SerialName("rewardCredits") val rewardCredits: Double = 0.0,
)

/** Réponse de `GET /referrals/me`. */
@Serializable
data class MyReferrals(
    @SerialName("referralCode") val referralCode: String? = null,
    val referrals: List<ReferralItem> = emptyList(),
    val stats: ReferralStats = ReferralStats(),
)

/** Chemins REST du parrainage (source unique, partagée). */
object ReferralEndpoints {
    const val ME = "/referrals/me"
    fun claim(id: String) = "/referrals/$id/claim"
}

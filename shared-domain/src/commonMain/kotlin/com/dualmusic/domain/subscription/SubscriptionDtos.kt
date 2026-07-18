package com.dualmusic.domain.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Offre d'abonnement (`subscription_plans`).
 *
 * @property tier `pro` | `premium`.
 * @property price prix affiché (l'ACHAT réel se fait via Google Play Billing sur mobile).
 */
@Serializable
data class SubscriptionPlan(
    val id: String,
    val name: String? = null,
    val tier: String? = null,
    val price: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int? = null,
    val description: String? = null,
)

/**
 * Abonnement courant du caller (`GET /subscriptions/me`).
 * `isActive = false` (ou objet vide) quand aucun abonnement actif.
 */
@Serializable
data class MySubscription(
    @SerialName("subscription_type") val subscriptionType: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** Chemins REST des abonnements (source unique, partagée). */
object SubscriptionEndpoints {
    const val PLANS = "/subscriptions/plans"
    const val ME = "/subscriptions/me"
    // NB: /checkout & /activate = achat → géré par Play Billing sur mobile (différé).
}

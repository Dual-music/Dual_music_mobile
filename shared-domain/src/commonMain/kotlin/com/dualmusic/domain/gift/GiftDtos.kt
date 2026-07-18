package com.dualmusic.domain.gift

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cadeau possédé dans l'inventaire du caller (`GET /gifts/inventory`).
 * Ligne à plat : id du cadeau + quantité + infos du catalogue jointes.
 */
@Serializable
data class InventoryItem(
    @SerialName("gift_id") val giftId: String,
    val quantity: Int = 0,
    val name: String? = null,
    val price: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
)

/** Chemins REST des cadeaux (source unique, partagée). */
object GiftEndpoints {
    /** Catalogue des cadeaux virtuels. */
    const val CATALOG = "/gifts"
    /** Inventaire du caller. */
    const val INVENTORY = "/gifts/inventory"
    /** Achat dans l'inventaire : opération de portefeuille (débit atomique). */
    const val PURCHASE = "/wallet/gifts/purchase"
}

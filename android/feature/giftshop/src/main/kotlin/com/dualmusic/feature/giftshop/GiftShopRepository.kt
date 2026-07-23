package com.dualmusic.feature.giftshop

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.gift.GiftEndpoints
import com.dualmusic.domain.gift.InventoryItem
import com.dualmusic.domain.model.VirtualGift
import com.dualmusic.domain.model.Wallet
import com.dualmusic.domain.wallet.PurchaseGiftRequest
import com.dualmusic.domain.wallet.WalletEndpoints
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès REST à la boutique de cadeaux.
 *
 * Catalogue + inventaire + achat (débit atomique du solde). L'achat est **idempotent**.
 */
class GiftShopRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Catalogue des cadeaux virtuels achetables. */
    suspend fun catalog(): List<VirtualGift> =
        api.request(Endpoint.get(GiftEndpoints.CATALOG), ListSerializer(VirtualGift.serializer()))

    /** Inventaire possédé par le caller. */
    suspend fun inventory(): List<InventoryItem> =
        api.request(Endpoint.get(GiftEndpoints.INVENTORY), ListSerializer(InventoryItem.serializer()))

    /** Solde du portefeuille (en crédits) ; 0 en cas d'échec. */
    suspend fun balance(): Double =
        runCatching { api.request(Endpoint.get(WalletEndpoints.BALANCE), Wallet.serializer()).balance }
            .getOrDefault(0.0)

    /**
     * Achète des cadeaux dans l'inventaire (débit du solde).
     * @return true si l'achat a réussi.
     */
    suspend fun purchase(
        giftId: String,
        quantity: Int = 1,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): Boolean {
        val body = json.encodeToString(
            PurchaseGiftRequest.serializer(),
            PurchaseGiftRequest(giftId = giftId, quantity = quantity),
        )
        return runCatching {
            api.request<Unit>(Endpoint.post(GiftEndpoints.PURCHASE, body, idempotencyKey = idempotencyKey))
        }.isSuccess
    }
}

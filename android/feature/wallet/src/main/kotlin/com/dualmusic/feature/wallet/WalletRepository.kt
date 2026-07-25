package com.dualmusic.feature.wallet

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.payment.CreditPurchase
import com.dualmusic.domain.payment.PaymentEndpoints
import com.dualmusic.domain.wallet.PurchaseGiftRequest
import com.dualmusic.domain.wallet.RevenueEvent
import com.dualmusic.domain.wallet.SendGiftRequest
import com.dualmusic.domain.wallet.SpendItem
import com.dualmusic.domain.wallet.VoteRequest
import com.dualmusic.domain.wallet.WalletBalance
import com.dualmusic.domain.wallet.WalletEndpoints
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès aux opérations du portefeuille `/wallet/…`.
 *
 * Lectures (solde, historiques) et débits (vote, cadeau). Les débits sont **idempotents** :
 * on envoie une clé d'idempotence pour qu'un rejeu réseau ne débite jamais deux fois.
 *
 * Convention de `Endpoint` : le corps est une **chaîne JSON déjà sérialisée** — on encode
 * donc les DTOs ici via [json].
 *
 * @property api client HTTP partagé (enveloppe + Bearer + refresh gérés en amont).
 */
class WalletRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Solde courant en crédits + contre-valeur € (calculée serveur, fait foi). */
    suspend fun balance(): WalletBalance =
        api.request(Endpoint.get(WalletEndpoints.BALANCE), WalletBalance.serializer())

    /**
     * Revenus agrégés par événement source (ce que l'utilisateur a gagné).
     * @param since borne ISO-8601 optionnelle (filtrage serveur par période).
     */
    suspend fun revenues(since: String? = null): List<RevenueEvent> =
        api.request(
            Endpoint.get(
                WalletEndpoints.REVENUES,
                query = since?.let { mapOf("since" to it) } ?: emptyMap(),
            ),
            ListSerializer(RevenueEvent.serializer()),
        )

    /** Dépenses du caller (cadeaux envoyés, votes, tickets…), les plus récentes d'abord. */
    suspend fun spending(): List<SpendItem> =
        api.request(Endpoint.get(WalletEndpoints.SPENDING), ListSerializer(SpendItem.serializer()))

    /** Achats de crédits (recharges) du caller — `GET /payments/history`. */
    suspend fun purchases(): List<CreditPurchase> =
        api.request(Endpoint.get(PaymentEndpoints.HISTORY), ListSerializer(CreditPurchase.serializer()))

    /**
     * Vote payant pour un artiste dans un duel (débit atomique côté serveur).
     * @param idempotencyKey clé unique de l'action (générée par défaut).
     */
    suspend fun vote(
        duelId: String,
        artistId: String,
        amount: Double,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            VoteRequest.serializer(),
            VoteRequest(duelId = duelId, artistId = artistId, amount = amount),
        )
        api.request<Unit>(Endpoint.post(WalletEndpoints.VOTE, body, idempotencyKey = idempotencyKey))
    }

    /** Envoie un cadeau de l'inventaire dans un contexte d'événement (duel/live/concert). */
    suspend fun sendGift(
        request: SendGiftRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(SendGiftRequest.serializer(), request)
        api.request<Unit>(Endpoint.post(WalletEndpoints.GIFTS_SEND, body, idempotencyKey = idempotencyKey))
    }

    /** Achète des cadeaux dans l'inventaire (débit du solde). */
    suspend fun purchaseGift(
        giftId: String,
        quantity: Int = 1,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            PurchaseGiftRequest.serializer(),
            PurchaseGiftRequest(giftId = giftId, quantity = quantity),
        )
        api.request<Unit>(Endpoint.post(WalletEndpoints.GIFTS_PURCHASE, body, idempotencyKey = idempotencyKey))
    }
}

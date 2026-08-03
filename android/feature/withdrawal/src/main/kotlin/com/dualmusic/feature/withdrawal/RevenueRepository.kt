package com.dualmusic.feature.withdrawal

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.wallet.EventTransaction
import com.dualmusic.domain.wallet.RevenueBreakdown
import com.dualmusic.domain.wallet.RevenueEvent
import com.dualmusic.domain.wallet.WalletBalance
import com.dualmusic.domain.wallet.WalletEndpoints
import kotlinx.serialization.builtins.ListSerializer

/**
 * Accès REST aux **revenus** du caller (onglet « Mes revenus » de l'Espace Manager/Artiste).
 * Mêmes endpoints que le web → mêmes données pour un même compte.
 */
class RevenueRepository(private val api: ApiClient) {

    /** Solde + contre-valeur € (sert à dériver un taux €/crédit pour l'aperçu fiat). */
    suspend fun balance(): WalletBalance =
        api.request(Endpoint.get(WalletEndpoints.BALANCE), WalletBalance.serializer())

    /** Revenus agrégés par événement (`GET /wallet/revenues?since=`). */
    suspend fun revenues(since: String? = null): List<RevenueEvent> =
        api.request(
            Endpoint.get(WalletEndpoints.REVENUES, query = since?.let { mapOf("since" to it) } ?: emptyMap()),
            ListSerializer(RevenueEvent.serializer()),
        )

    /** Répartition par type de source pour un événement (`GET /wallet/revenues/breakdown?sourceId=`). */
    suspend fun breakdown(sourceId: String): List<RevenueBreakdown> =
        api.request(
            Endpoint.get(WalletEndpoints.REVENUES_BREAKDOWN, query = mapOf("sourceId" to sourceId)),
            ListSerializer(RevenueBreakdown.serializer()),
        )

    /** Transactions détaillées paginées (`GET /wallet/transactions?sourceId=&limit=&offset=`). */
    suspend fun transactions(sourceId: String, limit: Int = 10, offset: Int = 0): List<EventTransaction> =
        api.request(
            Endpoint.get(
                WalletEndpoints.TRANSACTIONS,
                query = mapOf("sourceId" to sourceId, "limit" to limit.toString(), "offset" to offset.toString()),
            ),
            ListSerializer(EventTransaction.serializer()),
        )
}

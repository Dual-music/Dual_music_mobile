package com.dualmusic.feature.withdrawal

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.model.WithdrawalNet
import com.dualmusic.domain.model.WithdrawalRequest
import com.dualmusic.domain.withdrawal.CreateWithdrawalRequest
import com.dualmusic.domain.withdrawal.NetRequest
import com.dualmusic.domain.withdrawal.PayoutMethodData
import com.dualmusic.domain.withdrawal.PinStatus
import com.dualmusic.domain.withdrawal.SetPinRequest
import com.dualmusic.domain.withdrawal.WithdrawalEndpoints
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès REST au flux de retrait.
 *
 * Sécurité : la création d'un retrait exige le **PIN de retrait** (6 chiffres), re-vérifié
 * côté serveur avec verrouillage après échecs répétés. Idempotence sur la demande.
 */
class WithdrawalRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** L'utilisateur a-t-il déjà défini un PIN de retrait ? */
    suspend fun hasPin(): Boolean =
        api.request(Endpoint.get(WithdrawalEndpoints.PIN), PinStatus.serializer()).hasPin

    /** Crée/remplace le PIN (currentPin requis si un PIN existe déjà). */
    suspend fun setPin(newPin: String, currentPin: String? = null) {
        val body = json.encodeToString(SetPinRequest.serializer(), SetPinRequest(newPin, currentPin))
        api.request<Unit>(Endpoint.post(WithdrawalEndpoints.PIN, body))
    }

    /** Méthodes de retrait enregistrées (défaut en premier). */
    suspend fun methods(): List<PayoutMethodData> =
        api.request(Endpoint.get(WithdrawalEndpoints.METHODS), ListSerializer(PayoutMethodData.serializer()))

    /** Aperçu du net après frais pour un montant brut (le net réel fait foi côté serveur). */
    suspend fun net(amount: Double): WithdrawalNet {
        val body = json.encodeToString(NetRequest.serializer(), NetRequest(amount))
        return api.request(Endpoint.post(WithdrawalEndpoints.NET, body), WithdrawalNet.serializer())
    }

    /** Demandes de retrait du caller (les plus récentes d'abord). */
    suspend fun myRequests(): List<WithdrawalRequest> =
        api.request(Endpoint.get(WithdrawalEndpoints.MINE), ListSerializer(WithdrawalRequest.serializer()))

    /**
     * Crée une demande de retrait. Réserve les fonds atomiquement côté serveur après
     * vérification du PIN.
     * @param pin PIN de retrait (6 chiffres).
     */
    suspend fun createRequest(
        amount: Double,
        pin: String,
        payoutMethodId: String?,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val body = json.encodeToString(
            CreateWithdrawalRequest.serializer(),
            CreateWithdrawalRequest(amount = amount, pin = pin, payoutMethodId = payoutMethodId),
        )
        api.request<Unit>(Endpoint.post(WithdrawalEndpoints.CREATE, body, idempotencyKey = idempotencyKey))
    }
}

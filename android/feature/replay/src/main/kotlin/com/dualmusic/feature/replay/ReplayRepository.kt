package com.dualmusic.feature.replay

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.replay.ReplayAccess
import com.dualmusic.domain.replay.ReplayEndpoints
import com.dualmusic.domain.replay.ReplayVideo
import com.dualmusic.domain.replay.UnlockReplayRequest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Accès REST aux rediffusions.
 *
 * Catalogue + vérification d'accès + déblocage (débit atomique via le portefeuille).
 */
class ReplayRepository(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Catalogue des replays (publics / débloqués selon le user). */
    suspend fun replays(limit: Int = 50): List<ReplayVideo> =
        api.request(
            Endpoint.get(ReplayEndpoints.LIST, query = mapOf("limit" to limit.toString())),
            ListSerializer(ReplayVideo.serializer()),
        )

    /** Détail d'un replay. */
    suspend fun replay(id: String): ReplayVideo =
        api.request(Endpoint.get(ReplayEndpoints.detail(id)), ReplayVideo.serializer())

    /** Le caller a-t-il déjà accès à ce replay (gratuit ou débloqué) ? */
    suspend fun hasAccess(id: String): Boolean =
        api.request(Endpoint.get(ReplayEndpoints.access(id)), ReplayAccess.serializer()).hasAccess

    /** Enregistre une vue (best-effort, non bloquant côté UI). */
    suspend fun registerView(id: String) {
        api.request<Unit>(Endpoint.post(ReplayEndpoints.views(id)))
    }

    /**
     * Débloque un replay premium (débit atomique + idempotent).
     * @return true si le déblocage a réussi.
     */
    suspend fun unlock(id: String, idempotencyKey: String = UUID.randomUUID().toString()): Boolean {
        val body = json.encodeToString(UnlockReplayRequest.serializer(), UnlockReplayRequest(id))
        return runCatching {
            api.request<Unit>(Endpoint.post(ReplayEndpoints.UNLOCK, body, idempotencyKey = idempotencyKey))
        }.isSuccess
    }
}

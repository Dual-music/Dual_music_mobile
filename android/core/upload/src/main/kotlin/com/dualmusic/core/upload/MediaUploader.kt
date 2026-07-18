package com.dualmusic.core.upload

import com.dualmusic.core.network.ApiClient
import com.dualmusic.core.network.Endpoint
import com.dualmusic.domain.upload.ConfirmRequest
import com.dualmusic.domain.upload.ConfirmResult
import com.dualmusic.domain.upload.PresignRequest
import com.dualmusic.domain.upload.PresignResult
import com.dualmusic.domain.upload.UploadEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Upload de média en trois temps (voir [com.dualmusic.domain.upload]) :
 *  1. `POST /uploads/presign` (via [api], authentifié) → URL signée + clé + URL publique.
 *  2. `PUT` brut des octets vers l'URL signée (via [rawHttp], SANS auth ni préfixe API).
 *  3. `POST /uploads/confirm` (best-effort) → URL finale exploitable.
 *
 * @param api client HTTP applicatif (presign/confirm, Bearer + enveloppe).
 */
class MediaUploader(private val api: ApiClient) {

    private val json = Json { explicitNulls = false }

    /** Client dédié au PUT signé : aucune configuration d'auth/base-url (URL absolue). */
    private val rawHttp: HttpClient = HttpClient(OkHttp)

    /**
     * Envoie [media] dans la [category] donnée et renvoie l'URL publique finale du média.
     *
     * @param media fichier local lu ([readLocalMedia]).
     * @param category catégorie serveur (voir [com.dualmusic.domain.upload.UploadCategory]) :
     *   `image` pour une pochette (≤ 5 Mo), `sponsor` pour un média de pub (≤ 500 Mo).
     * @return l'URL publique à persister sur la ressource (pochette, mediaUrl…).
     * @throws IllegalStateException si le PUT échoue ou si aucune URL exploitable n'est obtenue.
     */
    suspend fun upload(media: LocalMedia, category: String): String {
        // 1. Presign.
        val presignBody = json.encodeToString(
            PresignRequest.serializer(),
            PresignRequest(
                category = category,
                filename = media.filename,
                contentType = media.contentType,
                size = media.size,
            ),
        )
        val presign: PresignResult = api.request(
            Endpoint.post(UploadEndpoints.PRESIGN, presignBody),
            PresignResult.serializer(),
        )

        // 2. PUT brut des octets vers l'URL signée.
        val put: HttpResponse = rawHttp.put(presign.uploadUrl) {
            contentType(ContentType.parse(media.contentType))
            setBody(media.bytes)
        }
        check(put.status.isSuccessLike()) { "Échec de l'envoi du fichier (HTTP ${put.status.value})." }

        // 3. Confirm (best-effort : validation magic-bytes + scan côté serveur).
        val confirmed: ConfirmResult? = runCatching {
            val confirmBody = json.encodeToString(ConfirmRequest.serializer(), ConfirmRequest(presign.key))
            api.request(Endpoint.post(UploadEndpoints.CONFIRM, confirmBody), ConfirmResult.serializer())
        }.getOrNull()

        return confirmed?.publicUrl
            ?: confirmed?.url
            ?: presign.publicUrl
            ?: error("Upload confirmé mais aucune URL publique retournée.")
    }

    /** Vrai pour tout statut 2xx (le stockage objet répond souvent 200/204 sur un PUT). */
    private fun HttpStatusCode.isSuccessLike(): Boolean = value in 200..299
}

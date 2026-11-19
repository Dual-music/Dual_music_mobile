package com.dualmusic.core.network

import com.dualmusic.domain.api.ApiResponse
import com.dualmusic.domain.api.DomainError
import com.dualmusic.domain.api.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod as KtorMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Client HTTP typé de Dual Music (Android).
 *
 * - Préfixe `…/api/v1`, attache le Bearer JWT, envoie l'`Idempotency-Key` si présent.
 * - Décode l'enveloppe `{ data, meta }` / `{ error }` ; renvoie `data` ou lève [DomainError].
 * - Sur `401`, effectue **un** refresh partagé (single-flight via [Mutex]) puis rejoue.
 *
 * @param baseUrl URL publique SANS `/api/v1` (ex. `https://api.dualmusic.app`).
 * @param scope portée coroutine hébergeant la tâche de refresh partagée (app scope).
 */
class ApiClient(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher,
    private val scope: CoroutineScope,
    private val http: HttpClient = defaultEngine(),
) {
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true // Tolère les champs backend non modélisés côté mobile.
        explicitNulls = false
        // Un champ non-nullable reçu `null` (ou un enum inconnu) retombe sur sa valeur par défaut
        // au lieu de faire échouer TOUTE la désérialisation (sinon une liste entière devient vide).
        coerceInputValues = true
    }

    private val refreshMutex = Mutex()
    private var refreshInFlight: Deferred<Boolean>? = null

    // MARK: API publique

    /**
     * Exécute une requête et renvoie `data` typé.
     * `dataSerializer` doit être un [KSerializer] (et non un simple [DeserializationStrategy]) :
     * `ApiResponse.serializer(...)` généré exige un `KSerializer<T>` pour construire le
     * sérialiseur de l'enveloppe.
     */
    suspend fun <T> request(endpoint: Endpoint, dataSerializer: KSerializer<T>): T {
        val raw = executeWithRefresh(endpoint)
        return unwrap(raw, ApiResponse.serializer(dataSerializer))
    }

    /** Variante reified pour un appel concis : `client.request<Wallet>(endpoint)`. */
    suspend inline fun <reified T> request(endpoint: Endpoint): T =
        request(endpoint, serializer())

    // MARK: Cœur d'exécution + refresh single-flight

    private suspend fun executeWithRefresh(endpoint: Endpoint, isRetry: Boolean = false): Raw {
        val response = perform(endpoint)
        if (response.status.value == 401 && !endpoint.anonymous && !isRetry) {
            if (refreshSession()) {
                return executeWithRefresh(endpoint, isRetry = true)
            }
            tokenStore.clear()
            throw DomainError(401, "UNAUTHORIZED", "Session expirée")
        }
        return Raw(response.status.value, runCatching { response.bodyAsText() }.getOrDefault(""))
    }

    /** Lance ou rejoint l'unique refresh en vol. `true` si la session est de nouveau valide. */
    private suspend fun refreshSession(): Boolean {
        val job = refreshMutex.withLock {
            refreshInFlight ?: scope.async {
                val token = tokenStore.refreshToken() ?: return@async false
                runCatching {
                    val pair = refresher.refresh(token)
                    tokenStore.setTokens(pair.access, pair.refresh)
                    true
                }.getOrDefault(false)
            }.also { refreshInFlight = it }
        }
        val result = job.await()
        refreshMutex.withLock { if (refreshInFlight === job) refreshInFlight = null }
        return result
    }

    private suspend fun perform(endpoint: Endpoint): HttpResponse {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/api/v1/")
            append(endpoint.path.trimStart('/'))
        }
        return try {
            http.request(url) {
                method = endpoint.method.toKtor()
                endpoint.query.forEach { (k, v) -> this.url.parameters.append(k, v) }
                header("Accept", "application/json")
                // On demande une réponse NON compressée. Le backend (middleware `compression`)
                // gzip les réponses > ~1 Ko ; or le moteur Ktor/OkHttp ne les décompresse pas ici
                // → le corps gzippé illisible faisait échouer le décodage (ex. login) et remontait
                // en « Connexion instable ». `identity` = pas de compression, corps JSON brut.
                header("Accept-Encoding", "identity")
                endpoint.idempotencyKey?.let { header("Idempotency-Key", it) }
                if (!endpoint.anonymous) {
                    tokenStore.accessToken()?.let { header("Authorization", "Bearer $it") }
                }
                endpoint.body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it)
                }
            }
        } catch (e: Throwable) {
            // Jamais le message brut de l'exception réseau : un timeout Ktor/OkHttp inclut l'URL
            // complète et des paramètres internes ("Socket timeout has expired [url=...
            // socket_timeout=unknown] ms"), illisible et déroutant affiché tel quel à l'utilisateur.
            throw DomainError(0, ErrorCode.NETWORK, humanizeNetworkException(e))
        }
    }

    /** Traduit une exception réseau bas niveau (timeout, DNS, connexion…) en message lisible. */
    private fun humanizeNetworkException(e: Throwable): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("timeout") -> "Connexion trop lente. Vérifiez votre connexion internet puis réessayez."
            msg.contains("unable to resolve host") || msg.contains("failed to connect") || msg.contains("unreachable") ->
                "Connexion interrompue. Vérifiez votre connexion internet puis réessayez."
            else -> "Erreur réseau. Réessayez dans un instant."
        }
    }

    /** Applique la règle succès/erreur sur l'enveloppe décodée. */
    private fun <T> unwrap(raw: Raw, envSerializer: DeserializationStrategy<ApiResponse<T>>): T {
        if (raw.status == 204 || raw.body.isBlank()) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T // Endpoints d'action sans contenu.
        }
        val env = runCatching { json.decodeFromString(envSerializer, raw.body) }.getOrElse {
            if (raw.status !in 200..299) throw httpError(raw.status)
            throw DomainError(0, "DECODING_ERROR", it.message ?: "Décodage impossible")
        }
        if (raw.status !in 200..299 || env.error != null) {
            // Sur une erreur de validation, le backend précise le(s) champ(s) fautif(s) dans
            // `details` (JSON arbitraire, forme variable selon le code d'erreur). On le traduit
            // en texte lisible plutôt que d'exposer la syntaxe JSON brute (accolades,
            // `clé=valeur`) telle quelle à l'utilisateur.
            val fieldErrors = detailsAsMap(env.error?.details)
            val detail = fieldErrors.entries.joinToString(" — ") { (k, v) -> "${humanizeKey(k)} : $v" }
            throw DomainError(
                httpStatus = raw.status,
                code = env.error?.code ?: "HTTP_${raw.status}",
                message = listOfNotNull(env.error?.message ?: "Erreur HTTP ${raw.status}", detail.ifBlank { null })
                    .joinToString(" — "),
                details = fieldErrors,
            )
        }
        return env.data ?: throw DomainError(raw.status, "NO_DATA", "Champ data absent")
    }

    private fun httpError(status: Int) = DomainError(status, "HTTP_$status", "Erreur HTTP $status")

    /**
     * Aplati un `details` JSON en `Map<champ, valeur lisible>` — ne garde que les entrées d'un
     * objet dont la valeur est un scalaire (string/nombre/booléen) ; un objet/tableau imbriqué
     * est ignoré plutôt que de produire du JSON brut illisible.
     */
    private fun detailsAsMap(details: JsonElement?): Map<String, String> {
        val obj = details as? JsonObject ?: return emptyMap()
        return obj.entries.mapNotNull { (key, value) ->
            val prim = value as? JsonPrimitive ?: return@mapNotNull null
            val text = prim.jsonPrimitive.content
            if (text.isBlank() || text == "null") null else key to text
        }.toMap()
    }

    /** `"birthDate"` → `"Birth date"`, `"min_seconds"` → `"Min seconds"`. */
    private fun humanizeKey(key: String): String {
        val spaced = key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').lowercase()
        return spaced.replaceFirstChar { it.uppercase() }
    }

    /** Réponse brute intermédiaire (statut + corps texte). */
    private data class Raw(val status: Int, val body: String)

    companion object {
        /** Moteur Ktor par défaut (OkHttp). Le certificate pinning s'ajoute dans `core-auth`. */
        fun defaultEngine(): HttpClient = HttpClient(OkHttp)
    }
}

private fun HttpMethod.toKtor(): KtorMethod = when (this) {
    HttpMethod.GET -> KtorMethod.Get
    HttpMethod.POST -> KtorMethod.Post
    HttpMethod.PUT -> KtorMethod.Put
    HttpMethod.PATCH -> KtorMethod.Patch
    HttpMethod.DELETE -> KtorMethod.Delete
}

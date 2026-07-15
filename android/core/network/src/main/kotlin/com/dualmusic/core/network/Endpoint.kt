package com.dualmusic.core.network

/** Verbes HTTP supportés. */
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/**
 * Description déclarative d'une requête, indépendante du transport.
 * Les `feature-*` composent des [Endpoint] ; [ApiClient] les exécute.
 *
 * @property body corps déjà sérialisé en JSON (String), ou null. On sérialise dans le
 *   repository via le `Json` partagé pour garder [ApiClient] agnostique du type de corps.
 * @property anonymous si vrai : pas de Bearer, pas de refresh (endpoints publics/auth).
 * @property idempotencyKey en-tête `Idempotency-Key` pour les débits (votes, cadeaux, achats).
 */
data class Endpoint(
    val method: HttpMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    val anonymous: Boolean = false,
    val idempotencyKey: String? = null,
) {
    companion object {
        fun get(path: String, query: Map<String, String> = emptyMap(), anonymous: Boolean = false) =
            Endpoint(HttpMethod.GET, path, query, anonymous = anonymous)

        fun post(path: String, body: String? = null, idempotencyKey: String? = null) =
            Endpoint(HttpMethod.POST, path, body = body, idempotencyKey = idempotencyKey)

        fun patch(path: String, body: String? = null) = Endpoint(HttpMethod.PATCH, path, body = body)

        fun delete(path: String, body: String? = null) = Endpoint(HttpMethod.DELETE, path, body = body)
    }
}

package com.dualmusic.domain.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Enveloppe de réponse standard du backend Dual Music.
 *
 * Toute réponse REST a l'une de deux formes :
 *  - **succès** : `{ "data": <T>, "meta": { "requestId": ..., "pagination"?: ... } }`
 *  - **erreur** : `{ "error": { "code": ..., "message": ..., "details"?: ... } }`
 *
 * `core-network` décode cette enveloppe UNE seule fois : il renvoie `data` au repository,
 * ou lève une `ApiError` construite depuis `error`. Les couches supérieures ne
 * manipulent jamais l'enveloppe directement.
 *
 * @param T type utile transporté par `data`.
 */
@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val meta: ApiMeta? = null,
    val error: ApiErrorBody? = null,
)

/**
 * Métadonnées de réponse (traçabilité + pagination éventuelle).
 *
 * @property requestId identifiant de corrélation renvoyé par le backend (logs/support).
 * @property pagination présent uniquement sur les listes paginées.
 */
@Serializable
data class ApiMeta(
    val requestId: String? = null,
    val pagination: Pagination? = null,
)

/**
 * Pagination — supporte les deux modes du backend : `page` (offset) et `cursor` (keyset).
 *
 * @property mode "page" ou "cursor".
 * @property nextCursor curseur d'appel suivant en mode cursor (null si dernière page).
 * @property hasMore indique s'il reste des éléments.
 */
@Serializable
data class Pagination(
    val mode: String? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val totalPages: Int? = null,
    val hasMore: Boolean? = null,
    val nextCursor: String? = null,
)

/**
 * Corps d'erreur métier renvoyé par le backend.
 *
 * @property code code machine stable (ex. `WALLET_INSUFFICIENT`, `PIN_WRONG`) — c'est sur
 *   ce champ que l'UI branche ses messages/comportements, jamais sur `message` (localisé).
 * @property message message lisible (déjà localisé côté serveur le cas échéant).
 * @property details contexte structuré optionnel (champ invalide, seuils, …).
 */
@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

/**
 * Enveloppe pour un accusé de réception idempotent (achats, votes, cadeaux…).
 * Le backend expose une clé d'idempotence pour éviter les doubles débits sur retry réseau.
 */
@Serializable
data class Ack(
    @SerialName("success") val success: Boolean = true,
    @SerialName("transactionId") val transactionId: String? = null,
)

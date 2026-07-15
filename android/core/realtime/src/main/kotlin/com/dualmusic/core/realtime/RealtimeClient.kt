package com.dualmusic.core.realtime

import com.dualmusic.domain.realtime.Realtime
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.json.JSONObject
import java.net.URI

/**
 * Client temps réel Socket.IO de Dual Music (Android).
 *
 * Un [Socket] par namespace (`/chat`, `/live`, `/notifications`), partageant le handshake
 * JWT. Reconnexion exponentielle (1→30 s) déléguée au SDK. Les événements serveur sont
 * exposés via [NamespaceSession.on] avec décodage typé kotlinx.serialization.
 *
 * Exemple :
 * ```
 * val rt = RealtimeClient(baseUrl) { tokenStore.accessToken() }
 * val live = rt.session(Realtime.Namespace.LIVE)
 * live.onConnect { live.join(Realtime.RoomType.DUEL, duelId) }
 * val sub = live.on(Realtime.RealtimeEvent.VOTE, VotePayload.serializer()) { vote -> ... }
 * live.connect()
 * ```
 *
 * @param baseUrl URL publique du backend (ex. `https://api.dualmusic.app`).
 * @param jwtProvider fournit le JWT courant pour le handshake.
 */
class RealtimeClient(
    private val baseUrl: String,
    private val jwtProvider: suspend () -> String?,
) {
    private val sessions = mutableMapOf<String, NamespaceSession>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Récupère (ou crée) la session d'un namespace. */
    @Synchronized
    fun session(namespace: String): NamespaceSession =
        sessions.getOrPut(namespace) { NamespaceSession(baseUrl, namespace, jwtProvider, json) }

    /** Déconnecte tous les namespaces (arrière-plan / logout). */
    fun disconnectAll() = sessions.values.forEach { it.disconnect() }
}

/** Session d'un namespace : connexion, join/leave, écoute typée. */
class NamespaceSession internal constructor(
    private val baseUrl: String,
    private val namespace: String,
    private val jwtProvider: suspend () -> String?,
    private val json: Json,
) {
    private var socket: Socket? = null

    /** Construit les options avec le JWT en handshake (`auth.token`) + reconnexion. */
    private fun options(token: String?): IO.Options = IO.Options.builder()
        .setTransports(arrayOf("websocket"))
        .setReconnection(true)
        .setReconnectionDelay(1_000)
        .setReconnectionDelayMax(30_000)
        .setRandomizationFactor(0.5) // jitter
        .setAuth(token?.let { mapOf("token" to it) } ?: emptyMap())
        .build()

    /** Connecte le socket du namespace après injection du JWT. */
    suspend fun connect() {
        val token = jwtProvider()
        val uri = URI.create(baseUrl.trimEnd('/') + namespace)
        val s = IO.socket(uri, options(token))
        socket = s
        s.connect()
    }

    fun disconnect() { socket?.disconnect(); socket = null }

    /** Rejoint une room `type:id`. */
    fun join(type: Realtime.RoomType, id: String) {
        socket?.emit(Realtime.EVENT_JOIN, JSONObject(mapOf("type" to type.wire, "id" to id)))
    }

    /** Quitte une room. */
    fun leave(type: Realtime.RoomType, id: String) {
        socket?.emit(Realtime.EVENT_LEAVE, JSONObject(mapOf("type" to type.wire, "id" to id)))
    }

    /** Callback de connexion établie (pour (re)join les rooms). */
    fun onConnect(handler: () -> Unit) {
        socket?.on(Socket.EVENT_CONNECT) { handler() }
    }

    /** Callback de déconnexion. */
    fun onDisconnect(handler: () -> Unit) {
        socket?.on(Socket.EVENT_DISCONNECT) { handler() }
    }

    /**
     * Écoute un événement serveur et décode son premier argument JSON en [T].
     * @param event nom d'événement (voir [Realtime.RealtimeEvent]).
     * @param deserializer serializer kotlinx du payload.
     */
    fun <T> on(event: String, deserializer: DeserializationStrategy<T>, handler: (T) -> Unit) {
        socket?.on(event) { args ->
            val first = args.firstOrNull() as? JSONObject ?: return@on
            runCatching { json.decodeFromString(deserializer, first.toString()) }
                .getOrNull()
                ?.let(handler)
        }
    }

    /** Variante reified : `session.on<VotePayload>(VOTE) { ... }`. */
    inline fun <reified T> on(event: String, noinline handler: (T) -> Unit) =
        on(event, serializer(), handler)
}

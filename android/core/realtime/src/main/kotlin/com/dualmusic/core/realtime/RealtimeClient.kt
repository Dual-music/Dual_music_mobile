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

    // Handlers mémorisés AVANT que le socket existe. Indispensable : le VM appelle
    // onConnect{}/on{} AVANT connect(), donc `socket` est null à ce moment-là. On les
    // (ré)enregistre sur le socket dans connect() (et à chaque reconnexion/re-création),
    // sinon les callbacks sont perdus (bug: le /chat ne rejoignait jamais sa room).
    private val connectHandlers = mutableListOf<() -> Unit>()
    private val disconnectHandlers = mutableListOf<() -> Unit>()
    private val eventHandlers = mutableListOf<Pair<String, (Array<out Any?>) -> Unit>>()

    /** Construit les options avec le JWT en handshake (`auth.token`) + reconnexion. */
    private fun options(token: String?): IO.Options = IO.Options.builder()
        .setTransports(arrayOf("websocket"))
        .setReconnection(true)
        .setReconnectionDelay(1_000)
        .setReconnectionDelayMax(30_000)
        .setRandomizationFactor(0.5) // jitter
        .setAuth(token?.let { mapOf("token" to it) } ?: emptyMap())
        .build()

    /** Connecte le socket du namespace après injection du JWT + (ré)applique tous les handlers. */
    suspend fun connect() {
        val token = jwtProvider()
        val uri = URI.create(baseUrl.trimEnd('/') + namespace)
        socket?.disconnect() // ferme l'ancien socket avant d'en créer un nouveau (pas de doublon)
        val s = IO.socket(uri, options(token))
        // (Ré)enregistre TOUS les handlers mémorisés sur ce nouveau socket, peu importe l'ordre
        // des appels onConnect/on par rapport à connect().
        connectHandlers.forEach { h -> s.on(Socket.EVENT_CONNECT) { h() } }
        disconnectHandlers.forEach { h -> s.on(Socket.EVENT_DISCONNECT) { h() } }
        eventHandlers.forEach { (event, raw) -> s.on(event) { args -> raw(args) } }
        socket = s
        s.connect()
    }

    /** Déconnecte + oublie les handlers (session partagée : évite l'accumulation entre écrans). */
    fun disconnect() {
        socket?.disconnect()
        socket = null
        connectHandlers.clear()
        disconnectHandlers.clear()
        eventHandlers.clear()
    }

    /** Rejoint une room `type:id`. */
    fun join(type: Realtime.RoomType, id: String) {
        socket?.emit(Realtime.EVENT_JOIN, JSONObject(mapOf("type" to type.wire, "id" to id)))
    }

    /** Quitte une room. */
    fun leave(type: Realtime.RoomType, id: String) {
        socket?.emit(Realtime.EVENT_LEAVE, JSONObject(mapOf("type" to type.wire, "id" to id)))
    }

    /**
     * Émet un événement arbitraire (client → serveur). Utilisé pour le relais de broadcast
     * éphémère (`broadcast:join`, `broadcast`) : réactions emojis, animations… Le serveur
     * relaie aux autres membres du canal (l'émetteur applique son effet localement).
     * @param payload argument JSON (String de canal, ou [JSONObject]). `null` = sans argument.
     */
    fun emit(event: String, payload: Any? = null) {
        if (payload == null) socket?.emit(event) else socket?.emit(event, payload)
    }

    /** Callback de connexion établie (pour (re)join les rooms). Mémorisé + posé si socket prêt. */
    fun onConnect(handler: () -> Unit) {
        connectHandlers += handler
        socket?.on(Socket.EVENT_CONNECT) { handler() }
    }

    /** Callback de déconnexion. Mémorisé + posé si socket prêt. */
    fun onDisconnect(handler: () -> Unit) {
        disconnectHandlers += handler
        socket?.on(Socket.EVENT_DISCONNECT) { handler() }
    }

    /**
     * Écoute un événement serveur et décode son premier argument JSON en [T].
     * Le handler est mémorisé (et réappliqué à chaque connect) pour survivre à l'ordre des appels.
     * @param event nom d'événement (voir [Realtime.RealtimeEvent]).
     * @param deserializer serializer kotlinx du payload.
     */
    fun <T> on(event: String, deserializer: DeserializationStrategy<T>, handler: (T) -> Unit) {
        val raw: (Array<out Any?>) -> Unit = { args ->
            val first = args.firstOrNull() as? JSONObject
            if (first != null) {
                runCatching { json.decodeFromString(deserializer, first.toString()) }
                    .getOrNull()
                    ?.let(handler)
            }
        }
        eventHandlers += event to raw
        socket?.on(event) { args -> raw(args) }
    }

    /** Variante reified : `session.on<VotePayload>(VOTE) { ... }`. */
    inline fun <reified T> on(event: String, noinline handler: (T) -> Unit) =
        on(event, serializer(), handler)
}

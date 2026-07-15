import Foundation
import SocketIO

/// Client temps réel Socket.IO de Dual Music (iOS).
///
/// Un `SocketManager` par URL de base ; chaque namespace (`/chat`, `/live`,
/// `/notifications`) est un `SocketIOClient` distinct partageant le handshake JWT.
/// Reconnexion exponentielle gérée par le SDK (1→30 s). Les événements serveur sont
/// exposés en handlers typés décodés depuis JSON.
///
/// Cycle de vie type :
/// ```
/// let rt = RealtimeClient(baseURL: url) { await tokenStore.accessToken() }
/// let live = rt.session(.live)
/// live.onConnect { live.join(.duel, id: duelId) }
/// let sub = live.onEvent(Realtime.Event.vote, as: VotePayload.self) { vote in ... }
/// live.connect()
/// ```
public final class RealtimeClient {

    private let manager: SocketManager
    private var sessions: [Realtime.Namespace: NamespaceSession] = [:]
    private let lock = NSLock()

    /// - Parameters:
    ///   - baseURL: URL publique du backend (ex. `https://api.dualmusic.app`). Le SDK gère
    ///     l'upgrade en WebSocket sur `/socket.io/`.
    ///   - jwtProvider: fournit le JWT courant pour le handshake (connectParams `token`).
    public init(baseURL: URL, jwtProvider: @escaping @Sendable () async -> String?) {
        // Note : le token est résolu de façon synchrone au moment de la config. Pour un
        // refresh, appeler `reconnect(with:)` avec le nouveau jeton.
        self.manager = SocketManager(
            socketURL: baseURL,
            config: [
                .log(false),
                .compress,
                .reconnects(true),
                .reconnectWait(1),
                .reconnectWaitMax(30),
                .randomizationFactor(0.5), // jitter
                .forceWebsockets(true),
            ]
        )
        self.jwtProvider = jwtProvider
    }

    private let jwtProvider: @Sendable () async -> String?

    /// Récupère (ou crée) la session d'un namespace.
    public func session(_ namespace: Realtime.Namespace) -> NamespaceSession {
        lock.lock(); defer { lock.unlock() }
        if let existing = sessions[namespace] { return existing }
        let socket = manager.socket(forNamespace: namespace.rawValue)
        let session = NamespaceSession(namespace: namespace, socket: socket, jwtProvider: jwtProvider)
        sessions[namespace] = session
        return session
    }

    /// Reconnecte tous les namespaces avec un nouveau jeton (après refresh JWT).
    public func reconnect(with token: String) {
        manager.setConfigs([.connectParams(["token": token])])
        manager.reconnect()
    }

    /// Déconnecte tous les namespaces (mise en arrière-plan, logout).
    public func disconnectAll() {
        lock.lock(); let all = sessions.values; lock.unlock()
        all.forEach { $0.disconnect() }
    }
}

/// Session d'un namespace : connexion, join/leave de rooms, écoute typée d'événements.
public final class NamespaceSession {

    private let namespace: Realtime.Namespace
    private let socket: SocketIOClient
    private let jwtProvider: @Sendable () async -> String?
    private let decoder = JSONDecoder()

    init(namespace: Realtime.Namespace, socket: SocketIOClient, jwtProvider: @escaping @Sendable () async -> String?) {
        self.namespace = namespace
        self.socket = socket
        self.jwtProvider = jwtProvider
    }

    /// Connecte le socket après avoir injecté le JWT dans le handshake.
    public func connect() {
        Task {
            let token = await jwtProvider()
            // Le token est passé en connectParams du manager (partagé) ; ici on garantit
            // qu'il est à jour avant le connect du namespace.
            if let token { socket.manager?.setConfigs([.connectParams(["token": token])]) }
            socket.connect()
        }
    }

    public func disconnect() { socket.disconnect() }

    /// Rejoint une room `type:id` (émet `join { type, id }`).
    public func join(_ type: Realtime.RoomType, id: String) {
        socket.emit(Realtime.eventJoin, ["type": type.rawValue, "id": id])
    }

    /// Quitte une room.
    public func leave(_ type: Realtime.RoomType, id: String) {
        socket.emit(Realtime.eventLeave, ["type": type.rawValue, "id": id])
    }

    /// Callback de connexion établie (utile pour (re)join les rooms après reconnexion).
    @discardableResult
    public func onConnect(_ handler: @escaping @Sendable () -> Void) -> Subscription {
        let uuid = socket.on(clientEvent: .connect) { _, _ in handler() }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }

    /// Callback de déconnexion.
    @discardableResult
    public func onDisconnect(_ handler: @escaping @Sendable () -> Void) -> Subscription {
        let uuid = socket.on(clientEvent: .disconnect) { _, _ in handler() }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }

    /// Écoute un événement serveur et décode son premier argument JSON en `T`.
    /// - Returns: une `Subscription` à conserver ; `cancel()` retire le listener.
    @discardableResult
    public func onEvent<T: Decodable>(_ event: String, as type: T.Type, _ handler: @escaping @Sendable (T) -> Void) -> Subscription {
        let uuid = socket.on(event) { [decoder] data, _ in
            guard let first = data.first else { return }
            guard let payload = try? JSONSerialization.data(withJSONObject: first),
                  let decoded = try? decoder.decode(T.self, from: payload) else { return }
            handler(decoded)
        }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }
}

/// Handle d'annulation d'un listener. À conserver tant que l'écoute doit vivre.
public final class Subscription {
    private let onCancel: () -> Void
    private var cancelled = false
    init(_ onCancel: @escaping () -> Void) { self.onCancel = onCancel }
    public func cancel() { guard !cancelled else { return }; cancelled = true; onCancel() }
    deinit { cancel() }
}

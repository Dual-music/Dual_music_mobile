import Foundation
import SocketIO
import DomainModels

/// Client temps réel Socket.IO de Dual Music (iOS) — miroir de `RealtimeClient` (Android).
///
/// Un `SocketManager` par URL de base ; chaque namespace (`/chat`, `/live`,
/// `/notifications`) est un `SocketIOClient` distinct partageant le même transport et le
/// même handshake JWT. La reconnexion exponentielle (1 → 30 s, avec jitter) est déléguée au
/// SDK, exactement comme côté Android.
///
/// ```swift
/// let rt = RealtimeClient(baseURL: url) { await tokenStore.accessToken() }
/// let live = rt.session(.live)
/// live.onConnect { live.join(.duel, id: duelId) }   // (re)join après chaque reconnexion
/// let sub = live.onEvent(Realtime.Event.vote, as: VotePayload.self) { vote in … }
/// await live.connect()
/// ```
///
/// > Cycle de vie : conserver les ``Subscription`` retournées tant que l'écoute doit vivre
/// > (les relâcher retire le listener), et appeler ``disconnectAll()`` au logout.
public final class RealtimeClient {

    private let manager: SocketManager
    private var sessions: [Realtime.Namespace: NamespaceSession] = [:]
    private let lock = NSLock()
    private let jwtProvider: () async -> String?

    /// - Parameters:
    ///   - baseURL: URL publique du backend (ex. `https://api.dualmusic.app`). Le SDK gère
    ///     l'upgrade WebSocket sur `/socket.io/`.
    ///   - jwtProvider: fournit le JWT courant au moment du handshake. Résolu **à chaque
    ///     connexion** (et non une fois pour toutes) pour toujours utiliser un jeton frais.
    public init(baseURL: URL, jwtProvider: @escaping () async -> String?) {
        self.manager = SocketManager(
            socketURL: baseURL,
            config: [
                .log(false),
                .compress,
                .reconnects(true),
                .reconnectWait(1),
                .reconnectWaitMax(30),
                .randomizationFactor(0.5), // jitter, comme Android
                .forceWebsockets(true),
                .version(.three),          // compatible Socket.IO v3/v4 côté serveur
            ]
        )
        self.jwtProvider = jwtProvider
    }

    /// Récupère (ou crée) la session d'un namespace. Thread-safe.
    /// - Parameter namespace: namespace Socket.IO ciblé.
    public func session(_ namespace: Realtime.Namespace) -> NamespaceSession {
        lock.lock()
        defer { lock.unlock() }
        if let existing = sessions[namespace] { return existing }
        let socket = manager.socket(forNamespace: namespace.rawValue)
        let session = NamespaceSession(
            namespace: namespace,
            socket: socket,
            manager: manager,
            jwtProvider: jwtProvider
        )
        sessions[namespace] = session
        return session
    }

    /// Déconnecte tous les namespaces (mise en arrière-plan prolongée, logout).
    public func disconnectAll() {
        lock.lock()
        let all = Array(sessions.values)
        lock.unlock()
        all.forEach { $0.disconnect() }
        manager.disconnect()
    }
}

/// Session d'un namespace : connexion, join/leave de rooms, écoute typée d'événements.
public final class NamespaceSession {

    private let namespace: Realtime.Namespace
    private let socket: SocketIOClient
    /// Manager parent — conservé explicitement (plutôt que `socket.manager`, faiblement
    /// référencé) pour pouvoir réinjecter le jeton dans le handshake avant chaque connexion.
    private let manager: SocketManager
    private let jwtProvider: () async -> String?
    private let decoder = JSONDecoder()

    init(
        namespace: Realtime.Namespace,
        socket: SocketIOClient,
        manager: SocketManager,
        jwtProvider: @escaping () async -> String?
    ) {
        self.namespace = namespace
        self.socket = socket
        self.manager = manager
        self.jwtProvider = jwtProvider
    }

    /// Connecte le socket après avoir injecté le JWT dans le handshake.
    ///
    /// Le jeton est envoyé **deux fois** :
    /// - dans `auth` (payload de connexion Socket.IO v3/v4) → `socket.handshake.auth.token` ;
    /// - dans la query (`connectParams`) → `socket.handshake.query.token`.
    ///
    /// C'est volontaire : les middlewares Node lisent l'un ou l'autre selon leur version, et
    /// envoyer les deux rend le client indépendant de ce détail côté serveur.
    public func connect() async {
        let token = await jwtProvider()
        if let token {
            manager.setConfigs([.connectParams(["token": token])])
            socket.connect(withPayload: ["token": token])
        } else {
            socket.connect()
        }
    }

    /// Ferme la connexion de ce namespace.
    public func disconnect() {
        socket.disconnect()
    }

    /// Rejoint une room `type:id` (émet `join { type, id }`).
    /// - Parameters:
    ///   - type: type de room.
    ///   - id: identifiant de l'entité (duel, live, concert, compétition).
    public func join(_ type: Realtime.RoomType, id: String) {
        socket.emit(Realtime.eventJoin, ["type": type.rawValue, "id": id])
    }

    /// Quitte une room.
    /// - Parameters:
    ///   - type: type de room.
    ///   - id: identifiant de l'entité.
    public func leave(_ type: Realtime.RoomType, id: String) {
        socket.emit(Realtime.eventLeave, ["type": type.rawValue, "id": id])
    }

    /// Callback de connexion établie — indispensable pour **(re)joindre** les rooms après
    /// chaque reconnexion (le serveur ne les mémorise pas).
    /// - Parameter handler: exécuté sur la file principale.
    /// - Returns: abonnement à conserver.
    @discardableResult
    public func onConnect(_ handler: @escaping () -> Void) -> Subscription {
        let uuid = socket.on(clientEvent: .connect) { _, _ in
            DispatchQueue.main.async { handler() }
        }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }

    /// Callback de déconnexion (affichage d'un état « reconnexion… »).
    /// - Parameter handler: exécuté sur la file principale.
    @discardableResult
    public func onDisconnect(_ handler: @escaping () -> Void) -> Subscription {
        let uuid = socket.on(clientEvent: .disconnect) { _, _ in
            DispatchQueue.main.async { handler() }
        }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }

    /// Écoute un événement serveur et décode son premier argument JSON en `T`.
    ///
    /// Un payload illisible est **ignoré silencieusement** (comme côté Android) : un
    /// événement mal formé ne doit jamais faire tomber l'écran.
    ///
    /// - Parameters:
    ///   - event: nom d'événement (voir `Realtime.Event`).
    ///   - type: type de payload attendu.
    ///   - handler: appelé sur la file principale avec le payload décodé.
    /// - Returns: abonnement à conserver ; `cancel()` retire le listener.
    @discardableResult
    public func onEvent<T: Decodable>(
        _ event: String,
        as type: T.Type,
        _ handler: @escaping (T) -> Void
    ) -> Subscription {
        let decoder = self.decoder
        let uuid = socket.on(event) { data, _ in
            guard let first = data.first,
                  JSONSerialization.isValidJSONObject(first),
                  let raw = try? JSONSerialization.data(withJSONObject: first),
                  let decoded = try? decoder.decode(T.self, from: raw)
            else { return }
            DispatchQueue.main.async { handler(decoded) }
        }
        return Subscription { [weak socket] in socket?.off(id: uuid) }
    }
}

/// Handle d'annulation d'un listener. À conserver tant que l'écoute doit vivre : sa
/// libération (`deinit`) retire automatiquement le listener du socket.
public final class Subscription {
    private let onCancel: () -> Void
    private var cancelled = false

    init(_ onCancel: @escaping () -> Void) { self.onCancel = onCancel }

    /// Retire le listener. Idempotent.
    public func cancel() {
        guard !cancelled else { return }
        cancelled = true
        onCancel()
    }

    deinit { cancel() }
}

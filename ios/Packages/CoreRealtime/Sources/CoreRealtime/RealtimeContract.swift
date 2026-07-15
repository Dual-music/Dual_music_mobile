import Foundation

/// Contrat temps réel (miroir Swift de `shared-domain/realtime/RealtimeContract.kt`).
///
/// On duplique volontairement les constantes côté Swift plutôt que d'exposer les types
/// génériques Kotlin au framework KMM (plus simple et sûr à consommer). La source de
/// vérité reste `shared-domain` — garder les deux alignés.
public enum Realtime {

    /// Namespaces Socket.IO exposés par le backend.
    public enum Namespace: String, Sendable {
        case chat = "/chat"
        case live = "/live"
        case notifications = "/notifications"
    }

    /// Types de room joignables (payload `join { type, id }`).
    public enum RoomType: String, Sendable {
        case duel, concert, competition, live, leaderboard
    }

    /// Nom interne d'une room : `type:id` (identique au backend).
    public static func roomName(_ type: RoomType, _ id: String) -> String { "\(type.rawValue):\(id)" }

    /// Événements client → serveur.
    public static let eventJoin = "join"
    public static let eventLeave = "leave"

    /// Événements serveur → client (voir le contrat partagé pour les payloads).
    public enum Event {
        public static let vote = "vote"
        public static let gift = "gift"
        public static let txGift = "tx:gift"
        public static let txCredit = "tx:credit"
        public static let txWithdrawal = "tx:withdrawal"
        public static let status = "status"
        public static let timer = "timer"
        public static let presence = "presence"
        public static let chatMessage = "message"
        public static let notification = "notification"
    }
}

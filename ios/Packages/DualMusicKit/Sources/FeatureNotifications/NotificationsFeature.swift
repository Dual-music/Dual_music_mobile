import SwiftUI
import Observation
import CoreNetwork
import CoreRealtime
import CoreUI
import DomainModels

/// Accès REST au centre de notifications in-app.
///
/// Le flux temps réel (nouvelles notifications) passe par Socket.IO `/notifications` ; ce
/// repository couvre l'historique + les actions (marquer lu, tout lu).
public struct NotificationRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Dernière page de notifications, les plus récentes d'abord.
    public func list(limit: Int = 50) async throws -> [AppNotification] {
        try await http.request(
            .get(NotificationEndpoints.list, query: ["limit": String(limit)]),
            as: [AppNotification].self
        )
    }

    /// Nombre de notifications non lues (badge de la cloche).
    public func unreadCount() async throws -> Int {
        try await http.request(.get(NotificationEndpoints.unreadCount), as: UnreadCount.self).count
    }

    /// Marque une notification comme lue.
    public func markRead(id: String) async throws {
        try await http.send(.post(NotificationEndpoints.read(id)))
    }

    /// Marque toutes les notifications comme lues.
    public func markAllRead() async throws {
        try await http.send(.post(NotificationEndpoints.readAll))
    }
}

/// ViewModel du centre de notifications in-app.
///
/// Charge l'historique (REST) puis écoute le namespace Socket.IO `/notifications` : chaque
/// notification poussée à l'utilisateur courant est ajoutée en tête de liste. Aucun `join`
/// n'est nécessaire — le handshake JWT identifie le destinataire.
@Observable
@MainActor
public final class NotificationsViewModel {

    public private(set) var items: [AppNotification] = []
    /// Compteur de non-lues, utilisable pour un badge dans la barre supérieure.
    public var unreadCount: Int { items.filter { !$0.read }.count }

    private let repository: NotificationRepository
    private let realtime: RealtimeClient
    private var subscriptions: [Subscription] = []
    private var session: NamespaceSession?

    /// - Parameters:
    ///   - repository: accès REST aux notifications.
    ///   - realtime: client Socket.IO partagé.
    public init(repository: NotificationRepository, realtime: RealtimeClient) {
        self.repository = repository
        self.realtime = realtime
    }

    /// Charge l'historique + connecte le temps réel.
    public func start() async {
        if let list = try? await repository.list() { items = list }

        let notif = realtime.session(.notifications)
        session = notif
        subscriptions.append(notif.onEvent(Realtime.Event.notification, as: AppNotification.self) { [weak self] item in
            self?.items.insert(item, at: 0)
        })
        await notif.connect()
    }

    /// Coupe le temps réel.
    public func stop() {
        subscriptions.forEach { $0.cancel() }
        subscriptions.removeAll()
        session?.disconnect()
    }

    /// Marque une notification lue (optimiste puis serveur).
    public func markRead(id: String) async {
        items = items.map { $0.id == id ? $0.markedRead() : $0 }
        try? await repository.markRead(id: id)
    }

    /// Marque tout lu (optimiste puis serveur).
    public func markAllRead() async {
        items = items.map { $0.markedRead() }
        try? await repository.markAllRead()
    }
}

/// Centre de notifications in-app.
public struct NotificationsView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: NotificationsViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: NotificationsViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            if viewModel.unreadCount > 0 {
                HStack {
                    Spacer()
                    DMButton(s.markAllRead, style: .outline) {
                        Task { await viewModel.markAllRead() }
                    }
                    .frame(width: 160)
                }
            }

            if viewModel.items.isEmpty {
                DMEmptyState(title: s.noNotifications, subtitle: s.noNotificationsHint, systemImage: "bell")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.items) { item in
                            Button {
                                Task { await viewModel.markRead(id: item.id) }
                            } label: {
                                NotificationRow(item: item)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

/// Ligne de notification (pastille non-lu + titre + message).
private struct NotificationRow: View {
    @Environment(\.dmTheme) private var theme
    let item: AppNotification

    var body: some View {
        DMCard {
            HStack(alignment: .top, spacing: theme.spacing.sm) {
                Circle()
                    .fill(item.read ? Color.clear : theme.colors.accent)
                    .frame(width: 8, height: 8)
                    .padding(.top, 6)
                VStack(alignment: .leading, spacing: 2) {
                    if let title = item.title {
                        Text(title)
                            .font(DMFont.body)
                            .fontWeight(item.read ? .regular : .bold)
                            .foregroundStyle(theme.colors.foreground)
                    }
                    if let message = item.message {
                        Text(message)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }
}

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

    /// Préférences d'emails de notification courantes.
    public func emailPreferences() async throws -> NotificationPreferences {
        try await http.request(.get(NotificationEndpoints.preferences), as: NotificationPreferences.self)
    }

    /// Met à jour les préférences d'emails de notification.
    public func updateEmailPreferences(_ prefs: NotificationPreferences) async throws -> NotificationPreferences {
        try await http.request(.put(NotificationEndpoints.preferencesEmail, body: prefs), as: NotificationPreferences.self)
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
@MainActor
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

/// ViewModel des préférences d'emails de notification (`GET/PUT /notifications/preferences`).
///
/// Miroir de `NotificationPrefsViewModel` (Android).
@Observable
@MainActor
public final class NotificationPrefsViewModel {
    public private(set) var prefs = NotificationPreferences()

    private let repository: NotificationRepository

    /// - Parameter repository: accès REST notifications.
    public init(repository: NotificationRepository) {
        self.repository = repository
    }

    /// Charge les préférences courantes.
    public func load() async {
        prefs = (try? await repository.emailPreferences()) ?? NotificationPreferences()
    }

    /// Applique une nouvelle valeur (optimiste) et persiste côté serveur.
    public func update(_ newPrefs: NotificationPreferences) async {
        prefs = newPrefs
        if let updated = try? await repository.updateEmailPreferences(newPrefs) {
            prefs = updated
        }
    }
}

/// Écran « Notifs » du menu profil — préférences d'emails par catégorie (comme le web).
/// La liste in-app reste accessible via la cloche d'accueil.
@MainActor
public struct NotificationPrefsView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: NotificationPrefsViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: NotificationPrefsViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                // Notifications push (opt-out global).
                DMCard {
                    prefRow(s.pushNotifs, viewModel.prefs.pushEnabled) { newValue in
                        var p = viewModel.prefs
                        p.pushEnabled = newValue
                        Task { await viewModel.update(p) }
                    }
                }

                Text(s.emailNotifs).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                Text(s.emailNotifsHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)

                DMCard {
                    VStack(spacing: 0) {
                        prefRow(s.navConcerts, viewModel.prefs.emailConcerts) { newValue in
                            var p = viewModel.prefs
                            p.emailConcerts = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.navDuels, viewModel.prefs.emailDuels) { newValue in
                            var p = viewModel.prefs
                            p.emailDuels = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.navLives, viewModel.prefs.emailLives) { newValue in
                            var p = viewModel.prefs
                            p.emailLives = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.notifGifts, viewModel.prefs.emailGifts) { newValue in
                            var p = viewModel.prefs
                            p.emailGifts = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.notifVotes, viewModel.prefs.emailVotes) { newValue in
                            var p = viewModel.prefs
                            p.emailVotes = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.notifRequests, viewModel.prefs.emailRequests) { newValue in
                            var p = viewModel.prefs
                            p.emailRequests = newValue
                            Task { await viewModel.update(p) }
                        }
                        prefRow(s.notifAssignments, viewModel.prefs.emailAssignments) { newValue in
                            var p = viewModel.prefs
                            p.emailAssignments = newValue
                            Task { await viewModel.update(p) }
                        }
                        // Emails système : requis, non désactivable.
                        prefRow("\(s.notifSystem) · \(s.notifRequired)", true, enabled: false) { _ in }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }

    /// Ligne d'une préférence : libellé + interrupteur.
    private func prefRow(_ label: String, _ checked: Bool, enabled: Bool = true, onChange: @escaping (Bool) -> Void) -> some View {
        HStack {
            Text(label).foregroundStyle(theme.colors.foreground)
            Spacer()
            Toggle("", isOn: Binding(get: { checked }, set: onChange))
                .labelsHidden()
                .disabled(!enabled)
        }
        .padding(.vertical, theme.spacing.xs)
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

import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de l'espace admin.
///
/// Bascule l'ouverture des candidatures (artiste/manager) et assigne/révoque directement un
/// rôle à un utilisateur trouvé par recherche. Miroir de `AdminViewModel` Android.
@Observable
@MainActor
public final class AdminViewModel {

    public private(set) var managerEnabled = true
    public private(set) var artistEnabled = true
    public var query: String = ""
    public private(set) var users: [AdminUser] = []
    public private(set) var isSearching = false
    public private(set) var message: String?

    private let repository: ProfileRepository

    /// - Parameter repository: accès REST (méthodes `admin*`).
    public init(repository: ProfileRepository) {
        self.repository = repository
    }

    /// Charge l'état courant des deux réglages d'ouverture des candidatures.
    public func load() async {
        managerEnabled = await repository.requestsEnabled(RoleEndpoints.managerRequestsEnabled)
        artistEnabled = await repository.requestsEnabled(RoleEndpoints.artistRequestsEnabled)
    }

    /// Ouvre/ferme les candidatures « Devenir artiste ».
    public func toggleArtist(_ enabled: Bool) async {
        await setRequests(key: RoleEndpoints.artistRequestsEnabled, enabled: enabled) { self.artistEnabled = enabled }
    }

    /// Ouvre/ferme les candidatures « Devenir manager ».
    public func toggleManager(_ enabled: Bool) async {
        await setRequests(key: RoleEndpoints.managerRequestsEnabled, enabled: enabled) { self.managerEnabled = enabled }
    }

    /// Recherche des utilisateurs par nom/email.
    public func search() async {
        let q = query.trimmed
        guard !q.isEmpty else { return }
        isSearching = true
        message = nil
        defer { isSearching = false }
        do {
            users = try await repository.adminSearchUsers(query: q)
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.adminSearchFailed
        }
    }

    /// Assigne un rôle à un utilisateur.
    public func assign(userId: String, role: String) async {
        do {
            try await repository.adminAssignRole(userId: userId, role: role)
            message = "\(AppStrings.current.adminRoleAssigned) (\(role))"
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.adminUpdateFailed
        }
    }

    /// Révoque un rôle d'un utilisateur.
    public func revoke(userId: String, role: String) async {
        do {
            try await repository.adminRevokeRole(userId: userId, role: role)
            message = "\(AppStrings.current.adminRoleRevoked) (\(role))"
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.adminUpdateFailed
        }
    }

    private func setRequests(key: String, enabled: Bool, onSuccess: @escaping () -> Void) async {
        do {
            try await repository.adminSetRequestsEnabled(key: key, enabled: enabled)
            onSuccess()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.adminUpdateFailed
        }
    }
}

/// Espace admin : ouverture des candidatures + assignation directe de rôle.
@MainActor
public struct AdminView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: AdminViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: AdminViewModel) {
        self._viewModel = Bindable(viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.md) {
                Text(s.adminSpace)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(theme.colors.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if let message = viewModel.message { DMMessage(message) }

                // Ouverture des candidatures.
                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        DMSectionTitle(s.adminRoleRequests)
                        Text(s.adminRoleRequestsHint)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)

                        DMToggleRow(s.adminArtistRequests, isOn: Binding(
                            get: { viewModel.artistEnabled },
                            set: { enabled in Task { await viewModel.toggleArtist(enabled) } }
                        ))
                        DMToggleRow(s.adminManagerRequests, isOn: Binding(
                            get: { viewModel.managerEnabled },
                            set: { enabled in Task { await viewModel.toggleManager(enabled) } }
                        ))
                    }
                }

                // Assignation directe de rôle.
                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        DMSectionTitle(s.adminAssignRole)
                        DMTextField(
                            s.adminSearchPlaceholder,
                            text: $viewModel.query,
                            autocapitalization: .never,
                            onSubmit: { Task { await viewModel.search() } }
                        )
                        DMButton(
                            viewModel.isSearching ? s.adminSearching : s.adminSearch,
                            style: .secondary,
                            isLoading: viewModel.isSearching
                        ) {
                            Task { await viewModel.search() }
                        }
                    }
                }

                ForEach(viewModel.users) { user in
                    AdminUserRow(
                        user: user,
                        onAssign: { role in Task { await viewModel.assign(userId: user.id, role: role) } },
                        onRevoke: { role in Task { await viewModel.revoke(userId: user.id, role: role) } }
                    )
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }
}

/// Ligne d'un utilisateur trouvé : assigner ou révoquer artiste/manager.
private struct AdminUserRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let user: AdminUser
    let onAssign: (String) -> Void
    let onRevoke: (String) -> Void

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(user.label)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                if let email = user.email {
                    Text(email).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }
                HStack(spacing: theme.spacing.sm) {
                    DMButton(s.adminAddArtist) { onAssign("artist") }
                    DMButton(s.adminAddManager, style: .secondary) { onAssign("manager") }
                }
                HStack(spacing: theme.spacing.sm) {
                    DMButton(s.adminRemoveArtist, style: .outline) { onRevoke("artist") }
                    DMButton(s.adminRemoveManager, style: .outline) { onRevoke("manager") }
                }
            }
        }
    }
}

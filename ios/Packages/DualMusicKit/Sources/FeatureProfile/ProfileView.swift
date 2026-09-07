import SwiftUI
import Observation
import CoreUI
import DomainModels

/// ViewModel du profil : identité + statistiques.
@Observable
@MainActor
public final class ProfileViewModel {

    public private(set) var me: MeResponse?
    public private(set) var stats = UserStats()
    public private(set) var isLoading = false

    private let repository: ProfileRepository

    /// - Parameter repository: lectures REST du profil.
    public init(repository: ProfileRepository) {
        self.repository = repository
    }

    /// Rôles du caller (vide tant que le profil n'est pas chargé).
    public var roles: [UserRole] { me?.roles ?? [] }

    /// Vrai si l'utilisateur peut créer du contenu (artiste, manager ou admin).
    public var canCreate: Bool { roles.contains(where: { $0 == .artist || $0 == .manager || $0 == .admin }) }

    /// Vrai si l'utilisateur est administrateur.
    public var isAdmin: Bool { roles.contains(.admin) }

    /// Vrai si l'utilisateur n'est **que** fan (aucun rôle créateur).
    public var isPureFan: Bool { !canCreate }

    /// Charge le profil + les statistiques.
    ///
    /// Un échec des stats n'empêche pas l'affichage de l'identité (repli sur des stats
    /// vides), comme sur Android.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        me = try? await repository.me()
        stats = (try? await repository.stats()) ?? UserStats()
        isLoading = false
    }
}

/// Écran profil : identité (photo, nom, email, rôle) + statistiques filtrées par rôle,
/// avec un bouton menu qui ouvre « Mon espace ».
///
/// Comme sur le web, chaque profil ne voit **que** ses propres récapitulatifs : un fan ne
/// voit pas les stats artiste/manager, et inversement. Miroir de `ProfileScreen` Android.
@MainActor
public struct ProfileView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ProfileViewModel
    private let onOpenMenu: () -> Void

    /// - Parameters:
    ///   - viewModel: source d'état.
    ///   - onOpenMenu: ouvre la page de menu « Mon espace ».
    public init(viewModel: ProfileViewModel, onOpenMenu: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onOpenMenu = onOpenMenu
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                HStack {
                    Text(s.menuMySpace)
                        .font(DMFont.pageTitle)
                        .foregroundStyle(theme.colors.foreground)
                    Spacer()
                    Button(action: onOpenMenu) {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(theme.colors.foreground)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Menu"))
                }

                hero

                if viewModel.roles.contains(.artist) {
                    StatSection(title: s.spaceArtist, rows: [
                        (s.statVotesReceived, String(Int(viewModel.stats.artistStats.totalVotes))),
                        (s.statDuelsWon, "\(viewModel.stats.artistStats.wonDuels)/\(viewModel.stats.artistStats.totalDuels)"),
                        (s.statGiftsReceived, String(viewModel.stats.artistStats.totalGifts)),
                    ])
                }
                if viewModel.roles.contains(.manager) {
                    StatSection(title: s.spaceManager, rows: [
                        (s.statDuelsManaged, String(viewModel.stats.managerStats.totalDuelsManaged)),
                        (s.statActive, String(viewModel.stats.managerStats.activeDuels)),
                        (s.statGiftsReceived, String(viewModel.stats.managerStats.totalGiftsReceived)),
                    ])
                }
                if viewModel.isPureFan {
                    StatSection(title: s.spaceFan, rows: [
                        (s.statVotesCast, String(Int(viewModel.stats.fanStats.totalVotesCast))),
                        (s.statGiftsSent, String(viewModel.stats.fanStats.totalGiftsSent)),
                        (s.statTicketsBought, String(viewModel.stats.fanStats.totalTickets)),
                    ])
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    /// Bandeau d'identité : avatar (photo ou initiale), nom, email, rôle principal.
    private var hero: some View {
        DMCard {
            HStack(spacing: theme.spacing.md) {
                ZStack {
                    Circle().fill(theme.gradients.primary).frame(width: 64, height: 64)
                    if let url = viewModel.me?.profile?.avatarURL, !url.isEmpty {
                        DMRemoteImage(url: url, fallback: "👤")
                            .frame(width: 64, height: 64)
                            .clipShape(Circle())
                    } else {
                        Text(displayName.take(1).uppercased())
                            .font(DMFont.headline)
                            .foregroundStyle(theme.colors.primaryForeground)
                    }
                }
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    Text(displayName)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let email = viewModel.me?.user.email {
                        Text(email).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                    Text(primaryRoleLabel)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                }
                Spacer()
            }
        }
    }

    /// Nom affiché : nom du profil s'il existe, sinon la partie locale de l'email (jamais
    /// l'email complet — il est déjà affiché en dessous).
    private var displayName: String {
        if let name = viewModel.me?.profile?.displayName, !name.isEmpty, name != "Utilisateur" {
            return name
        }
        if let email = viewModel.me?.user.email, let local = email.split(separator: "@").first {
            return String(local).capitalizedFirst
        }
        return "Utilisateur"
    }

    /// Libellé du rôle principal (priorité admin > artiste > manager > modérateur > fan).
    private var primaryRoleLabel: String {
        let roles = viewModel.roles
        if roles.contains(.admin) { return s.roleAdmin }
        if roles.contains(.artist) { return s.roleArtist }
        if roles.contains(.manager) { return s.roleManager }
        if roles.contains(.moderator) { return s.roleModerator }
        return s.fan
    }
}

/// Bloc de statistiques (titre + lignes clé/valeur).
private struct StatSection: View {
    @Environment(\.dmTheme) private var theme
    let title: String
    let rows: [(String, String)]

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text(title)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.primaryGlow)
                ForEach(rows, id: \.0) { label, value in
                    HStack {
                        Text(label).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                        Spacer()
                        Text(value).font(DMFont.mono).foregroundStyle(theme.colors.foreground)
                    }
                }
            }
        }
    }
}

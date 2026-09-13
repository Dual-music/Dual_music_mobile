import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de l'espace « Mes Duels » du MANAGER (organisateur).
///
/// Le manager crée un duel entre DEUX artistes (il s'assigne arbitre via `managerId`) et voit
/// la liste des duels qu'il gère. Les contrôles en direct (minuteur/vainqueur/fin) se font
/// dans la room du duel (``DuelRoomView``).
@Observable
@MainActor
public final class ManagerDuelsViewModel {

    public private(set) var duels: [Duel] = []
    public private(set) var artists: [ArtistSummary] = []
    public private(set) var myId: String?
    public private(set) var creating = false
    /// Les managers ont-ils le droit de CRÉER des duels (réglage admin) ? Si faux, seule la
    /// liste des duels déjà assignés par l'admin est visible.
    public private(set) var canCreate = false
    public private(set) var message: String?

    private let repository: DuelRepository

    /// - Parameter repository: lectures/écritures REST des duels.
    public init(repository: DuelRepository) {
        self.repository = repository
    }

    /// Charge l'id du manager, ses duels gérés, l'annuaire des artistes, et le réglage de
    /// création.
    public func load() async {
        let myId = await repository.myUserId()
        self.myId = myId
        if let myId {
            duels = (try? await repository.managedDuels(managerId: myId)) ?? []
        }
        artists = (try? await repository.artists()) ?? []
        canCreate = await repository.managerDuelCreationEnabled()
    }

    /// Crée un duel entre 2 artistes (le manager s'assigne arbitre), puis recharge la liste.
    /// - Parameters:
    ///   - artist1Id: id UTILISATEUR de l'artiste 1 (``ArtistSummary/opponentUserId``).
    ///   - artist2Id: id UTILISATEUR de l'artiste 2.
    ///   - scheduled: date proposée (ISO), ou vide pour aucune date.
    public func createDuel(artist1Id: String, artist2Id: String, scheduled: String) async {
        guard let myId else { return }
        creating = true
        message = nil
        do {
            try await repository.createDuel(
                artist1Id: artist1Id,
                artist2Id: artist2Id,
                scheduledTime: scheduled.isEmpty ? nil : scheduled,
                managerId: myId
            )
            message = AppStrings.current.duelCreated
            await load()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
        creating = false
    }

    public func clearMessage() { message = nil }
}

/// Écran « Mes Duels » du manager : formulaire de création (2 artistes + date) + liste des
/// duels gérés (avec accès direct si en direct). Miroir de `ManagerDuelsScreen` Android.
@MainActor
public struct ManagerDuelsView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ManagerDuelsViewModel
    private let onOpenDuel: (Duel) -> Void

    @State private var query = ""
    /// 0 = aucun, 1 = artiste 1, 2 = artiste 2.
    @State private var picking = 0
    @State private var artist1: ArtistSummary?
    @State private var artist2: ArtistSummary?
    @State private var scheduled = ""

    /// - Parameters:
    ///   - viewModel: état + actions de l'espace manager.
    ///   - onOpenDuel: ouvre la room d'un duel en direct.
    public init(viewModel: ManagerDuelsViewModel, onOpenDuel: @escaping (Duel) -> Void = { _ in }) {
        self.viewModel = viewModel
        self.onOpenDuel = onOpenDuel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                if viewModel.canCreate {
                    createCard
                }
                Text(s.managedDuels).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                if viewModel.duels.isEmpty {
                    DMEmptyState(title: s.noDuels, subtitle: s.noDuelsHint, systemImage: "calendar")
                } else {
                    ForEach(viewModel.duels) { duel in
                        managedDuelRow(duel)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    /// Formulaire de création : deux emplacements d'artistes + date proposée (optionnelle).
    private var createCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.createDuelTitle).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                Text(s.createDuelHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)

                HStack(spacing: theme.spacing.sm) {
                    artistSlot(label: "🅰 \(s.artist1)", artist: artist1, selected: picking == 1) {
                        picking = picking == 1 ? 0 : 1
                    }
                    artistSlot(label: "🅱 \(s.artist2)", artist: artist2, selected: picking == 2) {
                        picking = picking == 2 ? 0 : 2
                    }
                }

                if picking != 0 {
                    TextField(s.searchArtist, text: $query)
                        .textFieldStyle(.roundedBorder)
                    ForEach(candidates) { a in
                        Button {
                            if picking == 1 { artist1 = a } else { artist2 = a }
                            picking = 0
                            query = ""
                        } label: {
                            Text("🎤  \(a.displayName)")
                                .foregroundStyle(theme.colors.foreground)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                        .padding(.vertical, theme.spacing.xs)
                    }
                }

                DMTextField(s.proposedDateOptional, text: $scheduled)

                if let message = viewModel.message {
                    Text(message).font(DMFont.caption).foregroundStyle(theme.colors.accent)
                }

                DMButton(viewModel.creating ? s.sending : s.createDuelAction, isLoading: viewModel.creating) {
                    guard let a1 = artist1?.opponentUserId, let a2 = artist2?.opponentUserId, a1 != a2 else { return }
                    Task {
                        await viewModel.createDuel(artist1Id: a1, artist2Id: a2, scheduled: scheduled)
                        artist1 = nil
                        artist2 = nil
                        scheduled = ""
                    }
                }
            }
        }
    }

    /// Candidats pour l'emplacement en cours de sélection : hors l'autre artiste déjà choisi,
    /// filtrés par la recherche.
    private var candidates: [ArtistSummary] {
        let other = picking == 1 ? artist2 : artist1
        return viewModel.artists
            .filter { $0.opponentUserId != other?.opponentUserId }
            .filter { query.isEmpty || $0.displayName.localizedCaseInsensitiveContains(query) }
            .prefix(8)
            .map { $0 }
    }

    private func artistSlot(label: String, artist: ArtistSummary?, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(DMFont.caption).bold().foregroundStyle(theme.colors.mutedForeground)
                Text(artist?.displayName ?? "—").foregroundStyle(theme.colors.foreground)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(theme.spacing.md)
            .background(
                selected ? theme.colors.accent.opacity(0.25) : theme.colors.secondary,
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
        }
        .buttonStyle(.plain)
    }

    private func managedDuelRow(_ duel: Duel) -> some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(duel.artist1?.displayName ?? s.artist1)  vs  \(duel.artist2?.displayName ?? s.artist2)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    HStack(spacing: theme.spacing.sm) {
                        if let time = isoMinute(duel.scheduledTime) {
                            Text(time).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                        }
                        Text(statusLabel(duel)).font(DMFont.caption).foregroundStyle(theme.colors.accent)
                    }
                }
                Spacer()
                if duel.status == .live {
                    Button(s.joinAction) { onOpenDuel(duel) }
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.primary)
                }
            }
        }
    }

    private func statusLabel(_ duel: Duel) -> String {
        switch duel.status {
        case .live: return s.statusLiveNow
        case .upcoming: return s.statusUpcoming
        case .ended: return s.statusEnded
        case .cancelled: return s.statusCancelled
        default: return duel.status.rawValue.capitalizedFirst
        }
    }
}

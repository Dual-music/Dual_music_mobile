import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de « Mes lives » (hôte) : liste les lives de l'artiste, en lance un nouveau,
/// termine le live actif. Miroir de `MyLivesViewModel` Android.
@Observable
@MainActor
public final class MyLivesViewModel {

    public private(set) var lives: [Live] = []
    public private(set) var isLoading = false
    public private(set) var errorMessage: String?

    /// Live tout juste créé, à ouvrir en diffusion (consommé par l'écran).
    public private(set) var pendingBroadcast: Live?

    private let artistId: String
    private let repository: LiveRepository

    /// - Parameters:
    ///   - artistId: identifiant de l'artiste connecté (`GET /lives?artistId=`).
    ///   - repository: lectures/actions REST des lives.
    public init(artistId: String, repository: LiveRepository) {
        self.artistId = artistId
        self.repository = repository
    }

    /// Live actuellement en direct, s'il y en a un (un seul possible à la fois).
    public var active: Live? { lives.first { $0.status == .live } }
    /// Lives terminés (historique), les plus récents en tête (ordre serveur).
    public var past: [Live] { lives.filter { $0.status == .ended } }

    /// Charge la liste des lives de l'artiste.
    public func load() async {
        isLoading = true
        lives = (try? await repository.myLives(artistId: artistId)) ?? []
        isLoading = false
    }

    /// Lance un live, puis recharge la liste.
    public func createLive(
        title: String,
        allowsDedications: Bool,
        allowGuests: Bool,
        dedicationMinPriceCredits: Double?
    ) async {
        errorMessage = nil
        do {
            let created = try await repository.createLive(
                title: title,
                allowsDedications: allowsDedications,
                allowGuests: allowGuests,
                dedicationMinPriceCredits: dedicationMinPriceCredits
            )
            pendingBroadcast = created
            await load()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.errOperationFailed
        }
    }

    /// Réinitialise le live en attente une fois la diffusion ouverte.
    public func consumePending() { pendingBroadcast = nil }

    /// Termine le live actif, puis recharge.
    public func endLive(id: String) async {
        try? await repository.endLive(liveId: id)
        await load()
    }
}

/// Écran « Mes Lives » — lancer un live, voir/terminer le live actif, historique des lives
/// terminés. Miroir de `MyLivesScreen` Android.
@MainActor
public struct MyLivesView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: MyLivesViewModel
    private let onStartBroadcast: (Live) -> Void

    @State private var title = ""
    @State private var allowsDedications = true
    @State private var allowGuests = true
    @State private var minPriceText = ""

    /// - Parameters:
    ///   - viewModel: état + actions.
    ///   - onStartBroadcast: appelé pour ouvrir la diffusion plein écran d'un live (nouveau
    ///     ou déjà actif).
    public init(viewModel: MyLivesViewModel, onStartBroadcast: @escaping (Live) -> Void) {
        self.viewModel = viewModel
        self.onStartBroadcast = onStartBroadcast
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

                createCard

                if let active = viewModel.active {
                    activeLiveCard(active)
                }

                if viewModel.isLoading {
                    DMLoadingBox()
                } else if viewModel.past.isEmpty && viewModel.active == nil {
                    DMEmptyState(title: s.noLives, subtitle: s.noLivesHint, systemImage: "video.fill")
                } else if !viewModel.past.isEmpty {
                    DMSectionTitle(s.history)
                    ForEach(viewModel.past) { live in PastLiveRow(live: live) }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        // Ouvre la diffusion plein écran dès qu'un live vient d'être créé.
        .onChange(of: viewModel.pendingBroadcast) { _, live in
            if let live {
                onStartBroadcast(live)
                viewModel.consumePending()
            }
        }
    }

    /// Formulaire de lancement d'un live.
    private var createCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.myLives)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                Text(s.myLivesHint)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                DMTextField(s.liveTitle, text: $title)
                DMToggleRow(s.dedicationsLabel, isOn: $allowsDedications)
                DMToggleRow(s.guestsLabel, isOn: $allowGuests)
                if allowsDedications {
                    DMTextField(s.minDedicationPrice, text: $minPriceText, keyboard: .numberPad)
                }
                DMButton(s.startLive, isEnabled: viewModel.active == nil && !viewModel.isLoading) {
                    Task {
                        await viewModel.createLive(
                            title: title,
                            allowsDedications: allowsDedications,
                            allowGuests: allowGuests,
                            dedicationMinPriceCredits: Double(minPriceText)
                        )
                        title = ""
                        minPriceText = ""
                    }
                }
            }
        }
    }

    /// Carte du live actif : reprendre la diffusion, ou la terminer.
    private func activeLiveCard(_ live: Live) -> some View {
        DMCard {
            HStack {
                Button { onStartBroadcast(live) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("🔴 \(live.title ?? s.liveActive)")
                            .font(DMFont.body).bold()
                            .foregroundStyle(theme.colors.primary)
                        Text("\(live.viewerCount) 👁")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                Button {
                    Task { await viewModel.endLive(id: live.id) }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(theme.colors.destructive, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.endLive))
            }
        }
    }
}

/// Ligne d'un live terminé : titre + nombre de spectateurs.
private struct PastLiveRow: View {
    @Environment(\.dmTheme) private var theme
    let live: Live

    var body: some View {
        DMCard {
            HStack {
                Text(live.title ?? "Live")
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                Text("\(live.viewerCount) 👁")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
        }
    }
}

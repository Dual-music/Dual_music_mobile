import SwiftUI
import LiveKit
import CoreUI
import DomainModels

/// Écran d'une room de duel (viewer).
///
/// Vidéo plein écran + overlays : barre de votes (part de chaque artiste, en direct),
/// minuteur, panneau de vote payant, cadeaux animés et chat.
/// Miroir de `DuelRoomScreen` Android.
@MainActor
public struct DuelRoomView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: DuelViewModel
    private let voteAmount: Double

    @State private var showReport = false

    /// - Parameters:
    ///   - viewModel: état + actions du duel.
    ///   - voteAmount: montant (crédits) d'un vote rapide.
    public init(viewModel: DuelViewModel, voteAmount: Double = 10) {
        self.viewModel = viewModel
        self.voteAmount = voteAmount
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // --- Couche vidéo (décodage matériel) ---
            if let track = viewModel.media.primaryVideoTrack {
                SwiftUIVideoView(track, layoutMode: .fill).ignoresSafeArea()
            } else {
                theme.gradients.hero.ignoresSafeArea()
            }

            LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .center, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            // --- Cadeau animé ---
            if let gift = viewModel.giftFeed.last {
                GiftBurstView(symbol: "🎁", label: gift.name) { viewModel.consumeOldestGift() }
                    .id(gift.id)
            }

            VStack {
                header
                Spacer()
                votePanel
            }
            .padding(theme.spacing.lg)
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
    }

    /// Haut : bouton signaler + barre de répartition des votes + minuteur.
    private var header: some View {
        VStack(spacing: theme.spacing.sm) {
            HStack {
                Button { showReport = true } label: {
                    Image(systemName: "flag.fill")
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                        .padding(theme.spacing.xs)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))
                Spacer()
            }
            VoteBar(
                leftName: viewModel.duel?.artist1?.displayName ?? s.artist1,
                rightName: viewModel.duel?.artist2?.displayName ?? s.artist2,
                leftTotal: viewModel.duel.flatMap { viewModel.voteTotals[$0.artist1Id] } ?? 0,
                rightTotal: viewModel.duel.flatMap { viewModel.voteTotals[$0.artist2Id] } ?? 0
            )
            if viewModel.timer.isRunning {
                Text(s.timerRunning)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /// Libellé localisé d'un motif de signalement.
    private func reportLabel(_ reason: ReportReason) -> String {
        switch reason {
        case .inappropriate: return s.reportInappropriate
        case .harassment: return s.reportHarassment
        case .spam: return s.reportSpam
        case .violence: return s.reportViolence
        }
    }

    /// Bas : panneau de vote payant.
    private var votePanel: some View {
        VStack(spacing: theme.spacing.sm) {
            if let error = viewModel.errorMessage {
                DMMessage(error, kind: .error)
            }
            HStack(spacing: theme.spacing.sm) {
                if let duel = viewModel.duel {
                    DMButton("\(s.vote) \(duel.artist1?.displayName ?? s.artist1)") {
                        Task { await viewModel.vote(artistId: duel.artist1Id, amount: voteAmount) }
                    }
                    DMButton("\(s.vote) \(duel.artist2?.displayName ?? s.artist2)", style: .secondary) {
                        Task { await viewModel.vote(artistId: duel.artist2Id, amount: voteAmount) }
                    }
                }
            }
            Text("\(s.oneVote) = \(formatAmount(voteAmount)) \(s.credits)")
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.mutedForeground)
        }
    }
}

/// Barre de répartition des votes entre les deux artistes.
///
/// La largeur de chaque segment reflète la part de crédits reçue (mise à jour en direct par
/// l'événement `vote`).
private struct VoteBar: View {
    @Environment(\.dmTheme) private var theme

    let leftName: String
    let rightName: String
    let leftTotal: Double
    let rightTotal: Double

    /// Part du segment gauche, bornée pour rester visible même à 0 %.
    private var leftShare: Double {
        let sum = leftTotal + rightTotal
        guard sum > 0 else { return 0.5 }
        return min(0.98, max(0.02, leftTotal / sum))
    }

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text("\(leftName) · \(Int(leftTotal))")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
                Spacer()
                Text("\(Int(rightTotal)) · \(rightName)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
            }
            GeometryReader { geo in
                HStack(spacing: 0) {
                    Rectangle()
                        .fill(theme.gradients.primary)
                        .frame(width: geo.size.width * leftShare)
                    Rectangle()
                        .fill(theme.colors.electricBlue)
                }
                .clipShape(RoundedRectangle(cornerRadius: theme.radius.sm, style: .continuous))
            }
            .frame(height: 10)
            .animation(.easeOut(duration: 0.3), value: leftShare)
        }
    }
}

/// Liste des duels — point d'entrée vers une room de duel.
@MainActor
public struct DuelsListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: DuelsListViewModel
    private let onOpen: (Duel) -> Void

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'un duel.
    public init(viewModel: DuelsListViewModel, onOpen: @escaping (Duel) -> Void) {
        self.viewModel = viewModel
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text(s.screenDuels)
                .font(DMFont.pageTitle)
                .foregroundStyle(theme.colors.foreground)
                .frame(maxWidth: .infinity)

            if viewModel.isLoading {
                DMLoadingBox()
            } else if viewModel.duels.isEmpty {
                DMEmptyState(title: s.noDuels, subtitle: s.noDuelsHint, systemImage: "calendar")
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: theme.spacing.sm) {
                        ForEach(viewModel.duels) { duel in
                            Button { onOpen(duel) } label: { DuelRow(duel: duel) }
                                .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }
}

/// Carte d'un duel : les deux artistes + son statut.
private struct DuelRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let duel: Duel

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(duel.artist1?.displayName ?? s.artist1)  vs  \(duel.artist2?.displayName ?? s.artist2)")
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let time = isoMinute(duel.scheduledTime) {
                        Text(time).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                Text(statusLabel)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(duel.status == .live ? theme.colors.accent : theme.colors.mutedForeground)
            }
        }
    }

    /// Libellé lisible du statut (mêmes textes qu'Android).
    private var statusLabel: String {
        switch duel.status {
        case .live: return s.statusLiveNow
        case .upcoming: return s.statusUpcoming
        case .ended: return s.statusEnded
        case .cancelled: return s.statusCancelled
        default: return duel.status.rawValue.capitalizedFirst
        }
    }
}

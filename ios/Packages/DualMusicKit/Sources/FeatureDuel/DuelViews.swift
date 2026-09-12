import SwiftUI
import LiveKit
import CoreLiveMedia
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

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: DuelChatMessage?
    @State private var showModeratorsSheet = false
    @State private var showFilterSheet = false

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

            // --- Couche vidéo : deux tuiles côte à côte (artiste 1 | artiste 2). Le manager
            // peut aussi diffuser (présentation/animation, room dédiée) mais n'a pas de tuile
            // dans ce premier jet — écart assumé, cf. `TODO-IOS.md`.
            HStack(spacing: 1) {
                artistTile(viewModel.mediaA1)
                artistTile(viewModel.mediaA2)
            }
            .ignoresSafeArea()

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
                chatOverlay
                votePanel
                if viewModel.canPublish { hostControls }
                messageBar
            }
            .padding(theme.spacing.lg)
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        // Manager ET modérateurs (pour voir qui d'autre a ce pouvoir) : liste + désignation.
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        .sheet(isPresented: $showFilterSheet) { filterSheet }
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
        // Confirmation de bannissement (manager uniquement — tap sur un auteur de message).
        .alert(
            "🚫 \(s.banAction) \(banTarget?.authorName ?? "") ?",
            isPresented: Binding(get: { banTarget != nil }, set: { if !$0 { banTarget = nil } }),
            presenting: banTarget
        ) { target in
            Button(s.cancel, role: .cancel) {}
            Button(s.banAction, role: .destructive) {
                Task { await viewModel.banUser(userId: target.userId, reason: String(target.content.prefix(200))) }
            }
        } message: { _ in
            Text(s.banConfirmMessage)
        }
    }

    /// Les 6 derniers messages visibles. Le manager peut bannir l'auteur d'un message en
    /// tapant sur son nom — jamais un des deux artistes en duel, ni lui-même.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(viewModel.visibleMessages.suffix(6)) { message in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(message.authorName)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                        .onTapGesture {
                            guard viewModel.canModerate, !isParticipant(message.userId) else { return }
                            banTarget = message
                        }
                    Text(message.content)
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Vrai pour les deux artistes du duel ou son manager — jamais bannissables.
    private func isParticipant(_ userId: String) -> Bool {
        guard let duel = viewModel.duel else { return false }
        return userId == duel.artist1Id || userId == duel.artist2Id || userId == duel.managerId
    }

    /// Saisie de message, sous le panneau de vote.
    private var messageBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(s.saySomething, text: $draft)
                .textFieldStyle(.plain)
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(.white.opacity(0.15), in: Capsule())
                .submitLabel(.send)
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(theme.colors.accent, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(draft.trimmed.isEmpty)
        }
        .padding(.top, theme.spacing.sm)
    }

    /// Envoie le brouillon puis vide le champ.
    private func send() {
        let text = draft
        draft = ""
        Task { await viewModel.sendMessage(text) }
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
                // Visible du manager ET des modérateurs eux-mêmes (pour qu'ils voient qui
                // d'autre a ce pouvoir) — pas seulement le manager.
                if viewModel.canModerate {
                    Button {
                        showModeratorsSheet = true
                        if viewModel.isManager { Task { await viewModel.loadViewers() } }
                    } label: {
                        Image(systemName: "person.2.fill")
                            .font(DMFont.caption)
                            .foregroundStyle(.white)
                            .padding(theme.spacing.xs)
                            .background(.black.opacity(0.4), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(s.moderators))
                }
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

    /// Feuille : modérateurs désignés (révocables par le manager) + désignation d'un
    /// spectateur connecté (manager uniquement). Visible aussi des modérateurs eux-mêmes, en
    /// lecture seule pour la partie désignation.
    private var moderatorsSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text(s.moderators).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            Text(s.moderatorsHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)

            if viewModel.moderators.isEmpty {
                Text(s.noModeratorsYet).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ForEach(viewModel.moderators) { moderator in
                    moderatorRow(moderator)
                }
            }

            if viewModel.isManager {
                Divider()
                Text(s.designateViewer).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                designateViewerSection
            }

            Spacer()
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Ligne d'un modérateur désigné — révocable par le manager seulement.
    private func moderatorRow(_ moderator: EventModerator) -> some View {
        HStack {
            Text(moderator.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
            Spacer()
            if viewModel.isManager {
                Button { Task { await viewModel.revokeModerator(userId: moderator.userId) } } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(theme.colors.destructive)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.revokeAction))
            }
        }
    }

    /// Picker de désignation (manager uniquement) : spectateurs connectés, hors modérateurs
    /// déjà désignés, désactivé à la limite (``maxEventModerators``).
    private var designateViewerSection: some View {
        let appointedIds = Set(viewModel.moderators.map(\.userId))
        let pickable = viewModel.viewers.filter { !appointedIds.contains($0.id) }
        return Group {
            if viewModel.moderators.count >= maxEventModerators {
                Text(s.atModeratorLimit).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else if pickable.isEmpty {
                Text(s.noViewersConnected).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        ForEach(pickable) { viewer in
                            HStack {
                                Text(viewer.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
                                Spacer()
                                Button(s.appointAction) { Task { await viewModel.appointModerator(userId: viewer.id) } }
                                    .font(DMFont.caption).bold()
                                    .buttonStyle(.plain)
                                    .foregroundStyle(theme.colors.primary)
                            }
                        }
                    }
                }
                .frame(maxHeight: 180)
            }
        }
    }

    /// Une tuile de slot : mon propre aperçu si je publie là, le flux distant sinon, un repli
    /// dégradé si personne ne diffuse encore.
    private func artistTile(_ client: LiveRoomClient) -> some View {
        Group {
            if let track = client.localVideoTrack ?? client.primaryVideoTrack {
                SwiftUIVideoView(track, layoutMode: .fill)
            } else {
                theme.gradients.hero
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
    }

    /// Contrôles du participant qui publie (artiste 1/2 ou manager) : démarrer la diffusion,
    /// puis mic/caméra/bascule/filtre une fois lancée.
    private var hostControls: some View {
        Group {
            if let m = viewModel.myMedia, !m.isCameraEnabled, !m.isMicrophoneEnabled {
                Button {
                    Task { await viewModel.startBroadcast() }
                } label: {
                    Text(s.startLive)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(.white)
                        .padding(.horizontal, theme.spacing.md)
                        .padding(.vertical, theme.spacing.sm)
                        .background(theme.colors.accent, in: Capsule())
                }
                .buttonStyle(.plain)
            } else {
                HStack(spacing: theme.spacing.md) {
                    controlButton(viewModel.myMedia?.isMicrophoneEnabled == true ? "mic.fill" : "mic.slash.fill") {
                        Task { await viewModel.toggleMic() }
                    }
                    controlButton(viewModel.myMedia?.isCameraEnabled == true ? "video.fill" : "video.slash.fill") {
                        Task { await viewModel.toggleCamera() }
                    }
                    controlButton("arrow.triangle.2.circlepath.camera.fill") {
                        Task { await viewModel.switchCamera() }
                    }
                    controlButton("camera.filters") {
                        showFilterSheet = true
                    }
                }
            }
        }
    }

    private func controlButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .foregroundStyle(.white)
                .padding(theme.spacing.sm)
                .background(.black.opacity(0.4), in: Circle())
        }
        .buttonStyle(.plain)
    }

    /// Feuille : grille des filtres couleur (voir ``VideoFilterPresets/all``).
    private var filterSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.colorFilters).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 84))], spacing: theme.spacing.md) {
                    ForEach(VideoFilterPresets.all) { preset in
                        Button {
                            viewModel.setColorFilter(id: preset.id, matrix: preset.matrix)
                        } label: {
                            VStack(spacing: theme.spacing.xs) {
                                Text(preset.emoji).font(.system(size: 28))
                                Text(filterLabel(preset.id))
                                    .font(DMFont.caption)
                                    .foregroundStyle(theme.colors.foreground)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(theme.spacing.sm)
                            .background(
                                viewModel.myMedia?.activeFilterId == preset.id ? theme.colors.accent.opacity(0.25) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Libellé localisé d'un filtre couleur.
    private func filterLabel(_ id: String) -> String {
        switch id {
        case "beauty": return s.filterBeauty
        case "smooth": return s.filterSmooth
        case "glow": return s.filterGlow
        case "warm": return s.filterWarm
        case "cool": return s.filterCool
        case "vivid": return s.filterVivid
        case "vintage": return s.filterVintage
        case "noir": return s.filterNoir
        case "studio": return s.filterStudio
        case "neon": return s.filterNeon
        case "dream": return s.filterDream
        default: return s.filterNone
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

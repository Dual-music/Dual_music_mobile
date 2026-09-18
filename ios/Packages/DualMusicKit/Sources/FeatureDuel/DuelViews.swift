import SwiftUI
import LiveKit
import CoreLiveMedia
import CoreUI
import DomainModels
import FeatureSponsor

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
    private let onLeave: () -> Void
    private let onOpenArtist: (String) -> Void

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: DuelChatMessage?
    @State private var showModeratorsSheet = false
    @State private var showFilterSheet = false
    @State private var showManagerSheet = false
    @State private var showGiftSheet = false
    @State private var showLeaderboardSheet = false
    @State private var showReactionBar = false
    @State private var showCancelRecordingConfirm = false
    @State private var recordingErrorMessage: String?
    @State private var showSponsorAdPicker = false

    /// - Parameters:
    ///   - viewModel: état + actions du duel.
    ///   - voteAmount: montant (crédits) d'un vote rapide.
    ///   - onLeave: retour au catalogue (gate d'accès, barrière de bannissement, fin de duel).
    ///   - onOpenArtist: ouvre le profil public d'un artiste (tap sur son nom dans la barre de vote).
    public init(
        viewModel: DuelViewModel,
        voteAmount: Double = 10,
        onLeave: @escaping () -> Void = {},
        onOpenArtist: @escaping (String) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.voteAmount = voteAmount
        self.onLeave = onLeave
        self.onOpenArtist = onOpenArtist
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

            // --- Cadeau reçu : carte glissante bas-gauche (Android a retiré le burst centré,
            // même traitement que Live/Concert) ---
            if let gift = viewModel.giftFeed.last {
                VStack {
                    Spacer()
                    HStack {
                        GiftReceivedCard(imageURL: gift.image, name: gift.name ?? s.sendGift, valueCredits: gift.value)
                        Spacer()
                    }
                    .padding(.leading, 10)
                    .padding(.bottom, 300)
                }
                .id(gift.id)
                .allowsHitTesting(false)
                .task(id: gift.id) {
                    try? await Task.sleep(nanoseconds: 3_200_000_000)
                    viewModel.consumeOldestGift()
                }
            }
            // --- Réactions emoji flottantes : essaim bas-droite (miroir Android) ---
            FloatingReactionsLayer(
                reactions: viewModel.emojiFeed.map { FloatingReactionItem(id: "\($0.id)", emoji: $0.emoji) },
                onFinished: { _ in viewModel.consumeOldestEmoji() }
            )

            VStack {
                header
                Spacer()
                chatOverlay
                if showReactionBar { reactionBar }
                votePanel
                if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }
                if viewModel.canPublish { hostControls }
                bottomBar
            }
            .padding(theme.spacing.lg)

            // Célébration du vainqueur : plein écran, persistante pour tous jusqu'à ce que le
            // manager l'arrête (ne termine pas le direct).
            if let winner = viewModel.winnerInfo {
                winnerCelebration(winner)
            }

            // Barrière de bannissement (parité web `BannedAccessGate`) : bloque tout dès que
            // JE suis banni par le manager.
            if viewModel.iAmBanned {
                bannedGate
            }

            // Pub sponsor : overlay vidéo plein écran pour tous quand une pub est active — le
            // déclencheur est l'icône du rail (voir `hostControls`), pas le bouton intégré.
            SponsorAdLayer(
                activeAd: viewModel.sponsorAd.activeAd,
                canTrigger: viewModel.isManager,
                ads: viewModel.sponsorAd.ads,
                busy: viewModel.sponsorAd.busy,
                onLoadAds: { viewModel.sponsorAd.loadAds() },
                onPlay: { viewModel.sponsorAd.play(adVideoId: $0) },
                onStop: { viewModel.sponsorAd.stop() },
                showTriggerButton: false
            )
        }
        .task { await viewModel.start() }
        .onDisappear { Task { await viewModel.stop() } }
        // Manager ET modérateurs (pour voir qui d'autre a ce pouvoir) : liste + désignation.
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        .sheet(isPresented: $showFilterSheet) { filterSheet }
        .sheet(isPresented: $showManagerSheet) { managerSheet }
        .sheet(isPresented: $showGiftSheet) { giftSheet }
        .sheet(isPresented: $showLeaderboardSheet) { leaderboardSheet }
        .confirmationDialog(s.sponsorStartAd, isPresented: $showSponsorAdPicker, titleVisibility: .visible) {
            if viewModel.sponsorAd.ads.isEmpty {
                Button(s.sponsorNoAds) {}.disabled(true)
            } else {
                ForEach(viewModel.sponsorAd.ads) { ad in
                    Button("\(ad.title) · \(ad.durationSeconds)s") { viewModel.sponsorAd.play(adVideoId: ad.id) }
                }
            }
        }
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
        // Gate d'accès programmé (parité web `ScheduledAccessGate`) : rendu en dernier →
        // toujours au-dessus (même de la barrière de bannissement).
        ScheduledAccessGateView(
            type: "duel",
            scheduledAtIso: viewModel.duel?.scheduledTime,
            status: viewModel.duel?.status.rawValue,
            isActor: viewModel.isActor,
            hasTicket: viewModel.hasTicket,
            ticketPrice: viewModel.duel?.ticketPrice ?? 0,
            onPurchase: { await viewModel.buyTicket() },
            onDismiss: onLeave
        )
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

    /// Barre du bas : saisie de message + likes/réactions + cadeau + classement.
    private var bottomBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(viewModel.iAmBanned ? s.banConfirmMessage : s.saySomething, text: $draft)
                .textFieldStyle(.plain)
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(.white.opacity(0.15), in: Capsule())
                .submitLabel(.send)
                .disabled(viewModel.iAmBanned)
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(theme.colors.accent, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(draft.trimmed.isEmpty || viewModel.iAmBanned)
            Button { viewModel.sendLike() } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "heart.fill")
                        .foregroundStyle(.white)
                        .padding(theme.spacing.sm)
                        .background(.black.opacity(0.4), in: Circle())
                    if viewModel.likes > 0 {
                        Text("\(viewModel.likes)")
                            .font(.system(size: 10)).bold()
                            .foregroundStyle(.white)
                            .padding(4)
                            .background(theme.colors.accent, in: Circle())
                            .offset(x: 4, y: -4)
                    }
                }
            }
            .buttonStyle(.plain)
            Button { showReactionBar.toggle() } label: {
                Image(systemName: "face.smiling.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(.black.opacity(0.4), in: Circle())
            }
            .buttonStyle(.plain)
            Button { showGiftSheet = true } label: {
                Image(systemName: "gift.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(theme.gradients.primary, in: Circle())
            }
            .buttonStyle(.plain)
            Button {
                showLeaderboardSheet = true
                Task { await viewModel.loadGiftLeaderboard() }
            } label: {
                Image(systemName: "trophy.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.sm)
                    .background(.black.opacity(0.4), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.top, theme.spacing.sm)
    }

    /// Barre d'emojis réactions (togglée par le bouton emoji de ``bottomBar``).
    private var reactionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.sm) {
                ForEach(Self.reactionEmojis, id: \.self) { emoji in
                    Button {
                        viewModel.sendReaction(emoji)
                    } label: {
                        Text(emoji)
                            .padding(theme.spacing.sm)
                            .background(.black.opacity(0.35), in: Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// Réactions rapides proposées (parité `DuelReactionEmojis` Android).
    private static let reactionEmojis = ["🔥", "😍", "👏", "🎵", "💎", "🎶", "⚡", "🌟", "😂"]

    /// Envoie le brouillon puis vide le champ.
    private func send() {
        guard !viewModel.iAmBanned else { return }
        let text = draft
        draft = ""
        Task { await viewModel.sendMessage(text) }
    }

    /// Haut : bouton signaler + barre de répartition des votes + minuteur.
    private var header: some View {
        VStack(spacing: theme.spacing.sm) {
            // Ligne 1 (allégée) : badge DUEL + likes + signaler + QUITTER rouge (tout le monde).
            // Miroir de `DuelRoomScreen.kt:367-376` (`LiveHeader(badgeText = "DUEL", showViewers = false)`).
            HStack(spacing: 6) {
                HStack(spacing: 5) {
                    Circle().fill(.white).frame(width: 7, height: 7)
                    Text("DUEL")
                        .font(.system(size: 11, weight: .black))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(theme.colors.destructive, in: Capsule())

                if viewModel.likes > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(Color(hex: 0xFF4D6D))
                        Text("\(viewModel.likes)").font(.system(size: 12, weight: .semibold)).foregroundStyle(.white)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.black.opacity(0.4), in: Capsule())
                }

                Spacer()

                Button { showReport = true } label: {
                    Image(systemName: "flag")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))

                Button { onLeave() } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(theme.colors.destructive, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.quitLiveAction))
            }
            HStack {
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
                if viewModel.isManager {
                    Button { showManagerSheet = true } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "slider.horizontal.3")
                                .font(DMFont.caption)
                                .foregroundStyle(.white)
                                .padding(theme.spacing.xs)
                                .background(theme.colors.accent, in: Circle())
                            RecordingRailBadge(active: viewModel.recordingCtl.active, paused: viewModel.recordingCtl.paused)
                                .offset(x: 4, y: -4)
                        }
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                ShareLink(item: s.shareLiveText) {
                    Image(systemName: "square.and.arrow.up")
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                        .padding(theme.spacing.xs)
                        .background(.black.opacity(0.4), in: Circle())
                }
                HStack(spacing: 4) {
                    Image(systemName: "eye.fill")
                    Text("\(viewModel.viewerCount)")
                }
                .font(DMFont.caption)
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.sm)
                .padding(.vertical, theme.spacing.xs)
                .background(.black.opacity(0.4), in: Capsule())
            }
            VoteBar(
                leftName: viewModel.duel?.artist1?.displayName ?? s.artist1,
                rightName: viewModel.duel?.artist2?.displayName ?? s.artist2,
                leftTotal: viewModel.duel.flatMap { viewModel.voteTotals[$0.artist1Id] } ?? 0,
                rightTotal: viewModel.duel.flatMap { viewModel.voteTotals[$0.artist2Id] } ?? 0,
                onLeftTap: { if let id = viewModel.duel?.artist1Id { onOpenArtist(id) } },
                onRightTap: { if let id = viewModel.duel?.artist2Id { onOpenArtist(id) } }
            )
            // Nom du meilleur donateur qui défile — miroir de `DuelRoomScreen.kt:422-432`.
            if let donor = viewModel.topDonor {
                HStack { ScrollingLabel("👑  \(donor.displayName)  ·  \(Int(donor.total)) 🎁") }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(Color.white.opacity(0.2), in: Capsule())
            }
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
                    if viewModel.isManager && (viewModel.duel?.allowsSponsorAds ?? true) && viewModel.winnerInfo == nil {
                        controlButton(viewModel.sponsorAd.activeAd != nil ? "megaphone.fill" : "megaphone") {
                            if viewModel.sponsorAd.activeAd != nil { viewModel.sponsorAd.stop() } else { showSponsorAdPicker = true; viewModel.sponsorAd.loadAds() }
                        }
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

    // MARK: - Panneau manager (arbitre)

    /// Panneau du manager : temps de parole (slider), mute/unmute par artiste, annonce du
    /// vainqueur (calcul automatique sur les votes), chat on/off, fin du duel.
    private var managerSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                HStack {
                    Text("🎛 " + s.managedDuels).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
                    Spacer()
                    Toggle(isOn: Binding(
                        get: { viewModel.duel?.chatEnabled ?? true },
                        set: { enabled in Task { await viewModel.toggleChat(enabled) } }
                    )) {
                        Text(s.chatLabel).font(DMFont.caption)
                    }
                    .labelsHidden()
                }

                ManagerDuelControls(
                    artist1Name: viewModel.duel?.artist1?.displayName ?? s.artist1,
                    artist2Name: viewModel.duel?.artist2?.displayName ?? s.artist2,
                    artist1Id: viewModel.duel?.artist1Id,
                    artist2Id: viewModel.duel?.artist2Id,
                    mutedArtists: viewModel.mutedArtists,
                    onToggleMute: { viewModel.toggleMuteArtist($0) },
                    onGiveTurn: { id, seconds in Task { await viewModel.startTimer(targetId: id, seconds: seconds) } },
                    onStopTimer: { Task { await viewModel.stopTimer() } },
                    timerRunning: viewModel.timer.isRunning,
                    onAnnounceWinner: { viewModel.announceWinnerAuto(); showManagerSheet = false },
                    onEnd: { viewModel.endDuel(onEnded: onLeave) }
                )

                Divider()
                Text("🔴 \(s.recording)").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                RecordingSessionControls(
                    mode: viewModel.recordingCtl.mode,
                    active: viewModel.recordingCtl.active,
                    paused: viewModel.recordingCtl.paused,
                    finalizing: viewModel.recordingCtl.finalizing,
                    accumulatedSeconds: viewModel.recordingCtl.accumulatedSeconds,
                    runStartedAt: viewModel.recordingCtl.runStartedAt,
                    busy: viewModel.recordingCtl.busy,
                    onStart: { viewModel.recordingCtl.start(onError: { recordingErrorMessage = $0 }) },
                    onPause: { viewModel.recordingCtl.pause(onError: { recordingErrorMessage = $0 }) },
                    onResume: { viewModel.recordingCtl.resume(onError: { recordingErrorMessage = $0 }) },
                    onCancel: { showCancelRecordingConfirm = true },
                    onSave: { viewModel.recordingCtl.save(onError: { recordingErrorMessage = $0 }) }
                )
                if let recordingErrorMessage {
                    Text(recordingErrorMessage).font(DMFont.caption).foregroundStyle(theme.colors.destructive)
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.large])
        .dmScreenBackground()
        .confirmationDialog(s.cancel, isPresented: $showCancelRecordingConfirm, titleVisibility: .visible) {
            Button(s.cancel, role: .destructive) {
                viewModel.recordingCtl.cancel(onError: { recordingErrorMessage = $0 })
                showCancelRecordingConfirm = false
            }
        }
    }

    // MARK: - Cadeaux

    /// Feuille : envoyer un cadeau possédé (ou en acheter un) à un artiste/manager du duel.
    private var giftSheet: some View {
        GiftSendSheet(
            targets: giftTargets,
            inventory: viewModel.inventory,
            catalog: viewModel.giftCatalog,
            onSend: { giftId, toUserId in
                Task { await viewModel.sendGift(giftId: giftId, toUserId: toUserId); showGiftSheet = false }
            },
            onPurchase: { giftId in Task { await viewModel.purchaseGift(giftId: giftId) } }
        )
        .task { await viewModel.loadGiftCatalog(); await viewModel.loadInventory() }
    }

    /// Destinataires possibles d'un cadeau : artiste 1, artiste 2, manager.
    private var giftTargets: [(id: String, name: String)] {
        guard let duel = viewModel.duel else { return [] }
        var targets: [(id: String, name: String)] = [
            (duel.artist1Id, duel.artist1?.displayName ?? s.artist1),
            (duel.artist2Id, duel.artist2?.displayName ?? s.artist2),
        ]
        if let managerId = duel.managerId {
            targets.append((managerId, duel.manager?.displayName ?? "Manager"))
        }
        return targets
    }

    // MARK: - Classement des donateurs

    private var leaderboardSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text("🏆 \(s.donors)").font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            if viewModel.leaderboard.isEmpty {
                Text(s.emptyRanking).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        ForEach(Array(viewModel.leaderboard.enumerated()), id: \.element.id) { index, entry in
                            HStack {
                                Text("\(medal(index)) \(entry.displayName)").foregroundStyle(theme.colors.foreground)
                                Spacer()
                                Text("\(entry.value) \(s.credits)").bold().foregroundStyle(theme.colors.accent)
                            }
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    private func medal(_ index: Int) -> String {
        switch index {
        case 0: return "🥇"
        case 1: return "🥈"
        case 2: return "🥉"
        default: return "#\(index + 1)"
        }
    }

    // MARK: - Célébration du vainqueur

    /// Plein écran, persistante pour tous jusqu'à ce que le manager l'arrête (n'arrête pas
    /// le direct). Miroir de `WinnerCelebration` Android (confetti + lueur + carte dorée) —
    /// remplace l'ancien texte simple.
    private func winnerCelebration(_ winner: DuelWinner) -> some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            WinnerCelebration(
                winnerName: winner.name,
                title: s.winnerTitle,
                avatarURL: winner.avatar,
                subtitle: "\(winner.votes) · \(winner.percent)%"
            )
            if viewModel.isManager {
                VStack {
                    Spacer()
                    DMButton(s.stopAction) { viewModel.stopWinnerAnnouncement() }
                        .frame(maxWidth: 220)
                        .padding(.bottom, theme.spacing.xl)
                }
            }
        }
    }

    // MARK: - Barrière de bannissement

    /// Plein écran opaque : bloque tout et empêche de rester dans ce duel dès que JE suis
    /// banni par le manager (parité web `BannedAccessGate`).
    private var bannedGate: some View {
        ZStack {
            theme.colors.background.ignoresSafeArea()
            VStack(spacing: theme.spacing.lg) {
                Image(systemName: "nosign").font(.system(size: 56)).foregroundStyle(theme.colors.destructive)
                Text(s.banConfirmMessage)
                    .font(DMFont.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(theme.colors.mutedForeground)
                DMButton(s.scheduledBackHome, action: onLeave)
                    .frame(maxWidth: 220)
            }
            .padding(theme.spacing.xl)
        }
    }
}

/// Panneau de contrôle du manager (arbitre) : minuteur de parole, mute par artiste, vainqueur.
private struct ManagerDuelControls: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let artist1Name: String
    let artist2Name: String
    let artist1Id: String?
    let artist2Id: String?
    let mutedArtists: Set<String>
    let onToggleMute: (String) -> Void
    let onGiveTurn: (String, Int) -> Void
    let onStopTimer: () -> Void
    let timerRunning: Bool
    let onAnnounceWinner: () -> Void
    let onEnd: () -> Void

    @State private var minutes: Double = 2

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            let mm = Int(minutes)
            Text("🎛 \(s.speakingTime) : \(mm) min").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
            Slider(value: $minutes, in: 1...120)
            if timerRunning {
                DMButton(s.stopAction, style: .destructive, action: onStopTimer)
            }
            HStack(spacing: theme.spacing.sm) {
                if let artist1Id {
                    DMButton("🎤 \(artist1Name)") { onGiveTurn(artist1Id, Int(minutes) * 60) }
                }
                if let artist2Id {
                    DMButton("🎤 \(artist2Name)", style: .secondary) { onGiveTurn(artist2Id, Int(minutes) * 60) }
                }
            }
            Text("🎙 " + s.speakingTime).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
            HStack(spacing: theme.spacing.sm) {
                if let artist1Id {
                    let muted = mutedArtists.contains(artist1Id)
                    DMButton(muted ? "🔊 \(artist1Name)" : "🔇 \(artist1Name)", style: muted ? .secondary : .outline) {
                        onToggleMute(artist1Id)
                    }
                }
                if let artist2Id {
                    let muted = mutedArtists.contains(artist2Id)
                    DMButton(muted ? "🔊 \(artist2Name)" : "🔇 \(artist2Name)", style: muted ? .secondary : .outline) {
                        onToggleMute(artist2Id)
                    }
                }
            }
            DMButton("🏆 \(s.announceWinner)", action: onAnnounceWinner)
            DMButton(s.endDuelBtn, style: .outline, action: onEnd)
        }
    }
}

/// Feuille d'envoi de cadeau : destinataire + onglets « Mes cadeaux » / « Boutique ».
private struct GiftSendSheet: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let targets: [(id: String, name: String)]
    let inventory: [InventoryItem]
    let catalog: [VirtualGift]
    let onSend: (String, String) -> Void
    let onPurchase: (String) -> Void

    @State private var target: String?
    @State private var showShop = false

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text("🎁 \(s.sendGift)").font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: theme.spacing.xs) {
                    ForEach(targets, id: \.id) { t in
                        pill("🎤 \(t.name)", selected: target == t.id) { target = t.id }
                    }
                }
            }

            HStack(spacing: theme.spacing.sm) {
                pill(s.myGifts, selected: !showShop) { showShop = false }
                pill(s.giftShopLabel, selected: showShop) { showShop = true }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    if !showShop {
                        if inventory.isEmpty {
                            Text(s.noGiftsBuyInShop).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                        }
                        ForEach(inventory) { item in
                            Button {
                                guard let target else { return }
                                onSend(item.giftId, target)
                            } label: {
                                HStack {
                                    Text("\(item.imageURL ?? "🎁")  \(item.name ?? "")").foregroundStyle(theme.colors.foreground)
                                    Spacer()
                                    Text("×\(item.quantity)").bold().foregroundStyle(theme.colors.accent)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    } else {
                        ForEach(catalog) { gift in
                            Button { onPurchase(gift.id) } label: {
                                HStack {
                                    Text("\(gift.emoji ?? "🎁")  \(gift.name)").foregroundStyle(theme.colors.foreground)
                                    Spacer()
                                    Text("\(Int(gift.price)) \(s.credits)").bold().foregroundStyle(theme.colors.accent)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.large])
        .dmScreenBackground()
        .onAppear { if target == nil { target = targets.first?.id } }
    }

    private func pill(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(DMFont.caption).bold()
                .foregroundStyle(selected ? theme.colors.primaryForeground : theme.colors.foreground)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(selected ? theme.colors.accent : Color.black.opacity(0.15), in: Capsule())
        }
        .buttonStyle(.plain)
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
    var onLeftTap: () -> Void = {}
    var onRightTap: () -> Void = {}

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
                    .onTapGesture(perform: onLeftTap)
                Spacer()
                Text("\(Int(rightTotal)) · \(rightName)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
                    .onTapGesture(perform: onRightTap)
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

/// Catalogue des duels (3 onglets : En direct / À venir / Replays + recherche) — miroir de
/// `DuelsListScreen.kt`.
@MainActor
public struct DuelsListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: DuelsListViewModel
    private let onOpen: (Duel) -> Void
    private let onOpenReplay: (ReplayVideo) -> Void
    private let onRequestSponsor: (String, String) -> Void

    @State private var tab = 0
    @State private var search = ""

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'un duel.
    ///   - onOpenReplay: callback à l'ouverture d'un replay de duel.
    ///   - onRequestSponsor: ouvre le sponsoring présélectionné sur ce duel (`"duel"`, id).
    public init(
        viewModel: DuelsListViewModel,
        onOpen: @escaping (Duel) -> Void,
        onOpenReplay: @escaping (ReplayVideo) -> Void = { _ in },
        onRequestSponsor: @escaping (String, String) -> Void = { _, _ in }
    ) {
        self.viewModel = viewModel
        self.onOpen = onOpen
        self.onOpenReplay = onOpenReplay
        self.onRequestSponsor = onRequestSponsor
    }

    private var query: String { search.trimmed.lowercased() }

    private func matches(_ duel: Duel) -> Bool {
        query.isEmpty
            || (duel.artist1?.displayName.lowercased().contains(query) ?? false)
            || (duel.artist2?.displayName.lowercased().contains(query) ?? false)
    }

    private func matches(_ replay: ReplayVideo) -> Bool {
        query.isEmpty || (replay.title?.lowercased().contains(query) ?? false)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.md) {
                Text(s.duelsPageTitle)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(theme.colors.primary)
                    .frame(maxWidth: .infinity)
                Text(s.duelsSubtitle)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .frame(maxWidth: .infinity)

                DMTextField(s.searchPlaceholder, text: $search)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: theme.spacing.xs) {
                        DMTabPill("\(s.duelTabLive) (\(viewModel.live.count))", selected: tab == 0) { tab = 0 }
                        DMTabPill("\(s.duelTabUpcoming) (\(viewModel.upcoming.count))", selected: tab == 1) { tab = 1 }
                        DMTabPill("\(s.duelTabReplays) (\(viewModel.replays.count))", selected: tab == 2) { tab = 2 }
                    }
                }

                if viewModel.isLoading {
                    DMLoadingBox()
                } else {
                    tabContent
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch tab {
        case 0:
            let filtered = viewModel.live.filter(matches)
            if filtered.isEmpty {
                DMEmptyState(title: s.noDuelsLive, systemImage: "calendar")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { duel in
                        DuelRow(
                            duel: duel,
                            votes: viewModel.votes[duel.id],
                            viewers: viewModel.presence[duel.id],
                            isLive: true,
                            onOpen: { onOpen(duel) },
                            onRequestSponsor: { onRequestSponsor("duel", duel.id) }
                        )
                    }
                }
            }
        case 1:
            let filtered = viewModel.upcoming.filter(matches)
            if filtered.isEmpty {
                DMEmptyState(title: s.noDuelsUpcoming, systemImage: "calendar")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { duel in
                        DuelRow(
                            duel: duel,
                            votes: viewModel.votes[duel.id],
                            viewers: nil,
                            isLive: false,
                            onOpen: { onOpen(duel) },
                            onRequestSponsor: { onRequestSponsor("duel", duel.id) }
                        )
                    }
                }
            }
        default:
            let filtered = viewModel.replays.filter(matches)
            if filtered.isEmpty {
                DMEmptyState(title: s.noDuelReplays, systemImage: "play.rectangle")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { replay in
                        DuelReplayRow(replay: replay, onOpen: { onOpenReplay(replay) })
                    }
                }
            }
        }
    }
}

/// Carte d'un duel (En direct / À venir) : badge statut, prix payant/gratuit, les deux artistes
/// avec avatar + votes, barre de répartition, spectateurs (en direct), bouton voter/voir +
/// « Sponsoriser » contextuel. Miroir de `DuelCard` (`DuelsListScreen.kt:212-276`).
private struct DuelRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let duel: Duel
    let votes: (Double, Double)?
    let viewers: Int?
    let isLive: Bool
    let onOpen: () -> Void
    let onRequestSponsor: () -> Void

    private var a1: Double { votes?.0 ?? 0 }
    private var a2: Double { votes?.1 ?? 0 }
    private var total: Double { a1 + a2 }
    private var leftShare: Double {
        guard total > 0 else { return 0.5 }
        return min(0.98, max(0.02, a1 / total))
    }

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                DMBadgePill(
                    isLive ? "🔥 \(s.liveBadge)" : "📅 \(s.upcomingBadge)",
                    foreground: .white,
                    background: isLive ? Color(hex: 0xB91C1C) : Color.black.opacity(0.4),
                    bold: true
                )

                if let time = isoMinute(duel.scheduledTime) {
                    Text("📅 \(time)").font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }

                if duel.ticketPrice > 0 {
                    DMBadgePill(
                        "\(s.duelPaid) • \(Int(duel.ticketPrice)) \(s.creditUnit)",
                        foreground: Color(hex: 0xF59E0B),
                        background: Color(hex: 0xF59E0B, alpha: 0.2)
                    )
                } else {
                    DMBadgePill(s.free, foreground: Color(hex: 0x10B981), background: Color(hex: 0x10B981, alpha: 0.2))
                }

                HStack(alignment: .top, spacing: theme.spacing.sm) {
                    ArtistColumnView(name: duel.artist1?.displayName ?? s.artist1, avatarURL: duel.artist1?.avatarURL, votes: Int(a1))
                    Text("🏆").font(.system(size: 20)).padding(.top, theme.spacing.lg)
                    ArtistColumnView(name: duel.artist2?.displayName ?? s.artist2, avatarURL: duel.artist2?.avatarURL, votes: Int(a2))
                }

                if total > 0 {
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            Rectangle().fill(theme.gradients.primary).frame(width: geo.size.width * leftShare)
                            Rectangle().fill(theme.colors.electricBlue)
                        }
                    }
                    .frame(height: 8)
                    .clipShape(RoundedRectangle(cornerRadius: theme.radius.sm, style: .continuous))
                }

                if isLive, let viewers {
                    Text("👥 \(viewers) \(s.spectators)")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                        .frame(maxWidth: .infinity, alignment: .center)
                }

                DMButton(isLive ? s.voteBtn : s.viewDuelBtn, action: onOpen)

                if duel.acceptsSponsors && !isDeadlinePassed(duel.sponsorSubmissionDeadline) {
                    DMButton(s.requestSponsorBtn, style: .outline, action: onRequestSponsor)
                }
            }
        }
        .onTapGesture(perform: onOpen)
    }
}

/// Colonne d'un artiste du duel : avatar 56 pt + nom + votes. Miroir de `ArtistColumn`
/// (`DuelsListScreen.kt:290-298`).
private struct ArtistColumnView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let name: String
    let avatarURL: String?
    let votes: Int

    var body: some View {
        VStack(spacing: 2) {
            DMRemoteImage(url: avatarURL, fallback: "🎤")
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            Text(name)
                .font(DMFont.caption).bold()
                .foregroundStyle(theme.colors.foreground)
                .multilineTextAlignment(.center)
                .lineLimit(1)
            Text("\(votes) \(s.votesWord)")
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.accent)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Carte d'un replay de duel : miniature 16:9 (badge « 🏆 VS », glyphe lecture, durée), titre,
/// vues, date d'enregistrement. Miroir de `ReplayCard` (`DuelsListScreen.kt:301-326`).
private struct DuelReplayRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let replay: ReplayVideo
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            DMCard(padded: false) {
                VStack(alignment: .leading, spacing: 0) {
                    DMRemoteImage(url: replay.thumbnailURL, fallback: "🎬")
                        .frame(maxWidth: .infinity)
                        .frame(height: 180)
                        .overlay { Text("▶").font(.system(size: 40)).foregroundStyle(.white) }
                        .overlay(alignment: .topLeading) {
                            DMBadgePill("🏆 VS", foreground: .white, background: Color(hex: 0xB91C1C), bold: true)
                                .padding(theme.spacing.sm)
                        }
                        .overlay(alignment: .bottomTrailing) {
                            DMBadgePill("🕒 \(durationLabel)", foreground: .white, background: Color.black.opacity(0.6))
                                .padding(theme.spacing.sm)
                        }
                        .clipped()

                    VStack(alignment: .leading, spacing: 4) {
                        Text(replay.title ?? "Duel")
                            .font(DMFont.body).bold()
                            .foregroundStyle(theme.colors.foreground)
                        HStack {
                            Text("👁 \(replay.viewsCount) \(s.views)")
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.mutedForeground)
                            Spacer()
                            if let date = isoDay(replay.recordedDate) {
                                Text(date).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                            }
                        }
                    }
                    .padding(theme.spacing.md)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var durationLabel: String {
        guard let d = replay.duration else { return "N/A" }
        return "\(d)s"
    }
}

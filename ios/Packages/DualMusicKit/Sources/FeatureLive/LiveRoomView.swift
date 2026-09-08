import SwiftUI
import LiveKit
import CoreLiveMedia
import CoreUI
import DomainModels

/// Écran d'un live (viewer) : vidéo plein écran + overlays chat / cadeaux / présence.
///
/// Style TikTok : la vidéo occupe tout l'écran, les overlays flottent par-dessus. Le rendu
/// vidéo utilise `SwiftUIVideoView` (décodage matériel VideoToolbox, zéro copie vers Metal).
/// Miroir de `LiveRoomScreen` Android.
@MainActor
public struct LiveRoomView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: LiveViewModel
    private let hostUserId: String
    private let quickGiftId: String
    private let prewarmedToken: LiveKitToken?
    private let onEnded: () -> Void

    @State private var draft = ""
    @State private var showReport = false
    @State private var banTarget: LiveChatMessage?
    @State private var showDedicationSheet = false
    @State private var showDedicationRequests = false
    @State private var dedicationMessage = ""
    @State private var dedicationPriceText = ""
    @State private var minPriceText = ""
    @State private var showGuestsSheet = false
    @State private var showModeratorsSheet = false

    /// - Parameters:
    ///   - viewModel: état + actions du live.
    ///   - hostUserId: destinataire des cadeaux.
    ///   - quickGiftId: cadeau rapide (vide → bouton inactif tant qu'aucun cadeau choisi).
    ///   - prewarmedToken: jeton LiveKit pré-chauffé par le feed.
    ///   - onEnded: hôte uniquement — appelé une fois le live terminé (retour à « Mes lives »).
    public init(
        viewModel: LiveViewModel,
        hostUserId: String,
        quickGiftId: String = "",
        prewarmedToken: LiveKitToken? = nil,
        onEnded: @escaping () -> Void = {}
    ) {
        self.viewModel = viewModel
        self.hostUserId = hostUserId
        self.quickGiftId = quickGiftId
        self.prewarmedToken = prewarmedToken
        self.onEnded = onEnded
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // --- Couche vidéo --- (l'hôte voit SON propre aperçu, pas le flux "primary" —
            // celui-ci n'existe que côté spectateur, alimenté par un participant DISTANT).
            if let track = displayTrack {
                SwiftUIVideoView(track, layoutMode: .fill)
                    .ignoresSafeArea()
            } else {
                theme.gradients.hero.ignoresSafeArea()
                if viewModel.isHost {
                    if !viewModel.media.isCameraEnabled {
                        startBroadcastButton
                    }
                } else if case .connecting = viewModel.media.connectionState {
                    DMLoadingBox()
                }
            }

            // Dégradé bas : lisibilité des overlays sur une vidéo claire.
            LinearGradient(
                colors: [.clear, .black.opacity(0.65)],
                startPoint: .center,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .allowsHitTesting(false)

            // --- Cadeau animé (dernier reçu, centré) ---
            if let gift = viewModel.giftFeed.last {
                GiftBurstView(symbol: "🎁", label: gift.giftName) {
                    viewModel.consumeOldestGift()
                }
                .id(gift.id)
            }

            // --- Overlays ---
            VStack {
                viewerBadge
                guestTilesStrip
                Spacer()
                chatOverlay
                if let feedback = viewModel.dedicationFeedback {
                    dedicationFeedbackBanner(feedback)
                }
                if viewModel.isHost {
                    hostControls
                    dedicationSettingsRow
                    guestSettingsRow
                } else {
                    if viewModel.isGuestAccepted { guestSelfControls }
                    actionBar
                }
            }
            .padding(theme.spacing.md)
        }
        .task {
            await viewModel.start(prewarmedToken: prewarmedToken)
        }
        .onDisappear {
            Task { await viewModel.stop() }
        }
        .onChange(of: viewModel.dedicationFeedback) { _, feedback in
            guard feedback != nil else { return }
            Task {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                viewModel.clearDedicationFeedback()
            }
        }
        // Fan : demande de dédicace (message + prix, prix plancher forcé par l'hôte).
        .sheet(isPresented: $showDedicationSheet) { dedicationRequestSheet }
        // Hôte : demandes en attente (accepter/rejeter) + historique (marquer comme livrée).
        .sheet(isPresented: $showDedicationRequests) { dedicationRequestsSheet }
        // Hôte : demandes d'invité en attente (accepter/rejeter) + invités actifs (retirer).
        .sheet(isPresented: $showGuestsSheet) { guestsSheet }
        // Hôte ET modérateurs (pour voir qui d'autre a ce pouvoir) : liste + désignation.
        .sheet(isPresented: $showModeratorsSheet) { moderatorsSheet }
        // Feuille de motifs (viewer uniquement — voir bouton drapeau de `viewerBadge`).
        .confirmationDialog(s.reportAction, isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportReason.allCases, id: \.self) { reason in
                Button(reportLabel(reason)) {
                    Task { await viewModel.report(reason: reason) }
                }
            }
            Button(s.cancel, role: .cancel) {}
        }
        // Confirmation de bannissement (hôte uniquement — tap sur un auteur de message).
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

    /// Compteur de spectateurs + boutons signaler/modérateurs, en haut.
    private var viewerBadge: some View {
        HStack {
            if !viewModel.isHost {
                Button { showReport = true } label: {
                    Image(systemName: "flag.fill")
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                        .padding(theme.spacing.xs)
                        .background(.black.opacity(0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.reportAction))
            }
            // Visible de l'hôte ET des modérateurs eux-mêmes (pour qu'ils voient qui d'autre a
            // ce pouvoir) — pas seulement l'hôte.
            if viewModel.canModerate {
                Button {
                    showModeratorsSheet = true
                    if viewModel.isHost { Task { await viewModel.loadViewers() } }
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
            HStack(spacing: 4) {
                Image(systemName: "eye.fill")
                Text("\(viewModel.viewerCount)")
            }
            .font(DMFont.caption)
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.md)
            .padding(.vertical, theme.spacing.xs)
            .background(.black.opacity(0.4), in: Capsule())
        }
    }

    /// Les 6 derniers messages visibles, du plus ancien au plus récent. L'hôte peut bannir
    /// l'auteur d'un message en tapant sur son nom.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(viewModel.visibleMessages.suffix(6)) { message in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(message.authorName)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                        .onTapGesture {
                            guard viewModel.canModerate, message.userId != hostUserId else { return }
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

    /// Libellé localisé d'un motif de signalement.
    private func reportLabel(_ reason: ReportReason) -> String {
        switch reason {
        case .inappropriate: return s.reportInappropriate
        case .harassment: return s.reportHarassment
        case .spam: return s.reportSpam
        case .violence: return s.reportViolence
        }
    }

    /// Barre d'action : saisie de message + bouton cadeau.
    private var actionBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField(s.saySomething, text: $draft)
                .textFieldStyle(.plain)
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(.white.opacity(0.15), in: Capsule())
                .submitLabel(.send)
                .onSubmit(send)

            if !viewModel.isGuestAccepted && viewModel.liveAllowGuests {
                Button {
                    Task {
                        if viewModel.myJoinRequestId == nil {
                            await viewModel.requestJoin()
                        } else {
                            await viewModel.cancelJoin()
                        }
                    }
                } label: {
                    Image(systemName: viewModel.myJoinRequestId == nil ? "hand.raised.fill" : "clock.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(
                            viewModel.myJoinRequestId == nil ? .white.opacity(0.15) : theme.colors.accent,
                            in: Circle()
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.raiseHand))
            }

            if viewModel.liveAllowsDedications {
                Button {
                    dedicationPriceText = String(Int(viewModel.dedicationMinPriceCredits))
                    showDedicationSheet = true
                } label: {
                    Image(systemName: "megaphone.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.15), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.dedication))
            }

            Button {
                Task { await viewModel.sendGift(giftId: quickGiftId, toUserId: hostUserId) }
            } label: {
                Image(systemName: "gift.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(theme.gradients.primary, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(quickGiftId.isEmpty)
            .opacity(quickGiftId.isEmpty ? 0.5 : 1)
            .dmGlow()
            .accessibilityLabel(Text(s.sendGift))
        }
    }

    /// Envoie le message saisi puis vide le champ.
    private func send() {
        let text = draft
        draft = ""
        Task { await viewModel.sendMessage(text) }
    }

    /// Piste à afficher en plein écran : l'aperçu de l'hôte lui-même, ou le flux du host
    /// pour un spectateur.
    private var displayTrack: VideoTrack? {
        viewModel.isHost ? viewModel.media.localVideoTrack : viewModel.media.primaryVideoTrack
    }

    /// Bouton central affiché avant que l'hôte n'ait démarré sa diffusion.
    private var startBroadcastButton: some View {
        Button {
            Task { await viewModel.startBroadcast() }
        } label: {
            HStack(spacing: theme.spacing.sm) {
                Image(systemName: "video.fill")
                Text(s.startLive)
            }
            .font(DMFont.body).bold()
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
            .background(theme.gradients.primary, in: Capsule())
        }
        .buttonStyle(.plain)
        .dmGlow()
    }

    /// Barre de contrôles hôte : micro, caméra, bascule caméra, terminer.
    private var hostControls: some View {
        HStack(spacing: theme.spacing.md) {
            controlButton(viewModel.media.isMicrophoneEnabled ? "mic.fill" : "mic.slash.fill") {
                Task { await viewModel.toggleMic() }
            }

            controlButton(viewModel.media.isCameraEnabled ? "video.fill" : "video.slash.fill") {
                Task { await viewModel.toggleCamera() }
            }

            controlButton("arrow.triangle.2.circlepath.camera.fill") {
                Task { await viewModel.switchCamera() }
            }

            Button {
                showDedicationRequests = true
                Task { await viewModel.loadDedications() }
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "megaphone.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.15), in: Circle())
                    if !viewModel.dedications.isEmpty {
                        Text("\(viewModel.dedications.count)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(4)
                            .background(theme.colors.destructive, in: Circle())
                            .offset(x: 4, y: -4)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(s.dedicationsLabel))

            Button {
                showGuestsSheet = true
                Task { await viewModel.loadJoinRequests() }
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "hand.raised.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.15), in: Circle())
                    if !viewModel.joinRequests.isEmpty {
                        Text("\(viewModel.joinRequests.count)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(4)
                            .background(theme.colors.destructive, in: Circle())
                            .offset(x: 4, y: -4)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(s.guestsLabel))

            Spacer()

            Button {
                Task {
                    try? await viewModel.endLive()
                    onEnded()
                }
            } label: {
                Text(s.endLive)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.sm)
                    .background(theme.colors.destructive, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    /// Petit bouton rond de la barre de contrôles hôte (icône seule).
    private func controlButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(.white.opacity(0.15), in: Circle())
        }
        .buttonStyle(.plain)
    }

    /// Bannière de confirmation/décision de dédicace (fan) — auto-masquée après quelques
    /// secondes (voir `.onChange(of: viewModel.dedicationFeedback)`).
    private func dedicationFeedbackBanner(_ text: String) -> some View {
        Text(text)
            .font(DMFont.caption).bold()
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.md)
            .padding(.vertical, theme.spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    /// Hôte : réglage des dédicaces pour ce live (on/off + prix minimum), appliqué en
    /// direct — visible par tous immédiatement (événement temps réel `settings`).
    private var dedicationSettingsRow: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Button {
                viewModel.setDedicationsEnabled(!viewModel.liveAllowsDedications)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "megaphone.fill")
                    Text(viewModel.liveAllowsDedications ? s.dedicationsEnabledOn : s.dedicationsEnabledOff)
                }
                .font(DMFont.caption).bold()
                .foregroundStyle(.white)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(
                    viewModel.liveAllowsDedications ? theme.colors.primary : .black.opacity(0.35),
                    in: Capsule()
                )
            }
            .buttonStyle(.plain)

            if viewModel.liveAllowsDedications {
                HStack(spacing: theme.spacing.sm) {
                    TextField(s.dedicationMinPriceLive, text: $minPriceText)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                        .padding(.horizontal, theme.spacing.md)
                        .padding(.vertical, theme.spacing.sm)
                        .background(.white.opacity(0.15), in: Capsule())
                    Button {
                        if let price = Double(minPriceText), price > 0 {
                            viewModel.setDedicationMinPrice(price)
                        }
                        minPriceText = ""
                    } label: {
                        Text(s.confirm)
                            .font(DMFont.caption).bold()
                            .foregroundStyle(.white)
                            .padding(.horizontal, theme.spacing.md)
                            .padding(.vertical, theme.spacing.sm)
                            .background(theme.colors.primary, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .onAppear { minPriceText = "" }
    }

    /// Feuille fan : compose et envoie une dédicace (prix jamais sous le minimum de l'hôte).
    private var dedicationRequestSheet: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            Text(s.dedication).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)
            Text(s.dedicationHint).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            DMTextField(s.dedication, text: $dedicationMessage, placeholder: s.saySomething, axis: .vertical)
            DMTextField(
                "\(s.credits) (min \(Int(viewModel.dedicationMinPriceCredits)))",
                text: $dedicationPriceText,
                keyboard: .numberPad
            )
            DMButton("\(s.send) (\(dedicationPriceValue) \(s.credits))") {
                Task {
                    await viewModel.dedicate(message: dedicationMessage, price: Double(dedicationPriceValue))
                    dedicationMessage = ""
                    showDedicationSheet = false
                }
            }
            Spacer()
        }
        .padding(theme.spacing.lg)
        .presentationDetents([.medium])
        .dmScreenBackground()
    }

    /// Prix saisi par le fan, jamais sous le minimum effectif de ce live.
    private var dedicationPriceValue: Int {
        max(Int(dedicationPriceText) ?? Int(viewModel.dedicationMinPriceCredits), Int(viewModel.dedicationMinPriceCredits))
    }

    /// Feuille hôte : demandes en attente (accepter/rejeter) + historique (marquer comme livrée).
    private var dedicationRequestsSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.dedicationsLabel).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)

                if viewModel.dedications.isEmpty && viewModel.dedicationHistory.isEmpty {
                    Text(s.noDedicationsYet).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }

                if !viewModel.dedications.isEmpty {
                    DMSectionTitle("\(s.pendingLabel) (\(viewModel.dedications.count))")
                    ForEach(viewModel.dedications) { dedication in
                        pendingDedicationRow(dedication)
                    }
                }

                if !viewModel.dedicationHistory.isEmpty {
                    DMSectionTitle("\(s.dedicationsAcceptedDelivered) (\(viewModel.dedicationHistory.count))")
                    ForEach(viewModel.dedicationHistory) { dedication in
                        historyDedicationRow(dedication)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.medium, .large])
        .dmScreenBackground()
    }

    /// Ligne d'une demande de dédicace en attente : accepter (débite maintenant) ou rejeter.
    private func pendingDedicationRow(_ dedication: LiveDedication) -> some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("\(dedication.fanName)  ·  \(Int(dedication.priceCredits)) \(s.credits)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
                Text(dedication.message)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                HStack(spacing: theme.spacing.sm) {
                    DMButton(s.accept) { Task { await viewModel.acceptDedication(id: dedication.id) } }
                    DMButton(s.rejectAction, style: .destructive) { Task { await viewModel.rejectDedication(id: dedication.id) } }
                }
            }
        }
    }

    /// Ligne d'une dédicace acceptée/livrée : marquer comme interprétée (si pas déjà livrée).
    private func historyDedicationRow(_ dedication: LiveDedication) -> some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                Text("\(dedication.fanName)  ·  \(Int(dedication.priceCredits)) \(s.credits)")
                    .font(DMFont.caption).bold()
                    .foregroundStyle(theme.colors.accent)
                Text(dedication.message)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                if dedication.status == "delivered" {
                    Text("✅ \(s.delivered)")
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.primary)
                } else {
                    DMButton(s.markDelivered) { Task { await viewModel.deliverDedication(id: dedication.id) } }
                }
            }
        }
    }

    // MARK: - Invités sur scène

    /// Tuiles des invités actifs, HORS moi-même — `guestClients` en est déjà la source
    /// (``LiveViewModel/reconcileGuestSubscriptions`` filtre l'appelant lui-même à la
    /// construction), pas besoin de connaître mon propre id ici.
    private var guestTilesStrip: some View {
        let ids = viewModel.guestClients.keys.sorted()
        return Group {
            if !ids.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: theme.spacing.sm) {
                        ForEach(ids, id: \.self) { userId in
                            guestTile(userId)
                        }
                    }
                }
            }
        }
    }

    /// Une tuile invité : vidéo si l'invité diffuse, sinon un repli avatar générique.
    private func guestTile(_ userId: String) -> some View {
        let client = viewModel.guestClients[userId]
        let name = viewModel.acceptedGuests.first { $0.userId == userId }?.displayName ?? s.viewerFallback
        return VStack(spacing: 2) {
            ZStack {
                if let track = client?.primaryVideoTrack {
                    SwiftUIVideoView(track, layoutMode: .fill)
                } else {
                    Color.black.opacity(0.4)
                    Image(systemName: "person.fill")
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            .frame(width: 60, height: 80)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(name)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: 60)
        }
    }

    /// Contrôles de l'invité accepté sur SA PROPRE publication (room dédiée) : micro, caméra,
    /// descendre de scène. Affiché EN PLUS de la barre d'action normale (le chat continue).
    private var guestSelfControls: some View {
        HStack(spacing: theme.spacing.md) {
            controlButton(viewModel.guestMedia.isMicrophoneEnabled ? "mic.fill" : "mic.slash.fill") {
                Task { await viewModel.toggleGuestMicrophone() }
            }
            controlButton(viewModel.guestMedia.isCameraEnabled ? "video.fill" : "video.slash.fill") {
                Task { await viewModel.toggleGuestCamera() }
            }
            Spacer()
            Button {
                Task { await viewModel.leaveStage() }
            } label: {
                Text(s.leaveStageAction)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.sm)
                    .background(theme.colors.destructive, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    /// Hôte : réglage des demandes d'invité pour ce live, appliqué en direct.
    private var guestSettingsRow: some View {
        Button {
            viewModel.setGuestsEnabled(!viewModel.liveAllowGuests)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "hand.raised.fill")
                Text(viewModel.liveAllowGuests ? s.guestsEnabledOn : s.guestsEnabledOff)
            }
            .font(DMFont.caption).bold()
            .foregroundStyle(.white)
            .padding(.horizontal, theme.spacing.md)
            .padding(.vertical, theme.spacing.sm)
            .background(viewModel.liveAllowGuests ? theme.colors.primary : .black.opacity(0.35), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    /// Feuille hôte : demandes en attente (accepter/rejeter) + invités actifs (retirer).
    private var guestsSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.guestsLabel).font(DMFont.pageTitle).foregroundStyle(theme.colors.foreground)

                if viewModel.joinRequests.isEmpty && viewModel.acceptedGuests.isEmpty {
                    Text(s.noGuestRequests).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }

                if !viewModel.joinRequests.isEmpty {
                    DMSectionTitle("\(s.pendingRequests) (\(viewModel.joinRequests.count))")
                    ForEach(viewModel.joinRequests) { request in
                        pendingGuestRow(request)
                    }
                }

                if !viewModel.acceptedGuests.isEmpty {
                    DMSectionTitle("\(s.activeGuests) (\(viewModel.acceptedGuests.count))")
                    ForEach(viewModel.acceptedGuests) { guest in
                        activeGuestRow(guest)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .presentationDetents([.medium, .large])
        .dmScreenBackground()
    }

    /// Ligne d'une demande d'invité en attente : accepter ou rejeter.
    private func pendingGuestRow(_ request: LiveJoinRequest) -> some View {
        DMCard {
            HStack {
                Text("✋ \(request.displayName)")
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                HStack(spacing: theme.spacing.sm) {
                    Button { Task { await viewModel.respondJoin(id: request.id, accept: true) } } label: {
                        Image(systemName: "checkmark")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(theme.colors.primary, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(s.accept))
                    Button { Task { await viewModel.respondJoin(id: request.id, accept: false) } } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(theme.colors.destructive, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(s.rejectAction))
                }
            }
        }
    }

    /// Ligne d'un invité actif : retirer (met fin à sa publication chez tout le monde).
    private func activeGuestRow(_ guest: LiveJoinRequest) -> some View {
        DMCard {
            HStack {
                Text("🎤 \(guest.displayName)")
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                Spacer()
                Button { Task { await viewModel.kickGuest(requestId: guest.id) } } label: {
                    Image(systemName: "person.fill.xmark")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(theme.colors.destructive, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(s.removeGuestAction))
            }
        }
    }

    // MARK: - Modérateurs désignés

    /// Feuille : modérateurs désignés (révocables par l'hôte) + désignation d'un spectateur
    /// connecté (hôte uniquement). Visible aussi des modérateurs eux-mêmes, en lecture seule
    /// pour la partie désignation.
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

            if viewModel.isHost {
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

    /// Ligne d'un modérateur désigné — révocable par l'hôte seulement.
    private func moderatorRow(_ moderator: EventModerator) -> some View {
        HStack {
            Text(moderator.displayName).font(DMFont.body).foregroundStyle(theme.colors.foreground)
            Spacer()
            if viewModel.isHost {
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

    /// Picker de désignation (hôte uniquement) : spectateurs connectés, hors modérateurs déjà
    /// désignés, désactivé à la limite (``maxEventModerators``).
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
}

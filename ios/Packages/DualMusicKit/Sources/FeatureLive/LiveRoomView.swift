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
                Spacer()
                chatOverlay
                if let feedback = viewModel.dedicationFeedback {
                    dedicationFeedbackBanner(feedback)
                }
                if viewModel.isHost {
                    hostControls
                    dedicationSettingsRow
                } else {
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

    /// Compteur de spectateurs + bouton signaler, en haut.
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
                            guard viewModel.isHost, message.userId != hostUserId else { return }
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
}

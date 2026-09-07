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
public struct LiveRoomView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: LiveViewModel
    private let hostUserId: String
    private let quickGiftId: String
    private let prewarmedToken: LiveKitToken?

    @State private var draft = ""

    /// - Parameters:
    ///   - viewModel: état + actions du live.
    ///   - hostUserId: destinataire des cadeaux.
    ///   - quickGiftId: cadeau rapide (vide → bouton inactif tant qu'aucun cadeau choisi).
    ///   - prewarmedToken: jeton LiveKit pré-chauffé par le feed.
    public init(
        viewModel: LiveViewModel,
        hostUserId: String,
        quickGiftId: String = "",
        prewarmedToken: LiveKitToken? = nil
    ) {
        self.viewModel = viewModel
        self.hostUserId = hostUserId
        self.quickGiftId = quickGiftId
        self.prewarmedToken = prewarmedToken
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // --- Couche vidéo ---
            if let track = viewModel.media.primaryVideoTrack {
                SwiftUIVideoView(track, layoutMode: .fill)
                    .ignoresSafeArea()
            } else {
                theme.gradients.hero.ignoresSafeArea()
                if case .connecting = viewModel.media.connectionState {
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
                actionBar
            }
            .padding(theme.spacing.md)
        }
        .task {
            await viewModel.start(prewarmedToken: prewarmedToken)
        }
        .onDisappear {
            Task { await viewModel.stop() }
        }
    }

    /// Compteur de spectateurs, en haut à droite.
    private var viewerBadge: some View {
        HStack {
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

    /// Les 6 derniers messages, du plus ancien au plus récent.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(viewModel.messages.suffix(6)) { message in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(message.authorName)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                    Text(message.content)
                        .font(DMFont.caption)
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
}

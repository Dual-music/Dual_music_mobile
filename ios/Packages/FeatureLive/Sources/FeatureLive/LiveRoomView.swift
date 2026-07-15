import SwiftUI
import LiveKit
import CoreUI
import CoreMedia

/// Écran d'un live (viewer) : vidéo plein écran + overlays chat / cadeaux / présence.
///
/// Style TikTok : la vidéo occupe tout l'écran, les overlays flottent par-dessus. Le rendu
/// vidéo utilise `SwiftUIVideoView` (décodage matériel, zero-copy vers Metal).
public struct LiveRoomView: View {
    @Environment(\.dmTheme) private var theme
    @State private var viewModel: LiveViewModel
    /// Host du live (destinataire des cadeaux).
    private let hostUserId: String
    /// Cadeau de démonstration (sélection réelle via feature-gifts).
    private let quickGiftId: String
    /// Jeton LiveKit pré-chauffé par le feed (réduit la latence d'entrée).
    private let prewarmedToken: LiveKitToken?

    public init(viewModel: LiveViewModel, hostUserId: String, quickGiftId: String, prewarmedToken: LiveKitToken? = nil) {
        _viewModel = State(initialValue: viewModel)
        self.hostUserId = hostUserId
        self.quickGiftId = quickGiftId
        self.prewarmedToken = prewarmedToken
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            videoLayer
            gradientScrim
            giftBurstLayer
            overlays
        }
        .background(.black)
        .task { await viewModel.start(prewarmedToken: prewarmedToken) }
        .onDisappear { Task { await viewModel.stop() } }
    }

    // MARK: Vidéo

    @ViewBuilder private var videoLayer: some View {
        if let track = viewModel.media.primaryVideoTrack {
            SwiftUIVideoView(track, layoutMode: .fill)
                .ignoresSafeArea()
        } else {
            // Placeholder pendant la connexion (spinner sur fond héro).
            theme.gradients.hero.ignoresSafeArea()
            ProgressView().tint(.white)
        }
    }

    /// Couche des cadeaux animés (halo GPU Metal) : affiche le dernier cadeau reçu.
    @ViewBuilder private var giftBurstLayer: some View {
        if let gift = viewModel.giftFeed.last {
            GiftBurstView(symbol: "🎁", label: gift.giftName)
                .id(gift.id) // re-déclenche l'animation à chaque nouveau cadeau
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
    }

    /// Dégradé bas pour lisibilité des overlays sur la vidéo.
    private var gradientScrim: some View {
        LinearGradient(colors: [.clear, .black.opacity(0.6)], startPoint: .center, endPoint: .bottom)
            .ignoresSafeArea()
    }

    // MARK: Overlays

    private var overlays: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            HStack {
                Spacer()
                // Compteur de spectateurs (présence Socket.IO).
                Label("\(viewModel.viewerCount)", systemImage: "eye.fill")
                    .font(DMFont.caption)
                    .foregroundStyle(.white)
                    .padding(.horizontal, theme.spacing.md).padding(.vertical, theme.spacing.xs)
                    .background(.black.opacity(0.4), in: Capsule())
            }

            Spacer()

            chatOverlay
            actionBar
        }
        .padding(theme.spacing.md)
    }

    /// Overlay de chat : dernières lignes, lisibles sur la vidéo.
    private var chatOverlay: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(viewModel.messages.suffix(6)) { msg in
                HStack(alignment: .top, spacing: 6) {
                    Text(msg.authorName ?? "Fan").font(DMFont.caption).bold().foregroundStyle(theme.colors.accent)
                    Text(msg.content).font(DMFont.caption).foregroundStyle(.white)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Barre d'action : saisie de message + envoi de cadeau rapide.
    private var actionBar: some View {
        HStack(spacing: theme.spacing.sm) {
            TextField("Dis quelque chose…", text: $viewModel.draftMessage)
                .textFieldStyle(.plain)
                .padding(theme.spacing.sm)
                .background(.white.opacity(0.15), in: Capsule())
                .foregroundStyle(.white)
                .onSubmit { Task { await viewModel.sendMessage() } }

            Button {
                Task { await viewModel.sendGift(giftId: quickGiftId, toUserId: hostUserId) }
            } label: {
                Image(systemName: "gift.fill")
                    .foregroundStyle(.white)
                    .padding(theme.spacing.md)
                    .background(theme.gradients.primary, in: Circle())
            }
            .dmGlow()
        }
    }
}

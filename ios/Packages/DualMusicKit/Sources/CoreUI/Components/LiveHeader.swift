import SwiftUI
import DomainModels

/// En-tête de DIRECT partagé par les 4 types d'événement (live / duel / concert /
/// compétition) — mêmes icônes et même disposition que le web et Android (`LiveHeader.kt`) :
///  - à gauche : badge rouge (ex. « LIVE », « DUEL », « CONCERT ») + libellé de l'événement,
///    compteur de **spectateurs** (œil) et compteur de **likes** (cœur, masqué si 0) ;
///  - à droite : **partager**, **participants**, **signaler**, **fermer**/**quitter**.
///
/// Miroir de `LiveHeader` (Compose, `core/ui/live/LiveHeader.kt`).
public struct LiveHeader: View {
    @Environment(\.dmStrings) private var s

    private let eventLabel: String
    private let viewerCount: Int
    private let likes: Int
    private let shareText: String?
    private let onReport: (() -> Void)?
    private let onClose: (() -> Void)?
    private let onQuit: (() -> Void)?
    private let mediaLabel: String?
    private let micOn: Bool?
    private let camOn: Bool?
    private let onParticipants: (() -> Void)?
    private let badgeText: String
    private let showViewers: Bool

    /// - Parameters:
    ///   - eventLabel: libellé court affiché à côté du badge (ex. « DUEL », « CONCERT »).
    ///   - viewerCount: nombre de spectateurs en direct.
    ///   - likes: total de likes (pastille masquée si 0).
    ///   - shareText: texte à partager (`ShareLink` natif ; `nil` → bouton masqué).
    ///   - onReport: signalement (`nil` → bouton masqué).
    ///   - onClose: fermeture (hôte, `nil` → bouton masqué).
    ///   - onQuit: bouton QUITTER rouge (spectateur, `nil` → bouton masqué).
    ///   - mediaLabel: nom du diffuseur (optionnel, parité web).
    ///   - micOn: état micro affiché à côté de `mediaLabel` (optionnel).
    ///   - camOn: état caméra affiché à côté de `mediaLabel` (optionnel).
    ///   - onParticipants: bouton participants (optionnel, `nil` → masqué).
    ///   - badgeText: texte du badge rouge (« LIVE » par défaut).
    ///   - showViewers: affiche le compteur de spectateurs sur cette ligne.
    public init(
        eventLabel: String,
        viewerCount: Int,
        likes: Int,
        shareText: String? = nil,
        onReport: (() -> Void)? = nil,
        onClose: (() -> Void)? = nil,
        onQuit: (() -> Void)? = nil,
        mediaLabel: String? = nil,
        micOn: Bool? = nil,
        camOn: Bool? = nil,
        onParticipants: (() -> Void)? = nil,
        badgeText: String = "LIVE",
        showViewers: Bool = true
    ) {
        self.eventLabel = eventLabel
        self.viewerCount = viewerCount
        self.likes = likes
        self.shareText = shareText
        self.onReport = onReport
        self.onClose = onClose
        self.onQuit = onQuit
        self.mediaLabel = mediaLabel
        self.micOn = micOn
        self.camOn = camOn
        self.onParticipants = onParticipants
        self.badgeText = badgeText
        self.showViewers = showViewers
    }

    public var body: some View {
        HStack {
            HStack(spacing: 6) {
                // Badge rouge (point + texte).
                HStack(spacing: 5) {
                    Circle().fill(.white).frame(width: 7, height: 7)
                    Text(badgeText)
                        .font(.system(size: 11, weight: .black))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color(hex: 0xDC2626), in: RoundedRectangle(cornerRadius: 6, style: .continuous))

                if !eventLabel.trimmed.isEmpty {
                    Text(eventLabel)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.black.opacity(0.4), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }

                if showViewers {
                    statPill(systemImage: "eye.fill", text: Self.compact(viewerCount), tint: .white)
                }
                if likes > 0 {
                    statPill(systemImage: "heart.fill", text: Self.compact(likes), tint: Color(hex: 0xFF4D6D))
                }

                if let mediaLabel, !mediaLabel.trimmed.isEmpty {
                    HStack(spacing: 4) {
                        Text(mediaLabel)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                        if let micOn {
                            Image(systemName: micOn ? "mic.fill" : "mic.slash.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(micOn ? Color(hex: 0x22C55E) : Color(hex: 0xFF4D6D))
                        }
                        if let camOn {
                            Image(systemName: camOn ? "video.fill" : "video.slash.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(camOn ? Color(hex: 0x22C55E) : Color(hex: 0xFF4D6D))
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.black.opacity(0.4), in: Capsule())
                }
            }

            Spacer(minLength: 8)

            HStack(spacing: 6) {
                if let shareText {
                    ShareLink(item: shareText) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(.black.opacity(0.4), in: Circle())
                    }
                    .accessibilityLabel(Text(s.shareAction))
                }
                if let onParticipants {
                    iconPill(systemImage: "person.2.fill", label: s.participantsAction, action: onParticipants)
                }
                if let onReport {
                    iconPill(systemImage: "flag", label: s.reportAction, action: onReport)
                }
                if let onClose {
                    iconPill(systemImage: "xmark", label: s.closeAction, action: onClose)
                }
                if let onQuit {
                    iconPill(systemImage: "rectangle.portrait.and.arrow.right", label: s.quitLiveAction, bg: Color(hex: 0xDC2626), action: onQuit)
                }
            }
        }
    }

    private func statPill(systemImage: String, text: String, tint: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: systemImage).font(.system(size: 12)).foregroundStyle(tint)
            Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.black.opacity(0.4), in: Capsule())
    }

    private func iconPill(systemImage: String, label: String, bg: Color = .black.opacity(0.4), action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(bg, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }

    /// Formatage compact d'un compteur (1200 → « 1.2k »).
    private static func compact(_ n: Int) -> String {
        switch n {
        case 1_000_000...: return "\(n / 1_000_000).\((n % 1_000_000) / 100_000)M"
        case 1_000...: return "\(n / 1_000).\((n % 1_000) / 100)k"
        default: return "\(n)"
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack {
            LiveHeader(
                eventLabel: "DUEL",
                viewerCount: 1240,
                likes: 58,
                shareText: "Regarde ce direct !",
                onReport: {},
                onQuit: {},
                badgeText: "DUEL"
            )
            Spacer()
        }
        .padding()
    }
    .dualMusicTheme(.dark)
}

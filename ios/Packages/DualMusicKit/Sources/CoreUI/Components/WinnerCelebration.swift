import SwiftUI
import AVFoundation
import DomainModels

/// Repli quand aucun son n'est configuré côté admin — même piste que Android
/// (`WinnerCelebration.kt` : `"https://assets.mixkit.co/active_storage/sfx/2010/2010-preview.mp3"`).
private let defaultWinnerSoundURL = "https://assets.mixkit.co/active_storage/sfx/2010/2010-preview.mp3"

/// Overlay de **célébration du vainqueur** — lueur radiale pulsante en fond, confettis qui
/// tombent, carte centrale avec couronne rebondissante, avatar en bague dorée (+ pastille
/// trophée), « 🎉 Titre 🎉 », nom en doré et sous-titre optionnel en pastille — le tout sur un
/// son en boucle (téléversé par l'admin, réglage public `winner_sound_url` ; repli sur un son
/// par défaut si non configuré).
///
/// Miroir de `WinnerCelebration`/`ConfettiLayer`/`WinnerCard` (Compose,
/// `core/ui/celebration/WinnerCelebration.kt`).
///
/// ```swift
/// WinnerCelebration(winnerName: winner.name, title: s.winnerTitle, avatarURL: winner.avatar, subtitle: s.winnerCongrats, soundURL: viewModel.winnerSoundUrl)
/// ```
public struct WinnerCelebration: View {
    private let winnerName: String
    private let title: String
    private let avatarURL: String?
    private let subtitle: String?
    private let soundURL: String?
    private let reduceAnimations: Bool

    @State private var queuePlayer: AVQueuePlayer?
    @State private var looper: AVPlayerLooper?

    /// - Parameters:
    ///   - winnerName: nom affiché du vainqueur.
    ///   - title: libellé (ex. « 🏆 Vainqueur »).
    ///   - avatarURL: URL de l'avatar (repli sur l'initiale si absente).
    ///   - subtitle: sous-titre optionnel (ex. « Félicitations ! »).
    ///   - soundURL: son personnalisé téléversé par l'admin (`nil`/vide → son par défaut).
    ///   - reduceAnimations: masque lueur + confettis + le rebond (préférence accessibilité).
    public init(
        winnerName: String,
        title: String,
        avatarURL: String? = nil,
        subtitle: String? = nil,
        soundURL: String? = nil,
        reduceAnimations: Bool = false
    ) {
        self.winnerName = winnerName
        self.title = title
        self.avatarURL = avatarURL
        self.subtitle = subtitle
        self.soundURL = soundURL
        self.reduceAnimations = reduceAnimations
    }

    public var body: some View {
        ZStack {
            if !reduceAnimations {
                PulsingGlow()
                ConfettiLayer()
            }
            WinnerCard(winnerName: winnerName, title: title, avatarURL: avatarURL, subtitle: subtitle, animate: !reduceAnimations)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(false)
        .onAppear { playSound() }
        .onDisappear { stopSound() }
    }

    /// Joue le son en boucle native (`AVPlayerLooper`, pas de bricolage à base de notification)
    /// tant que cette vue reste affichée — le manager qui arrête l'annonce, ou la sortie de la
    /// room, démonte cette vue et coupe le son via ``stopSound()``.
    private func playSound() {
        let urlString = soundURL?.nilIfBlank ?? defaultWinnerSoundURL
        guard let url = URL(string: urlString) else { return }
        let item = AVPlayerItem(url: url)
        let player = AVQueuePlayer()
        looper = AVPlayerLooper(player: player, templateItem: item)
        queuePlayer = player
        player.play()
    }

    private func stopSound() {
        looper?.disableLooping()
        queuePlayer?.pause()
        looper = nil
        queuePlayer = nil
    }
}

/// Lueur radiale douce qui pulse en fond.
private struct PulsingGlow: View {
    @State private var pulsing = false

    var body: some View {
        RadialGradient(
            colors: [Color(hex: 0xA63DDB, alpha: pulsing ? 0.5 : 0.2), .clear],
            center: .center,
            startRadius: 0,
            endRadius: 260
        )
        .scaleEffect(pulsing ? 1.3 : 1.0)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) { pulsing = true }
        }
    }
}

/// Un confetti individuel : chute continue avec oscillation latérale + rotation, s'estompe
/// après ~4,8s.
private struct Confetto: Identifiable {
    let id: Int
    let x: CGFloat
    let color: Color
    let size: CGFloat
    let phase: Double
    let drift: CGFloat
    let rotation: Double
}

private let confettiColors: [Color] = [
    Color(hex: 0xA63DDB), Color(hex: 0x3DA5FF), Color(hex: 0xFFD23D), Color(hex: 0xFF4D8D), Color(hex: 0x3DE0A0),
]

/// Couche de confettis animés (miroir de `ConfettiLayer` Compose : 90 pièces, chute en boucle
/// 2,2s, durée de vie totale ~6s avant fondu).
private struct ConfettiLayer: View {
    private let confetti: [Confetto] = (0..<90).map { i in
        var rng = SeededRandom(seed: i)
        return Confetto(
            id: i,
            x: rng.nextUnit(),
            color: confettiColors[i % confettiColors.count],
            size: 8 + rng.nextUnit() * 10,
            phase: Double(rng.nextUnit()),
            drift: rng.nextUnit(),
            rotation: Double(rng.nextUnit()) * 360
        )
    }

    @State private var visible = true

    var body: some View {
        TimelineView(.animation) { timeline in
            GeometryReader { proxy in
                ForEach(confetti) { c in
                    ConfettiPiece(confetto: c, referenceDate: timeline.date, canvasSize: proxy.size)
                }
            }
        }
        .opacity(visible ? 1 : 0)
        .animation(.easeOut(duration: 1.2), value: visible)
        .task {
            try? await Task.sleep(nanoseconds: 4_800_000_000)
            visible = false
        }
    }
}

/// Un confetti positionné pour l'instant courant — extrait en vue distincte pour que le
/// vérificateur de types n'ait plus à résoudre une seule énorme expression combinée
/// (`TimelineView` + `GeometryReader` + `ForEach` + calculs trigonométriques inline avait
/// dépassé le délai de vérification de types de Swift en CI).
private struct ConfettiPiece: View {
    let confetto: Confetto
    let referenceDate: Date
    let canvasSize: CGSize

    var body: some View {
        // Tout en `Double` (même type que `Confetto.phase`/`.rotation`) — pas de conversion
        // implicite CGFloat/Double en Swift, mélanger les deux ici avait provoqué le délai de
        // vérification de types dépassé en CI. Conversion vers `CGFloat` uniquement en sortie,
        // aux points d'appel SwiftUI qui l'exigent.
        let elapsed: Double = referenceDate.timeIntervalSinceReferenceDate
        let t: Double = elapsed.truncatingRemainder(dividingBy: 2.2) / 2.2
        let p: Double = (t + confetto.phase).truncatingRemainder(dividingBy: 1)
        let canvasWidth: Double = Double(canvasSize.width)
        let canvasHeight: Double = Double(canvasSize.height)
        let y: Double = p * (canvasHeight + 40) - 20
        let driftPhase: Double = (p + confetto.phase) * 2 * Double.pi * 2
        let x: Double = Double(confetto.x) * canvasWidth + sin(driftPhase) * Double(confetto.drift) * 36
        let rotationDegrees: Double = confetto.rotation + p * 540
        Rectangle()
            .fill(confetto.color)
            .frame(width: confetto.size, height: confetto.size * 1.6)
            .rotationEffect(.degrees(rotationDegrees))
            .position(x: CGFloat(x), y: CGFloat(y))
    }
}

/// Générateur pseudo-aléatoire déterministe (même graine → mêmes confettis à chaque rendu,
/// évite un `ForEach` instable avec `.random(in:)` ré-évalué à chaque frame).
private struct SeededRandom {
    private var state: UInt64
    init(seed: Int) { state = UInt64(bitPattern: Int64(seed * 2654435761 &+ 1)) | 1 }
    mutating func nextUnit() -> CGFloat {
        state = state &* 6364136223846793005 &+ 1
        return CGFloat((state >> 33) & 0xFFFFFF) / CGFloat(0xFFFFFF)
    }
}

/// Carte centrale : couronne rebondissante, avatar en bague dorée + pastille trophée, titre,
/// nom en doré, sous-titre en pastille.
private struct WinnerCard: View {
    let winnerName: String
    let title: String
    let avatarURL: String?
    let subtitle: String?
    let animate: Bool

    @State private var shown = false
    @State private var crownBounce = false

    var body: some View {
        VStack(spacing: 6) {
            Text("👑")
                .font(.system(size: 44))
                .offset(y: crownBounce ? -6 : 0)

            ZStack(alignment: .topTrailing) {
                if let avatarURL, !avatarURL.isEmpty {
                    DMRemoteImage(url: avatarURL, fallback: "🏆")
                        .frame(width: 92, height: 92)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color(hex: 0xFFD23D), lineWidth: 4))
                } else {
                    Circle()
                        .fill(LinearGradient(colors: [Color(hex: 0xFFD23D), Color(hex: 0xFFA53D)], startPoint: .top, endPoint: .bottom))
                        .frame(width: 92, height: 92)
                        .overlay(Circle().stroke(Color(hex: 0xFFD23D), lineWidth: 4))
                        .overlay(
                            Text(winnerName.prefix(1).uppercased())
                                .font(.system(size: 32, weight: .black))
                                .foregroundStyle(.white)
                        )
                }
                Text("🏆").font(.system(size: 26)).offset(x: 8, y: -8)
            }

            Text("🎉 \(title) 🎉")
                .font(.system(size: 18, weight: .black))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            Text(winnerName)
                .font(.system(size: 26, weight: .black))
                .foregroundStyle(Color(hex: 0xFFD23D))
                .multilineTextAlignment(.center)

            if let subtitle {
                Text(subtitle)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color(hex: 0xFFD23D))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.08), in: Capsule())
                    .overlay(Capsule().stroke(Color(hex: 0xFFD23D, alpha: 0.4), lineWidth: 1))
            }
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 22)
        .background(Color.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .scaleEffect(shown || !animate ? 1 : 0.6)
        .onAppear {
            withAnimation(animate ? .spring(response: 0.5, dampingFraction: 0.65) : .linear(duration: 0)) { shown = true }
            if animate {
                withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { crownBounce = true }
            }
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        WinnerCelebration(winnerName: "Alex", title: "🏆 Vainqueur", subtitle: "Félicitations !")
    }
}

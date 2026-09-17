import SwiftUI
import AVKit
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Corps de `POST /sponsors/ads/play` — clés déjà en camelCase côté backend (parité body Kotlin).
private struct SponsorAdPlayBody: Encodable, Sendable {
    let eventType: String
    let eventId: String
    let adVideoId: String
}

/// Accès REST à la diffusion de pubs sponsor pendant un événement (parité web/backend).
///
/// L'autorisation (seul l'hôte/manager/admin peut lancer) est imposée côté serveur.
public struct SponsorAdRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Pubs actives éligibles pour l'événement `eventType:eventId`.
    public func listAds(eventType: String, eventId: String) async -> [SponsorAdVideo] {
        (try? await http.request(
            .get(SponsorEndpoints.ads, query: ["eventType": eventType, "eventId": eventId]),
            as: [SponsorAdVideo].self
        )) ?? []
    }

    /// Lance la diffusion d'une pub. Renvoie l'enregistrement de diffusion (pour l'arrêter).
    public func play(eventType: String, eventId: String, adVideoId: String) async throws -> SponsorAdPlay {
        try await http.request(
            .post(SponsorEndpoints.adsPlay, body: SponsorAdPlayBody(eventType: eventType, eventId: eventId, adVideoId: adVideoId)),
            as: SponsorAdPlay.self
        )
    }

    /// Arrête une diffusion en cours.
    public func stop(playId: String) async throws {
        try await http.send(.post(SponsorEndpoints.adsStop(playId)))
    }
}

/// État + actions de diffusion de pub sponsor pour une room d'événement, réutilisable par les
/// ViewModels live/duel/concert/compétition (comme ``RecordingHolder``).
///
/// Le VM se contente de : (1) créer un holder, (2) relayer l'event temps réel `sponsor:ad` via
/// ``onEvent(_:)`` depuis son listener `/live` (déjà connecté et joint à la room), (3) exposer
/// ce holder à l'écran. La pub active est pilotée par le serveur (start/stop) → tous les
/// spectateurs se synchronisent.
@Observable
@MainActor
public final class SponsorAdHolder {

    /// Pub actuellement diffusée (overlay vidéo), ou `nil`.
    public private(set) var activeAd: SponsorAdVideo?
    /// Catalogue des pubs éligibles (chargé à la demande par l'hôte).
    public private(set) var ads: [SponsorAdVideo] = []
    /// Verrou anti double-tap pendant un play/stop.
    public private(set) var busy = false

    private var playId: String?
    private let eventType: String
    private let eventId: String
    private let repo: SponsorAdRepository

    /// - Parameters:
    ///   - eventType: `live` | `duel` | `concert` | `competition`.
    ///   - eventId: identifiant de l'événement.
    ///   - repo: accès REST (liste + play/stop).
    public init(eventType: String, eventId: String, repo: SponsorAdRepository) {
        self.eventType = eventType
        self.eventId = eventId
        self.repo = repo
    }

    /// À appeler depuis le listener temps réel `sponsor:ad` du VM.
    public func onEvent(_ p: SponsorAdPayload) {
        if p.action == "start" {
            activeAd = p.ad
            playId = p.playId
        } else {
            activeAd = nil
            playId = nil
        }
    }

    /// Charge les pubs éligibles (hôte : avant d'ouvrir le sélecteur).
    public func loadAds() {
        Task { ads = await repo.listAds(eventType: eventType, eventId: eventId) }
    }

    /// Lance la diffusion d'une pub (hôte). L'état sera confirmé par l'event serveur.
    public func play(adVideoId: String) {
        Task {
            busy = true
            if let play = try? await repo.play(eventType: eventType, eventId: eventId, adVideoId: adVideoId) {
                activeAd = ads.first { $0.id == adVideoId }
                playId = play.id
            }
            busy = false
        }
    }

    /// Arrête la diffusion en cours (hôte).
    public func stop() {
        let id = playId
        Task {
            busy = true
            if let id { try? await repo.stop(playId: id) }
            activeAd = nil
            playId = nil
            busy = false
        }
    }
}

/// Couche complète de diffusion pub sponsor à déposer dans le `ZStack` d'une room : le
/// **contrôle hôte** (bas-gauche) si `canTrigger`, et l'**overlay vidéo plein écran** (au-dessus
/// de tout) pour tous les spectateurs quand une pub est active.
public struct SponsorAdLayer: View {
    private let activeAd: SponsorAdVideo?
    private let canTrigger: Bool
    private let ads: [SponsorAdVideo]
    private let busy: Bool
    private let onLoadAds: () -> Void
    private let onPlay: (String) -> Void
    private let onStop: () -> Void
    /// Quand `false`, le bouton intégré « Démarrer pub » n'est pas rendu (déclenché ailleurs,
    /// ex. un rail). L'overlay vidéo + l'arrêt (pour l'hôte) restent actifs.
    private let showTriggerButton: Bool

    public init(
        activeAd: SponsorAdVideo?,
        canTrigger: Bool,
        ads: [SponsorAdVideo],
        busy: Bool,
        onLoadAds: @escaping () -> Void,
        onPlay: @escaping (String) -> Void,
        onStop: @escaping () -> Void,
        showTriggerButton: Bool = true
    ) {
        self.activeAd = activeAd
        self.canTrigger = canTrigger
        self.ads = ads
        self.busy = busy
        self.onLoadAds = onLoadAds
        self.onPlay = onPlay
        self.onStop = onStop
        self.showTriggerButton = showTriggerButton
    }

    public var body: some View {
        ZStack {
            if canTrigger && showTriggerButton {
                VStack {
                    Spacer()
                    HStack {
                        SponsorAdControl(active: activeAd != nil, ads: ads, busy: busy, onLoadAds: onLoadAds, onPlay: onPlay, onStop: onStop)
                        Spacer()
                    }
                }
                .padding(12)
            }

            if let ad = activeAd {
                SponsorAdOverlay(ad: ad, canStop: canTrigger, busy: busy, onStop: onStop, onEnded: { if canTrigger { onStop() } })
            }
        }
    }
}

/// Contrôle hôte : « Démarrer pub » (sélecteur) ou « Arrêter » selon l'état.
private struct SponsorAdControl: View {
    @Environment(\.dmStrings) private var s
    let active: Bool
    let ads: [SponsorAdVideo]
    let busy: Bool
    let onLoadAds: () -> Void
    let onPlay: (String) -> Void
    let onStop: () -> Void

    @State private var showPicker = false

    var body: some View {
        if active {
            DMButton(s.sponsorStopAd, style: .destructive, isEnabled: !busy, action: onStop)
        } else {
            DMButton(s.sponsorStartAd, style: .secondary, isEnabled: !busy) {
                onLoadAds()
                showPicker = true
            }
            .confirmationDialog(s.sponsorStartAd, isPresented: $showPicker, titleVisibility: .visible) {
                if ads.isEmpty {
                    Button(s.sponsorNoAds) {}.disabled(true)
                } else {
                    ForEach(ads) { ad in
                        Button("\(ad.title) · \(ad.durationSeconds)s") { onPlay(ad.id) }
                    }
                }
            }
        }
    }
}

/// Overlay vidéo plein écran + bannière « PUBLICITÉ » (+ bouton stop pour l'hôte).
private struct SponsorAdOverlay: View {
    @Environment(\.dmStrings) private var s
    let ad: SponsorAdVideo
    let canStop: Bool
    let busy: Bool
    let onStop: () -> Void
    let onEnded: () -> Void

    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
            VStack {
                Spacer()
                Text("📢 \(s.sponsorAdBadge)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 0.23, green: 0.18, blue: 0))
                    .padding(.horizontal, 14).padding(.vertical, 6)
                    .background(Color(red: 0.96, green: 0.62, blue: 0.04), in: Capsule())
                    .padding(.bottom, 28)
            }
            if canStop {
                VStack {
                    HStack {
                        Spacer()
                        DMButton(s.sponsorStopAd, style: .destructive, isEnabled: !busy, action: onStop)
                    }
                    Spacer()
                }
                .padding(12)
            }
        }
        .task(id: ad.id) {
            let item = AVPlayerItem(url: URL(string: ad.videoUrl) ?? URL(fileURLWithPath: "/dev/null"))
            let p = AVPlayer(playerItem: item)
            player = p
            p.play()
            for await _ in NotificationCenter.default.notifications(named: .AVPlayerItemDidPlayToEndTime, object: item) {
                onEnded()
                break
            }
        }
    }
}

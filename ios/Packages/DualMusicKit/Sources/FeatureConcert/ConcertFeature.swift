import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels
import FeatureGiftShop
import FeatureWallet

/// Accès REST aux concerts d'artistes.
///
/// Lectures (catalogue, détail, billetterie) et achat de dédicace. L'achat du **billet**
/// est un débit du portefeuille (`POST /wallet/tickets/concert`) — voir `FeatureWallet`.
public struct ConcertRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// Catalogue public des concerts d'artistes (approuvés).
    public func concerts(limit: Int = 50) async throws -> [Concert] {
        try await http.request(
            .get(ConcertEndpoints.artistList, query: ["limit": String(limit)]),
            as: [Concert].self
        )
    }

    /// Détail d'un concert.
    public func concert(id: String) async throws -> Concert {
        try await http.request(.get(ConcertEndpoints.artistDetail(id)), as: Concert.self)
    }

    /// Concerts admin (`GET /concerts`) — fusionnés avec le catalogue artiste (comme le web).
    /// Best-effort : un utilisateur non-admin reçoit un 403, traité comme une liste vide.
    public func adminConcerts(limit: Int = 100) async -> [Concert] {
        (try? await http.request(.get("/concerts", query: ["limit": String(limit)]), as: [Concert].self)) ?? []
    }

    /// Replays publics de concerts (`GET /replays?sourceType=concert&isPublic=true`).
    public func concertReplays() async -> [ReplayVideo] {
        (try? await http.request(
            .get(ReplayEndpoints.list, query: ["sourceType": "concert", "isPublic": "true", "limit": "100"]),
            as: [ReplayVideo].self
        )) ?? []
    }

    /// Concerts en attente d'approbation (admin) : `GET /artist-concerts?approvalStatus=pending`.
    public func pendingConcerts() async -> [Concert] {
        (try? await http.request(
            .get(ConcertEndpoints.artistList, query: ["approvalStatus": "pending", "limit": "100"]),
            as: [Concert].self
        )) ?? []
    }

    /// Approuve/rejette un concert artiste (admin) : `POST /artist-concerts/:id/review`.
    public func reviewConcert(id: String, approve: Bool) async throws {
        try await http.send(.post(ConcertEndpoints.artistDetail(id) + "/review", body: ConcertReviewBody(approve: approve)))
    }

    /// Billetterie : prix, places restantes, et si le caller a déjà son billet.
    public func ticketInfo(id: String) async throws -> ConcertTicketInfo {
        try await http.request(.get(ConcertEndpoints.ticketInfo(id)), as: ConcertTicketInfo.self)
    }

    /// Achète une dédicace pour un concert (débit atomique + idempotent).
    /// - Parameters:
    ///   - concertId: concert ciblé.
    ///   - message: texte lu par l'artiste pendant le concert.
    ///   - priceCredits: montant en crédits (obligatoire côté backend).
    ///   - idempotencyKey: clé unique de l'action.
    public func purchaseDedication(
        concertId: String,
        message: String,
        priceCredits: Double,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                ConcertEndpoints.dedicationsPurchase,
                body: DedicationRequest(concertId: concertId, message: message, priceCredits: priceCredits),
                idempotencyKey: idempotencyKey
            )
        )
    }

    /// Signale ce concert à la modération.
    ///
    /// ⚠️ Couche données seulement : il n'existe pas encore d'écran de room/direct pour les
    /// concerts côté iOS (voir `TODO-IOS.md`) — rien n'appelle cette méthode pour l'instant.
    public func reportLive(liveId: String, reason: ReportReason) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.reportsLive,
                body: ReportStreamBody(liveId: liveId, streamType: "concert", reason: reason.rawValue)
            )
        )
    }

    /// Bannit un spectateur (artiste uniquement) : il ne peut plus écrire ni rejoindre.
    public func createStreamBan(streamId: String, bannedUserId: String, reason: String?) async throws {
        try await http.send(
            .post(
                ModerationReportEndpoints.streamBans,
                body: StreamBanBody(streamId: streamId, streamType: "concert", bannedUserId: bannedUserId, reason: reason)
            )
        )
    }

    /// Spectateurs déjà bannis de ce concert (ids) — amorce l'affichage pour un arrivant
    /// tardif. Best-effort : une erreur réseau donne juste une liste vide.
    public func listStreamBans(concertId: String) async -> [String] {
        let rows = (try? await http.request(
            .get(ModerationReportEndpoints.streamBans, query: ["streamId": concertId, "streamType": "concert"]),
            as: [StreamBanRow].self
        )) ?? []
        return rows.compactMap(\.bannedUserId)
    }

    /// Historique de chat (dernière page) pour amorcer l'overlay.
    public func chatHistory(concertId: String) async throws -> [ConcertChatMessage] {
        try await http.request(
            .get(ConcertEndpoints.messages(concertId), query: ["limit": "50"]),
            as: [ConcertChatMessage].self
        )
    }

    /// Poste un message (le backend le diffuse ensuite via Socket.IO).
    public func postMessage(concertId: String, content: String) async throws {
        try await http.send(.post(ConcertEndpoints.messages(concertId), body: ConcertMessageBody(message: content)))
    }

    /// Envoie un cadeau à l'artiste dans le contexte du concert.
    public func sendGift(concertId: String, giftId: String, toUserId: String) async throws {
        try await http.send(
            .post(
                WalletEndpoints.giftsSend,
                body: SendGiftRequest(giftId: giftId, toUserId: toUserId, concertId: concertId),
                idempotencyKey: "gift-\(concertId)-\(giftId)-\(toUserId)-\(UUID().uuidString)"
            )
        )
    }

    /// Achète le billet spectateur (débit atomique + idempotent) — requis pour regarder un
    /// concert payant, sauf l'artiste lui-même.
    public func buyTicket(concertId: String, idempotencyKey: String = UUID().uuidString) async throws {
        try await http.send(
            .post(WalletEndpoints.ticketConcert, body: BuyConcertTicketBody(concertId: concertId), idempotencyKey: idempotencyKey)
        )
    }

    /// Artiste : démarre la diffusion côté backend (`status: live`) — appelé APRÈS que la
    /// publication caméra/micro a réellement réussi, jamais avant.
    public func goLive(concertId: String) async throws {
        try await http.send(.patch(ConcertEndpoints.artistDetail(concertId), body: ConcertStatusBody(status: "live")))
    }

    /// Artiste : termine le concert côté backend (`status: ended`).
    public func endConcert(concertId: String) async throws {
        try await http.send(.patch(ConcertEndpoints.artistDetail(concertId), body: ConcertStatusBody(status: "ended")))
    }

    /// Spectateurs actuellement connectés (artiste uniquement — vivier du picker).
    public func listCurrentViewers(concertId: String) async throws -> [DisplayProfile] {
        try await http.request(.get(ModerationEndpoints.viewers("concert", concertId)), as: [DisplayProfile].self)
    }

    /// Modérateurs désignés de ce concert (artiste + jusqu'à ``maxEventModerators``
    /// spectateurs).
    public func listEventModerators(concertId: String) async throws -> [EventModerator] {
        try await http.request(.get(ModerationEndpoints.moderators("concert", concertId)), as: [EventModerator].self)
    }

    /// Artiste : désigne un spectateur modérateur.
    public func appointModerator(concertId: String, userId: String) async throws {
        try await http.send(.post(ModerationEndpoints.moderators("concert", concertId), body: AppointModeratorBody(userId: userId)))
    }

    /// Artiste : révoque un modérateur désigné.
    public func revokeModerator(concertId: String, userId: String) async throws {
        try await http.send(.delete(ModerationEndpoints.revokeModerator("concert", concertId, userId)))
    }

    /// Prix minimum d'une dédicace de CONCERT (`economic_config.dedication` — jamais
    /// `dedication_live`, propre à Live, pas de surcharge par concert côté backend).
    public func dedicationMinPrice() async throws -> Double {
        let setting = try await http.request(
            .get(RoleEndpoints.publicSetting("economic_config")),
            as: ConcertEconomicConfigSetting.self
        )
        return setting.value?.dedication?.minPriceCredits ?? 10
    }

    /// Artiste : dédicaces reçues sur TOUS ses évènements (concerts + lives) — l'appelant
    /// filtre par `concertId == self.concertId`.
    public func artistDedications() async throws -> [ConcertDedication] {
        try await http.request(.get(ConcertEndpoints.dedicationsArtistMine), as: [ConcertDedication].self)
    }

    /// Artiste : accepte une dédicace EN ATTENTE — débite le fan maintenant.
    public func acceptDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationAccept(id)))
    }

    /// Artiste : rejette une dédicace EN ATTENTE — aucun débit.
    public func rejectDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationReject(id)))
    }

    /// Artiste : marque une dédicace acceptée comme interprétée en direct.
    public func deliverDedication(id: String) async throws {
        try await http.send(.post(ConcertEndpoints.dedicationDeliver(id)))
    }

    /// Compteur de « j'aime » persistant (endpoint générique des lives, réutilisé tel quel).
    /// Best-effort : une erreur réseau donne juste `0`.
    public func likesCount(concertId: String) async -> Int {
        (try? await http.request(.get("/lives/\(concertId)/likes"), as: LikesResponse.self))?.likes ?? 0
    }

    /// Ajoute un « j'aime » persistant. Best-effort, jamais bloquant pour l'utilisateur.
    public func likeConcert(concertId: String) async {
        try? await http.send(.post("/lives/\(concertId)/likes"))
    }

    /// Classement des donateurs du concert (`GET /leaderboards/gifts?contextType=concert`).
    public func giftLeaderboard(concertId: String) async throws -> [ConcertDonorEntry] {
        try await http.request(
            .get(LeaderboardEndpoints.gifts, query: ["contextType": "concert", "contextId": concertId]),
            as: [ConcertDonorEntry].self
        )
    }

    /// Artiste : active/désactive le chat de ce concert (`PATCH /artist-concerts/:id`).
    public func setChatEnabled(concertId: String, enabled: Bool) async throws {
        try await http.send(.patch(ConcertEndpoints.artistDetail(concertId), body: ConcertChatEnabledBody(chatEnabled: enabled)))
    }

    /// Vrai si le caller est admin — toujours considéré comme acteur (exempté de billet) par
    /// ``ScheduledAccessGateView``, mais pas exempté de l'attente de l'heure programmée.
    public func amIAdmin() async -> Bool {
        (try? await http.request(.get(UserEndpoints.me), as: MeResponse.self))?.roles.contains(.admin) ?? false
    }
}

/// Entrée du classement des donateurs (`GET /leaderboards/gifts`).
public struct ConcertDonorEntry: Decodable, Sendable, Identifiable {
    public let id: String
    public let userId: String?
    public let fullName: String?
    public let stageName: String?
    public let total: Double
    public let score: Double
    public let user: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case fullName = "full_name"
        case stageName = "stage_name"
        case total, score, user
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.opt(String.self, .userId)
        fullName = c.opt(String.self, .fullName)
        stageName = c.opt(String.self, .stageName)
        total = c.amount(.total)
        score = c.amount(.score)
        user = c.opt(DisplayProfile.self, .user)
        id = userId ?? UUID().uuidString
    }

    /// Nom affiché : profil hydraté, sinon nom de scène, sinon nom complet, sinon repli générique.
    @MainActor
    public var displayName: String {
        if let user { return user.displayName }
        if let s = stageName, !s.isEmpty { return s }
        if let f = fullName, !f.isEmpty { return f }
        return AppStrings.current.donors
    }

    /// Valeur affichée (crédits) : `total` si présent, sinon `score`.
    public var value: Int { Int(total > 0 ? total : score) }
}

/// Corps de `PATCH /artist-concerts/:id` — bascule du chat uniquement.
struct ConcertChatEnabledBody: Encodable, Sendable {
    let chatEnabled: Bool
}

/// Réponse de `GET /lives/:id/likes`.
struct LikesResponse: Decodable, Sendable {
    let likes: Int
}

/// Corps de `POST /concerts/:id/messages`.
struct ConcertMessageBody: Encodable, Sendable {
    let message: String
}

/// Réglage public `economic_config` (`GET /settings/public/economic_config`) — seul le prix
/// minimum de dédicace de concert nous intéresse ici (pas de section `dedication_live`).
struct ConcertEconomicConfigSetting: Decodable, Sendable {
    let value: ConcertEconomicConfigValue?
}
struct ConcertEconomicConfigValue: Decodable, Sendable {
    let dedication: ConcertDedicationConfigSection?
}
struct ConcertDedicationConfigSection: Decodable, Sendable {
    let minPriceCredits: Double?
    enum CodingKeys: String, CodingKey { case minPriceCredits = "min_price_credits" }
}

/// Dédicace payante reçue par l'artiste (`concert_dedications`, `concert_type="artist_concert"`).
/// `status` : `pending` (à traiter) | `paid`/`delivered` (déjà acceptée, éventuellement
/// livrée) | `rejected`.
public struct ConcertDedication: Decodable, Sendable, Identifiable, Equatable {
    public let id: String
    public let fanId: String
    public let message: String
    public let priceCredits: Double
    public let status: String
    public let concertId: String?
    public let concertType: String?
    public let fan: DisplayProfile?

    enum CodingKeys: String, CodingKey {
        case id
        case fanId = "fan_id"
        case message
        case priceCredits = "price_credits"
        case status
        case concertId = "concert_id"
        case concertType = "concert_type"
        case fan
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        fanId = c.val(String.self, .fanId, "")
        message = c.val(String.self, .message, "")
        priceCredits = c.amount(.priceCredits)
        status = c.val(String.self, .status, "paid")
        concertId = c.opt(String.self, .concertId)
        concertType = c.opt(String.self, .concertType)
        fan = c.opt(DisplayProfile.self, .fan)
    }

    /// Nom d'affichage du fan (repli « Fan » comme sur Live).
    @MainActor
    public var fanName: String { fan?.displayName ?? AppStrings.current.fan }
}

/// Corps de `POST /wallet/tickets/concert`.
struct BuyConcertTicketBody: Encodable, Sendable {
    let concertId: String
}

/// Corps de `PATCH /artist-concerts/:id` — mise à jour de statut (`live`/`ended`).
struct ConcertStatusBody: Encodable, Sendable {
    let status: String
}

/// Corps de `POST /artist-concerts/:id/review` — décision admin.
struct ConcertReviewBody: Encodable, Sendable {
    let approve: Bool
}

/// Corps de `POST /moderation/reports/live` (live/duel/concert, distingués par `streamType`).
struct ReportStreamBody: Encodable, Sendable {
    let liveId: String
    let streamType: String
    let reason: String
}

/// Corps de `POST /moderation/stream-bans` (live/duel/concert, distingués par `streamType`).
struct StreamBanBody: Encodable, Sendable {
    let streamId: String
    let streamType: String
    let bannedUserId: String
    let reason: String?
}

/// ViewModel du catalogue de concerts (3 onglets, parité `ConcertsViewModel` Android) : fusionne
/// `GET /artist-concerts` (public) et `GET /concerts` (admin, best-effort) avant de répartir par
/// statut, charge les replays publics, et — si le caller est admin — la file d'approbation.
@Observable
@MainActor
public final class ConcertsViewModel {

    public private(set) var live: [Concert] = []
    public private(set) var upcoming: [Concert] = []
    public private(set) var replays: [ReplayVideo] = []
    public private(set) var isLoading = false
    /// Concerts artiste en attente d'approbation admin — vide si le caller n'est pas admin.
    public private(set) var pending: [Concert] = []
    public private(set) var isAdmin = false
    /// Contre-valeur € d'UN crédit (solde courant / sa contre-valeur €) — miroir de
    /// `ConcertsViewModel.perCreditEur` Android, affichée à côté du prix des cartes.
    public private(set) var perCreditEur: Double = 0

    private let repository: ConcertRepository
    private let wallet: WalletRepository

    /// - Parameters:
    ///   - repository: lectures REST des concerts d'artistes.
    ///   - wallet: solde courant, pour calculer la contre-valeur € affichée sur les prix.
    public init(repository: ConcertRepository, wallet: WalletRepository) {
        self.repository = repository
        self.wallet = wallet
    }

    /// Charge le catalogue (fusion admin + artiste, répartie par statut) + replays + file admin.
    public func load() async {
        guard !isLoading else { return }
        isLoading = true
        let admin = await repository.adminConcerts()
        let artist = (try? await repository.concerts(limit: 100)) ?? []
        var seen = Set<String>()
        let all = (admin + artist).filter { seen.insert($0.id).inserted }
        live = all.filter { $0.status == .live }
        upcoming = all.filter { $0.status == .upcoming }
        replays = await repository.concertReplays()
        isLoading = false

        if let balance = try? await wallet.balance(), balance.balance > 0 {
            perCreditEur = balance.eurValue / balance.balance
        }

        isAdmin = await repository.amIAdmin()
        if isAdmin { await loadPending() }
    }

    private func loadPending() async {
        pending = await repository.pendingConcerts()
    }

    /// Admin : approuve/rejette un concert artiste en attente, puis recharge tout.
    public func review(id: String, approve: Bool) async {
        try? await repository.reviewConcert(id: id, approve: approve)
        await loadPending()
        await load()
    }
}

/// Catalogue des concerts d'artistes (3 onglets + recherche + file d'approbation admin) —
/// miroir de `ConcertsListScreen` Android.
@MainActor
public struct ConcertsListView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ConcertsViewModel
    private let onOpen: (Concert) -> Void
    private let onOpenReplay: (ReplayVideo) -> Void
    private let onRequestSponsor: (String, String) -> Void

    @State private var tab = 0
    @State private var search = ""

    /// - Parameters:
    ///   - viewModel: source du catalogue.
    ///   - onOpen: callback à l'ouverture d'un concert (détail/billetterie).
    ///   - onOpenReplay: callback à l'ouverture d'un replay de concert.
    ///   - onRequestSponsor: ouvre le sponsoring présélectionné sur ce concert (`"artist_concert"`, id).
    public init(
        viewModel: ConcertsViewModel,
        onOpen: @escaping (Concert) -> Void = { _ in },
        onOpenReplay: @escaping (ReplayVideo) -> Void = { _ in },
        onRequestSponsor: @escaping (String, String) -> Void = { _, _ in }
    ) {
        self.viewModel = viewModel
        self.onOpen = onOpen
        self.onOpenReplay = onOpenReplay
        self.onRequestSponsor = onRequestSponsor
    }

    private var query: String { search.trimmed.lowercased() }

    private func matches(_ concert: Concert) -> Bool {
        query.isEmpty
            || concert.title.lowercased().contains(query)
            || (concert.artist?.displayName.lowercased().contains(query) ?? false)
            || (concert.artistName?.lowercased().contains(query) ?? false)
    }

    private func matches(_ replay: ReplayVideo) -> Bool {
        query.isEmpty || (replay.title?.lowercased().contains(query) ?? false)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.md) {
                Text(s.screenConcerts)
                    .font(DMFont.pageTitle)
                    .foregroundStyle(theme.colors.foreground)
                    .frame(maxWidth: .infinity)

                DMTextField(s.searchPlaceholder, text: $search)

                if viewModel.isAdmin && !viewModel.pending.isEmpty {
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        Text("⏳ \(s.pendingApproval)").font(DMFont.body).bold().foregroundStyle(theme.colors.accent)
                        ForEach(viewModel.pending) { concert in
                            PendingConcertRow(
                                concert: concert,
                                onApprove: { Task { await viewModel.review(id: concert.id, approve: true) } },
                                onReject: { Task { await viewModel.review(id: concert.id, approve: false) } }
                            )
                        }
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: theme.spacing.xs) {
                        tabPill("\(s.statusLiveNow) (\(viewModel.live.count))", tab == 0) { tab = 0 }
                        tabPill("\(s.statusUpcoming) (\(viewModel.upcoming.count))", tab == 1) { tab = 1 }
                        tabPill("\(s.menuReplays) (\(viewModel.replays.count))", tab == 2) { tab = 2 }
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
                DMEmptyState(title: s.noConcertsScheduled, subtitle: s.noConcertsScheduledHint, systemImage: "calendar")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { concert in
                        ConcertRow(concert: concert, perCreditEur: viewModel.perCreditEur, onOpen: { onOpen(concert) }, onRequestSponsor: { onRequestSponsor("artist_concert", concert.id) })
                    }
                }
            }
        case 1:
            let filtered = viewModel.upcoming.filter(matches)
            if filtered.isEmpty {
                DMEmptyState(title: s.noConcertsScheduled, subtitle: s.noConcertsScheduledHint, systemImage: "calendar")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { concert in
                        ConcertRow(concert: concert, perCreditEur: viewModel.perCreditEur, onOpen: { onOpen(concert) }, onRequestSponsor: { onRequestSponsor("artist_concert", concert.id) })
                    }
                }
            }
        default:
            let filtered = viewModel.replays.filter(matches)
            if filtered.isEmpty {
                DMEmptyState(title: s.noReplays, subtitle: s.noReplaysHint, systemImage: "play.rectangle")
            } else {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(filtered) { replay in
                        Button { onOpenReplay(replay) } label: { ConcertReplayRow(replay: replay) }.buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func tabPill(_ text: String, _ selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(DMFont.caption).bold()
                .foregroundStyle(selected ? theme.colors.primaryForeground : theme.colors.foreground)
                .padding(.horizontal, theme.spacing.md)
                .padding(.vertical, theme.spacing.sm)
                .background(selected ? theme.colors.accent : Color.black.opacity(0.1), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Ligne d'un concert artiste en attente d'approbation admin.
private struct PendingConcertRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let concert: Concert
    let onApprove: () -> Void
    let onReject: () -> Void

    var body: some View {
        DMCard {
            HStack {
                Text(concert.title).font(DMFont.body).foregroundStyle(theme.colors.foreground)
                Spacer()
                Button(s.accept, action: onApprove).font(DMFont.caption).bold().foregroundStyle(theme.colors.primary)
                Button(s.rejectAction, action: onReject).font(DMFont.caption).bold().foregroundStyle(theme.colors.destructive)
            }
        }
    }
}

/// Ligne d'un replay de concert : couverture 16:9 + badge lecture, titre, date. Miroir de
/// `ConcertReplayCard` (`ConcertsScreen.kt:402-427`).
private struct ConcertReplayRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let replay: ReplayVideo

    var body: some View {
        DMCard(padded: false) {
            VStack(alignment: .leading, spacing: 0) {
                DMRemoteImage(url: replay.thumbnailURL, fallback: "🎬")
                    .frame(maxWidth: .infinity)
                    .frame(height: 180)
                    .overlay { Text("▶").font(.system(size: 40)).foregroundStyle(.white) }
                    .overlay(alignment: .topTrailing) {
                        if replay.videoURL != nil {
                            DMBadgePill("▶ \(s.replayAvailable)", foreground: .white, background: Color(hex: 0x10B981, alpha: 0.8))
                                .padding(theme.spacing.sm)
                        }
                    }
                    .clipped()

                VStack(alignment: .leading, spacing: 4) {
                    Text(replay.title ?? s.screenConcerts).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                    if let date = isoDay(replay.recordedDate) {
                        Text("📅 \(date)").font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                .padding(theme.spacing.md)
            }
        }
    }
}

/// Carte d'un concert : couverture (badge « en direct » si live), badge prix (+ contre-valeur
/// €) /gratuit, titre, date, dédicaces, bouton « Sponsoriser » contextuel. Miroir de
/// `ConcertCard` (`ConcertsScreen.kt:253-333`).
private struct ConcertRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let concert: Concert
    /// Contre-valeur € d'UN crédit (0 = inconnue → pastille sans équivalent €).
    let perCreditEur: Double
    let onOpen: () -> Void
    let onRequestSponsor: () -> Void

    var body: some View {
        DMCard(padded: false) {
            VStack(alignment: .leading, spacing: 0) {
                DMRemoteImage(url: concert.cover, fallback: "🎵")
                    .frame(maxWidth: .infinity)
                    .frame(height: 180)
                    .overlay {
                        if concert.status == .live { Text("▶").font(.system(size: 40)).foregroundStyle(.white) }
                    }
                    .overlay(alignment: .topLeading) {
                        if concert.status == .live {
                            DMBadgePill("🔴 \(s.liveBadge)", foreground: .white, background: Color(hex: 0xEF4444), bold: true)
                                .padding(theme.spacing.sm)
                        }
                    }
                    .clipped()

                VStack(alignment: .leading, spacing: theme.spacing.sm) {
                    Button(action: onOpen) {
                        VStack(alignment: .leading, spacing: theme.spacing.sm) {
                            if concert.ticketPrice > 0 {
                                let eur = perCreditEur > 0 ? " \(formatEuro(concert.ticketPrice * perCreditEur))" : ""
                                DMBadgePill("🪙 \(formatCredits(concert.ticketPrice))\(eur)", foreground: theme.colors.foreground, background: Color.black.opacity(0.4))
                            } else {
                                DMBadgePill("🎁 \(s.freeLabel)", foreground: Color(hex: 0x10B981), background: Color(hex: 0x10B981, alpha: 0.2))
                            }
                            Text(concert.title)
                                .font(DMFont.body).bold()
                                .foregroundStyle(theme.colors.foreground)
                            if let date = isoMinute(concert.scheduledDate) {
                                Text("📅 \(date)").font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                            }
                            if concert.allowsDedications {
                                Text(s.dedicationsOpen).font(DMFont.caption).foregroundStyle(theme.colors.accent)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)

                    if concert.isArtistConcert && concert.allowsSponsorAds && !isDeadlinePassed(concert.sponsorSubmissionDeadline) {
                        DMButton(s.requestSponsorBtn, style: .outline, action: onRequestSponsor)
                    }
                }
                .padding(theme.spacing.md)
            }
        }
    }
}

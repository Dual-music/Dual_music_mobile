import SwiftUI
import PhotosUI
import Observation
import CoreNetwork
import CoreUI
import CoreUpload
import DomainModels

/// Événement sponsorisable (fusion des catalogues duels / concerts / compétitions).
public struct SponsorableEvent: Identifiable, Sendable, Equatable {
    /// `duel` | `artist_concert` | `competition` (valeurs attendues par le backend).
    public let type: String
    /// Identifiant de l'événement.
    public let eventId: String
    /// Libellé affiché dans la liste de sélection.
    public let label: String

    /// Identité composite : deux catalogues peuvent partager un même id.
    public var id: String { "\(type):\(eventId)" }
}

/// ViewModel du sponsoring : paliers, mes demandes, paiement, et création (média + événement).
@Observable
@MainActor
public final class SponsorViewModel {

    public private(set) var tiers: [SponsorTier] = []
    public private(set) var requests: [SponsorRequest] = []
    public private(set) var events: [SponsorableEvent] = []

    // Brouillon de création.
    public private(set) var mediaURL: String?
    public private(set) var mediaType: String?
    public private(set) var mediaDurationSeconds = 0
    public private(set) var isUploadingMedia = false
    public private(set) var isSubmitting = false
    public private(set) var message: String?

    private let http: HTTPClient
    private let uploader: MediaUploader

    /// - Parameters:
    ///   - http: client HTTP.
    ///   - uploader: upload du média de pub (catégorie `sponsor`).
    public init(http: HTTPClient, uploader: MediaUploader) {
        self.http = http
        self.uploader = uploader
    }

    /// Charge paliers + demandes + événements sponsorisables.
    public func load() async {
        tiers = (try? await http.request(.get(SponsorEndpoints.tiers), as: [SponsorTier].self)) ?? []
        requests = (try? await http.request(.get(SponsorEndpoints.myRequests), as: [SponsorRequest].self)) ?? []
        events = await loadEvents()
    }

    /// Fusionne les catalogues (concerts d'artistes, compétitions, duels) en événements à venir.
    private func loadEvents() async -> [SponsorableEvent] {
        let query = ["limit": "50"]

        let concerts = ((try? await http.request(
            .get(ConcertEndpoints.artistList, query: query), as: [Concert].self
        )) ?? [])
            .filter { $0.status == .upcoming || $0.status == .live }
            .map { SponsorableEvent(type: "artist_concert", eventId: $0.id, label: $0.title) }

        let competitions = ((try? await http.request(
            .get(CompetitionEndpoints.list, query: query), as: [Competition].self
        )) ?? [])
            .filter { $0.status != "ended" && $0.status != "cancelled" }
            .map { SponsorableEvent(type: "competition", eventId: $0.id, label: "🏆 \($0.title)") }

        let duels = ((try? await http.request(
            .get(DuelEndpoints.list, query: query), as: [Duel].self
        )) ?? [])
            .filter { $0.status == .upcoming || $0.status == .live }
            .map { duel -> SponsorableEvent in
                let a = duel.artist1?.displayName ?? "?"
                let b = duel.artist2?.displayName ?? "?"
                return SponsorableEvent(type: "duel", eventId: duel.id, label: "⚔️ \(a) vs \(b)")
            }

        return concerts + competitions + duels
    }

    /// Paie une demande approuvée (débit idempotent), puis recharge.
    /// - Parameter id: identifiant de la demande.
    public func pay(id: String) async {
        let s = AppStrings.current
        do {
            try await http.send(.post(SponsorEndpoints.pay(id), idempotencyKey: UUID().uuidString))
            message = s.sponsorPaid
            await load()
        } catch {
            message = (error as? APIError)?.message ?? s.errPaymentFailed
        }
    }

    /// Affiche un message (erreur de lecture de fichier, sélection manquante…).
    public func setMessage(_ text: String?) { message = text }

    /// Slot de durée par défaut pour une image = plus petit palier (`min_seconds`), sinon 5 s.
    public var imageDurationSlot: Int {
        max(1, tiers.map(\.minSeconds).min() ?? 5)
    }

    /// Upload le média de pub et mémorise URL/type/durée dans le brouillon.
    ///
    /// La durée d'une vidéo est **mesurée localement** (AVFoundation) ; pour une image, on
    /// applique le slot du plus petit palier. Le prix, lui, est calculé par le serveur.
    /// - Parameter item: élément choisi dans le sélecteur photo/vidéo.
    public func uploadMedia(_ item: PhotosPickerItem) async {
        let s = AppStrings.current
        isUploadingMedia = true
        message = nil
        defer { isUploadingMedia = false }
        do {
            let media = try await PhotoPickerLoader.load(item)
            guard let kind = media.mediaKind else {
                message = s.unsupportedMedia
                return
            }
            let duration = kind == "video"
                ? (await PhotoPickerLoader.videoDurationSeconds(media) ?? 30)
                : imageDurationSlot

            mediaURL = try await uploader.upload(media, category: UploadCategory.sponsor)
            mediaType = kind
            mediaDurationSeconds = min(600, max(1, duration))
        } catch {
            message = error.localizedDescription
        }
    }

    /// Crée la demande de sponsoring pour `event` avec le média du brouillon.
    ///
    /// Le prix n'est pas envoyé : il est calculé côté serveur d'après la durée.
    /// - Parameters:
    ///   - event: événement ciblé.
    ///   - description: texte libre (optionnel).
    ///   - onDone: exécuté après création réussie.
    public func createRequest(event: SponsorableEvent, description: String, onDone: @escaping () -> Void) async {
        let s = AppStrings.current
        guard let mediaURL, let mediaType else {
            message = s.errAddMediaFirst
            return
        }
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }
        do {
            try await http.send(
                .post(
                    SponsorEndpoints.create,
                    body: CreateSponsorRequest(
                        eventType: event.type,
                        eventId: event.eventId,
                        mediaType: mediaType,
                        mediaUrl: mediaURL,
                        mediaDurationSeconds: min(600, max(1, mediaDurationSeconds)),
                        description: description.nilIfBlank
                    )
                )
            )
            self.mediaURL = nil
            self.mediaType = nil
            self.mediaDurationSeconds = 0
            message = s.requestSent
            await load()
            onDone()
        } catch {
            message = (error as? APIError)?.message ?? s.sendFailed
        }
    }
}

/// Écran de sponsoring : « Mes demandes » (tarifs + demandes + paiement) et « Nouvelle ».
@MainActor
public struct SponsorView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: SponsorViewModel
    @State private var tab = 0

    /// - Parameter viewModel: source d'état.
    public init(viewModel: SponsorViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            if let message = viewModel.message { DMMessage(message) }
            DMTabBar(titles: [s.myRequests, s.newTab], selection: $tab)

            if tab == 0 {
                MyRequestsTab(viewModel: viewModel)
            } else {
                NewRequestTab(viewModel: viewModel) { tab = 0 }
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }
}

/// Onglet « Mes demandes » : grille tarifaire + demandes (paiement si approuvée).
private struct MyRequestsTab: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let viewModel: SponsorViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.tiersByDuration)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)

                ForEach(viewModel.tiers) { tier in
                    DMCard {
                        HStack {
                            Text("\(tier.label ?? s.tier) (\(tier.minSeconds)-\(tier.maxSeconds)s)")
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.foreground)
                            Spacer()
                            Text(formatCredits(tier.priceCredits))
                                .font(DMFont.mono)
                                .foregroundStyle(theme.colors.accent)
                        }
                    }
                }

                Text(s.myRequests)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .padding(.top, theme.spacing.sm)

                if viewModel.requests.isEmpty {
                    DMEmptyState(title: s.noSponsorRequests, subtitle: s.noSponsorRequestsHint, systemImage: "star")
                }

                ForEach(viewModel.requests) { request in
                    SponsorRequestRow(request: request) {
                        Task { await viewModel.pay(id: request.id) }
                    }
                }
            }
        }
    }
}

/// Onglet « Nouvelle » : choix de l'événement + média + description + envoi.
private struct NewRequestTab: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let viewModel: SponsorViewModel
    let onSent: () -> Void

    @State private var selected: SponsorableEvent?
    @State private var description = ""
    @State private var pickedItem: PhotosPickerItem?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.step1ChooseEvent)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)

                if viewModel.events.isEmpty {
                    DMEmptyState(title: s.noEvents, subtitle: s.noEventsHint, systemImage: "magnifyingglass")
                }

                ForEach(viewModel.events) { event in
                    Button { selected = event } label: {
                        DMCard(isSelected: selected?.id == event.id) {
                            Text((selected?.id == event.id ? "◉ " : "○ ") + event.label)
                                .font(DMFont.body)
                                .fontWeight(selected?.id == event.id ? .bold : .regular)
                                .foregroundStyle(
                                    selected?.id == event.id ? theme.colors.foreground : theme.colors.mutedForeground
                                )
                        }
                    }
                    .buttonStyle(.plain)
                }

                Text(s.step2Media)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .padding(.top, theme.spacing.sm)

                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        Text(mediaStatus)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)

                        PhotosPicker(selection: $pickedItem, matching: .any(of: [.images, .videos])) {
                            Text(viewModel.mediaURL != nil ? s.changeMedia : s.chooseMedia)
                                .font(DMFont.button)
                                .foregroundStyle(theme.colors.secondaryForeground)
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .background(theme.colors.secondary)
                                .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                        }
                        .disabled(viewModel.isUploadingMedia)
                    }
                }

                DMTextField(s.descriptionOptional, text: $description, axis: .vertical)

                DMButton(
                    viewModel.isSubmitting ? s.sending : s.submitRequest,
                    isLoading: viewModel.isSubmitting,
                    isEnabled: !viewModel.isSubmitting
                ) {
                    guard let event = selected else {
                        viewModel.setMessage(s.selectEventError)
                        return
                    }
                    Task { await viewModel.createRequest(event: event, description: description, onDone: onSent) }
                }
            }
        }
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            Task { await viewModel.uploadMedia(item) }
        }
    }

    /// Texte d'état du média du brouillon.
    private var mediaStatus: String {
        if viewModel.isUploadingMedia { return s.uploading }
        if viewModel.mediaURL != nil {
            return "\(s.mediaReady) (\(viewModel.mediaType ?? "-"), \(viewModel.mediaDurationSeconds)s)."
        }
        return s.noMedia
    }
}

/// Ligne d'une demande de sponsoring (paiement si approuvée).
private struct SponsorRequestRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let request: SponsorRequest
    let onPay: () -> Void

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(request.eventType ?? s.event)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    Text(statusLabel)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                if request.payable {
                    DMButton("\(s.pay) \(Int(request.priceCredits))", action: onPay)
                        .frame(width: 140)
                }
            }
        }
    }

    private var statusLabel: String {
        switch request.status {
        case "pending": return s.sponsorStatusPending
        case "approved": return s.sponsorStatusApproved
        case "rejected": return s.statusRejected
        default: return request.status
        }
    }
}

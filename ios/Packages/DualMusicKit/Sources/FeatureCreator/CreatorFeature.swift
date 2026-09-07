import SwiftUI
import PhotosUI
import Observation
import CoreNetwork
import CoreUI
import CoreUpload
import DomainModels

/// ViewModel des outils créateur : défis de duel (répondre), mes concerts, création de concert.
@Observable
@MainActor
public final class CreatorViewModel {

    public private(set) var myUserId: String?
    public private(set) var duelRequests: [DuelRequestItem] = []
    public private(set) var concerts: [Concert] = []
    /// URL publique de la pochette uploadée (prête à être persistée).
    public private(set) var coverURL: String?
    public private(set) var isUploadingCover = false
    public private(set) var isSubmitting = false
    public private(set) var message: String?

    private let http: HTTPClient
    private let uploader: MediaUploader

    /// - Parameters:
    ///   - http: client HTTP (lectures + POST).
    ///   - uploader: upload de la pochette (presign → PUT → confirm).
    public init(http: HTTPClient, uploader: MediaUploader) {
        self.http = http
        self.uploader = uploader
    }

    /// Charge l'id du caller + les défis + les concerts.
    public func load() async {
        let me = try? await http.request(.get(UserEndpoints.me), as: MeResponse.self)
        myUserId = me?.user.id
        duelRequests = (try? await http.request(
            .get(CreatorEndpoints.duelRequestsMine), as: [DuelRequestItem].self
        )) ?? []
        concerts = (try? await http.request(
            .get(CreatorEndpoints.myConcerts), as: [Concert].self
        )) ?? []
    }

    /// Répond à un défi reçu (accepter/refuser), puis recharge.
    /// - Parameters:
    ///   - id: identifiant du défi.
    ///   - accept: `true` pour accepter.
    public func respond(id: String, accept: Bool) async {
        try? await http.send(.post(CreatorEndpoints.duelRespond(id), body: RespondDuelRequest(accept: accept)))
        await load()
    }

    /// Affiche un message (erreur de lecture de fichier…).
    public func setMessage(_ text: String?) { message = text }

    /// Upload une image comme pochette de concert (catégorie `image`, ≤ 5 Mo).
    /// - Parameter item: élément choisi dans le sélecteur photo.
    public func uploadCover(_ item: PhotosPickerItem) async {
        isUploadingCover = true
        message = nil
        defer { isUploadingCover = false }
        do {
            let media = try await PhotoPickerLoader.load(item, maxBytes: 5 * 1024 * 1024)
            coverURL = try await uploader.upload(media, category: UploadCategory.image)
        } catch {
            message = error.localizedDescription
        }
    }

    /// Crée un concert d'artiste.
    ///
    /// La pochette (si présente) doit déjà être uploadée (``uploadCover(_:)``) : on envoie
    /// son URL. Le concert est créé en `approval_status=pending` côté backend.
    ///
    /// - Parameters:
    ///   - title: titre (requis).
    ///   - description: description libre.
    ///   - scheduledDate: date saisie au format `AAAA-MM-JJTHH:MM` (complétée en ISO).
    ///   - ticketPrice: prix du billet en crédits.
    ///   - maxTickets: jauge maximale (optionnelle).
    ///   - allowsDedications: autorise les dédicaces.
    ///   - allowsSponsorAds: autorise les pubs sponsors.
    ///   - onDone: exécuté après création réussie.
    public func createConcert(
        title: String,
        description: String,
        scheduledDate: String,
        ticketPrice: Double,
        maxTickets: Int?,
        allowsDedications: Bool,
        allowsSponsorAds: Bool,
        onDone: @escaping () -> Void
    ) async {
        let s = AppStrings.current
        guard !title.trimmed.isEmpty, !scheduledDate.trimmed.isEmpty else {
            message = s.errTitleDateRequired
            return
        }
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }
        do {
            try await http.send(
                .post(
                    ConcertEndpoints.artistList,
                    body: CreateArtistConcert(
                        title: title.trimmed,
                        description: description.nilIfBlank,
                        scheduledDate: Self.normalizeISODate(scheduledDate.trimmed),
                        ticketPrice: ticketPrice,
                        maxTickets: maxTickets,
                        coverImageUrl: coverURL,
                        allowsDedications: allowsDedications,
                        allowsSponsorAds: allowsSponsorAds
                    )
                )
            )
            coverURL = nil
            message = s.concertCreated
            await load()
            onDone()
        } catch {
            message = (error as? APIError)?.message ?? s.errCreateFailed
        }
    }

    /// Complète une saisie `YYYY-MM-DDTHH:MM` en ISO `…:00` si nécessaire.
    /// - Parameter input: date saisie.
    static func normalizeISODate(_ input: String) -> String {
        let pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}$"
        return input.range(of: pattern, options: .regularExpression) != nil ? "\(input):00" : input
    }
}

/// Espace créateur : Défis de duel · Mes concerts · Créer un concert.
@MainActor
public struct CreatorView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: CreatorViewModel
    @State private var tab = 0

    /// - Parameter viewModel: source d'état.
    public init(viewModel: CreatorViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: theme.spacing.md) {
            if let message = viewModel.message { DMMessage(message) }
            DMTabBar(titles: [s.tabChallenges, s.myConcerts, s.create], selection: $tab)

            switch tab {
            case 0: challengesTab
            case 1: concertsTab
            default: CreateConcertForm(viewModel: viewModel) { tab = 1 }
            }
        }
        .padding(theme.spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var challengesTab: some View {
        if viewModel.duelRequests.isEmpty {
            DMEmptyState(title: s.noChallenges, subtitle: s.noChallengesHint, systemImage: "bell")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.duelRequests) { request in
                        DuelRequestRow(
                            request: request,
                            // Seul le destinataire peut répondre, et seulement si en attente.
                            canRespond: request.opponentId == viewModel.myUserId && request.status == "pending",
                            onAccept: { Task { await viewModel.respond(id: request.id, accept: true) } },
                            onDecline: { Task { await viewModel.respond(id: request.id, accept: false) } }
                        )
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var concertsTab: some View {
        if viewModel.concerts.isEmpty {
            DMEmptyState(title: s.noConcerts, subtitle: s.noConcertsHint, systemImage: "calendar")
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: theme.spacing.sm) {
                    ForEach(viewModel.concerts) { MyConcertRow(concert: $0) }
                }
            }
        }
    }
}

/// Formulaire de création d'un concert (pochette optionnelle + champs).
private struct CreateConcertForm: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let viewModel: CreatorViewModel
    let onCreated: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var date = ""
    @State private var price = "0"
    @State private var maxTickets = ""
    @State private var dedications = true
    @State private var sponsorAds = true
    @State private var pickedItem: PhotosPickerItem?

    var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.sm) {
                DMTextField(s.titleRequired, text: $title)
                DMTextField(s.description, text: $description, axis: .vertical)
                DMTextField(
                    s.dateFormatLabel,
                    text: $date,
                    placeholder: "2026-08-01T20:00",
                    autocapitalization: .never
                )
                DMTextField(s.ticketPriceLabel, text: $price, keyboard: .decimalPad, autocapitalization: .never)
                DMTextField(s.maxTicketsLabel, text: $maxTickets, keyboard: .numberPad, autocapitalization: .never)

                DMCard {
                    VStack(spacing: theme.spacing.sm) {
                        DMToggleRow(s.allowDedications, isOn: $dedications)
                        DMToggleRow(s.allowSponsorAds, isOn: $sponsorAds)
                    }
                }

                DMCard {
                    VStack(alignment: .leading, spacing: theme.spacing.xs) {
                        Text(coverStatus)
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                        PhotosPicker(selection: $pickedItem, matching: .images) {
                            Text(viewModel.coverURL != nil ? s.changeCover : s.chooseCover)
                                .font(DMFont.button)
                                .foregroundStyle(theme.colors.secondaryForeground)
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .background(theme.colors.secondary)
                                .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                        }
                        .disabled(viewModel.isUploadingCover)
                    }
                }

                DMButton(
                    viewModel.isSubmitting ? s.creating : s.createConcert,
                    isLoading: viewModel.isSubmitting,
                    isEnabled: !viewModel.isSubmitting
                ) {
                    Task {
                        await viewModel.createConcert(
                            title: title,
                            description: description,
                            scheduledDate: date,
                            ticketPrice: Double(price.replacingOccurrences(of: ",", with: ".")) ?? 0,
                            maxTickets: Int(maxTickets.digitsOnly),
                            allowsDedications: dedications,
                            allowsSponsorAds: sponsorAds,
                            onDone: onCreated
                        )
                    }
                }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            Task { await viewModel.uploadCover(item) }
        }
    }

    /// Texte d'état de la pochette.
    private var coverStatus: String {
        if viewModel.isUploadingCover { return s.uploadingCover }
        return viewModel.coverURL != nil ? s.coverReady : s.noCover
    }
}

/// Ligne d'un défi de duel : accepter/refuser si reçu, sinon statut.
private struct DuelRequestRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let request: DuelRequestItem
    let canRespond: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(request.message ?? s.duelChallenge)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                if let date = isoMinute(request.proposedDate) {
                    Text("\(s.proposed) : \(date)")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
                if canRespond {
                    HStack(spacing: theme.spacing.sm) {
                        DMButton(s.accept, action: onAccept)
                        DMButton(s.decline, style: .outline, action: onDecline)
                    }
                } else {
                    Text(statusLabel)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                }
            }
        }
    }

    private var statusLabel: String {
        switch request.status {
        case "pending": return s.statusPending
        case "accepted": return s.statusAccepted
        case "declined": return s.statusDeclined
        default: return request.status
        }
    }
}

/// Ligne d'un concert de l'artiste.
private struct MyConcertRow: View {
    @Environment(\.dmTheme) private var theme
    let concert: Concert

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(concert.title)
                        .font(DMFont.body).bold()
                        .foregroundStyle(theme.colors.foreground)
                    if let date = isoMinute(concert.scheduledDate) {
                        Text(date).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                Text(concert.status.rawValue.capitalizedFirst)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
        }
    }
}

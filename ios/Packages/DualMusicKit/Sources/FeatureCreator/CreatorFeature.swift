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
    /// Annuaire des artistes (pour rechercher un adversaire à défier).
    public private(set) var artists: [ArtistSummary] = []
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
        artists = (try? await http.request(.get(ArtistEndpoints.list), as: [ArtistSummary].self)) ?? []
    }

    /// Répond à un défi reçu (accepter/refuser), avec retour clair, puis recharge.
    /// - Parameters:
    ///   - id: identifiant du défi.
    ///   - accept: `true` pour accepter.
    public func respond(id: String, accept: Bool) async {
        let s = AppStrings.current
        do {
            try await http.send(.post(CreatorEndpoints.duelRespond(id), body: RespondDuelRequest(accept: accept)))
            message = accept ? s.duelAccepted : s.duelDeclined
        } catch {
            message = (error as? APIError)?.message ?? s.errCreateFailed
        }
        await load()
    }

    /// Envoie une invitation de duel à un artiste (date + message optionnels), puis recharge.
    /// - Parameters:
    ///   - opponentId: id UTILISATEUR de l'adversaire (``ArtistSummary/opponentUserId``).
    ///   - proposedDate: date proposée, saisie `AAAA-MM-JJTHH:MM` (normalisée en ISO UTC).
    ///   - message: message d'accompagnement, optionnel.
    public func createDuel(opponentId: String, proposedDate: String?, message: String?) async {
        let s = AppStrings.current
        isSubmitting = true
        self.message = nil
        defer { isSubmitting = false }
        do {
            try await http.send(
                .post(
                    CreatorEndpoints.duelRequestCreate,
                    body: CreateDuelRequest(
                        opponentId: opponentId,
                        proposedDate: proposedDate?.trimmed.nilIfBlank.map(Self.normalizeISODate),
                        message: message?.trimmed.nilIfBlank
                    )
                )
            )
            self.message = s.duelRequestSent
            await load()
        } catch {
            self.message = (error as? APIError)?.message ?? s.errCreateFailed
        }
    }

    /// Change la date proposée d'un défi ENVOYÉ encore en attente (émetteur). Renotifie
    /// l'adversaire côté backend.
    /// - Parameters:
    ///   - id: identifiant du défi.
    ///   - newDate: nouvelle date saisie `AAAA-MM-JJTHH:MM` (normalisée en ISO UTC).
    public func changeDuelDate(id: String, newDate: String) async {
        guard !newDate.trimmed.isEmpty else { return }
        let s = AppStrings.current
        do {
            try await http.send(
                .patch(CreatorEndpoints.duelChangeDate(id), body: ChangeDuelDateRequest(proposedDate: Self.normalizeISODate(newDate.trimmed)))
            )
            message = s.duelDateChanged
            await load()
        } catch {
            message = (error as? APIError)?.message ?? s.errCreateFailed
        }
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
    // « Demander un Duel ».
    @State private var duelQuery = ""
    @State private var selectedArtist: ArtistSummary?
    @State private var proposedDate = ""
    @State private var duelMessage = ""

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

    /// Candidats au défi : tous les artistes, hors moi-même, filtrés par la recherche —
    /// limités à 10 comme Android.
    private var duelCandidates: [ArtistSummary] {
        Array(
            viewModel.artists
                .filter { $0.opponentUserId != viewModel.myUserId }
                .filter { duelQuery.isEmpty || $0.displayName.localizedCaseInsensitiveContains(duelQuery) }
                .prefix(10)
        )
    }

    /// Demandes ENVOYÉES (moi = émetteur) et REÇUES (moi = destinataire).
    private var sentRequests: [DuelRequestItem] { viewModel.duelRequests.filter { $0.requesterId == viewModel.myUserId } }
    private var receivedRequests: [DuelRequestItem] { viewModel.duelRequests.filter { $0.opponentId == viewModel.myUserId } }

    private func opponentName(_ userId: String?) -> String {
        viewModel.artists.first { $0.opponentUserId == userId }?.displayName ?? s.artistSingular
    }

    @ViewBuilder
    private var challengesTab: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: theme.spacing.sm) {
                requestDuelCard

                Text(s.mySentRequests).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                if sentRequests.isEmpty {
                    Text(s.noSentRequests).foregroundStyle(theme.colors.mutedForeground)
                } else {
                    ForEach(sentRequests) { request in
                        SentDuelRow(
                            opponentName: opponentName(request.opponentId),
                            request: request,
                            onChangeDate: { newDate in Task { await viewModel.changeDuelDate(id: request.id, newDate: newDate) } }
                        )
                    }
                }

                Text(s.receivedInvitations)
                    .font(DMFont.body).bold()
                    .foregroundStyle(theme.colors.foreground)
                    .padding(.top, theme.spacing.md)
                if receivedRequests.isEmpty {
                    Text(s.noChallengesHint).foregroundStyle(theme.colors.mutedForeground)
                } else {
                    ForEach(receivedRequests) { request in
                        DuelRequestRow(
                            request: request,
                            canRespond: request.status == "pending",
                            onAccept: { Task { await viewModel.respond(id: request.id, accept: true) } },
                            onDecline: { Task { await viewModel.respond(id: request.id, accept: false) } }
                        )
                    }
                }
            }
        }
    }

    /// « Demander un Duel » : rechercher un artiste, le sélectionner, puis envoyer l'invitation.
    private var requestDuelCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.requestDuel).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                Text(s.requestDuelHint).foregroundStyle(theme.colors.mutedForeground)
                DMTextField(s.searchArtist, text: $duelQuery)

                Text("\(duelCandidates.count) \(s.artistsAvailable)")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)

                if duelCandidates.isEmpty {
                    Text(s.noArtistAvailable).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                } else {
                    ForEach(duelCandidates) { artist in
                        let isSelected = selectedArtist?.opponentUserId == artist.opponentUserId
                        Button { selectedArtist = artist } label: {
                            Text("🎤  \(artist.displayName)")
                                .font(DMFont.body).bold()
                                .foregroundStyle(isSelected ? theme.colors.primary : theme.colors.foreground)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(theme.spacing.md)
                                .background(
                                    isSelected ? theme.colors.primary.opacity(0.15) : theme.colors.muted.opacity(0.3),
                                    in: RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }

                if let artist = selectedArtist {
                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        Text("\(artist.displayName) · \(s.selectedArtist)")
                            .font(DMFont.body).bold()
                            .foregroundStyle(theme.colors.foreground)
                        DMTextField(
                            s.proposedDateOptional,
                            text: $proposedDate,
                            placeholder: "2026-08-01T20:00",
                            autocapitalization: .never
                        )
                        DMTextField(s.messageOptional, text: $duelMessage)
                        DMButton(
                            viewModel.isSubmitting ? s.sending : s.sendDuelRequest,
                            isLoading: viewModel.isSubmitting,
                            isEnabled: !viewModel.isSubmitting
                        ) {
                            Task { await viewModel.createDuel(opponentId: artist.opponentUserId, proposedDate: proposedDate, message: duelMessage) }
                            selectedArtist = nil
                            proposedDate = ""
                            duelMessage = ""
                        }
                    }
                    .padding(.top, theme.spacing.xs)
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
@MainActor
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

/// Ligne d'un défi ENVOYÉ : statut + reproposition de date tant qu'il est en attente.
private struct SentDuelRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let opponentName: String
    let request: DuelRequestItem
    let onChangeDate: (String) -> Void

    @State private var editing = false
    @State private var newDate = ""

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.xs) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("🎤 \(opponentName)").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                        if let date = isoMinute(request.proposedDate) {
                            Text("\(s.duelPlanned) \(date)")
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.mutedForeground)
                        }
                    }
                    Spacer()
                    Text(sentStatusLabel)
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.accent)
                }
                if request.status == "pending" {
                    if !editing {
                        DMButton(s.changeDate, style: .outline) { editing = true }
                    } else {
                        DMTextField(
                            s.proposedDateOptional,
                            text: $newDate,
                            placeholder: "2026-08-01T20:00",
                            autocapitalization: .never
                        )
                        HStack(spacing: theme.spacing.sm) {
                            DMButton(s.save, isEnabled: !newDate.trimmed.isEmpty) {
                                onChangeDate(newDate)
                                editing = false
                            }
                            DMButton(s.cancel, style: .outline) { editing = false }
                        }
                    }
                }
            }
        }
    }

    private var sentStatusLabel: String {
        switch request.status {
        case "pending": return s.statusPending
        case "accepted", "admin_pending": return s.statusAccepted
        case "approved": return s.statusApproved
        case "rejected", "declined": return s.statusRejected
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

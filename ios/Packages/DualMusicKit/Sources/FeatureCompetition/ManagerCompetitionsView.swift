import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de l'espace « Mes Compétitions » du MANAGER (organisateur).
///
/// Contrairement au duel (création soumise à un réglage admin), un manager peut toujours créer
/// une compétition — aucun réglage `manager_competition_creation` équivalent n'existe côté
/// backend. Le formulaire simplifie l'éligibilité géographique d'Android/web (portée +
/// multi-sélection de pays) à un simple champ pays libre ; écart assumé, documenté ici plutôt
/// que silencieux.
@Observable
@MainActor
public final class ManagerCompetitionsViewModel {

    public private(set) var competitions: [Competition] = []
    public private(set) var myId: String?
    public private(set) var creating = false
    public private(set) var message: String?

    private let repository: CompetitionRepository

    /// - Parameter repository: lectures/écritures REST des compétitions.
    public init(repository: CompetitionRepository) {
        self.repository = repository
    }

    /// Charge l'id du manager + ses compétitions gérées.
    public func load() async {
        let myId = await repository.myUserId()
        self.myId = myId
        if myId != nil {
            competitions = (try? await repository.myCompetitions()) ?? []
        }
    }

    /// Crée une compétition (le manager s'assigne organisateur), puis recharge la liste. Le
    /// backend force `status = "draft"` à la création quel que soit le statut transmis — une
    /// publication explicite (``publish(id:)``) est nécessaire pour ouvrir les votes.
    public func createCompetition(_ body: CreateCompetitionBody) async {
        creating = true
        message = nil
        do {
            try await repository.createCompetition(body)
            message = AppStrings.current.competitionCreated
            await load()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
        creating = false
    }

    /// Publie une compétition en attente (ouvre les votes), puis recharge la liste.
    public func publish(id: String) async {
        do {
            try await repository.publish(id: id)
            await load()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }

    public func clearMessage() { message = nil }
}

/// Écran « Mes Compétitions » du manager : formulaire de création + liste des compétitions
/// gérées (publication si encore en brouillon, accès direct si en direct). Miroir simplifié de
/// `ManagerCompetitionsScreen` Android (voir la note de simplification sur l'éligibilité
/// géographique dans ``ManagerCompetitionsViewModel``).
@MainActor
public struct ManagerCompetitionsView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: ManagerCompetitionsViewModel
    private let onOpenCompetition: (Competition) -> Void

    @State private var showForm = false
    @State private var title = ""
    @State private var description = ""
    @State private var coverURL = ""
    @State private var mode = "online"
    @State private var maxCandidates = "10"
    @State private var rewardDescription = ""
    @State private var rewardAmount = ""
    @State private var entryFeeRequired = false
    @State private var entryFeeAmount = ""
    @State private var acceptsSponsors = true
    @State private var country = ""
    @State private var startAt = ""
    @State private var endAt = ""
    @State private var applicationOpensAt = ""
    @State private var applicationDeadline = ""

    /// - Parameters:
    ///   - viewModel: état + actions de l'espace manager.
    ///   - onOpenCompetition: ouvre la room d'une compétition en direct.
    public init(viewModel: ManagerCompetitionsViewModel, onOpenCompetition: @escaping (Competition) -> Void = { _ in }) {
        self.viewModel = viewModel
        self.onOpenCompetition = onOpenCompetition
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                DMButton(showForm ? s.cancel : s.createCompetitionAction) { showForm.toggle() }
                if showForm { createCard }

                Text(s.managedCompetitions).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                if viewModel.competitions.isEmpty {
                    DMEmptyState(title: s.noCompetitions, subtitle: s.noCompetitionsHint, systemImage: "star")
                } else {
                    ForEach(viewModel.competitions) { competition in
                        managedRow(competition)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    /// Formulaire de création — champs essentiels du formulaire web/Android
    /// (`CreateCompetitionBody`), éligibilité géographique simplifiée à un champ pays libre.
    private var createCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                DMTextField(s.createCompetitionTitle, text: $title)
                DMTextField(s.descriptionOptional, text: $description, axis: .vertical)
                DMTextField("URL de la pochette (optionnel)", text: $coverURL, keyboard: .URL, autocapitalization: .never)

                Picker("Mode", selection: $mode) {
                    Text("En ligne").tag("online")
                    Text("Présentiel").tag("onsite")
                }
                .pickerStyle(.segmented)

                DMTextField("Candidats max", text: $maxCandidates, keyboard: .numberPad)
                DMTextField("Récompense (description)", text: $rewardDescription)
                DMTextField("Récompense (crédits)", text: $rewardAmount, keyboard: .numberPad)

                Toggle("Frais d'inscription", isOn: $entryFeeRequired)
                if entryFeeRequired {
                    DMTextField("Montant (crédits)", text: $entryFeeAmount, keyboard: .numberPad)
                }
                Toggle("Accepte les sponsors", isOn: $acceptsSponsors)

                DMTextField("Pays (optionnel)", text: $country)

                DateTimeField(label: "Début", text: $startAt)
                DateTimeField(label: "Fin", text: $endAt)
                DateTimeField(label: "Ouverture des candidatures (optionnel)", text: $applicationOpensAt)
                DateTimeField(label: "Date limite des candidatures (optionnel)", text: $applicationDeadline)

                if let message = viewModel.message {
                    Text(message).font(DMFont.caption).foregroundStyle(theme.colors.accent)
                }

                DMButton(viewModel.creating ? s.sending : s.createCompetitionAction, isLoading: viewModel.creating) {
                    guard let myId = viewModel.myId, !title.trimmed.isEmpty else { return }
                    let body = CreateCompetitionBody(
                        managerId: myId,
                        title: title.trimmed,
                        description: description.nilIfBlank,
                        coverUrl: coverURL.nilIfBlank,
                        mode: mode,
                        maxCandidates: Int(maxCandidates) ?? 10,
                        rewardDescription: rewardDescription.nilIfBlank,
                        rewardAmount: Double(rewardAmount) ?? 0,
                        entryFeeRequired: entryFeeRequired,
                        entryFeeAmount: Double(entryFeeAmount) ?? 0,
                        acceptsSponsors: acceptsSponsors,
                        country: country.nilIfBlank,
                        applicationOpensAt: applicationOpensAt.nilIfBlank,
                        applicationDeadline: applicationDeadline.nilIfBlank,
                        startAt: startAt.nilIfBlank,
                        endAt: endAt.nilIfBlank
                    )
                    Task {
                        await viewModel.createCompetition(body)
                        title = ""; description = ""; coverURL = ""; rewardDescription = ""; rewardAmount = ""
                        entryFeeAmount = ""; country = ""; startAt = ""; endAt = ""
                        applicationOpensAt = ""; applicationDeadline = ""
                        showForm = false
                    }
                }
            }
        }
    }

    private func managedRow(_ competition: Competition) -> some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(competition.title).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                    Text(competition.status.capitalizedFirst).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                }
                Spacer()
                if competition.status == "draft" {
                    DMButton("Publier") { Task { await viewModel.publish(id: competition.id) } }
                        .frame(width: 100)
                } else if competition.status == "live" || competition.status == "open" {
                    Button(s.joinAction) { onOpenCompetition(competition) }
                        .font(DMFont.caption).bold()
                        .foregroundStyle(theme.colors.primary)
                }
            }
        }
    }
}

/// Champ de date/heure ISO simplifié (texte libre, même convention que ``DateTimePickerField``
/// Android — pas de composant natif ISO côté SwiftUI, format attendu : `AAAA-MM-JJTHH:MM`).
private struct DateTimeField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        DMTextField(label, text: $text, placeholder: "2026-08-01T20:00")
    }
}

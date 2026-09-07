import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// Accès REST au flux de retrait.
///
/// Sécurité : la création d'un retrait exige le **PIN de retrait** (6 chiffres), re-vérifié
/// côté serveur avec verrouillage après échecs répétés. La demande est idempotente.
public struct WithdrawalRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    /// L'utilisateur a-t-il déjà défini un PIN de retrait ?
    public func hasPin() async throws -> Bool {
        try await http.request(.get(WithdrawalEndpoints.pin), as: PinStatus.self).hasPin
    }

    /// Crée/remplace le PIN (`currentPin` requis si un PIN existe déjà).
    public func setPin(newPin: String, currentPin: String? = nil) async throws {
        try await http.send(.post(WithdrawalEndpoints.pin, body: SetPinRequest(newPin: newPin, currentPin: currentPin)))
    }

    /// Méthodes de retrait enregistrées (celle par défaut en premier).
    public func methods() async throws -> [PayoutMethodData] {
        try await http.request(.get(WithdrawalEndpoints.methods), as: [PayoutMethodData].self)
    }

    /// Aperçu du net après frais pour un montant brut.
    ///
    /// ⚠️ C'est **cette valeur serveur** qui est affichée — jamais un calcul local.
    public func net(amount: Double) async throws -> WithdrawalNet {
        try await http.request(.post(WithdrawalEndpoints.net, body: NetRequest(amount: amount)), as: WithdrawalNet.self)
    }

    /// Demandes de retrait du caller (les plus récentes d'abord).
    public func myRequests() async throws -> [WithdrawalRequest] {
        try await http.request(.get(WithdrawalEndpoints.mine), as: [WithdrawalRequest].self)
    }

    /// Crée une demande de retrait. Réserve les fonds atomiquement côté serveur après
    /// vérification du PIN.
    public func createRequest(
        amount: Double,
        pin: String,
        payoutMethodId: String?,
        idempotencyKey: String = UUID().uuidString
    ) async throws {
        try await http.send(
            .post(
                WithdrawalEndpoints.create,
                body: CreateWithdrawalRequest(amount: amount, pin: pin, payoutMethodId: payoutMethodId),
                idempotencyKey: idempotencyKey
            )
        )
    }
}

/// ViewModel du retrait.
///
/// Aucun calcul d'argent local : le net affiché vient de `/withdrawals/net`, et la demande
/// est validée + réservée côté serveur. Le PIN est re-vérifié par le backend.
@Observable
@MainActor
public final class WithdrawalViewModel {

    /// `nil` tant qu'on ne sait pas si un PIN existe, puis `true`/`false`.
    public private(set) var hasPin: Bool?
    public private(set) var methods: [PayoutMethodData] = []
    public private(set) var selectedMethodId: String?
    public var amount: String = ""
    public private(set) var net: WithdrawalNet?
    public private(set) var requests: [WithdrawalRequest] = []
    public private(set) var isLoading = false
    public private(set) var isSubmitting = false
    public private(set) var submitted = false
    public private(set) var errorMessage: String?

    private let repository: WithdrawalRepository
    /// Tâche d'aperçu du net en cours (annulée à chaque frappe → une seule requête utile).
    private var netTask: Task<Void, Never>?

    /// - Parameter repository: accès au flux de retrait.
    public init(repository: WithdrawalRepository) {
        self.repository = repository
    }

    /// Charge PIN, méthodes et historique.
    public func load() async {
        isLoading = true
        errorMessage = nil
        hasPin = (try? await repository.hasPin()) ?? false
        methods = (try? await repository.methods()) ?? []
        requests = (try? await repository.myRequests()) ?? []
        selectedMethodId = methods.first(where: { $0.isDefault })?.id ?? methods.first?.id
        isLoading = false
    }

    /// Met à jour le montant et rafraîchit l'aperçu du net (debounce 400 ms).
    /// - Parameter value: saisie brute.
    public func onAmountChange(_ value: String) {
        amount = value
        errorMessage = nil
        netTask?.cancel()
        guard let parsed = Double(value.replacingOccurrences(of: ",", with: ".")), parsed > 0 else {
            net = nil
            return
        }
        netTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled, let self else { return }
            if let preview = try? await self.repository.net(amount: parsed) {
                self.net = preview
            }
        }
    }

    /// Sélectionne une méthode de retrait.
    public func selectMethod(id: String) { selectedMethodId = id }

    /// Crée le PIN (première configuration).
    public func createPin(_ pin: String) async {
        do {
            try await repository.setPin(newPin: pin)
            hasPin = true
        } catch {
            errorMessage = Self.friendly(error)
        }
    }

    /// Envoie la demande de retrait avec le PIN saisi.
    public func submit(pin: String) async {
        let s = AppStrings.current
        guard let value = Double(amount.replacingOccurrences(of: ",", with: ".")), value > 0 else {
            errorMessage = s.errInvalidAmount
            return
        }
        guard let methodId = selectedMethodId else {
            errorMessage = s.errChooseMethod
            return
        }
        guard Validators.isValidWithdrawalPin(pin) else {
            errorMessage = s.errPin6
            return
        }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            try await repository.createRequest(amount: value, pin: pin, payoutMethodId: methodId)
            requests = (try? await repository.myRequests()) ?? requests
            submitted = true
            amount = ""
            net = nil
        } catch {
            errorMessage = Self.friendly(error)
        }
    }

    /// Réinitialise le drapeau de succès (après affichage du message).
    public func consumeSubmitted() { submitted = false }

    /// Traduit les codes d'erreur sensibles du flux de retrait.
    private static func friendly(_ error: Error) -> String {
        let s = AppStrings.current
        guard let api = error as? APIError else { return s.errOperationFailed }
        switch api.code {
        case ErrorCode.pinWrong: return s.errPinWrong
        case ErrorCode.pinLocked: return s.errPinLocked
        default: break
        }
        if api.isInsufficientBalance { return s.errBalanceTooLow }
        return api.message
    }
}

/// Écran de retrait des crédits.
///
/// Étapes : (1) définir un PIN si absent, (2) choisir une méthode, (3) saisir un montant
/// (net après frais affiché, calculé serveur), (4) confirmer avec le PIN. Historique en bas.
public struct WithdrawalView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: WithdrawalViewModel

    @State private var pin = ""
    @State private var newPin = ""

    /// - Parameter viewModel: état + actions.
    public init(viewModel: WithdrawalViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                if viewModel.isLoading { DMLoadingBox().frame(height: 80) }
                if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }
                if viewModel.submitted { DMMessage(s.withdrawSubmitted) }

                switch viewModel.hasPin {
                case .some(false):
                    createPinCard
                case .some(true):
                    withdrawForm
                case nil:
                    EmptyView() // en chargement
                }

                if !viewModel.requests.isEmpty {
                    DMSectionTitle(s.history)
                    ForEach(viewModel.requests) { RequestRow(request: $0) }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }

    /// Carte de création du PIN de retrait (première configuration).
    private var createPinCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.createWithdrawPin)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.foreground)
                pinField(text: $newPin)
                DMButton(s.createPin, isEnabled: newPin.count == 6) {
                    Task { await viewModel.createPin(newPin) }
                }
            }
        }
    }

    /// Formulaire de retrait : méthode + montant + net + PIN.
    @ViewBuilder
    private var withdrawForm: some View {
        if viewModel.methods.isEmpty {
            DMCard {
                Text(s.noWithdrawMethod)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }
        } else {
            methodsSection
            amountSection
            pinSection
        }
    }

    /// Liste des méthodes de retrait enregistrées (sélection par tap).
    private var methodsSection: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            DMSectionTitle(s.method)
            ForEach(viewModel.methods) { method in
                Button { viewModel.selectMethod(id: method.id) } label: {
                    DMCard(isSelected: method.id == viewModel.selectedMethodId) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(method.label ?? method.method)
                                .font(DMFont.body).bold()
                                .foregroundStyle(theme.colors.foreground)
                            Text(method.subtitle)
                                .font(DMFont.caption)
                                .foregroundStyle(theme.colors.mutedForeground)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// Montant à retirer + aperçu du net après frais (calculé serveur).
    @ViewBuilder
    private var amountSection: some View {
        DMTextField(
            s.amountCredits,
            text: Binding(get: { viewModel.amount }, set: { viewModel.onAmountChange($0) }),
            keyboard: .decimalPad,
            autocapitalization: .never
        )

        if let net = viewModel.net {
            DMCard {
                HStack {
                    Text("\(s.fees) : \(Int(net.feePct)) %")
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                    Spacer()
                    Text("\(s.net) : \(formatAmount(net.net)) \(s.credits)")
                        .font(DMFont.mono)
                        .foregroundStyle(theme.colors.primary)
                }
            }
        }
    }

    /// PIN de retrait + bouton de confirmation.
    private var pinSection: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            DMSectionTitle(s.withdrawPin)
            pinField(text: $pin)
            DMButton(
                viewModel.isSubmitting ? s.sending : s.requestWithdraw,
                isLoading: viewModel.isSubmitting,
                isEnabled: !viewModel.isSubmitting && pin.count == 6
            ) {
                Task {
                    await viewModel.submit(pin: pin)
                    pin = ""
                }
            }
        }
    }

    /// Champ PIN 6 chiffres (clavier numérique, masqué).
    private func pinField(text: Binding<String>) -> some View {
        DMTextField("••••••", text: text, isSecure: true, keyboard: .numberPad, autocapitalization: .never)
            .onChange(of: text.wrappedValue) { _, newValue in
                let digits = newValue.digitsOnly.take(6)
                if digits != newValue { text.wrappedValue = digits }
            }
    }
}

/// Ligne d'historique d'une demande de retrait.
private struct RequestRow: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    let request: WithdrawalRequest

    var body: some View {
        DMCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(formatAmount(request.amount)) \(s.credits)")
                        .font(DMFont.mono)
                        .foregroundStyle(theme.colors.foreground)
                    if let day = isoDay(request.createdAt) {
                        Text(day).font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
                    }
                }
                Spacer()
                Text(statusLabel)
                    .font(DMFont.caption).bold()
                    .foregroundStyle(statusColor)
            }
        }
    }

    private var statusLabel: String {
        switch request.status {
        case .pending: return s.statusPending
        case .approved: return s.statusApproved
        case .processing: return s.statusProcessing
        case .completed: return s.statusPaid
        case .rejected: return s.statusRejected
        case .failed: return s.statusFailed
        }
    }

    private var statusColor: Color {
        switch request.status {
        case .completed: return theme.colors.primary
        // `processing` reste neutre : l'argent n'est pas encore chez l'utilisateur.
        case .rejected, .failed: return theme.colors.destructive
        default: return theme.colors.mutedForeground
        }
    }
}

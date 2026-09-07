import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel de la recharge de crédits par **Mobile Money** (CinetPay).
///
/// Récupère le catalogue de pays, initie le paiement (avec `Idempotency-Key`) et expose
/// l'URL hébergée à ouvrir. Le crédit du compte se fait ensuite côté serveur (webhook) :
/// l'app n'ajoute jamais de crédits elle-même. Miroir de `RechargeViewModel` Android.
@Observable
@MainActor
public final class RechargeViewModel {

    public private(set) var countries: [CinetpayCountry] = []
    public private(set) var selected: CinetpayCountry?
    /// Code de l'opérateur Mobile Money choisi (ex. `OM`, `MOMO`).
    public private(set) var selectedOperator: String?
    public var amount: String = ""
    public var phone: String = ""
    public private(set) var isLoading = false
    public private(set) var message: String?
    /// URL de paiement à ouvrir (consommée par la vue puis remise à `nil`).
    public private(set) var paymentURL: URL?

    private let http: HTTPClient

    /// - Parameter http: client HTTP applicatif.
    public init(http: HTTPClient) {
        self.http = http
    }

    /// Charge la liste des pays Mobile Money disponibles et présélectionne le premier.
    public func load() async {
        let list = (try? await http.request(
            .get(PaymentEndpoints.cinetpayCountries, anonymous: true),
            as: [CinetpayCountry].self
        )) ?? []
        countries = list
        if selected == nil {
            selected = list.first
            selectedOperator = list.first?.operators.first?.code
        }
    }

    /// Sélectionne un pays et réinitialise l'opérateur sur le premier disponible.
    public func select(country: CinetpayCountry) {
        selected = country
        selectedOperator = country.operators.first?.code
        message = nil
    }

    /// Sélectionne l'opérateur Mobile Money.
    public func select(operatorCode: String) {
        selectedOperator = operatorCode
        message = nil
    }

    /// Initie le paiement ; en cas de succès, expose l'URL hébergée à ouvrir.
    public func pay() async {
        let s = AppStrings.current
        guard let credits = Int(amount.digitsOnly), credits >= 1 else {
            message = s.errEnterValidAmount
            return
        }
        guard let country = selected else {
            message = s.errChooseCountry
            return
        }
        isLoading = true
        message = nil
        defer { isLoading = false }
        do {
            let response: CinetpayInitResponse = try await http.request(
                .post(
                    PaymentEndpoints.cinetpayInit,
                    body: CinetpayInitRequest(
                        amount: credits,
                        countryCode: country.countryCode,
                        phone: phone.nilIfBlank,
                        paymentMethod: selectedOperator ?? country.operators.first?.code
                    ),
                    idempotencyKey: UUID().uuidString
                ),
                as: CinetpayInitResponse.self
            )
            paymentURL = URL(string: response.paymentUrl)
            message = s.openingPayment
        } catch {
            message = (error as? APIError)?.message ?? s.errRechargeFailed
        }
    }

    /// À appeler après avoir ouvert l'URL, pour éviter de la rouvrir.
    public func consumePaymentURL() {
        paymentURL = nil
    }
}

/// Écran de recharge : montant en crédits + pays + opérateur + numéro Mobile Money →
/// ouvre le paiement hébergé. Le compte est crédité automatiquement après paiement.
///
/// Miroir de `RechargeScreen` Android ; l'ouverture de l'URL utilise `openURL`
/// (Safari View Controller système) au lieu d'un `Intent`.
public struct RechargeView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    @Environment(\.openURL) private var openURL

    @Bindable private var viewModel: RechargeViewModel

    /// - Parameter viewModel: source d'état.
    public init(viewModel: RechargeViewModel) {
        self._viewModel = Bindable(viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(s.rechargeCredits)
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)
                Text(s.rechargeHint)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)

                DMCard {
                    VStack(spacing: theme.spacing.md) {
                        DMTextField(
                            s.amountCredits,
                            text: $viewModel.amount,
                            keyboard: .numberPad,
                            autocapitalization: .never
                        )
                        .onChange(of: viewModel.amount) { _, newValue in
                            let digits = newValue.digitsOnly
                            if digits != newValue { viewModel.amount = digits }
                        }

                        DMPicker(
                            label: s.country,
                            selection: viewModel.selected?.displayName ?? s.chooseDots,
                            options: viewModel.countries
                        ) { country in
                            Text("\(country.displayName) \(country.phonePrefix ?? "")")
                        } onSelect: { country in
                            viewModel.select(country: country)
                        }

                        if let operators = viewModel.selected?.operators, !operators.isEmpty {
                            DMPicker(
                                label: s.operatorLabel,
                                selection: currentOperatorLabel(operators),
                                options: operators
                            ) { op in
                                Text(op.displayLabel)
                            } onSelect: { op in
                                viewModel.select(operatorCode: op.code)
                            }
                        }

                        DMTextField(
                            s.mobileMoneyNumber,
                            text: $viewModel.phone,
                            placeholder: "\(viewModel.selected?.phonePrefix ?? "")…",
                            keyboard: .phonePad,
                            autocapitalization: .never
                        )

                        if let message = viewModel.message { DMMessage(message) }

                        DMButton(
                            viewModel.isLoading ? s.initializing : s.payByMobileMoney,
                            isLoading: viewModel.isLoading,
                            isEnabled: !viewModel.isLoading
                        ) {
                            Task { await viewModel.pay() }
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
        // Ouvre la page de paiement hébergée dès qu'elle est disponible, puis la consomme.
        .onChange(of: viewModel.paymentURL) { _, url in
            guard let url else { return }
            openURL(url)
            viewModel.consumePaymentURL()
        }
    }

    /// Libellé de l'opérateur courant (repli sur le code puis sur « Choisir… »).
    private func currentOperatorLabel(_ operators: [CinetpayOperator]) -> String {
        if let code = viewModel.selectedOperator,
           let match = operators.first(where: { $0.code == code }) {
            return match.displayLabel
        }
        return viewModel.selectedOperator ?? s.chooseDots
    }
}

import Foundation

/*
 * DTOs de paiement — miroir de `shared-domain/payment/PaymentDtos.kt`.
 *
 * Recharge de crédits par **Mobile Money** (CinetPay), flux « hosted payment URL » :
 *  1. `CinetpayInitRequest` → `POST /payments/cinetpay/init` (Bearer + `Idempotency-Key`)
 *     → `CinetpayInitResponse` (URL de paiement hébergée).
 *  2. L'app ouvre `paymentUrl` (SFSafariViewController / navigateur système).
 *  3. Le webhook serveur crédite le compte automatiquement (idempotent) — aucune action app.
 *
 * ⚠️ Requête en camelCase ; réponses `cinetpay/init` en camelCase, catalogue pays en snake_case.
 */

/// Corps de `POST /payments/cinetpay/init`.
public struct CinetpayInitRequest: Encodable, Sendable {
    /// Montant en crédits (entier ≥ 1).
    public let amount: Int
    /// Code pays ISO-2 (ex. `CI`, `CM`).
    public let countryCode: String
    public let phone: String?
    /// Opérateur Mobile Money (code d'un `operators` du pays) ; optionnel.
    public let paymentMethod: String?

    public init(amount: Int, countryCode: String, phone: String? = nil, paymentMethod: String? = nil) {
        self.amount = amount
        self.countryCode = countryCode
        self.phone = phone
        self.paymentMethod = paymentMethod
    }
}

/// Réponse de `POST /payments/cinetpay/init`.
public struct CinetpayInitResponse: Codable, Sendable {
    public let paymentUrl: String
    public let merchantTransactionId: String?
    public let credits: Int

    enum CodingKeys: String, CodingKey { case paymentUrl, merchantTransactionId, credits }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        paymentUrl = c.val(String.self, .paymentUrl, "")
        merchantTransactionId = c.opt(String.self, .merchantTransactionId)
        credits = c.int(.credits)
    }
}

/// Opérateur Mobile Money d'un pays.
public struct CinetpayOperator: Codable, Sendable, Identifiable, Equatable {
    public let code: String
    public let label: String?

    public var id: String { code }

    enum CodingKeys: String, CodingKey { case code, label }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        code = c.val(String.self, .code, "")
        label = c.opt(String.self, .label)
    }

    /// Libellé affichable (repli sur le code).
    public var displayLabel: String { label ?? code }
}

/// Pays actif pour la recharge (`GET /payments/cinetpay/countries`).
public struct CinetpayCountry: Codable, Sendable, Identifiable, Equatable {
    public let countryCode: String
    public let countryName: String?
    public let currency: String?
    public let phonePrefix: String?
    public let operators: [CinetpayOperator]

    public var id: String { countryCode }

    enum CodingKeys: String, CodingKey {
        case countryCode = "country_code"
        case countryName = "country_name"
        case currency
        case phonePrefix = "phone_prefix"
        case operators
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        countryCode = c.val(String.self, .countryCode, "")
        countryName = c.opt(String.self, .countryName)
        currency = c.opt(String.self, .currency)
        phonePrefix = c.opt(String.self, .phonePrefix)
        operators = c.val([CinetpayOperator].self, .operators, [])
    }

    /// Libellé affichable (nom du pays, repli sur le code ISO).
    public var displayName: String { countryName ?? countryCode }
}

/// Corps de `POST /payments/stripe/subscription` — achat d'abonnement (carte).
public struct StripeSubscriptionRequest: Encodable, Sendable {
    public let plan: String
    public init(plan: String) { self.plan = plan }
}

/// Réponse d'un checkout Stripe (`{ url }`).
public struct StripeCheckoutResponse: Codable, Sendable {
    public let url: String

    enum CodingKeys: String, CodingKey { case url }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = c.val(String.self, .url, "")
    }
}

/// Achat de crédits (recharge) du caller — élément de `GET /payments/history`.
public struct CreditPurchase: Codable, Sendable, Identifiable {
    public let id: String
    public let creditsAmount: Double
    public let paidAmount: Double?
    public let currency: String?
    /// `pending` | `completed` | `failed`…
    public let status: String?
    public let paymentMethod: String?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case creditsAmount = "credits_amount"
        case paidAmount = "paid_amount"
        case currency, status
        case paymentMethod = "payment_method"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, UUID().uuidString)
        creditsAmount = c.amount(.creditsAmount)
        paidAmount = c.amountIfPresent(.paidAmount)
        currency = c.opt(String.self, .currency)
        status = c.opt(String.self, .status)
        paymentMethod = c.opt(String.self, .paymentMethod)
        createdAt = c.opt(String.self, .createdAt)
    }
}

import Foundation

/*
 * DTOs de retrait — miroir de `shared-domain/withdrawal/WithdrawalDtos.kt`.
 *
 * Sécurité : la création d'un retrait exige le **PIN de retrait** (6 chiffres), re-vérifié
 * côté serveur avec verrouillage après échecs répétés. La demande est idempotente.
 */

/// Méthode de retrait enregistrée (`user_payout_methods`).
///
/// ⚠️ Valeur `method` canonique côté backend : `mobile_money | bank | paypal`
/// (ne PAS utiliser `bank_transfer`).
public struct PayoutMethodData: Codable, Sendable, Identifiable, Equatable {
    public let id: String
    public let method: String
    public let label: String?
    public let accountHolder: String?
    public let bankName: String?
    public let iban: String?
    public let paypalEmail: String?
    public let phoneNumber: String?
    public let mobileOperator: String?
    public let isDefault: Bool

    enum CodingKeys: String, CodingKey {
        case id, method, label
        case accountHolder = "account_holder"
        case bankName = "bank_name"
        case iban
        case paypalEmail = "paypal_email"
        case phoneNumber = "phone_number"
        case mobileOperator = "mobile_operator"
        case isDefault = "is_default"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.val(String.self, .id, "")
        method = c.val(String.self, .method, "")
        label = c.opt(String.self, .label)
        accountHolder = c.opt(String.self, .accountHolder)
        bankName = c.opt(String.self, .bankName)
        iban = c.opt(String.self, .iban)
        paypalEmail = c.opt(String.self, .paypalEmail)
        phoneNumber = c.opt(String.self, .phoneNumber)
        mobileOperator = c.opt(String.self, .mobileOperator)
        isDefault = c.bool(.isDefault)
    }

    /// Sous-titre lisible selon le type de méthode (opérateur • numéro, banque • IBAN, email).
    public var subtitle: String {
        switch method {
        case "mobile_money":
            return [mobileOperator, phoneNumber].compactMap { $0?.isEmpty == false ? $0 : nil }.joined(separator: " • ")
        case "bank":
            return [bankName, iban].compactMap { $0?.isEmpty == false ? $0 : nil }.joined(separator: " • ")
        case "paypal":
            return paypalEmail ?? ""
        default:
            return ""
        }
    }
}

/// Réponse de `GET /withdrawals/pin` — l'utilisateur a-t-il déjà un PIN de retrait.
public struct PinStatus: Codable, Sendable {
    public let hasPin: Bool

    enum CodingKeys: String, CodingKey { case hasPin }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hasPin = c.bool(.hasPin)
    }
}

/// Réponse de `POST /withdrawals/pin/verify`.
public struct PinVerifyResult: Codable, Sendable {
    public let valid: Bool

    enum CodingKeys: String, CodingKey { case valid }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        valid = c.bool(.valid)
    }
}

/// Corps de `POST /withdrawals/pin` — création/remplacement du PIN (6 chiffres).
public struct SetPinRequest: Encodable, Sendable {
    public let newPin: String
    public let currentPin: String?
    public init(newPin: String, currentPin: String? = nil) {
        self.newPin = newPin
        self.currentPin = currentPin
    }
}

/// Corps de `POST /withdrawals/net` — aperçu du net après frais.
public struct NetRequest: Encodable, Sendable {
    public let amount: Double
    public init(amount: Double) { self.amount = amount }
}

/// Corps de `POST /withdrawals` — demande de retrait.
///
/// Le ``pin`` (6 chiffres) est re-vérifié côté serveur avec verrouillage après échecs.
public struct CreateWithdrawalRequest: Encodable, Sendable {
    public let amount: Double
    public let pin: String
    public let payoutMethodId: String?
    public let provider: String?

    public init(amount: Double, pin: String, payoutMethodId: String? = nil, provider: String? = nil) {
        self.amount = amount
        self.pin = pin
        self.payoutMethodId = payoutMethodId
        self.provider = provider
    }
}

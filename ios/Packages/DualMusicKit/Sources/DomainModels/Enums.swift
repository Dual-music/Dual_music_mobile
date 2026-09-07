import Foundation

/// Rôles utilisateur (source de vérité : table `user_roles` du backend).
///
/// Un utilisateur peut cumuler plusieurs rôles. Miroir de
/// `shared-domain/model/Enums.kt#UserRole`.
public enum UserRole: String, Codable, Sendable, CaseIterable {
    case fan
    case artist
    case manager
    case moderator
    case admin

    /// Décodage tolérant : un rôle inconnu (ajouté côté backend) retombe sur `.fan`
    /// au lieu de faire échouer toute la réponse.
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = UserRole(rawValue: raw) ?? .fan
    }
}

/// Cycle de vie d'un duel / live / concert / compétition (valeurs backend).
///
/// Miroir de `shared-domain/model/Enums.kt#EventStatus`.
public enum EventStatus: String, Codable, Sendable {
    case upcoming
    case live
    case ended
    case cancelled
    /// Statut spécifique aux concerts en attente de validation admin.
    case pending
    case approved
    case rejected

    /// Décodage tolérant (statut inconnu → `.upcoming`).
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = EventStatus(rawValue: raw) ?? .upcoming
    }
}

/// Contexte d'un cadeau / flux d'engagement (aligne les rooms temps réel).
public enum EventContext: String, Codable, Sendable {
    case duel, live, concert, competition
}

/// Méthode de retrait (payout).
///
/// ⚠️ Valeurs canoniques backend : `mobile_money | bank | paypal`. Ne jamais réintroduire
/// `bank_transfer` (valeur web historique, invalide côté API).
public enum PayoutMethod: String, Codable, Sendable {
    case mobileMoney = "mobile_money"
    case bank
    case paypal
}

/// Fournisseur de paiement pour la **recharge** de crédits.
///
/// Sur mobile, l'achat de crédits doit passer par le magasin (`apple_iap` sur iOS) pour
/// être conforme aux règles App Store dès lors qu'il s'agit de contenu numérique consommé
/// dans l'app. Les fournisseurs `cinetpay` / `moneroo` / `stripe` restent pour le web.
public enum RechargeProvider: String, Codable, Sendable {
    case appleIAP = "apple_iap"
    case googlePlay = "google_play"
    case cinetpay
    case moneroo
    case stripe
}

/// Statut d'une demande de retrait.
///
/// Cycle de vie : `pending` → `approved` → `processing` → `completed` | `failed`,
/// ou `rejected` si un administrateur refuse la demande (crédits recrédités).
///
/// - `processing` : l'ordre de transfert est parti chez l'opérateur Mobile Money.
///   Les crédits sont débités mais l'argent n'est pas encore versé — cet état
///   empêche qu'un même retrait parte deux fois.
/// - `failed` : le transfert a définitivement échoué ; les crédits ont été rendus.
public enum WithdrawalStatus: String, Codable, Sendable {
    case pending
    case approved
    case processing
    case completed
    case rejected
    case failed

    /// Décodage tolérant (statut inconnu → `.pending`).
    public init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = WithdrawalStatus(rawValue: raw) ?? .pending
    }
}

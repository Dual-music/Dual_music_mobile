import Foundation

/// Résultat d'un aperçu de net de retrait (affichage uniquement).
public struct NetPreview: Sendable, Equatable {
    public let fee: Double
    public let net: Double
    public let feePct: Double

    public init(fee: Double, net: Double, feePct: Double) {
        self.fee = fee
        self.net = net
        self.feePct = feePct
    }
}

/// Calculs économiques d'**APERÇU** — affichage uniquement.
///
/// ⚠️ RÈGLE CRITIQUE : ces fonctions servent à donner un aperçu instantané dans l'UI
/// (« ≈ 5,00 € », « net estimé »). Elles ne font **PAS foi**. Les montants réels (débits,
/// splits, commissions, crédits portés) sont calculés et validés par le backend via des
/// procédures stockées atomiques. Toujours confirmer via l'API (`/withdrawals/net`,
/// endpoints pricing, solde) avant d'engager de l'argent.
///
/// Miroir de `shared-domain/economy/CreditMath.kt`.
public enum CreditMath {

    /// Invariant métier : 1 crédit = 0,50 €. (Le backend reste la référence.)
    public static let creditEurValue: Double = 0.50

    /// Aperçu de la contre-valeur en euros d'un montant de crédits.
    /// - Parameter credits: nombre de crédits.
    /// - Returns: valeur affichable en euros (non contractuelle).
    public static func creditsToEurPreview(_ credits: Double) -> Double {
        credits * creditEurValue
    }

    /// Aperçu du nombre de crédits pour un montant en euros (arrondi **plancher**, comme le
    /// backend qui plafonne au crédit entier).
    /// - Parameter eur: montant en euros.
    public static func eurToCreditsPreview(_ eur: Double) -> Int {
        guard creditEurValue > 0 else { return 0 }
        return Int((eur / creditEurValue).rounded(.down))
    }

    /// Aperçu du net d'un retrait après frais.
    /// - Parameters:
    ///   - amountCredits: montant brut demandé (crédits).
    ///   - feePct: pourcentage de frais applicable (obtenu de la config plateforme).
    /// - Returns: frais, net et pourcentage — le net **réel** vient de `POST /withdrawals/net`.
    public static func withdrawalNetPreview(amountCredits: Double, feePct: Double) -> NetPreview {
        let fee = roundCredits(amountCredits * feePct / 100.0)
        let net = roundCredits(amountCredits - fee)
        return NetPreview(fee: fee, net: net, feePct: feePct)
    }

    /// Arrondi à 2 décimales (les crédits sont en `DECIMAL(18,2)` côté backend).
    private static func roundCredits(_ value: Double) -> Double {
        (value * 100).rounded() / 100.0
    }
}

/// Validation d'entrée côté client (**aperçu UX seulement**).
///
/// But : feedback immédiat dans les formulaires (activer/désactiver un bouton, message
/// inline). La validation **faisant foi** reste côté backend (Joi). Ces règles reflètent
/// celles du serveur pour éviter des allers-retours inutiles, sans les remplacer.
///
/// Miroir de `shared-domain/validation/Validators.kt`.
public enum Validators {

    /// OTP : exactement 6 chiffres (aligné sur `OTP_LENGTH=6`).
    public static func isValidOtp(_ code: String) -> Bool {
        matches(code, "^[0-9]{6}$")
    }

    /// PIN de retrait : exactement 6 chiffres.
    public static func isValidWithdrawalPin(_ pin: String) -> Bool {
        matches(pin, "^[0-9]{6}$")
    }

    /// Téléphone au format E.164 approximatif : `+` suivi de 8 à 15 chiffres.
    public static func isValidPhone(_ phone: String) -> Bool {
        matches(phone.trimmingCharacters(in: .whitespacesAndNewlines), "^\\+?[1-9][0-9]{7,14}$")
    }

    /// Email — vérification légère (le backend valide strictement).
    public static func isValidEmail(_ email: String) -> Bool {
        matches(email.trimmingCharacters(in: .whitespacesAndNewlines), "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }

    /// Montant positif et plafonné (crédits/euros).
    public static func isValidAmount(_ amount: Double) -> Bool {
        amount > 0 && amount <= 1_000_000
    }

    /// Vrai si le montant demandé est couvert par le solde (aperçu avant appel API).
    public static func canAfford(amount: Double, balance: Double) -> Bool {
        amount > 0 && amount <= balance
    }

    /// Test d'expression régulière sur la chaîne **entière**.
    private static func matches(_ value: String, _ pattern: String) -> Bool {
        value.range(of: pattern, options: .regularExpression) != nil
    }
}

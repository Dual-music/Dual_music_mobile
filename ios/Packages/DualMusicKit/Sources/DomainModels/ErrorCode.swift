import Foundation

/// Catalogue des codes d'erreur connus du backend (non exhaustif — voir OpenAPI).
///
/// Centralisé ici pour un branchement UI cohérent iOS/Android : l'app teste **toujours**
/// `code` (stable), jamais `message` (localisé côté serveur).
/// Miroir de `shared-domain/api/DomainError.kt#ErrorCode`.
public enum ErrorCode {
    public static let unknown = "UNKNOWN"
    public static let network = "NETWORK_ERROR"
    public static let validation = "VALIDATION_ERROR"
    public static let notFound = "NOT_FOUND"
    public static let forbidden = "FORBIDDEN"
    public static let conflict = "CONFLICT"

    // Portefeuille / paiements
    public static let walletInsufficient = "WALLET_INSUFFICIENT"
    public static let amountInvalid = "AMOUNT_INVALID"
    public static let alreadyTicketed = "ALREADY_TICKETED"
    public static let ticketsSoldOut = "TICKETS_SOLD_OUT"
    public static let paymentProviderError = "PAYMENT_PROVIDER_ERROR"

    // PIN de retrait
    public static let pinWrong = "PIN_WRONG"
    public static let pinNotSet = "PIN_NOT_SET"
    public static let pinLocked = "PIN_LOCKED"

    // OTP / vérification
    public static let otpInvalid = "OTP_INVALID"
    public static let otpExpired = "OTP_EXPIRED"
}

import Foundation

/// Formatage de la devise interne, aligné sur le web **et** sur Android : le nombre
/// (séparateur de milliers, sans décimale inutile) suivi du mot **« Crédit » / « Crédits »**
/// — jamais « cr. » ni « 💎 » seul.
///
/// ```swift
/// formatCredits(1250)  // "1 250 Crédits"
/// formatCredits(1)     // "1 Crédit"
/// formatCredits(12.5)  // "12,5 Crédits"
/// ```
///
/// - Parameters:
///   - credits: montant en crédits.
///   - withUnit: ajoute le mot « Crédit(s) » (défaut) ; `false` = nombre seul.
///   - locale: locale de formatage (défaut : celle de l'appareil).
/// - Returns: la chaîne prête à afficher.
public func formatCredits(_ credits: Double, withUnit: Bool = true, locale: Locale = .current) -> String {
    let number = formatAmount(credits, locale: locale)
    guard withUnit else { return number }
    let unit = credits > 1 ? "Crédits" : "Crédit"
    return "\(number) \(unit)"
}

/// Formate un montant : séparateur de milliers, 0 décimale si l'entier est exact, 2 sinon.
///
/// - Parameters:
///   - value: valeur à formater.
///   - locale: locale de formatage.
/// - Returns: la représentation numérique.
public func formatAmount(_ value: Double, locale: Locale = .current) -> String {
    let f = NumberFormatter()
    f.locale = locale
    f.numberStyle = .decimal
    f.maximumFractionDigits = value.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2
    return f.string(from: NSNumber(value: value)) ?? String(value)
}

/// Formate une contre-valeur en euros (« ≈ 12,50 € »).
///
/// - Parameters:
///   - eur: montant en euros (calculé **par le backend**).
///   - locale: locale de formatage.
public func formatEuro(_ eur: Double, locale: Locale = .current) -> String {
    let f = NumberFormatter()
    f.locale = locale
    f.numberStyle = .currency
    f.currencyCode = "EUR"
    return "≈ " + (f.string(from: NSNumber(value: eur)) ?? String(format: "%.2f €", eur))
}

/// Extrait la partie date (10 premiers caractères) d'un horodatage ISO 8601.
///
/// Les listes affichent `2026-08-01` ; on évite un `DateFormatter` complet pour rester
/// aligné avec Android (`it.take(10)`) et éviter tout décalage de fuseau à l'affichage.
/// - Parameter iso: horodatage ISO (peut être `nil`).
public func isoDay(_ iso: String?) -> String? {
    guard let iso, iso.count >= 10 else { return iso }
    return String(iso.prefix(10))
}

/// Extrait date + heure (`2026-08-01T20:00`) d'un horodatage ISO 8601.
/// - Parameter iso: horodatage ISO (peut être `nil`).
public func isoMinute(_ iso: String?) -> String? {
    guard let iso, iso.count >= 16 else { return iso }
    return String(iso.prefix(16))
}

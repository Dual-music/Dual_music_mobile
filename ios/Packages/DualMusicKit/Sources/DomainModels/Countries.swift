import Foundation

/// Pays sélectionnable (inscription, édition de profil).
///
/// - `code` : code ISO-2 envoyé au backend en `countryCode` (ex. `CI`).
/// - `dial` : indicatif téléphonique envoyé en `phoneCountryCode` (ex. `+225`).
public struct Country: Sendable, Identifiable, Equatable, Hashable {
    public let code: String
    public let name: String
    public let dial: String

    public var id: String { code }

    public init(code: String, name: String, dial: String) {
        self.code = code
        self.name = name
        self.dial = dial
    }

    /// Libellé affiché dans les sélecteurs : « France (+33) ».
    public var label: String { "\(name) (\(dial))" }
}

/// Liste curatée des pays — marchés cibles de Dual Music (Afrique francophone + Europe +
/// quelques grands marchés). Partagée entre l'inscription et l'édition de profil.
/// Miroir strict de `shared-domain/geo/Countries.kt` (même ordre, mêmes valeurs).
public enum Countries {

    /// Tous les pays proposés, dans l'ordre d'affichage.
    public static let all: [Country] = [
        Country(code: "CI", name: "Côte d'Ivoire", dial: "+225"),
        Country(code: "SN", name: "Sénégal", dial: "+221"),
        Country(code: "CM", name: "Cameroun", dial: "+237"),
        Country(code: "ML", name: "Mali", dial: "+223"),
        Country(code: "BF", name: "Burkina Faso", dial: "+226"),
        Country(code: "BJ", name: "Bénin", dial: "+229"),
        Country(code: "TG", name: "Togo", dial: "+228"),
        Country(code: "NE", name: "Niger", dial: "+227"),
        Country(code: "GN", name: "Guinée", dial: "+224"),
        Country(code: "CG", name: "Congo", dial: "+242"),
        Country(code: "CD", name: "RD Congo", dial: "+243"),
        Country(code: "GA", name: "Gabon", dial: "+241"),
        Country(code: "TD", name: "Tchad", dial: "+235"),
        Country(code: "MG", name: "Madagascar", dial: "+261"),
        Country(code: "CF", name: "Centrafrique", dial: "+236"),
        Country(code: "MR", name: "Mauritanie", dial: "+222"),
        Country(code: "MA", name: "Maroc", dial: "+212"),
        Country(code: "DZ", name: "Algérie", dial: "+213"),
        Country(code: "TN", name: "Tunisie", dial: "+216"),
        Country(code: "NG", name: "Nigeria", dial: "+234"),
        Country(code: "GH", name: "Ghana", dial: "+233"),
        Country(code: "FR", name: "France", dial: "+33"),
        Country(code: "BE", name: "Belgique", dial: "+32"),
        Country(code: "CH", name: "Suisse", dial: "+41"),
        Country(code: "LU", name: "Luxembourg", dial: "+352"),
        Country(code: "CA", name: "Canada", dial: "+1"),
        Country(code: "US", name: "États-Unis", dial: "+1"),
        Country(code: "GB", name: "Royaume-Uni", dial: "+44"),
        Country(code: "DE", name: "Allemagne", dial: "+49"),
        Country(code: "ES", name: "Espagne", dial: "+34"),
        Country(code: "IT", name: "Italie", dial: "+39"),
        Country(code: "PT", name: "Portugal", dial: "+351"),
    ]

    /// Pays par défaut (France).
    public static let `default`: Country = all.first { $0.code == "FR" }
        ?? Country(code: "FR", name: "France", dial: "+33")

    /// Retrouve un pays par son code ISO-2, sinon ``default``.
    /// - Parameter code: code ISO-2 (ex. `CI`).
    public static func byCode(_ code: String?) -> Country {
        all.first { $0.code == code } ?? `default`
    }

    /// Retrouve un pays par son indicatif (`+225`), sinon `nil`.
    /// - Parameter dial: indicatif téléphonique.
    public static func byDial(_ dial: String?) -> Country? {
        all.first { $0.dial == dial }
    }
}

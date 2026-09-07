import Foundation

/*
 * Utilitaires de chaîne partagés par tous les modules.
 *
 * Déclarés ici (et non dupliqués dans chaque feature) pour garantir un comportement
 * identique partout : c'est le même nettoyage d'entrée qui est appliqué avant chaque appel
 * réseau, quel que soit l'écran.
 */

public extension String {

    /// Chaîne sans espaces ni retours à la ligne en début/fin.
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }

    /// La chaîne nettoyée, ou `nil` si elle est vide — pratique pour n'envoyer au backend
    /// que les champs réellement renseignés.
    var nilIfBlank: String? {
        let value = trimmed
        return value.isEmpty ? nil : value
    }

    /// Chaîne sans les caractères indiqués en début/fin.
    /// - Parameter set: caractères à retirer (ex. `"/"`).
    func trimmed(charactersIn set: String) -> String {
        trimmingCharacters(in: CharacterSet(charactersIn: set))
    }

    /// Ne conserve que les chiffres (saisie de code OTP, PIN, numéro de téléphone).
    var digitsOnly: String { filter(\.isNumber) }

    /// Les `count` premiers caractères (équivalent de `take(n)` en Kotlin).
    /// - Parameter count: nombre de caractères à conserver.
    func take(_ count: Int) -> String { String(prefix(count)) }

    /// Première lettre en majuscule, reste inchangé (équivalent de
    /// `replaceFirstChar { it.uppercase() }`).
    var capitalizedFirst: String {
        guard let first else { return self }
        return first.uppercased() + dropFirst()
    }
}

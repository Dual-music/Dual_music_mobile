import Foundation

/*
 * Outils de décodage tolérants — équivalent Swift de la configuration kotlinx utilisée
 * côté Android : `Json { ignoreUnknownKeys = true; explicitNulls = false }` + valeurs par
 * défaut sur les champs de DTO.
 *
 * Swift ignore déjà les clés inconnues, MAIS lève une erreur dès qu'une clé attendue est
 * absente ou d'un type inattendu. Or le backend :
 *  - omet fréquemment les champs nuls (`explicitNulls = false` côté serveur aussi) ;
 *  - renvoie parfois les colonnes `DECIMAL(18,2)` sous forme de **chaîne** (comportement
 *    par défaut du driver PostgreSQL sur `numeric`).
 *
 * Ces helpers rendent le décodage résilient : une valeur manquante ou mal typée retombe
 * sur le défaut au lieu de faire échouer TOUTE la réponse.
 */

public extension KeyedDecodingContainer {

    /// Décode une valeur **optionnelle**, en tolérant l'absence de clé ET un type inattendu.
    ///
    /// - Parameters:
    ///   - type: type attendu.
    ///   - key: clé JSON.
    /// - Returns: la valeur décodée, ou `nil` si absente/illisible.
    func opt<T: Decodable>(_ type: T.Type, _ key: Key) -> T? {
        (try? decodeIfPresent(T.self, forKey: key)) ?? nil
    }

    /// Décode une valeur avec **repli** si la clé est absente ou illisible.
    ///
    /// - Parameters:
    ///   - type: type attendu.
    ///   - key: clé JSON.
    ///   - fallback: valeur utilisée si le décodage échoue.
    func val<T: Decodable>(_ type: T.Type, _ key: Key, _ fallback: T) -> T {
        opt(T.self, key) ?? fallback
    }

    /// Décode un **montant** (crédits, prix, totaux) de façon tolérante.
    ///
    /// Accepte un nombre JSON *ou* une chaîne numérique (`"12.50"`), car les colonnes
    /// `numeric` de PostgreSQL peuvent être sérialisées en chaîne. Les montants faisant
    /// foi restent ceux calculés par le backend — ce décodage ne fait que les lire.
    ///
    /// - Parameters:
    ///   - key: clé JSON.
    ///   - fallback: valeur si absente/illisible (défaut `0`).
    func amount(_ key: Key, _ fallback: Double = 0) -> Double {
        if let d = opt(Double.self, key) { return d }
        if let s = opt(String.self, key), let d = Double(s) { return d }
        return fallback
    }

    /// Variante optionnelle de ``amount(_:_:)`` : `nil` si le champ est absent.
    func amountIfPresent(_ key: Key) -> Double? {
        if let d = opt(Double.self, key) { return d }
        if let s = opt(String.self, key) { return Double(s) }
        return nil
    }

    /// Décode un entier de façon tolérante (nombre, chaîne ou nombre flottant).
    ///
    /// - Parameters:
    ///   - key: clé JSON.
    ///   - fallback: valeur si absente/illisible (défaut `0`).
    func int(_ key: Key, _ fallback: Int = 0) -> Int {
        if let i = opt(Int.self, key) { return i }
        if let d = opt(Double.self, key) { return Int(d) }
        if let s = opt(String.self, key), let i = Int(s) { return i }
        return fallback
    }

    /// Décode un booléen de façon tolérante (`true`, `"true"`, `1`).
    ///
    /// - Parameters:
    ///   - key: clé JSON.
    ///   - fallback: valeur si absente/illisible (défaut `false`).
    func bool(_ key: Key, _ fallback: Bool = false) -> Bool {
        if let b = opt(Bool.self, key) { return b }
        if let i = opt(Int.self, key) { return i != 0 }
        if let s = opt(String.self, key) { return s == "true" || s == "1" }
        return fallback
    }
}

/// Valeur JSON libre (`data` d'une notification, `details` d'une erreur…).
///
/// Équivalent Swift de `kotlinx.serialization.json.JsonElement` : permet de transporter
/// une charge utile arbitraire sans la modéliser, tout en restant `Codable` et `Sendable`.
public enum JSONValue: Codable, Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let v = try? c.decode(Bool.self) { self = .bool(v); return }
        if let v = try? c.decode(Double.self) { self = .number(v); return }
        if let v = try? c.decode(String.self) { self = .string(v); return }
        if let v = try? c.decode([JSONValue].self) { self = .array(v); return }
        if let v = try? c.decode([String: JSONValue].self) { self = .object(v); return }
        self = .null
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }

    /// Accès pratique à une clé d'objet (utile au routage deeplink d'une notification).
    /// - Parameter key: clé recherchée.
    /// - Returns: la valeur associée, ou `nil` si ce n'est pas un objet / clé absente.
    public subscript(key: String) -> JSONValue? {
        if case .object(let dict) = self { return dict[key] }
        return nil
    }

    /// Représentation texte si la valeur est une chaîne (sinon `nil`).
    public var stringValue: String? {
        if case .string(let s) = self { return s }
        return nil
    }
}

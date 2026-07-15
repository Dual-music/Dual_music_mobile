import SwiftUI

/// Typographie Dual Music.
///
/// Le web utilise la pile système ; sur iOS on s'appuie donc sur **SF Pro** (police
/// système) via les styles dynamiques, pour un rendu natif et accessible (Dynamic Type).
/// Si une police de marque est ajoutée, la centraliser ici.
public enum DMFont {
    /// Grand titre d'écran (ex. nom d'un live).
    public static let title = Font.system(.largeTitle, design: .rounded).weight(.bold)
    /// Titre de section / carte.
    public static let headline = Font.system(.title3, design: .rounded).weight(.semibold)
    /// Corps de texte standard.
    public static let body = Font.system(.body)
    /// Texte secondaire / métadonnées.
    public static let caption = Font.system(.caption)
    /// Libellé de bouton (CTA).
    public static let button = Font.system(.headline, design: .rounded).weight(.bold)
    /// Montants (crédits, prix) — chiffres à chasse fixe pour l'alignement.
    public static let mono = Font.system(.body, design: .monospaced).weight(.semibold)
}

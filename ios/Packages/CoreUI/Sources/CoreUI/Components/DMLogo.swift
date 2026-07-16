import SwiftUI

/// Logo Dual Music (iOS).
///
/// Affiche **exactement le même logo que le site web** (asset `logo-tr.png` réutilisé,
/// embarqué dans `CoreUI` via `Bundle.module`), pour la parité de marque entre
/// plateformes. Le ratio d'origine est préservé ; l'appelant contrôle la hauteur.
public struct DMLogo: View {
    /// Hauteur cible du logo (la largeur s'ajuste au ratio).
    private let height: CGFloat

    /// - Parameter height: hauteur en points (défaut 64).
    public init(height: CGFloat = 64) {
        self.height = height
    }

    public var body: some View {
        // Chargé depuis le bundle du package (résilient si l'asset venait à manquer).
        (dmLogoImage ?? Image(systemName: "music.note"))
            .resizable()
            .scaledToFit()
            .frame(height: height)
            .accessibilityLabel("Dual Music")
    }

    /// Charge l'image du logo depuis les ressources du package.
    private var dmLogoImage: Image? {
        #if canImport(UIKit)
        if let ui = UIImage(named: "dm_logo", in: .module, with: nil) {
            return Image(uiImage: ui)
        }
        #endif
        return nil
    }
}

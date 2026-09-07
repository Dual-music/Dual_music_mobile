import Foundation

/// Configuration d'exécution de l'app iOS.
///
/// La valeur de `API_BASE_URL` est lue depuis l'`Info.plist` (clé `DMApiBaseURL`), elle-même
/// alimentée par les **build settings** de la configuration Xcode (`Debug` / `Release`).
/// Aucune URL n'est donc codée en dur dans le code Swift, contrairement à
/// `MainActivity.kt` côté Android — changer d'environnement ne demande pas de recompiler
/// une constante enfouie dans un fichier source.
///
/// Voir `ios/project.yml` :
/// - `Debug`   → `DM_API_BASE_URL = http://localhost:4000` (à adapter, cf. note ci-dessous)
/// - `Release` → `DM_API_BASE_URL = https://api.dualmusic.app`
///
/// > **Test sur iPhone physique** : `localhost` désigne le téléphone lui-même. Utiliser
/// > l'IP locale du Mac/PC qui héberge le backend (ex. `http://192.168.1.20:4000`), les
/// > deux appareils étant sur le même Wi-Fi. Le simulateur, lui, accepte `localhost`.
public enum AppConfig {

    /// URL publique du backend, **sans** le suffixe `/api/v1`.
    public static let apiBaseURL: URL = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "DMApiBaseURL") as? String
        if let raw, let url = URL(string: raw), url.scheme != nil {
            return url
        }
        // Repli : évite un crash si la clé est absente (build mal configuré). L'app
        // affichera simplement des erreurs réseau explicites au lieu de planter au lancement.
        assertionFailure("DMApiBaseURL absent ou invalide dans Info.plist — vérifier ios/project.yml")
        return URL(string: "https://api.dualmusic.app")!
    }()

    /// Identifiant de service Keychain pour les jetons (isolé par bundle).
    public static let keychainService: String = (Bundle.main.bundleIdentifier ?? "com.dualmusic.app") + ".auth"

    /// Client ID Google (OAuth iOS), lu depuis l'`Info.plist` (`GIDClientID`).
    ///
    /// `nil` → le bouton « Continuer avec Google » est **masqué** : l'app reste pleinement
    /// utilisable en email/mot de passe tant que Google n'est pas configuré.
    public static var googleClientID: String? {
        (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String)?.nilIfBlankLocal
    }

    /// Version affichable de l'app (`1.0.0 (42)`).
    public static var displayVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(version) (\(build))"
    }
}

private extension String {
    /// Variante locale de `nilIfBlank` (l'app n'importe pas `DomainModels` ici).
    var nilIfBlankLocal: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

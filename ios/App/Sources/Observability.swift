import Foundation
import Sentry

/// DSN Sentry du projet.
///
/// ⚠️ À renseigner depuis le dashboard Sentry (Settings → Projects → Client Keys). Laissé
/// VIDE par défaut : Sentry se désactive alors proprement (aucun crash, aucun envoi) — le
/// build et l'app fonctionnent sans configuration. En production, injecte-le plutôt via une
/// variable de build / un secret CI que de le committer en clair. Miroir de
/// `DualMusicApp.kt` (Android) — mêmes réglages, pour des taux de crash comparables.
private let sentryDSN = ""

/// Initialise l'observabilité (Sentry) au démarrage : capture automatique des exceptions
/// non gérées et des traces de performance. Sans DSN, l'appel est un no-op sûr.
enum Observability {
    static func configureIfPossible() {
        guard !sentryDSN.isEmpty else { return } // Désactivé tant que le DSN n'est pas fourni.
        SentrySDK.start { options in
            options.dsn = sentryDSN
            // Échantillonnage des traces de perf : 20 % en prod (ajuster selon le volume).
            options.tracesSampleRate = 0.2
            // Ne jamais envoyer d'infos sensibles (PII) par défaut.
            options.sendDefaultPii = false
            #if DEBUG
            options.environment = "development"
            #else
            options.environment = "production"
            #endif
            if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                options.releaseName = "dualmusic-ios@\(version)"
            }
        }
    }
}

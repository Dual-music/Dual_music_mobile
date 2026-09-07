import SwiftUI
import CoreUI
import FeatureAuth

/// Racine de l'app : aiguille entre le tunnel d'authentification et la coque connectée.
///
/// Miroir du `when (authState)` de `MainActivity` côté Android :
/// - `loading` → écran de démarrage (réhydratation de session depuis le Keychain) ;
/// - `signedOut` → ``FeatureAuth/SignInView`` ;
/// - `pendingEmailVerification` → ``FeatureAuth/EmailVerifyView`` ;
/// - `pendingProfileCompletion` → ``FeatureAuth/ProfileCompletionView`` ;
/// - `signedIn` → ``MainShellView``.
struct RootView: View {
    @Environment(\.dmTheme) private var theme

    let container: AppContainer

    var body: some View {
        ZStack {
            DMScreenBackground()

            switch container.auth.authState {
            case .loading:
                launchScreen

            case .signedOut:
                SignInView(viewModel: container.auth)

            case let .pendingEmailVerification(_, email):
                EmailVerifyView(viewModel: container.auth, email: email)

            case .pendingProfileCompletion:
                ProfileCompletionView(viewModel: container.auth)

            case .signedIn:
                MainShellView(container: container)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: container.auth.authState)
        .task {
            // Réhydratation au lancement (une seule fois).
            if case .loading = container.auth.authState {
                await container.auth.bootstrap()
            }
        }
        .task(id: isSignedIn) {
            // Une fois connecté : autorisation de notifications puis enregistrement du
            // jeton push — même séquence qu'Android (permission demandée après le login).
            guard isSignedIn else { return }
            await PushService.shared.requestAuthorization()
            await container.registerPushTokenIfAvailable()
        }
    }

    /// Vrai quand la session est active (déclencheur de l'enregistrement push).
    private var isSignedIn: Bool {
        if case .signedIn = container.auth.authState { return true }
        return false
    }

    /// Écran de démarrage : logo centré pendant la réhydratation de session.
    private var launchScreen: some View {
        VStack(spacing: theme.spacing.lg) {
            DMLogo(height: 96)
            ProgressView().tint(theme.colors.primary)
        }
    }
}

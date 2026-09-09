import SwiftUI
import AuthenticationServices
import CoreUI
import DomainModels

/// Écran d'authentification — connexion, inscription, mot de passe oublié, réinitialisation.
///
/// Reprend **à l'identique** les champs et la navigation entre modes de l'écran Android
/// (`SignInScreen`) : logo, sous-titre contextuel, carte de formulaire, CTA, bouton Google
/// (connexion uniquement) et liens de bascule.
@MainActor
public struct SignInView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s
    @Environment(\.colorScheme) private var colorScheme

    @Bindable private var viewModel: AuthViewModel

    /// - Parameter viewModel: source d'état partagée avec les autres écrans d'auth.
    public init(viewModel: AuthViewModel) {
        self._viewModel = Bindable(viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                DMLogo(height: 72)
                    .padding(.top, theme.spacing.xxl)

                Text(subtitle)
                    .font(DMFont.body)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .multilineTextAlignment(.center)

                DMCard {
                    VStack(spacing: theme.spacing.md) {
                        fields

                        if let info = viewModel.infoMessage { DMMessage(info) }
                        if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

                        DMButton(
                            submitLabel,
                            isLoading: viewModel.isSubmitting,
                            isEnabled: viewModel.canSubmit
                        ) {
                            Task { await viewModel.submit() }
                        }

                        // Bouton système Apple : ni SDK tiers ni fournisseur à injecter
                        // (contrairement à Google) — `AuthenticationServices` est un
                        // framework Apple standard, toujours disponible. Requis par la
                        // règle App Store 4.8 dès qu'une connexion sociale tierce (Google)
                        // est proposée ; le texte/logo sont imposés par Apple, non
                        // personnalisables (Human Interface Guidelines).
                        if viewModel.mode == .login {
                            SignInWithAppleButton(
                                .signIn,
                                onRequest: { request in request.requestedScopes = [.fullName, .email] },
                                onCompletion: { result in handleAppleSignIn(result) }
                            )
                            .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                            .frame(height: 50)
                            .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                            .disabled(viewModel.isSubmitting)
                        }

                        if viewModel.mode == .login, viewModel.googleIdTokenProvider != nil {
                            DMButton(s.continueWithGoogle, style: .outline, isEnabled: !viewModel.isSubmitting) {
                                Task { await viewModel.signInWithGoogle() }
                            }
                        }

                        modeLinks
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }

    // MARK: - Champs par mode

    @ViewBuilder
    private var fields: some View {
        switch viewModel.mode {
        case .login:
            emailField
            DMTextField(s.password, text: $viewModel.password, isSecure: true)

        case .register:
            emailField
            DMTextField(s.passwordStar, text: $viewModel.password, help: s.atLeast8, isSecure: true)
            DMTextField(
                s.confirmPasswordStar,
                text: $viewModel.confirmPassword,
                help: mismatchHelp,
                isSecure: true
            )

        case .forgot:
            Text(s.forgotHint)
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.mutedForeground)
                .frame(maxWidth: .infinity, alignment: .leading)
            emailField

        case .reset:
            emailField
            DMTextField(
                s.codeFromEmail,
                text: $viewModel.resetCode,
                keyboard: .numberPad,
                autocapitalization: .never
            )
            .onChange(of: viewModel.resetCode) { _, newValue in
                let digits = newValue.digitsOnly.take(8)
                if digits != newValue { viewModel.resetCode = digits }
            }
            DMTextField(s.newPassword, text: $viewModel.newPassword, help: s.atLeast8, isSecure: true)
        }
    }

    private var emailField: some View {
        DMTextField(
            s.email,
            text: $viewModel.email,
            placeholder: s.emailPlaceholder,
            keyboard: .emailAddress,
            autocapitalization: .never
        )
    }

    /// Message d'aide affiché quand la confirmation ne correspond pas au mot de passe.
    private var mismatchHelp: String? {
        guard !viewModel.confirmPassword.isEmpty, viewModel.confirmPassword != viewModel.password else { return nil }
        return s.passwordsDontMatch
    }

    /// Extrait l'identity token (+ le nom complet, seulement s'il vient d'être fourni par
    /// Apple — première autorisation uniquement) du résultat `SignInWithAppleButton`, puis
    /// délègue à `AuthViewModel`. Un échec/annulation reste silencieux (`.canceled` n'est
    /// pas une erreur à afficher), exactement comme un utilisateur qui ferme le sélecteur
    /// de compte Google sans choisir.
    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        guard case let .success(authorization) = result,
              let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let identityToken = String(data: tokenData, encoding: .utf8)
        else { return }

        let fullName = credential.fullName.flatMap { components -> String? in
            let formatted = PersonNameComponentsFormatter.localizedString(from: components, style: .default)
            return formatted.trimmed.nilIfBlank
        }

        Task { await viewModel.signInWithApple(identityToken: identityToken, fullName: fullName) }
    }

    // MARK: - Liens de bascule

    @ViewBuilder
    private var modeLinks: some View {
        switch viewModel.mode {
        case .login:
            linkButton(s.forgotPassword) { viewModel.setMode(.forgot) }
            linkButton(s.noAccountSignUp) { viewModel.setMode(.register) }
        case .register:
            linkButton(s.alreadyAccountSignIn) { viewModel.setMode(.login) }
        case .forgot, .reset:
            linkButton(s.backToLogin) { viewModel.setMode(.login) }
        }
    }

    private func linkButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.primaryGlow)
                .frame(maxWidth: .infinity)
                .padding(.vertical, theme.spacing.xs)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Libellés contextuels

    private var subtitle: String {
        switch viewModel.mode {
        case .login: return s.authLoginSubtitle
        case .register: return s.authRegisterSubtitle
        case .forgot: return s.resetPasswordTitle
        case .reset: return s.authResetSubtitle
        }
    }

    private var submitLabel: String {
        if viewModel.isSubmitting { return s.pleaseWait }
        switch viewModel.mode {
        case .login: return s.signIn
        case .register: return s.createAccount
        case .forgot: return s.sendCode
        case .reset: return s.reset
        }
    }
}

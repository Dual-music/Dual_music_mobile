import SwiftUI
import CoreUI
import DomainModels

/// Écran de vérification de l'email après inscription (étape 2/3).
///
/// Le code a été envoyé automatiquement à l'inscription ; l'utilisateur le saisit ici. La
/// vérification est **non bloquante** : « Passer pour l'instant » permet d'entrer dans
/// l'app et de valider plus tard. Miroir de `EmailVerifyScreen` Android.
public struct EmailVerifyView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: AuthViewModel
    private let email: String

    /// - Parameters:
    ///   - viewModel: source d'état + actions (vérifier / renvoyer / passer).
    ///   - email: adresse destinataire (affichage).
    public init(viewModel: AuthViewModel, email: String) {
        self._viewModel = Bindable(viewModel)
        self.email = email
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                DMLogo(height: 64).padding(.top, theme.spacing.xxl)

                Text(s.verifyEmailTitle)
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)

                Text("\(s.verifyEmailSentTo) \(email).")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .multilineTextAlignment(.center)

                DMCard {
                    VStack(spacing: theme.spacing.md) {
                        DMTextField(
                            s.codeFromEmail,
                            text: $viewModel.verifyCode,
                            keyboard: .numberPad,
                            autocapitalization: .never
                        )
                        .onChange(of: viewModel.verifyCode) { _, newValue in
                            let digits = newValue.digitsOnly.take(8)
                            if digits != newValue { viewModel.verifyCode = digits }
                        }

                        if let info = viewModel.infoMessage { DMMessage(info) }
                        if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

                        DMButton(
                            viewModel.isSubmitting ? s.verifying : s.validate,
                            isLoading: viewModel.isSubmitting,
                            isEnabled: (4...8).contains(viewModel.verifyCode.count) && !viewModel.isSubmitting
                        ) {
                            Task { await viewModel.verifyEmail() }
                        }

                        DMButton(s.resendCode, style: .secondary) {
                            Task { await viewModel.resendEmailCode() }
                        }

                        DMButton(s.skipForNow, style: .outline) {
                            viewModel.skipVerification()
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }
}

/// Étape 3 de l'inscription (après validation du code email) : saisie des informations de
/// profil — **nom** (requis), **pays** et **numéro** (optionnel).
///
/// Enregistre via `PATCH /users/me` puis entre dans l'app. « Plus tard » permet de compléter
/// ultérieurement depuis le profil. Miroir de `ProfileCompletionScreen` Android.
public struct ProfileCompletionView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: AuthViewModel

    /// - Parameter viewModel: source d'état (partagée avec l'écran d'auth).
    public init(viewModel: AuthViewModel) {
        self._viewModel = Bindable(viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                DMLogo(height: 64).padding(.top, theme.spacing.xxl)

                Text(s.completeProfileTitle)
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)

                Text(s.completeProfileSubtitle)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                    .multilineTextAlignment(.center)

                DMCard {
                    VStack(spacing: theme.spacing.md) {
                        DMTextField(s.fullNameStar, text: $viewModel.fullName)

                        DMPicker(
                            label: s.countryStar,
                            selection: viewModel.country.label,
                            options: Countries.all
                        ) { country in
                            Text(country.label)
                        } onSelect: { country in
                            viewModel.country = country
                        }

                        DMTextField(
                            s.phoneOptional,
                            text: $viewModel.phone,
                            placeholder: "\(viewModel.country.dial)612345678",
                            keyboard: .phonePad,
                            autocapitalization: .never
                        )

                        if let info = viewModel.infoMessage { DMMessage(info) }
                        if let error = viewModel.errorMessage { DMMessage(error, kind: .error) }

                        DMButton(
                            viewModel.isSubmitting ? s.saving : s.finish,
                            isLoading: viewModel.isSubmitting,
                            isEnabled: !viewModel.isSubmitting && !viewModel.fullName.trimmed.isEmpty
                        ) {
                            Task { await viewModel.completeProfile() }
                        }

                        DMButton(s.later, style: .outline, isEnabled: !viewModel.isSubmitting) {
                            viewModel.skipProfile()
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
    }
}

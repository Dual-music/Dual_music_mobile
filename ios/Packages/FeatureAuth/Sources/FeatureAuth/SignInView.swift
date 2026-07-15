import SwiftUI
import CoreUI

/// Écran de connexion — email + mot de passe (primaire), point d'entrée Google.
///
/// Utilise le design system `CoreUI` (fond héro, CTA dégradé, thème sombre) pour une
/// parité visuelle avec le web.
public struct SignInView: View {
    @Environment(\.dmTheme) private var theme
    @State private var viewModel: AuthViewModel

    /// Callback déclenché lorsque l'utilisateur veut lancer Google (ouverture web session).
    private let onGoogle: () -> Void

    public init(viewModel: AuthViewModel, onGoogle: @escaping () -> Void = {}) {
        _viewModel = State(initialValue: viewModel)
        self.onGoogle = onGoogle
    }

    public var body: some View {
        ZStack {
            theme.gradients.hero.ignoresSafeArea()

            ScrollView {
                VStack(spacing: theme.spacing.lg) {
                    header

                    DMCard {
                        VStack(spacing: theme.spacing.md) {
                            field("Email", text: $viewModel.email, keyboard: .emailAddress)
                            secureField("Mot de passe", text: $viewModel.password)

                            if let error = viewModel.errorMessage {
                                Text(error)
                                    .font(DMFont.caption)
                                    .foregroundStyle(theme.colors.destructive)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            DMButton("Se connecter", isLoading: viewModel.isSubmitting, isEnabled: viewModel.canSubmit) {
                                Task { await viewModel.signIn() }
                            }

                            dividerOr

                            DMButton("Continuer avec Google", style: .outline) { onGoogle() }
                        }
                    }
                }
                .padding(theme.spacing.lg)
            }
        }
        .dualMusicTheme(theme)
    }

    // MARK: Sous-vues

    private var header: some View {
        VStack(spacing: theme.spacing.sm) {
            Text("Dual Music")
                .font(DMFont.title)
                .foregroundStyle(theme.colors.foreground)
            Text("Connecte-toi pour rejoindre les lives")
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.mutedForeground)
        }
        .padding(.top, theme.spacing.xxl)
    }

    private var dividerOr: some View {
        HStack {
            Rectangle().fill(theme.colors.border).frame(height: 1)
            Text("ou").font(DMFont.caption).foregroundStyle(theme.colors.mutedForeground)
            Rectangle().fill(theme.colors.border).frame(height: 1)
        }
    }

    private func field(_ placeholder: String, text: Binding<String>, keyboard: UIKeyboardType = .default) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .padding(theme.spacing.md)
            .background(theme.colors.input)
            .foregroundStyle(theme.colors.foreground)
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.sm, style: .continuous))
    }

    private func secureField(_ placeholder: String, text: Binding<String>) -> some View {
        SecureField(placeholder, text: text)
            .padding(theme.spacing.md)
            .background(theme.colors.input)
            .foregroundStyle(theme.colors.foreground)
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.sm, style: .continuous))
    }
}

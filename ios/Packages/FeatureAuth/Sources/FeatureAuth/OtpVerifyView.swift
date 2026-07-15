import SwiftUI
import CoreUI
import CoreNetwork

/// Écran de **vérification du numéro de téléphone** par OTP (post-login).
///
/// Rappel : ce n'est PAS une connexion passwordless — l'utilisateur est déjà authentifié
/// (email/mot de passe) ; on vérifie ici la propriété de son numéro via un code SMS.
public struct OtpVerifyView: View {
    @Environment(\.dmTheme) private var theme

    private let repository: AuthRepository
    private let onVerified: () -> Void

    @State private var code: String = ""
    @State private var isSubmitting = false
    @State private var sending = false
    @State private var error: String?

    public init(repository: AuthRepository, onVerified: @escaping () -> Void) {
        self.repository = repository
        self.onVerified = onVerified
    }

    public var body: some View {
        VStack(spacing: theme.spacing.lg) {
            VStack(spacing: theme.spacing.sm) {
                Text("Vérifie ton numéro")
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)
                Text("Saisis le code à 6 chiffres reçu par SMS")
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
            }

            TextField("––––––", text: $code)
                .keyboardType(.numberPad)
                .font(.system(.title, design: .monospaced))
                .multilineTextAlignment(.center)
                .padding(theme.spacing.md)
                .background(theme.colors.input)
                .foregroundStyle(theme.colors.foreground)
                .clipShape(RoundedRectangle(cornerRadius: theme.radius.sm, style: .continuous))
                .onChange(of: code) { _, new in
                    code = String(new.filter(\.isNumber).prefix(6))
                }

            if let error { Text(error).font(DMFont.caption).foregroundStyle(theme.colors.destructive) }

            DMButton("Vérifier", isLoading: isSubmitting, isEnabled: code.count == 6) {
                Task { await verify() }
            }

            Button(sending ? "Envoi…" : "Renvoyer le code") { Task { await resend() } }
                .font(DMFont.caption)
                .foregroundStyle(theme.colors.primary)
                .disabled(sending)
        }
        .padding(theme.spacing.lg)
        .task { await resend() } // envoie un premier code à l'ouverture
    }

    private func verify() async {
        isSubmitting = true; error = nil
        do {
            try await repository.verifyPhoneOtp(code: code)
            onVerified()
        } catch let api as APIError {
            error = api.code == "OTP_INVALID" ? "Code incorrect." : "Vérification impossible."
        } catch { error = "Vérification impossible." }
        isSubmitting = false
    }

    private func resend() async {
        sending = true; error = nil
        try? await repository.sendPhoneOtp()
        sending = false
    }
}

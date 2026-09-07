import Foundation
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// État global de session — pilote la racine de l'app.
public enum AuthState: Equatable {
    /// Réhydratation au démarrage.
    case loading
    case signedOut
    /// Inscrit mais email non vérifié : on propose l'écran de saisie du code.
    case pendingEmailVerification(user: AuthUser, email: String)
    /// Email vérifié : on demande les informations de profil (nom, pays, numéro).
    case pendingProfileCompletion(user: AuthUser)
    case signedIn(user: AuthUser)

    public static func == (lhs: AuthState, rhs: AuthState) -> Bool {
        switch (lhs, rhs) {
        case (.loading, .loading), (.signedOut, .signedOut): return true
        case let (.pendingEmailVerification(a, ae), .pendingEmailVerification(b, be)): return a.id == b.id && ae == be
        case let (.pendingProfileCompletion(a), .pendingProfileCompletion(b)): return a.id == b.id
        case let (.signedIn(a), .signedIn(b)): return a.id == b.id
        default: return false
        }
    }
}

/// Écran actif de la zone d'authentification.
public enum AuthMode: Equatable {
    case login
    case register
    case forgot
    case reset
}

/// ViewModel de l'authentification (MVI léger) — miroir de `AuthViewModel` Android.
///
/// Gère la connexion, l'inscription en 3 étapes (compte → code email → profil), le mot de
/// passe oublié (demande + reset par code) et la connexion Google native.
///
/// Le fournisseur d'ID token Google est **injecté** (``googleIdTokenProvider``) : le SDK
/// Google reste dans la cible applicative, ce module ne dépend d'aucun tiers.
@Observable
@MainActor
public final class AuthViewModel {

    // MARK: État de session

    /// État global (piloté par la racine de l'app).
    public private(set) var authState: AuthState = .loading

    // MARK: État du formulaire

    public var mode: AuthMode = .login
    public var email: String = ""
    public var password: String = ""
    public var confirmPassword: String = ""
    public var fullName: String = ""
    public var phone: String = ""
    public var country: Country = Countries.default
    public var referralCode: String = ""
    /// Code de réinitialisation reçu par email (normalisé en chiffres par la vue).
    public var resetCode: String = ""
    public var newPassword: String = ""
    /// Code de vérification de l'email (normalisé en chiffres par la vue).
    public var verifyCode: String = ""

    public private(set) var isSubmitting = false
    public private(set) var errorMessage: String?
    public private(set) var infoMessage: String?

    private let repository: AuthRepository

    /// Fournit un ID token Google (SDK Google Sign-In, câblé par l'app).
    /// `nil` → le bouton Google est masqué.
    public var googleIdTokenProvider: (@MainActor () async throws -> String)?

    /// Appelé après une connexion réussie (enregistrement du jeton push, préchargements…).
    public var onSignedIn: (@MainActor () -> Void)?

    /// - Parameter repository: accès REST à `/auth/…`.
    public init(repository: AuthRepository) {
        self.repository = repository
    }

    // MARK: - Validation

    /// Le formulaire courant est-il soumettable ? (règles alignées sur le backend).
    public var canSubmit: Bool {
        guard !isSubmitting else { return false }
        switch mode {
        case .login:
            return email.contains("@") && password.count >= 8
        case .register:
            // Étape 1 : email + mot de passe + confirmation uniquement. Les autres infos
            // (nom, pays, numéro) sont demandées APRÈS validation du code email.
            return email.contains("@") && password.count >= 8 && password == confirmPassword
        case .forgot:
            return email.contains("@")
        case .reset:
            return email.contains("@") && (4...8).contains(resetCode.count) && newPassword.count >= 8
        }
    }

    /// Change de mode (connexion/inscription/oublié/reset), en nettoyant les messages.
    public func setMode(_ newMode: AuthMode) {
        mode = newMode
        errorMessage = nil
        infoMessage = nil
    }

    /// Efface les messages affichés (appelé à chaque édition de champ).
    public func clearMessages() {
        errorMessage = nil
        infoMessage = nil
    }

    // MARK: - Cycle de vie

    /// Réhydrate la session au lancement (via le refresh token persisté au Keychain).
    public func bootstrap() async {
        do {
            let me = try await repository.me()
            authState = .signedIn(user: me.user)
            onSignedIn?()
        } catch {
            authState = .signedOut
        }
    }

    // MARK: - Actions

    /// Soumet le formulaire courant selon ``mode``.
    public func submit() async {
        guard canSubmit else { return }
        isSubmitting = true
        errorMessage = nil
        infoMessage = nil
        defer { isSubmitting = false }

        switch mode {
        case .login:
            do {
                let session = try await repository.login(email: email.trimmed, password: password)
                authState = .signedIn(user: session.user)
                onSignedIn?()
            } catch {
                errorMessage = friendlyMessage(error)
            }

        case .register:
            // Le backend envoie automatiquement le code de vérification par email.
            do {
                let session = try await repository.register(
                    RegisterRequest(
                        email: email.trimmed,
                        password: password,
                        referralCode: referralCode.trimmed.uppercased().nilIfBlank
                    )
                )
                authState = .pendingEmailVerification(user: session.user, email: email.trimmed)
            } catch {
                errorMessage = friendlyMessage(error)
            }

        case .forgot:
            do {
                try await repository.forgotPassword(email: email.trimmed)
                mode = .reset
                infoMessage = AppStrings.current.resetCodeSent
            } catch {
                errorMessage = friendlyMessage(error)
            }

        case .reset:
            do {
                try await repository.resetPassword(
                    email: email.trimmed,
                    code: resetCode.digitsOnly,
                    newPassword: newPassword
                )
                mode = .login
                password = ""
                infoMessage = AppStrings.current.passwordResetDone
            } catch {
                errorMessage = friendlyMessage(error)
            }
        }
    }

    /// Connexion Google native : récupère l'ID token via le SDK puis l'échange côté backend.
    public func signInWithGoogle() async {
        guard let provider = googleIdTokenProvider else { return }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            let idToken = try await provider()
            let session = try await repository.loginWithGoogle(idToken: idToken)
            authState = .signedIn(user: session.user)
            onSignedIn?()
        } catch {
            errorMessage = (error as? APIError)?.message ?? AppStrings.current.errGoogleSignInFailed
        }
    }

    /// Vérifie le code email ; en cas de succès, passe à la saisie des infos de profil.
    public func verifyEmail() async {
        guard case let .pendingEmailVerification(user, _) = authState else { return }
        guard (4...8).contains(verifyCode.count) else { return }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            try await repository.verifyEmailOtp(code: verifyCode.digitsOnly)
            authState = .pendingProfileCompletion(user: user)
        } catch {
            errorMessage = friendlyMessage(error)
        }
    }

    /// (Re)envoie le code de vérification email.
    public func resendEmailCode() async {
        do {
            try await repository.sendEmailOtp()
            infoMessage = AppStrings.current.newCodeSent
        } catch {
            errorMessage = friendlyMessage(error)
        }
    }

    /// Passe la vérification (non bloquante) → saisie des infos de profil.
    public func skipVerification() {
        guard case let .pendingEmailVerification(user, _) = authState else { return }
        authState = .pendingProfileCompletion(user: user)
    }

    /// Étape 3 : enregistre les informations de profil (nom, pays, numéro) puis entre dans
    /// l'app. Le numéro est optionnel ; le nom est requis.
    public func completeProfile() async {
        guard case let .pendingProfileCompletion(user) = authState else { return }
        guard !fullName.trimmed.isEmpty else {
            errorMessage = AppStrings.current.errNameRequired
            return
        }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            try await repository.updateProfile(
                UpdateProfileRequest(
                    fullName: fullName.trimmed,
                    countryCode: country.code,
                    phone: phone.nilIfBlank,
                    phoneCountryCode: country.dial
                )
            )
            authState = .signedIn(user: user)
            onSignedIn?()
        } catch {
            errorMessage = friendlyMessage(error)
        }
    }

    /// Passe la saisie des infos de profil (complétables plus tard depuis le profil).
    public func skipProfile() {
        guard case let .pendingProfileCompletion(user) = authState else { return }
        authState = .signedIn(user: user)
        onSignedIn?()
    }

    /// Déconnexion : invalide la session serveur, efface le Keychain, réinitialise le form.
    public func signOut() async {
        await repository.logout()
        authState = .signedOut
        resetForm()
    }

    // MARK: - Interne

    private func resetForm() {
        mode = .login
        email = ""
        password = ""
        confirmPassword = ""
        fullName = ""
        phone = ""
        country = Countries.default
        referralCode = ""
        resetCode = ""
        newPassword = ""
        verifyCode = ""
        errorMessage = nil
        infoMessage = nil
    }

    /// Traduit une erreur backend en message utilisateur (mêmes cas qu'Android).
    private func friendlyMessage(_ error: Error) -> String {
        let s = AppStrings.current
        guard let api = error as? APIError else { return s.errGeneric }
        switch true {
        case api.httpStatus == 401: return s.errWrongCredentials
        case api.httpStatus == 409: return s.errEmailTaken
        case api.code == ErrorCode.otpInvalid, api.code == ErrorCode.otpExpired: return s.errCodeInvalid
        case api.code == ErrorCode.validation: return s.errInvalidFields
        case api.isRetriable: return s.errUnstableConnection
        default: return api.message
        }
    }
}

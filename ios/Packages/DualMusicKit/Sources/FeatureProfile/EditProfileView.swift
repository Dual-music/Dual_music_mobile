import SwiftUI
import PhotosUI
import Observation
import CoreNetwork
import CoreUI
import CoreUpload
import DomainModels

/// ViewModel d'édition du profil.
///
/// Préremplit depuis `/auth/me`, gère l'upload d'avatar (presign → PUT → confirm),
/// l'enregistrement (`PATCH /users/me`) et le **changement de mot de passe**
/// (`POST /auth/password/change`). Miroir de `EditProfileViewModel` Android.
@Observable
@MainActor
public final class EditProfileViewModel {

    public var fullName: String = ""
    public var bio: String = ""
    public private(set) var avatarURL: String?
    public var country: Country = Countries.default
    public var phone: String = ""
    public var currentPassword: String = ""
    public var newPassword: String = ""

    public private(set) var isUploading = false
    public private(set) var isSaving = false
    public private(set) var isChangingPassword = false
    public private(set) var message: String?
    public private(set) var passwordMessage: String?

    private let repository: ProfileRepository
    private let uploader: MediaUploader

    /// - Parameters:
    ///   - repository: lectures + écriture profil + mot de passe.
    ///   - uploader: upload de l'avatar (catégorie `avatar`).
    public init(repository: ProfileRepository, uploader: MediaUploader) {
        self.repository = repository
        self.uploader = uploader
    }

    /// Préremplit le formulaire avec le profil courant.
    public func load() async {
        guard let me = try? await repository.me() else { return }
        fullName = me.profile?.fullName ?? ""
        avatarURL = me.profile?.avatarURL
        country = Countries.byCode(me.profile?.countryCode)
        phone = me.profile?.phone ?? ""
    }

    /// Affiche un message (erreur de lecture de fichier, par exemple).
    public func setMessage(_ text: String?) { message = text }

    /// Upload l'avatar sélectionné et mémorise son URL.
    ///
    /// - Parameter item: élément choisi dans le sélecteur photo (image seule, ≤ 5 Mo :
    ///   c'est la limite de la catégorie `avatar` côté backend).
    public func uploadAvatar(_ item: PhotosPickerItem) async {
        isUploading = true
        message = nil
        defer { isUploading = false }
        do {
            let media = try await PhotoPickerLoader.load(item, maxBytes: 5 * 1024 * 1024)
            avatarURL = try await uploader.upload(media, category: UploadCategory.avatar)
        } catch {
            message = error.localizedDescription
        }
    }

    /// Enregistre le profil (nom, bio, pays, numéro, avatar).
    /// - Parameter onDone: exécuté après un enregistrement réussi.
    public func save(onDone: @escaping () -> Void) async {
        isSaving = true
        message = nil
        defer { isSaving = false }
        do {
            try await repository.updateProfile(
                UpdateProfileRequest(
                    fullName: fullName.nilIfBlank,
                    countryCode: country.code,
                    phone: phone.nilIfBlank,
                    phoneCountryCode: country.dial,
                    bio: bio.nilIfBlank,
                    avatarURL: avatarURL
                )
            )
            message = AppStrings.current.profileUpdated
            onDone()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.saveFailed
        }
    }

    /// Change le mot de passe (nouveau ≥ 8 caractères).
    public func changePassword() async {
        guard newPassword.count >= 8 else {
            passwordMessage = AppStrings.current.newPasswordTooShort
            return
        }
        isChangingPassword = true
        passwordMessage = nil
        defer { isChangingPassword = false }
        do {
            try await repository.changePassword(currentPassword: currentPassword, newPassword: newPassword)
            currentPassword = ""
            newPassword = ""
            passwordMessage = AppStrings.current.passwordChanged
        } catch {
            passwordMessage = (error as? APIError)?.message ?? AppStrings.current.changeFailed
        }
    }
}

/// Écran d'édition du profil : avatar, nom, bio, pays, numéro et mot de passe.
@MainActor
public struct EditProfileView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: EditProfileViewModel
    private let onSaved: () -> Void

    @State private var pickedItem: PhotosPickerItem?

    /// - Parameters:
    ///   - viewModel: source d'état.
    ///   - onSaved: appelé après un enregistrement réussi (retour au profil).
    public init(viewModel: EditProfileViewModel, onSaved: @escaping () -> Void) {
        self._viewModel = Bindable(viewModel)
        self.onSaved = onSaved
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.md) {
                avatarSection
                infoCard
                passwordCard
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            Task { await viewModel.uploadAvatar(item) }
        }
    }

    /// Avatar (photo ou initiale) + bouton de changement.
    private var avatarSection: some View {
        VStack(spacing: theme.spacing.sm) {
            ZStack {
                Circle().fill(theme.gradients.primary).frame(width: 96, height: 96)
                if let url = viewModel.avatarURL, !url.isEmpty {
                    DMRemoteImage(url: url, fallback: "👤")
                        .frame(width: 96, height: 96)
                        .clipShape(Circle())
                } else {
                    Text(viewModel.fullName.take(1).uppercased().nilIfBlank ?? "?")
                        .font(DMFont.headline)
                        .foregroundStyle(theme.colors.primaryForeground)
                }
            }
            PhotosPicker(selection: $pickedItem, matching: .images) {
                Text(viewModel.isUploading ? s.uploading : s.changePhoto)
                    .font(DMFont.button)
                    .foregroundStyle(theme.colors.secondaryForeground)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(theme.colors.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
            }
            .disabled(viewModel.isUploading)
        }
    }

    /// Informations de profil.
    private var infoCard: some View {
        DMCard {
            VStack(spacing: theme.spacing.md) {
                DMTextField(s.fullName, text: $viewModel.fullName)
                DMTextField(s.bio, text: $viewModel.bio, axis: .vertical)

                DMPicker(
                    label: s.country,
                    selection: viewModel.country.label,
                    options: Countries.all
                ) { country in
                    Text(country.label)
                } onSelect: { country in
                    viewModel.country = country
                }

                DMTextField(
                    s.phoneNumber,
                    text: $viewModel.phone,
                    placeholder: viewModel.country.dial,
                    keyboard: .phonePad,
                    autocapitalization: .never
                )
                .onChange(of: viewModel.phone) { _, newValue in
                    let digits = newValue.digitsOnly
                    if digits != newValue { viewModel.phone = digits }
                }

                if let message = viewModel.message { DMMessage(message) }

                DMButton(
                    viewModel.isSaving ? s.saving : s.save,
                    isLoading: viewModel.isSaving,
                    isEnabled: !viewModel.isSaving && !viewModel.isUploading
                ) {
                    Task { await viewModel.save(onDone: onSaved) }
                }
            }
        }
    }

    /// Changement de mot de passe.
    private var passwordCard: some View {
        DMCard {
            VStack(spacing: theme.spacing.md) {
                DMSectionTitle(s.changePassword)
                DMTextField(s.currentPassword, text: $viewModel.currentPassword, isSecure: true)
                DMTextField(s.newPassword, text: $viewModel.newPassword, help: s.atLeast8, isSecure: true)
                if let message = viewModel.passwordMessage { DMMessage(message) }
                DMButton(
                    viewModel.isChangingPassword ? s.changing : s.changePassword,
                    style: .secondary,
                    isLoading: viewModel.isChangingPassword,
                    isEnabled: !viewModel.isChangingPassword
                ) {
                    Task { await viewModel.changePassword() }
                }
            }
        }
    }
}

import SwiftUI
import PhotosUI
import Observation
import CoreNetwork
import CoreUI
import CoreUpload
import DomainModels

/// ViewModel du **profil public créateur** (artiste ou manager) : charge le profil artiste
/// (`GET /users/:id`) ou manager (`GET /managers/me`), et enregistre les champs éditables +
/// les **liens sociaux** (`PATCH /artists/me` ou `PATCH /managers/me`). Miroir de
/// `PublicProfileViewModel` (Android).
@Observable
@MainActor
public final class PublicProfileViewModel {
    public private(set) var isArtist = true
    /// Nom de scène (artiste) ou nom affiché (manager).
    public var name = ""
    public var bio = ""
    public var experience = ""
    public private(set) var coverURL: String?
    public var isPublic = true
    public var social: [String: String] = [:]
    public private(set) var loading = true
    public private(set) var uploading = false
    public private(set) var saving = false
    public private(set) var message: String?

    private let repository: ProfileRepository
    private let uploader: MediaUploader

    /// - Parameters:
    ///   - repository: lectures + écritures profils créateurs.
    ///   - uploader: upload de l'image de couverture (catégorie `image`).
    public init(repository: ProfileRepository, uploader: MediaUploader) {
        self.repository = repository
        self.uploader = uploader
    }

    /// Plateformes sociales à proposer selon le rôle.
    public var platforms: [SocialPlatform] { isArtist ? SocialPlatform.artist : SocialPlatform.manager }

    /// Charge le profil public correspondant au rôle.
    public func load(isArtist: Bool) async {
        loading = true
        self.isArtist = isArtist
        if isArtist {
            let me = try? await repository.me()
            var ap: ArtistProfile?
            if let uid = me?.user.id { ap = try? await repository.myArtistProfile(userId: uid) }
            name = ap?.stageName?.nilIfBlank ?? me?.profile?.stageName ?? ""
            bio = ap?.bio ?? ""
            coverURL = ap?.coverImageURL
            isPublic = ap?.isPublic ?? true
            social = ap?.socialLinks ?? [:]
        } else {
            let mp = try? await repository.myManagerProfile()
            name = mp?.displayName ?? ""
            bio = mp?.bio ?? ""
            experience = mp?.experience ?? ""
            coverURL = mp?.coverImageURL
            isPublic = mp?.isPublic ?? true
            social = mp?.socialLinks ?? [:]
        }
        loading = false
    }

    /// Upload l'image de couverture et mémorise son URL.
    public func uploadCover(_ item: PhotosPickerItem) async {
        uploading = true
        message = nil
        defer { uploading = false }
        do {
            let media = try await PhotoPickerLoader.load(item, maxBytes: 5 * 1024 * 1024)
            coverURL = try await uploader.upload(media, category: UploadCategory.image)
        } catch {
            message = error.localizedDescription
        }
    }

    /// Enregistre le profil public (champs + liens sociaux non vides).
    /// - Parameter onDone: exécuté après un enregistrement réussi.
    public func save(onDone: @escaping () -> Void) async {
        saving = true
        message = nil
        defer { saving = false }
        let cleanedSocial = social.filter { !$0.value.trimmed.isEmpty }
        do {
            if isArtist {
                try await repository.updateArtistProfile(
                    UpdateArtistProfileRequest(
                        stageName: name.nilIfBlank,
                        bio: bio.nilIfBlank,
                        coverImageURL: coverURL,
                        isPublic: isPublic,
                        socialLinks: cleanedSocial
                    )
                )
            } else {
                try await repository.updateManagerProfile(
                    UpdateManagerProfileRequest(
                        displayName: name.nilIfBlank,
                        bio: bio.nilIfBlank,
                        experience: experience.nilIfBlank,
                        coverImageURL: coverURL,
                        isPublic: isPublic,
                        socialLinks: cleanedSocial
                    )
                )
            }
            message = AppStrings.current.profileUpdated
            onDone()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.saveFailed
        }
    }
}

/// Éditeur du **profil public** artiste/manager : image de couverture, nom de scène / nom
/// affiché, bio (+ expérience pour le manager), **liens de réseaux sociaux** (URL complètes),
/// et visibilité publique.
@MainActor
public struct PublicProfileEditView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    @Bindable private var viewModel: PublicProfileViewModel
    private let isArtist: Bool
    private let onSaved: () -> Void

    @State private var pickedItem: PhotosPickerItem?

    /// - Parameters:
    ///   - viewModel: source d'état.
    ///   - isArtist: vrai pour le profil artiste, faux pour le profil manager.
    ///   - onSaved: appelé après un enregistrement réussi.
    public init(viewModel: PublicProfileViewModel, isArtist: Bool, onSaved: @escaping () -> Void) {
        self._viewModel = Bindable(viewModel)
        self.isArtist = isArtist
        self.onSaved = onSaved
    }

    public var body: some View {
        Group {
            if viewModel.loading {
                DMLoadingBox()
            } else {
                ScrollView {
                    VStack(spacing: theme.spacing.md) {
                        coverSection
                        infoCard
                        socialCard
                        visibilityCard
                        if let message = viewModel.message { DMMessage(message) }
                        DMButton(
                            viewModel.saving ? s.saving : s.save,
                            isLoading: viewModel.saving,
                            isEnabled: !viewModel.saving && !viewModel.uploading
                        ) {
                            Task { await viewModel.save(onDone: onSaved) }
                        }
                    }
                    .padding(theme.spacing.lg)
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load(isArtist: isArtist) }
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            Task { await viewModel.uploadCover(item) }
        }
    }

    /// Image de couverture (16:9) + bouton de changement.
    private var coverSection: some View {
        VStack(spacing: theme.spacing.sm) {
            ZStack {
                theme.colors.card
                if let cover = viewModel.coverURL, !cover.isEmpty {
                    DMRemoteImage(url: cover, fallback: "🖼️")
                } else {
                    Text(s.coverImage).foregroundStyle(theme.colors.mutedForeground)
                }
            }
            .aspectRatio(16.0 / 9.0, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))

            PhotosPicker(selection: $pickedItem, matching: .images) {
                Text(viewModel.uploading ? s.uploading : s.changeCover)
                    .font(DMFont.button)
                    .foregroundStyle(theme.colors.secondaryForeground)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(theme.colors.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
            }
            .disabled(viewModel.uploading)
        }
    }

    private var infoCard: some View {
        DMCard {
            VStack(spacing: theme.spacing.md) {
                DMTextField(isArtist ? s.stageName : s.fullName, text: $viewModel.name)
                DMTextField(s.bio, text: $viewModel.bio, axis: .vertical)
                if !isArtist {
                    DMTextField(s.managerExpLabel, text: $viewModel.experience, axis: .vertical)
                }
            }
        }
    }

    /// Liens sociaux : une plateforme éditable par ligne, selon le rôle.
    private var socialCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                DMSectionTitle(s.socialLinksHeader)
                Text(s.socialLinksHint)
                    .font(DMFont.caption)
                    .foregroundStyle(theme.colors.mutedForeground)
                ForEach(viewModel.platforms, id: \.self) { platform in
                    DMTextField(
                        platform.label,
                        text: Binding(
                            get: { viewModel.social[platform.key] ?? "" },
                            set: { viewModel.social[platform.key] = $0 }
                        ),
                        placeholder: platform.hint,
                        autocapitalization: .never
                    )
                }
            }
        }
    }

    private var visibilityCard: some View {
        DMCard {
            HStack {
                Text(s.makeProfilePublic).foregroundStyle(theme.colors.foreground)
                Spacer()
                Toggle("", isOn: $viewModel.isPublic).labelsHidden()
            }
        }
    }
}

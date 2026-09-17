import SwiftUI
import PhotosUI
import Observation
import CoreNetwork
import CoreUI
import CoreUpload
import DomainModels
import FeatureProfile
import FeatureReplay

/// ViewModel « Contenu » (créateur) : publie une vidéo lifestyle (upload → `POST /lifestyle`)
/// et liste les vidéos + replays de l'artiste (`GET /lifestyle?artistId=me`, replays via
/// `mine=true`). Mêmes endpoints que le web. Miroir de `MyContentViewModel` (Android).
@Observable
@MainActor
public final class MyContentViewModel {
    public private(set) var artistName = "Artiste"
    public private(set) var videos: [LifestyleVideo] = []
    public private(set) var replays: [ReplayVideo] = []
    public private(set) var pendingVideoURL: String?
    public private(set) var pendingThumbnailURL: String?
    public private(set) var pendingDuration = "0:00"
    public private(set) var uploading = false
    public private(set) var uploadingThumb = false
    public private(set) var submitting = false
    public private(set) var message: String?

    private var myUserId: String?
    private let contentRepository: ContentRepository
    private let replayRepository: ReplayRepository
    private let profileRepository: ProfileRepository
    private let uploader: MediaUploader

    /// - Parameters:
    ///   - contentRepository: publication + lecture des vidéos lifestyle.
    ///   - replayRepository: lecture + réglages des replays possédés.
    ///   - profileRepository: identité du créateur (nom affiché).
    ///   - uploader: upload vidéo/miniature (presign → PUT → confirm).
    public init(
        contentRepository: ContentRepository,
        replayRepository: ReplayRepository,
        profileRepository: ProfileRepository,
        uploader: MediaUploader
    ) {
        self.contentRepository = contentRepository
        self.replayRepository = replayRepository
        self.profileRepository = profileRepository
        self.uploader = uploader
    }

    /// Charge l'artiste + ses vidéos + ses replays.
    public func load() async {
        let me = try? await profileRepository.me()
        myUserId = me?.user.id
        artistName = me?.profile?.displayName ?? "Artiste"
        if let uid = myUserId {
            videos = (try? await contentRepository.myLifestyle(artistId: uid)) ?? []
        }
        replays = (try? await replayRepository.myReplays()) ?? []
    }

    /// Upload la vidéo choisie (catégorie lifestyle) et mémorise son URL + durée.
    public func uploadVideo(_ media: LocalMedia) async {
        uploading = true
        message = nil
        let seconds = await PhotoPickerLoader.videoDurationSeconds(media) ?? 0
        do {
            pendingVideoURL = try await uploader.upload(media, category: UploadCategory.lifestyle)
            pendingDuration = Self.formatDuration(seconds)
        } catch {
            message = error.localizedDescription
        }
        uploading = false
    }

    /// Upload la miniature choisie (catégorie image) et mémorise son URL.
    public func uploadThumbnail(_ media: LocalMedia) async {
        uploadingThumb = true
        message = nil
        do {
            pendingThumbnailURL = try await uploader.upload(media, category: UploadCategory.image)
        } catch {
            message = error.localizedDescription
        }
        uploadingThumb = false
    }

    /// Signale une erreur (lecture/plafond de taille) à l'UI.
    public func setMessage(_ text: String?) { message = text }

    /// Publie la vidéo (URL déjà uploadée) via `POST /lifestyle`, puis recharge.
    public func publish(title: String, description: String, onDone: @escaping () -> Void) async {
        guard !title.trimmed.isEmpty, let videoURL = pendingVideoURL else {
            message = AppStrings.current.errTitleDateRequired
            return
        }
        submitting = true
        message = nil
        do {
            try await contentRepository.publish(
                CreateLifestyleRequest(
                    artistName: artistName,
                    title: title.trimmed,
                    videoURL: videoURL,
                    thumbnailURL: pendingThumbnailURL,
                    description: description.nilIfBlank,
                    duration: pendingDuration
                )
            )
            pendingVideoURL = nil
            pendingThumbnailURL = nil
            message = AppStrings.current.videoPublished
            await load()
            onDone()
        } catch {
            message = AppStrings.current.errCreateFailed
        }
        submitting = false
    }

    /// Réglages (prix, publication) d'un replay — hôte/propriétaire.
    public func updateReplaySettings(id: String, replayPrice: Double, isPublic: Bool, onDone: @escaping (Bool) -> Void) async {
        let ok = await replayRepository.updateSettings(id: id, replayPrice: replayPrice, isPublic: isPublic)
        if ok { await load() }
        onDone(ok)
    }

    /// Remplace le fichier vidéo d'un replay (téléversement direct, catégorie `replay`).
    public func replaceReplayVideo(id: String, media: LocalMedia, onDone: @escaping (Bool) -> Void) async {
        do {
            let url = try await uploader.upload(media, category: UploadCategory.replay)
            let ok = await replayRepository.replaceVideo(id: id, videoURL: url)
            if ok { await load() }
            onDone(ok)
        } catch {
            onDone(false)
        }
    }

    /// Durée au format `m:ss` (repli `0:00`).
    private static func formatDuration(_ totalSeconds: Int) -> String {
        guard totalSeconds > 0 else { return "0:00" }
        return "\(totalSeconds / 60):\(String(format: "%02d", totalSeconds % 60))"
    }
}

/// Écran « Contenu » — publier une vidéo lifestyle + voir ses vidéos et replays.
/// Équivalent mobile de l'onglet Contenu du web. Miroir de `MyContentScreen` (Android).
///
/// - Parameter isArtist: masque la publication lifestyle + « Mes vidéos » pour un manager
///   (pas d'artiste) qui accède à cet écran seulement pour gérer les replays de duels/
///   compétitions qu'il gère — ces deux sections n'ont pas de sens pour lui.
@MainActor
public struct MyContentView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: MyContentViewModel
    private let isArtist: Bool

    @State private var title = ""
    @State private var description = ""
    @State private var manageReplay: ReplayVideo?
    @State private var videoPicker: PhotosPickerItem?
    @State private var thumbPicker: PhotosPickerItem?

    public init(viewModel: MyContentViewModel, isArtist: Bool = true) {
        self.viewModel = viewModel
        self.isArtist = isArtist
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.md) {
                if let message = viewModel.message { DMMessage(message) }

                if isArtist { publishCard }

                if isArtist {
                    Text(s.myVideos).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                    if viewModel.videos.isEmpty {
                        Text(s.noMyVideos).foregroundStyle(theme.colors.mutedForeground)
                    } else {
                        ForEach(viewModel.videos) { v in
                            ContentRow(title: v.title ?? "—", meta: "❤ \(v.likesCount) · 👁 \(v.viewsCount)")
                        }
                    }
                }

                Text(s.myReplays).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                if viewModel.replays.isEmpty {
                    Text(s.noMyReplays).foregroundStyle(theme.colors.mutedForeground)
                } else {
                    ForEach(viewModel.replays) { r in
                        Button { manageReplay = r } label: {
                            ContentRow(
                                title: r.title ?? "—",
                                meta: "👁 \(r.viewsCount)" + (r.isPublic ? "" : " · Brouillon")
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .dmScreenBackground()
        .task { await viewModel.load() }
        .onChange(of: videoPicker) { _, item in
            guard let item else { return }
            Task {
                do {
                    let media = try await PhotoPickerLoader.load(item, maxBytes: 500 * 1024 * 1024)
                    await viewModel.uploadVideo(media)
                } catch {
                    viewModel.setMessage(s.fileUnreadable)
                }
            }
        }
        .onChange(of: thumbPicker) { _, item in
            guard let item else { return }
            Task {
                do {
                    let media = try await PhotoPickerLoader.load(item, maxBytes: 5 * 1024 * 1024)
                    await viewModel.uploadThumbnail(media)
                } catch {
                    viewModel.setMessage(s.fileUnreadable)
                }
            }
        }
        .sheet(item: $manageReplay) { replay in
            ReplayManageSheet(
                replay: replay,
                onDismiss: { manageReplay = nil },
                onSave: { price, isPublic in
                    Task { await viewModel.updateReplaySettings(id: replay.id, replayPrice: price, isPublic: isPublic) { _ in manageReplay = nil } }
                },
                onReplaceVideo: { media in
                    Task { await viewModel.replaceReplayVideo(id: replay.id, media: media) { _ in manageReplay = nil } }
                }
            )
        }
    }

    /// Publier une vidéo lifestyle (artiste seulement — sans objet pour un manager).
    private var publishCard: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(s.publishLifestyle).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                DMTextField(s.titleRequired, text: $title)
                DMTextField(s.description, text: $description, axis: .vertical)

                PhotosPicker(selection: $videoPicker, matching: .videos) {
                    Text(viewModel.uploading ? "…" : s.chooseVideo)
                        .font(DMFont.button)
                        .foregroundStyle(theme.colors.secondaryForeground)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.colors.secondary)
                        .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                }
                .disabled(viewModel.uploading)

                if viewModel.pendingVideoURL != nil {
                    Text("\(s.videoReady) · \(viewModel.pendingDuration)").foregroundStyle(theme.colors.accent)
                }

                PhotosPicker(selection: $thumbPicker, matching: .images) {
                    Text(viewModel.uploadingThumb ? "…" : (viewModel.pendingThumbnailURL != nil ? "Miniature ✓" : "Miniature (optionnel)"))
                        .font(DMFont.button)
                        .foregroundStyle(theme.colors.secondaryForeground)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.colors.secondary)
                        .clipShape(RoundedRectangle(cornerRadius: theme.radius.md, style: .continuous))
                }
                .disabled(viewModel.uploadingThumb)

                DMButton(
                    s.publishVideo,
                    isEnabled: viewModel.pendingVideoURL != nil && !viewModel.submitting
                ) {
                    Task {
                        await viewModel.publish(title: title, description: description) {
                            title = ""
                            description = ""
                        }
                    }
                }
            }
        }
    }
}

/// Ligne simple de contenu (titre + méta).
private struct ContentRow: View {
    @Environment(\.dmTheme) private var theme
    let title: String
    let meta: String

    var body: some View {
        DMCard {
            HStack {
                Text(title).font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                Spacer()
                Text(meta).foregroundStyle(theme.colors.mutedForeground)
            }
        }
    }
}

/// Feuille de gestion d'un replay : prix, publication, téléchargement, remplacement vidéo.
/// Miroir de `ReplayManageDialog` (Android).
@MainActor
private struct ReplayManageSheet: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.openURL) private var openURL

    let replay: ReplayVideo
    let onDismiss: () -> Void
    let onSave: (Double, Bool) -> Void
    let onReplaceVideo: (LocalMedia) -> Void

    @State private var price: String
    @State private var isPublic: Bool
    @State private var uploadingVideo = false
    @State private var videoPicker: PhotosPickerItem?

    init(replay: ReplayVideo, onDismiss: @escaping () -> Void, onSave: @escaping (Double, Bool) -> Void, onReplaceVideo: @escaping (LocalMedia) -> Void) {
        self.replay = replay
        self.onDismiss = onDismiss
        self.onSave = onSave
        self.onReplaceVideo = onReplaceVideo
        self._price = State(initialValue: String(Int(replay.replayPrice)))
        self._isPublic = State(initialValue: replay.isPublic)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.md) {
            HStack {
                Text("🎛️ Gestion du replay").font(DMFont.body).bold().foregroundStyle(theme.colors.foreground)
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(theme.colors.mutedForeground)
                }
                .buttonStyle(.plain)
            }

            DMCard {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Rendre public").foregroundStyle(theme.colors.foreground)
                        Text("Visible sur la page des replays une fois activé.")
                            .font(DMFont.caption)
                            .foregroundStyle(theme.colors.mutedForeground)
                    }
                    Spacer()
                    Toggle("", isOn: $isPublic).labelsHidden()
                }
            }

            DMTextField("Prix (crédits — 0 = gratuit)", text: $price, keyboard: .numberPad)
                .onChange(of: price) { _, newValue in
                    let digits = newValue.digitsOnly
                    if digits != newValue { price = digits }
                }

            DMButton("Enregistrer les réglages") {
                onSave(Double(price) ?? 0, isPublic)
            }

            HStack(spacing: theme.spacing.sm) {
                DMButton("Télécharger", style: .outline) {
                    if let urlString = replay.videoURL, let url = URL(string: urlString) { openURL(url) }
                }
                PhotosPicker(selection: $videoPicker, matching: .videos) {
                    Text(uploadingVideo ? "…" : "Remplacer la vidéo")
                        .font(DMFont.button)
                        .foregroundStyle(theme.colors.foreground)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .overlay(RoundedRectangle(cornerRadius: theme.radius.md).stroke(theme.colors.border))
                }
                .disabled(uploadingVideo)
            }
        }
        .padding(theme.spacing.lg)
        .onChange(of: videoPicker) { _, item in
            guard let item else { return }
            Task {
                uploadingVideo = true
                do {
                    let media = try await PhotoPickerLoader.load(item, maxBytes: 2048 * 1024 * 1024)
                    onReplaceVideo(media)
                } catch {
                    uploadingVideo = false
                }
            }
        }
    }
}

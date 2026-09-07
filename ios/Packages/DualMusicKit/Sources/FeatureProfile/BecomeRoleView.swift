import SwiftUI
import Observation
import CoreNetwork
import CoreUI
import DomainModels

/// ViewModel des candidatures de rôle (fan → artiste / manager).
///
/// Charge le gating (réglages admin) + le statut de mes candidatures, puis soumet les
/// demandes. Miroir de `BecomeRoleViewModel` Android.
@Observable
@MainActor
public final class BecomeRoleViewModel {

    public private(set) var artistEnabled = true
    public private(set) var managerEnabled = true
    public private(set) var artistPending = false
    public private(set) var managerPending = false
    public private(set) var isSubmitting = false
    public private(set) var message: String?

    private let repository: ProfileRepository

    /// - Parameter repository: accès REST profil/rôles.
    public init(repository: ProfileRepository) {
        self.repository = repository
    }

    /// Charge les réglages d'ouverture + mes candidatures en attente.
    public func load() async {
        artistEnabled = await repository.requestsEnabled(RoleEndpoints.artistRequestsEnabled)
        managerEnabled = await repository.requestsEnabled(RoleEndpoints.managerRequestsEnabled)
        artistPending = ((try? await repository.myArtistRequests()) ?? []).contains { $0.status == "pending" }
        managerPending = ((try? await repository.myManagerRequests()) ?? []).contains { $0.status == "pending" }
    }

    /// Soumet une candidature artiste (description ≥ 10 caractères).
    /// - Parameters:
    ///   - description: présentation du projet musical.
    ///   - documentURL: lien d'un justificatif (optionnel).
    ///   - socialLinks: liens réseaux sociaux (les valeurs vides sont retirées).
    public func applyArtist(description: String, documentURL: String, socialLinks: [String: String]) async {
        guard description.trimmed.count >= 10 else {
            message = AppStrings.current.artistDescMinError
            return
        }
        await submit {
            try await self.repository.applyArtist(
                ApplyArtistRequest(
                    description: description.trimmed,
                    socialLinks: socialLinks.filter { !$0.value.trimmed.isEmpty },
                    justificationDocumentUrl: documentURL.nilIfBlank
                )
            )
        }
    }

    /// Soumet une candidature manager (bio + expérience requises).
    public func applyManager(bio: String, experience: String) async {
        guard bio.trimmed.count >= 10, experience.trimmed.count >= 5 else {
            message = AppStrings.current.managerFieldsRequired
            return
        }
        await submit {
            try await self.repository.applyManager(
                ApplyManagerRequest(bio: bio.trimmed, experience: experience.trimmed)
            )
        }
    }

    /// Exécute une soumission en gérant l'état de chargement et le rechargement du gating.
    private func submit(_ action: @escaping () async throws -> Void) async {
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }
        do {
            try await action()
            message = AppStrings.current.requestSent
            await load()
        } catch {
            message = (error as? APIError)?.message ?? AppStrings.current.sendFailed
        }
    }
}

/// Écran dédié **« Devenir artiste »** (réservé aux fans) — formulaire séparé, comme le web.
@MainActor
public struct BecomeArtistView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: BecomeRoleViewModel

    @State private var description = ""
    @State private var documentURL = ""
    @State private var instagram = ""
    @State private var tiktok = ""
    @State private var youtube = ""
    @State private var twitter = ""
    @State private var facebook = ""
    @State private var spotify = ""

    /// - Parameter viewModel: source d'état (partagée avec l'écran manager).
    public init(viewModel: BecomeRoleViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                if let message = viewModel.message { DMMessage(message) }

                RoleCard(title: s.menuBecomeArtist, enabled: viewModel.artistEnabled, pending: viewModel.artistPending) {
                    DMTextField(s.artistProjectLabel, text: $description, axis: .vertical)
                    DMTextField(s.justificationDocLabel, text: $documentURL, keyboard: .URL, autocapitalization: .never)

                    Text(s.socialNetworksOptional)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    DMTextField("Instagram", text: $instagram, keyboard: .URL, autocapitalization: .never)
                    DMTextField("TikTok", text: $tiktok, keyboard: .URL, autocapitalization: .never)
                    DMTextField("YouTube", text: $youtube, keyboard: .URL, autocapitalization: .never)
                    DMTextField("X (Twitter)", text: $twitter, keyboard: .URL, autocapitalization: .never)
                    DMTextField("Facebook", text: $facebook, keyboard: .URL, autocapitalization: .never)
                    DMTextField("Spotify", text: $spotify, keyboard: .URL, autocapitalization: .never)

                    DMButton(
                        viewModel.isSubmitting ? s.sending : s.sendApplication,
                        isLoading: viewModel.isSubmitting,
                        isEnabled: !viewModel.isSubmitting
                    ) {
                        Task {
                            await viewModel.applyArtist(
                                description: description,
                                documentURL: documentURL,
                                socialLinks: [
                                    "instagram": instagram,
                                    "tiktok": tiktok,
                                    "youtube": youtube,
                                    "twitter": twitter,
                                    "facebook": facebook,
                                    "spotify": spotify,
                                ]
                            )
                        }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }
}

/// Écran dédié **« Devenir manager »** (réservé aux fans).
///
/// ⚠️ N'apparaît dans le menu que si l'admin a ouvert les candidatures manager.
@MainActor
public struct BecomeManagerView: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    private let viewModel: BecomeRoleViewModel

    @State private var bio = ""
    @State private var experience = ""

    /// - Parameter viewModel: source d'état (partagée avec l'écran artiste).
    public init(viewModel: BecomeRoleViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.lg) {
                if let message = viewModel.message { DMMessage(message) }

                RoleCard(title: s.menuBecomeManager, enabled: viewModel.managerEnabled, pending: viewModel.managerPending) {
                    DMTextField(s.bio, text: $bio, axis: .vertical)
                    DMTextField(s.managerExpLabel, text: $experience, axis: .vertical)
                    DMButton(
                        viewModel.isSubmitting ? s.sending : s.sendApplication,
                        isLoading: viewModel.isSubmitting,
                        isEnabled: !viewModel.isSubmitting
                    ) {
                        Task { await viewModel.applyManager(bio: bio, experience: experience) }
                    }
                }
            }
            .padding(theme.spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dmScreenBackground()
        .task { await viewModel.load() }
    }
}

/// Carte d'une candidature de rôle.
///
/// Affiche le formulaire si les candidatures sont ouvertes **et** qu'aucune n'est déjà en
/// attente ; sinon un message explicite (en attente de validation, ou fermées par l'admin).
private struct RoleCard<Form: View>: View {
    @Environment(\.dmTheme) private var theme
    @Environment(\.dmStrings) private var s

    let title: String
    let enabled: Bool
    let pending: Bool
    @ViewBuilder let form: Form

    var body: some View {
        DMCard {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(title)
                    .font(DMFont.headline)
                    .foregroundStyle(theme.colors.foreground)
                if pending {
                    Text(s.applicationPending)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                } else if !enabled {
                    Text(s.applicationsClosed)
                        .font(DMFont.caption)
                        .foregroundStyle(theme.colors.mutedForeground)
                } else {
                    form
                }
            }
        }
    }
}

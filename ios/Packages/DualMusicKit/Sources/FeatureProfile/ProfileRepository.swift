import Foundation
import CoreNetwork
import DomainModels

/// Accès REST au profil : lecture (identité + stats), **édition** (`PATCH /users/me`),
/// **demandes de rôle** (devenir artiste/manager) avec leur gating public, et les
/// opérations **admin** (réglages plateforme + assignation de rôle).
///
/// Miroir de `ProfileRepository` côté Android.
public struct ProfileRepository: Sendable {

    private let http: HTTPClient

    public init(http: HTTPClient) {
        self.http = http
    }

    // MARK: - Profil

    /// Profil + rôles du caller.
    public func me() async throws -> MeResponse {
        try await http.request(.get(UserEndpoints.me), as: MeResponse.self)
    }

    /// Statistiques agrégées (artiste/manager/fan).
    public func stats() async throws -> UserStats {
        try await http.request(.get(UserEndpoints.myStats), as: UserStats.self)
    }

    /// Met à jour le profil (nom, bio, avatar, pays, numéro).
    public func updateProfile(_ request: UpdateProfileRequest) async throws {
        try await http.send(.patch(UserEndpoints.updateMe, body: request))
    }

    /// Programme la suppression du compte (délai de grâce de 20 jours côté backend).
    public func requestAccountDeletion() async throws {
        try await http.send(.post(UserEndpoints.meDeletion))
    }

    /// Annule une suppression de compte programmée.
    public func cancelAccountDeletion() async throws {
        try await http.send(.delete(UserEndpoints.meDeletion))
    }

    /// Change le mot de passe (utilisateur connecté).
    public func changePassword(currentPassword: String, newPassword: String) async throws {
        try await http.send(
            .post(
                AuthEndpoints.passwordChange,
                body: ChangePasswordRequest(currentPassword: currentPassword.nilIfBlank, newPassword: newPassword)
            )
        )
    }

    // MARK: - Profils publics créateurs (artiste / manager)

    /// Profil public artiste du caller. Lu via `GET /users/:id` (pas de `GET /artists/me`
    /// côté backend) — `nil` si l'utilisateur n'a pas encore de profil artiste.
    public func myArtistProfile(userId: String) async throws -> ArtistProfile? {
        try await http.request(.get(CreatorEndpoints.publicProfile(userId)), as: PublicProfileResponse.self).artistProfile
    }

    /// Profil public COMPLET d'un utilisateur (compte + profil artiste + suivi). `GET /users/:id`.
    public func publicProfile(userId: String) async throws -> PublicProfileResponse {
        try await http.request(.get(CreatorEndpoints.publicProfile(userId)), as: PublicProfileResponse.self)
    }

    /// Suit / ne suit plus un artiste. `POST` / `DELETE` `/users/:id/follow`.
    public func setFollow(userId: String, follow: Bool) async throws {
        if follow {
            try await http.send(.post(ArtistEndpoints.follow(userId)))
        } else {
            try await http.send(.delete(ArtistEndpoints.follow(userId)))
        }
    }

    /// Met à jour le profil public artiste (`PATCH /artists/me`).
    public func updateArtistProfile(_ request: UpdateArtistProfileRequest) async throws {
        try await http.send(.patch(CreatorEndpoints.artistMe, body: request))
    }

    /// Profil public manager du caller (`GET /managers/me`).
    public func myManagerProfile() async throws -> ManagerProfile {
        try await http.request(.get(CreatorEndpoints.managerMe), as: ManagerProfile.self)
    }

    /// Met à jour le profil public manager (`PATCH /managers/me`).
    public func updateManagerProfile(_ request: UpdateManagerProfileRequest) async throws {
        try await http.send(.patch(CreatorEndpoints.managerMe, body: request))
    }

    // MARK: - Candidatures de rôle

    /// Candidature artiste.
    public func applyArtist(_ request: ApplyArtistRequest) async throws {
        try await http.send(.post(RoleEndpoints.artistApply, body: request))
    }

    /// Candidature manager.
    public func applyManager(_ request: ApplyManagerRequest) async throws {
        try await http.send(.post(RoleEndpoints.managerApply, body: request))
    }

    /// Mes candidatures artiste (pour détecter un statut en attente).
    public func myArtistRequests() async throws -> [RoleRequest] {
        try await http.request(.get(RoleEndpoints.artistMine), as: [RoleRequest].self)
    }

    /// Mes candidatures manager.
    public func myManagerRequests() async throws -> [RoleRequest] {
        try await http.request(.get(RoleEndpoints.managerMine), as: [RoleRequest].self)
    }

    /// Vrai si les candidatures pour ce rôle sont ouvertes (réglage admin).
    ///
    /// Par défaut **ouvert** si le réglage est absent/illisible : on ne bloque jamais un
    /// utilisateur à cause d'un réglage manquant (même politique qu'Android).
    /// - Parameter key: `artist_requests_enabled` ou `manager_requests_enabled`.
    public func requestsEnabled(_ key: String) async -> Bool {
        await settingEnabled(key, default: true)
    }

    /// Lit un flag public `{enabled}`, avec une valeur par défaut si le réglage est absent/illisible.
    /// - Parameters:
    ///   - key: clé du réglage public.
    ///   - default: valeur de repli.
    public func settingEnabled(_ key: String, default defaultValue: Bool) async -> Bool {
        let setting = try? await http.request(
            .get(RoleEndpoints.publicSetting(key)),
            as: PublicSetting.self
        )
        return setting?.value?.enabled ?? defaultValue
    }

    // MARK: - Admin

    /// Recherche d'utilisateurs (nom/email) — réservé admin.
    public func adminSearchUsers(query: String) async throws -> [AdminUser] {
        try await http.request(.get(AdminEndpoints.usersSearch, query: ["q": query]), as: [AdminUser].self)
    }

    /// Assigne un rôle (`fan`/`artist`/`manager`/`moderator`/`admin`) à un utilisateur.
    public func adminAssignRole(userId: String, role: String) async throws {
        try await http.send(.post(AdminEndpoints.roles, body: AssignRoleRequest(userId: userId, role: role)))
    }

    /// Révoque un rôle d'un utilisateur.
    public func adminRevokeRole(userId: String, role: String) async throws {
        try await http.send(.delete(AdminEndpoints.roles, body: AssignRoleRequest(userId: userId, role: role)))
    }

    /// Ouvre/ferme les candidatures d'un rôle.
    /// - Parameters:
    ///   - key: clé de réglage plateforme.
    ///   - enabled: nouvel état.
    public func adminSetRequestsEnabled(key: String, enabled: Bool) async throws {
        try await http.send(.put(AdminEndpoints.setting(key), body: SettingBody(value: SettingEnabled(enabled: enabled))))
    }
}

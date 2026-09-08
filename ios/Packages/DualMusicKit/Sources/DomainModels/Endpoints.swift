import Foundation

/*
 * Catalogue des chemins REST — source unique côté iOS, miroir exact des objets
 * `*Endpoints` de `shared-domain`. Tous les chemins sont **relatifs à `/api/v1`** :
 * `CoreNetwork.HTTPClient` ajoute le préfixe.
 *
 * Règle : aucune feature ne doit écrire une URL en dur ; elle passe toujours par ce
 * catalogue, afin qu'un changement de route se répercute en un seul endroit (et reste
 * comparable ligne à ligne avec Android).
 */

/// Chemins d'authentification (`/auth/…`).
public enum AuthEndpoints {
    public static let login = "/auth/login"
    public static let register = "/auth/register"
    public static let refresh = "/auth/refresh"
    public static let logout = "/auth/logout"
    public static let me = "/auth/me"
    public static let otpPhoneSend = "/auth/otp/phone/send"
    public static let otpPhoneVerify = "/auth/otp/phone/verify"
    public static let otpEmailSend = "/auth/otp/email/send"
    public static let otpEmailVerify = "/auth/otp/email/verify"
    public static let passwordForgot = "/auth/password/forgot"
    public static let passwordReset = "/auth/password/reset"
    public static let passwordChange = "/auth/password/change"
    public static let oauthGoogleStart = "/auth/oauth/google"
    /// Échange natif de l'ID token Google (mobile).
    public static let oauthGoogleNative = "/auth/oauth/google/native"
}

/// Chemins du profil / utilisateur.
public enum UserEndpoints {
    /// Profil + rôles du caller (réhydratation).
    public static let me = "/auth/me"
    /// Statistiques agrégées du caller.
    public static let myStats = "/users/me/stats"
    /// Mise à jour du profil du caller.
    public static let updateMe = "/users/me"
    /// Suppression de compte à effet différé (POST = programmer, DELETE = annuler).
    public static let meDeletion = "/users/me/deletion"
    /// Badges d'un utilisateur.
    public static func badges(_ id: String) -> String { "/users/\(id)/badges" }
}

/// Chemins du portefeuille.
public enum WalletEndpoints {
    /// Solde : la racine du routeur wallet.
    public static let balance = "/wallet"
    public static let revenues = "/wallet/revenues"
    public static let spending = "/wallet/spending"
    public static let revenuesBreakdown = "/wallet/revenues/breakdown"
    public static let transactions = "/wallet/transactions"
    public static let vote = "/wallet/vote"
    public static let giftsPurchase = "/wallet/gifts/purchase"
    public static let giftsSend = "/wallet/gifts/send"
    public static let ticketDuel = "/wallet/tickets/duel"
    public static let ticketConcert = "/wallet/tickets/concert"
    public static let replayUnlock = "/wallet/replays/unlock"
}

/// Chemins des paiements.
public enum PaymentEndpoints {
    public static let cinetpayInit = "/payments/cinetpay/init"
    public static let cinetpayCountries = "/payments/cinetpay/countries"
    public static let stripeSubscription = "/payments/stripe/subscription"
    public static let history = "/payments/history"
    /// Règle l'achat StoreKit déjà payé côté client (`transactionId`) — le serveur
    /// re-vérifie auprès d'Apple et ne fait jamais confiance au crédit annoncé.
    public static let appleVerify = "/payments/apple/verify"
}

/// Chemins des lives (feed vertical).
public enum LiveEndpoints {
    public static let list = "/lives"
    public static func detail(_ id: String) -> String { "/lives/\(id)" }
    public static func messages(_ id: String) -> String { "/lives/\(id)/messages" }
    public static func end(_ id: String) -> String { "/lives/\(id)/end" }
    /// Hôte : réglages du live (dédicaces/invités/chat), mise à jour partielle.
    public static func settings(_ id: String) -> String { "/lives/\(id)/settings" }
    /// Spectateur : demande à rejoindre en invité (« lever la main »).
    public static func join(_ id: String) -> String { "/lives/\(id)/join" }
    /// Demandes d'invité de ce live : `?status=pending|accepted`.
    public static func joinRequests(_ id: String) -> String { "/lives/\(id)/join-requests" }
    /// Annule SA PROPRE demande (autorisé au demandeur quel que soit son statut courant —
    /// contrairement à ``respondJoin(_:)``, réservé à l'hôte).
    public static func cancelJoin(_ requestId: String) -> String { "/lives/join-requests/\(requestId)" }
    /// Hôte : répond à une demande (`accepted`/`rejected`/`ended`).
    public static func respondJoin(_ requestId: String) -> String { "/lives/join-requests/\(requestId)/respond" }
}

/// Chemins des duels.
///
/// Rappel : le **vote payant** n'est PAS ici — c'est une opération de portefeuille
/// (``WalletEndpoints/vote``, procédure atomique).
public enum DuelEndpoints {
    public static let list = "/duels"
    /// Tallies de votes en masse : `?ids=a,b,c`.
    public static let votesBatch = "/duels/votes/batch"
    public static func detail(_ id: String) -> String { "/duels/\(id)" }
    public static func votes(_ id: String) -> String { "/duels/\(id)/votes" }
    public static func messages(_ id: String) -> String { "/duels/\(id)/messages" }
    public static func myTicket(_ id: String) -> String { "/duels/\(id)/my-ticket" }
}

/// Chemins des concerts.
///
/// Deux familles côté backend : `/artist-concerts` (catalogue public créé par les artistes)
/// et `/concerts` (routes transverses : billetterie, dédicaces, rappels).
/// ⚠️ L'ACHAT d'un billet est un débit : ``WalletEndpoints/ticketConcert``.
public enum ConcertEndpoints {
    public static let artistList = "/artist-concerts"
    public static let artistMine = "/artist-concerts/me"
    public static let dedicationsMine = "/concerts/dedications/me"
    public static let dedicationsArtistMine = "/concerts/dedications/artist/me"
    public static let dedicationsPurchase = "/concerts/dedications"
    public static func artistDetail(_ id: String) -> String { "/artist-concerts/\(id)" }
    public static func ticketInfo(_ id: String) -> String { "/concerts/\(id)/ticket-info" }
    public static func reminder(_ id: String) -> String { "/concerts/\(id)/reminder" }
    /// Hôte : accepte une dédicace EN ATTENTE — débite le fan maintenant.
    public static func dedicationAccept(_ id: String) -> String { "/concerts/dedications/\(id)/accept" }
    /// Hôte : rejette une dédicace EN ATTENTE — aucun débit.
    public static func dedicationReject(_ id: String) -> String { "/concerts/dedications/\(id)/reject" }
    /// Hôte : marque une dédicace acceptée comme interprétée en direct.
    public static func dedicationDeliver(_ id: String) -> String { "/concerts/dedications/\(id)/deliver" }
}

/// Chemins des compétitions.
public enum CompetitionEndpoints {
    public static let list = "/competitions"
    public static let candidaciesMine = "/competitions/candidacies/mine"
    public static let ticketsMine = "/competitions/tickets/mine"
    public static func detail(_ id: String) -> String { "/competitions/\(id)" }
    public static func candidates(_ id: String) -> String { "/competitions/\(id)/candidates" }
    public static func vote(_ id: String) -> String { "/competitions/\(id)/vote" }
    public static func gifts(_ id: String) -> String { "/competitions/\(id)/gifts" }
    public static func tickets(_ id: String) -> String { "/competitions/\(id)/tickets" }
    public static func myTicket(_ id: String) -> String { "/competitions/\(id)/my-ticket" }
}

/// Chemins des cadeaux.
public enum GiftEndpoints {
    public static let catalog = "/gifts"
    public static let inventory = "/gifts/inventory"
    /// Achat dans l'inventaire : opération de portefeuille (débit atomique).
    public static let purchase = "/wallet/gifts/purchase"
}

/// Chemins des rediffusions.
public enum ReplayEndpoints {
    public static let list = "/replays"
    /// Déblocage : opération de portefeuille (débit atomique).
    public static let unlock = "/wallet/replays/unlock"
    public static func detail(_ id: String) -> String { "/replays/\(id)" }
    public static func access(_ id: String) -> String { "/replays/\(id)/access" }
    public static func views(_ id: String) -> String { "/replays/\(id)/views" }
    public static func likes(_ id: String) -> String { "/replays/\(id)/likes" }
}

/// Chemins des classements.
public enum LeaderboardEndpoints {
    public static let artists = "/leaderboards/artists"
    public static let donors = "/leaderboards/donors"
    public static let seasons = "/leaderboards/seasons"
    public static let winners = "/leaderboards/winners"
}

/// Chemins du parrainage.
public enum ReferralEndpoints {
    public static let me = "/referrals/me"
    public static func claim(_ id: String) -> String { "/referrals/\(id)/claim" }
}

/// Chemins des abonnements.
public enum SubscriptionEndpoints {
    public static let plans = "/subscriptions/plans"
    public static let me = "/subscriptions/me"
}

/// Chemins du contenu (lifestyle + blog).
public enum ContentEndpoints {
    public static let lifestyle = "/lifestyle"
    public static let blogs = "/blogs"
    public static func lifestyleViews(_ id: String) -> String { "/lifestyle/\(id)/views" }
    public static func lifestyleLikes(_ id: String) -> String { "/lifestyle/\(id)/likes" }
    public static func blog(_ id: String) -> String { "/blogs/\(id)" }
    public static func blogViews(_ id: String) -> String { "/blogs/\(id)/views" }
}

/// Chemins de l'annuaire artistes / suivi.
public enum ArtistEndpoints {
    public static let list = "/artists"
    public static let following = "/users/me/following"
    public static func follow(_ id: String) -> String { "/users/\(id)/follow" }
}

/// Chemins des notifications.
public enum NotificationEndpoints {
    public static let list = "/notifications"
    public static let unreadCount = "/notifications/unread-count"
    public static let readAll = "/notifications/read-all"
    /// Enregistrement/suppression d'un jeton d'appareil (push mobile).
    public static let devices = "/notifications/devices"
    public static func read(_ id: String) -> String { "/notifications/\(id)/read" }
    public static func remove(_ id: String) -> String { "/notifications/\(id)" }
}

/// Chemins des retraits.
public enum WithdrawalEndpoints {
    public static let pin = "/withdrawals/pin"
    public static let pinVerify = "/withdrawals/pin/verify"
    public static let net = "/withdrawals/net"
    public static let methods = "/withdrawals/methods"
    public static let mine = "/withdrawals/me"
    public static let create = "/withdrawals"
}

/// Chemins des demandes de rôle + réglages publics.
public enum RoleEndpoints {
    public static let artistApply = "/artists/requests"
    public static let artistMine = "/artists/requests/me"
    public static let managerApply = "/managers/requests"
    public static let managerMine = "/managers/requests/me"

    /// Réglage public autorisant les candidatures.
    public static func publicSetting(_ key: String) -> String { "/settings/public/\(key)" }

    public static let artistRequestsEnabled = "artist_requests_enabled"
    public static let managerRequestsEnabled = "manager_requests_enabled"
}

/// Chemins de l'espace admin.
public enum AdminEndpoints {
    public static let usersSearch = "/admin/users/search"
    public static let roles = "/admin/roles"
    public static func setting(_ key: String) -> String { "/admin/settings/\(key)" }
}

/// Chemins du sponsoring.
public enum SponsorEndpoints {
    public static let tiers = "/sponsors/tiers"
    public static let myRequests = "/sponsors/requests/me"
    public static let create = "/sponsors/requests"
    public static func pay(_ id: String) -> String { "/sponsors/requests/\(id)/pay" }
}

/// Chemins des outils créateur.
public enum CreatorEndpoints {
    public static let duelRequestsMine = "/duels/requests/mine"
    public static let duelRequestCreate = "/duels/requests"
    public static func duelRespond(_ id: String) -> String { "/duels/requests/\(id)/respond" }
    public static let myConcerts = "/artist-concerts/me"
}

/// Chemin du service de jetons LiveKit.
public enum MediaEndpoints {
    public static let livekitToken = "/livekit/token"
}

/// Chemins de l'upload média.
public enum UploadEndpoints {
    public static let presign = "/uploads/presign"
    public static let confirm = "/uploads/confirm"
}

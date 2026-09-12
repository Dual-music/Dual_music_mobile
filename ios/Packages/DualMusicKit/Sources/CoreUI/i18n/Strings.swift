import SwiftUI

/// Langue de l'interface.
///
/// Miroir de `core/ui/i18n/Strings.kt#Language`. La langue est persistée par le contrôleur
/// de langue de l'app et injectée dans l'environnement SwiftUI.
public enum AppLanguage: String, Sendable, CaseIterable, Identifiable {
    case fr
    case en

    public var id: String { rawValue }
}

/// Table de chaînes localisées de l'interface.
///
/// Chaque écran lit ses libellés via `@Environment(\.dmStrings)`. Pour ajouter une langue,
/// fournir une nouvelle instance statique ci-dessous.
///
/// > Choix d'implémentation : une **structure de chaînes** plutôt que `Localizable.strings`,
/// > pour rester strictement aligné sur Android (mêmes clés, mêmes textes, même ordre) et
/// > permettre un diff ligne à ligne entre les deux plateformes.
public struct DMStrings: Sendable {
    // Navigation basse.
    public let navHome: String
    public let navLives: String
    public let navDuels: String
    public let navConcerts: String
    public let navCompetitions: String
    // Accueil.
    public let homeTitle: String
    public let homeSubtitle: String
    public let lifestyle: String
    public let ranking: String
    public let artists: String
    // Barre supérieure.
    public let notifications: String
    public let profile: String
    // Préférences.
    public let preferences: String
    public let appearance: String
    public let themeLight: String
    public let themeDark: String
    public let themeSystem: String
    public let language: String
    public let french: String
    public let english: String
    public let account: String
    public let deleteAccount: String
    public let cancelDeletion: String
    public let confirm: String
    public let cancel: String
    // Titres d'écrans catalogue.
    public let screenDuels: String
    public let screenConcerts: String
    public let screenCompetitions: String
    public let screenArtists: String
    public let screenDiscover: String
    public let screenRankings: String
    // Communs.
    public let loading: String
    public let retry: String
    public let save: String
    public let emptyGeneric: String
    // Menu profil.
    public let menuMySpace: String
    public let menuDashboard: String
    public let menuFollowing: String
    public let menuSubscription: String
    public let menuTransactions: String
    public let menuReferral: String
    public let menuSponsor: String
    public let menuBecomeArtist: String
    public let menuBecomeManager: String
    public let menuCreatorSpace: String
    public let menuWithdraw: String
    public let menuReplays: String
    public let menuGiftShop: String
    public let menuEditProfile: String
    public let menuSignOut: String
    public let menuAdminSpace: String
    // Portefeuille / recharge / boutique.
    public let walletExpenses: String
    public let walletIncome: String
    public let creditPurchases: String
    public let noPurchases: String
    public let rechargeCredits: String
    public let amountCredits: String
    public let mobileMoneyNumber: String
    /// Libellé « Opérateur » (nommé `operatorLabel` : `operator` est un mot-clé Swift).
    public let operatorLabel: String
    public let country: String
    public let buy: String
    public let myBalance: String
    public let recharge: String
    public let rechargeHint: String
    public let payByMobileMoney: String
    public let initializing: String
    public let giftShopHint: String
    public let emptyShop: String
    public let emptyShopHint: String
    // Authentification.
    public let authLoginSubtitle: String
    public let authRegisterSubtitle: String
    public let authResetSubtitle: String
    public let resetPasswordTitle: String
    public let pleaseWait: String
    public let signIn: String
    public let createAccount: String
    public let sendCode: String
    public let reset: String
    public let email: String
    public let emailPlaceholder: String
    public let password: String
    public let passwordStar: String
    public let confirmPasswordStar: String
    public let newPassword: String
    public let atLeast8: String
    public let passwordsDontMatch: String
    public let codeFromEmail: String
    public let forgotPassword: String
    public let noAccountSignUp: String
    public let alreadyAccountSignIn: String
    public let backToLogin: String
    public let chooseCountry: String
    public let countryStar: String
    public let continueWithGoogle: String
    public let googleCancelled: String
    public let forgotHint: String
    public let verifyEmailTitle: String
    public let verifyEmailSentTo: String
    public let verifying: String
    public let validate: String
    public let resendCode: String
    public let skipForNow: String
    public let completeProfileTitle: String
    public let completeProfileSubtitle: String
    public let fullNameStar: String
    public let phoneOptional: String
    public let finish: String
    public let later: String
    public let resetCodeSent: String
    public let passwordResetDone: String
    public let newCodeSent: String
    public let errWrongCredentials: String
    public let errEmailTaken: String
    public let errInvalidFields: String
    public let errUnstableConnection: String
    public let errGeneric: String
    // Profil : édition + candidatures de rôle.
    public let bio: String
    public let fullName: String
    public let phoneNumber: String
    public let avatar: String
    public let changePhoto: String
    public let uploading: String
    public let saving: String
    public let changePassword: String
    public let currentPassword: String
    public let changing: String
    public let uploadFailed: String
    public let saveFailed: String
    public let changeFailed: String
    public let fileUnreadable: String
    public let newPasswordTooShort: String
    public let artistProjectLabel: String
    public let justificationDocLabel: String
    public let socialNetworksOptional: String
    public let sending: String
    public let sendApplication: String
    public let managerExpLabel: String
    public let artistDescMinError: String
    public let managerFieldsRequired: String
    public let sendFailed: String
    public let applicationsClosed: String
    public let applicationPending: String
    public let spaceArtist: String
    public let spaceManager: String
    public let spaceFan: String
    public let statVotesReceived: String
    public let statDuelsWon: String
    public let statGiftsReceived: String
    public let statDuelsManaged: String
    public let statActive: String
    public let statVotesCast: String
    public let statGiftsSent: String
    public let statTicketsBought: String
    public let roleAdmin: String
    public let roleArtist: String
    public let roleManager: String
    public let roleModerator: String
    // Notifications / parrainage / abonnement / retrait.
    public let markAllRead: String
    public let noNotifications: String
    public let noNotificationsHint: String
    public let yourReferralCode: String
    public let copyCode: String
    public let codeCopied: String
    public let yourReferrals: String
    public let referee: String
    public let claim: String
    public let shareReferralTitle: String
    public let stripeSubscriptionHint: String
    public let offer: String
    public let subscribeByCard: String
    public let history: String
    public let createWithdrawPin: String
    public let createPin: String
    public let noWithdrawMethod: String
    public let method: String
    public let fees: String
    public let net: String
    public let withdrawPin: String
    public let requestWithdraw: String
    public let statusPending: String
    public let statusApproved: String
    public let statusProcessing: String
    public let statusPaid: String
    public let statusRejected: String
    public let statusFailed: String
    public let pendingRewards: String
    public let claimed: String
    public let subscriptionActive: String
    public let withdrawSubmitted: String
    public let credits: String
    public let errPinWrong: String
    public let errPinLocked: String
    public let errBalanceTooLow: String
    public let errOperationFailed: String
    // Contenu / artistes / classement / replays.
    public let blog: String
    public let video: String
    public let article: String
    public let videoUnavailable: String
    public let by: String
    public let noArtists: String
    public let noArtistsHint: String
    public let followed: String
    public let follow: String
    public let donors: String
    public let emptyRanking: String
    public let emptyRankingHint: String
    public let periodic: String
    public let seasonActive: String
    public let seasonEnded: String
    public let mysteryReward: String
    public let noReplays: String
    public let noReplaysHint: String
    public let replay: String
    public let free: String
    public let replayPremium: String
    public let unlocking: String
    public let unlockFor: String
    public let unlockFailed: String
    public let back: String
    public let backToReplays: String
    public let likeAction: String
    public let followers: String
    public let views: String
    // Espace créateur + sponsor.
    public let tabChallenges: String
    public let myConcerts: String
    public let create: String
    public let noChallenges: String
    public let noChallengesHint: String
    public let noConcerts: String
    public let noConcertsHint: String
    public let titleRequired: String
    public let description: String
    public let dateFormatLabel: String
    public let ticketPriceLabel: String
    public let maxTicketsLabel: String
    public let ticketRequired: String
    public let buyTicket: String
    public let allowDedications: String
    public let allowSponsorAds: String
    public let uploadingCover: String
    public let noCover: String
    public let changeCover: String
    public let chooseCover: String
    public let creating: String
    public let createConcert: String
    public let duelChallenge: String
    public let proposed: String
    public let accept: String
    public let decline: String
    public let statusAccepted: String
    public let statusDeclined: String
    public let myRequests: String
    public let newTab: String
    public let tiersByDuration: String
    public let tier: String
    public let noSponsorRequests: String
    public let noSponsorRequestsHint: String
    public let unsupportedMedia: String
    public let noEvents: String
    public let noEventsHint: String
    public let noMedia: String
    public let changeMedia: String
    public let chooseMedia: String
    public let descriptionOptional: String
    public let submitRequest: String
    public let selectEventError: String
    public let event: String
    public let pay: String
    public let sponsorStatusPending: String
    public let sponsorStatusApproved: String
    public let coverReady: String
    public let mediaReady: String
    public let step1ChooseEvent: String
    public let step2Media: String
    // Duels / compétitions / lives.
    public let artist1: String
    public let artist2: String
    public let vote: String
    public let oneVote: String
    public let noCandidates: String
    public let noCandidatesHint: String
    public let timerRunning: String
    public let artistSingular: String
    public let noLives: String
    public let noLivesHint: String
    public let noDuels: String
    public let noDuelsHint: String
    public let noConcertsScheduled: String
    public let noConcertsScheduledHint: String
    public let noCompetitions: String
    public let noCompetitionsHint: String
    public let statusLiveNow: String
    public let statusUpcoming: String
    public let statusEnded: String
    public let statusCancelled: String
    public let freeLabel: String
    public let dedicationsOpen: String
    public let saySomething: String
    public let sendGift: String
    // Espace admin.
    public let adminSpace: String
    public let adminRoleRequests: String
    public let adminRoleRequestsHint: String
    public let adminArtistRequests: String
    public let adminManagerRequests: String
    public let adminAssignRole: String
    public let adminSearchPlaceholder: String
    public let adminSearch: String
    public let adminSearching: String
    public let adminAddArtist: String
    public let adminAddManager: String
    public let adminRemoveArtist: String
    public let adminRemoveManager: String
    public let adminRoleAssigned: String
    public let adminRoleRevoked: String
    public let adminUpdateFailed: String
    public let adminSearchFailed: String
    // Suppression de compte.
    public let deletionScheduledPrefix: String
    public let deletionConfirmHint: String
    // Messages d'erreur / succès (ViewModels).
    public let errInvalidAmount: String
    public let errChooseMethod: String
    public let errPin6: String
    public let errPurchaseFailed: String
    public let errEnterValidAmount: String
    public let errChooseCountry: String
    public let openingPayment: String
    public let errRechargeFailed: String
    public let errVoteFailed: String
    public let errSubscriptionUnavailable: String
    public let errAddMediaFirst: String
    public let errTitleDateRequired: String
    public let errCreateFailed: String
    public let errCodeInvalid: String
    public let errGoogleSignInFailed: String
    public let errAppleSignInFailed: String
    public let errNameRequired: String
    public let errPaymentFailed: String
    public let errWalletLoadFailed: String
    public let errSessionExpired: String
    public let sponsorPaid: String
    public let requestSent: String
    public let concertCreated: String
    public let live: String
    public let chooseDots: String
    public let profileUpdated: String
    public let passwordChanged: String
    public let purchased: String
    public let fan: String
    // Modération : signalement + bannissement.
    public let reportAction: String
    public let reportInappropriate: String
    public let reportHarassment: String
    public let reportSpam: String
    public let reportViolence: String
    public let reportSent: String
    public let banAction: String
    public let banConfirmMessage: String
    // « Mes lives » (hôte).
    public let myLives: String
    public let myLivesHint: String
    public let liveTitle: String
    public let startLive: String
    public let liveActive: String
    public let endLive: String
    public let dedicationsLabel: String
    public let guestsLabel: String
    public let minDedicationPrice: String
    // Dédicaces en direct (fan + hôte).
    public let dedication: String
    public let dedicationHint: String
    public let send: String
    public let noDedicationsYet: String
    public let pendingLabel: String
    public let dedicationsAcceptedDelivered: String
    public let rejectAction: String
    public let markDelivered: String
    public let delivered: String
    public let dedicationsEnabledOn: String
    public let dedicationsEnabledOff: String
    public let dedicationMinPriceLive: String
    // Invités sur scène (fan + hôte).
    public let noGuestRequests: String
    public let pendingRequests: String
    public let activeGuests: String
    public let viewerFallback: String
    public let raiseHand: String
    public let removeGuestAction: String
    public let leaveStageAction: String
    public let guestsEnabledOn: String
    public let guestsEnabledOff: String
    // Modérateurs désignés (hôte + fan).
    public let moderators: String
    public let moderatorsHint: String
    public let noModeratorsYet: String
    public let designateViewer: String
    public let atModeratorLimit: String
    public let noViewersConnected: String
    public let appointAction: String
    public let revokeAction: String
    // Chat on/off (hôte).
    public let chatDisabled: String
    public let chatEnabledOn: String
    public let chatEnabledOff: String
    // Recharge StoreKit (iOS).
    public let chooseCreditsPack: String
    public let buyAction: String
    public let purchasing: String
    public let purchasePending: String
    public let noCreditPacksAvailable: String
    public let creditsAdded: String
}

// MARK: - Tables de traduction

public extension DMStrings {

    /// Chaînes **françaises** (langue par défaut).
    static let fr = DMStrings(
        navHome: "Accueil",
        navLives: "Lives",
        navDuels: "Duels",
        navConcerts: "Concerts",
        navCompetitions: "Compét.",
        homeTitle: "Participez aux duels musicaux en direct",
        homeSubtitle: "Votez pour vos artistes, offrez des cadeaux et vivez la compétition musicale.",
        lifestyle: "Lifestyle",
        ranking: "Classement",
        artists: "Artistes",
        notifications: "Notifications",
        profile: "Profil",
        preferences: "Préférences",
        appearance: "Apparence",
        themeLight: "Clair",
        themeDark: "Sombre",
        themeSystem: "Système",
        language: "Langue",
        french: "Français",
        english: "Anglais",
        account: "Compte",
        deleteAccount: "Supprimer mon compte",
        cancelDeletion: "Annuler la suppression",
        confirm: "Confirmer",
        cancel: "Annuler",
        screenDuels: "Duels",
        screenConcerts: "Concerts",
        screenCompetitions: "Compétitions",
        screenArtists: "Artistes",
        screenDiscover: "Découvrir",
        screenRankings: "Classements",
        loading: "Chargement…",
        retry: "Réessayer",
        save: "Enregistrer",
        emptyGeneric: "Rien à afficher pour le moment.",
        menuMySpace: "Mon espace",
        menuDashboard: "Tableau de bord",
        menuFollowing: "Suivis",
        menuSubscription: "Abonnement",
        menuTransactions: "Mes transactions",
        menuReferral: "Programme de parrainage",
        menuSponsor: "Sponsor",
        menuBecomeArtist: "Devenir artiste",
        menuBecomeManager: "Devenir manager",
        menuCreatorSpace: "Espace créateur",
        menuWithdraw: "Retirer mes crédits",
        menuReplays: "Replays",
        menuGiftShop: "Boutique de cadeaux",
        menuEditProfile: "Modifier le profil",
        menuSignOut: "Déconnexion",
        menuAdminSpace: "Espace admin",
        walletExpenses: "Dépenses",
        walletIncome: "Revenus",
        creditPurchases: "Achats de crédits",
        noPurchases: "Aucun achat de crédits pour le moment.",
        rechargeCredits: "Recharger des crédits",
        amountCredits: "Montant (crédits)",
        mobileMoneyNumber: "Numéro Mobile Money (optionnel)",
        operatorLabel: "Opérateur",
        country: "Pays",
        buy: "Acheter",
        myBalance: "Mon solde",
        recharge: "Recharger",
        rechargeHint: "Paie par Mobile Money. Ton compte est crédité automatiquement après le paiement.",
        payByMobileMoney: "Payer par Mobile Money",
        initializing: "Initialisation…",
        giftShopHint: "Achète des cadeaux virtuels à envoyer aux artistes pendant les duels.",
        emptyShop: "Boutique vide",
        emptyShopHint: "Les cadeaux seront bientôt disponibles.",
        authLoginSubtitle: "Connecte-toi pour rejoindre les lives",
        authRegisterSubtitle: "Crée ton compte pour rejoindre les lives",
        authResetSubtitle: "Saisis le code reçu et ton nouveau mot de passe",
        resetPasswordTitle: "Réinitialiser le mot de passe",
        pleaseWait: "Veuillez patienter…",
        signIn: "Se connecter",
        createAccount: "Créer mon compte",
        sendCode: "Envoyer le code",
        reset: "Réinitialiser",
        email: "Email",
        emailPlaceholder: "votremail@exemple.com",
        password: "Mot de passe",
        passwordStar: "Mot de passe *",
        confirmPasswordStar: "Confirmer le mot de passe *",
        newPassword: "Nouveau mot de passe",
        atLeast8: "Au moins 8 caractères",
        passwordsDontMatch: "Les mots de passe ne correspondent pas",
        codeFromEmail: "Code reçu par email",
        forgotPassword: "Mot de passe oublié ?",
        noAccountSignUp: "Pas de compte ? S'inscrire",
        alreadyAccountSignIn: "Déjà un compte ? Se connecter",
        backToLogin: "Retour à la connexion",
        chooseCountry: "Choisir un pays",
        countryStar: "Pays *",
        continueWithGoogle: "Continuer avec Google",
        googleCancelled: "Connexion Google annulée.",
        forgotHint: "Reçois un code par email pour réinitialiser ton mot de passe.",
        verifyEmailTitle: "Vérifie ton email",
        verifyEmailSentTo: "Un code de vérification a été envoyé à",
        verifying: "Vérification…",
        validate: "Valider",
        resendCode: "Renvoyer le code",
        skipForNow: "Passer pour l'instant",
        completeProfileTitle: "Complète ton profil",
        completeProfileSubtitle: "Ton email est vérifié ✅. Dis-nous en un peu plus pour finaliser ton compte.",
        fullNameStar: "Nom complet *",
        phoneOptional: "Téléphone (optionnel)",
        finish: "Terminer",
        later: "Plus tard",
        resetCodeSent: "Si un compte existe, un code a été envoyé par email.",
        passwordResetDone: "Mot de passe réinitialisé. Connecte-toi.",
        newCodeSent: "Nouveau code envoyé.",
        errWrongCredentials: "Email ou mot de passe incorrect.",
        errEmailTaken: "Cet email est déjà utilisé.",
        errInvalidFields: "Champs invalides (email valide + mot de passe ≥ 8 caractères).",
        errUnstableConnection: "Connexion instable. Réessaie.",
        errGeneric: "Une erreur est survenue. Réessaie.",
        bio: "Bio",
        fullName: "Nom complet",
        phoneNumber: "Numéro de téléphone",
        avatar: "Avatar",
        changePhoto: "Changer la photo",
        uploading: "Upload…",
        saving: "Enregistrement…",
        changePassword: "Changer le mot de passe",
        currentPassword: "Mot de passe actuel",
        changing: "Changement…",
        uploadFailed: "Échec de l'upload.",
        saveFailed: "Enregistrement impossible.",
        changeFailed: "Changement impossible.",
        fileUnreadable: "Fichier illisible.",
        newPasswordTooShort: "Le nouveau mot de passe doit faire au moins 8 caractères.",
        artistProjectLabel: "Présente ton projet musical *",
        justificationDocLabel: "Lien d'un document justificatif (optionnel)",
        socialNetworksOptional: "Réseaux sociaux (optionnel)",
        sending: "Envoi…",
        sendApplication: "Envoyer ma candidature",
        managerExpLabel: "Expérience",
        artistDescMinError: "Décris ton projet (au moins 10 caractères).",
        managerFieldsRequired: "Renseigne ta bio et ton expérience.",
        sendFailed: "Envoi impossible.",
        applicationsClosed: "Les candidatures sont actuellement fermées. L'administrateur désigne directement les promotions.",
        applicationPending: "⏳ Ta demande est en attente de validation.",
        spaceArtist: "Espace Artiste",
        spaceManager: "Espace Manager",
        spaceFan: "Espace Fan",
        statVotesReceived: "Votes reçus",
        statDuelsWon: "Duels gagnés",
        statGiftsReceived: "Cadeaux reçus",
        statDuelsManaged: "Duels gérés",
        statActive: "En cours",
        statVotesCast: "Votes effectués",
        statGiftsSent: "Cadeaux envoyés",
        statTicketsBought: "Billets achetés",
        roleAdmin: "Admin",
        roleArtist: "Artiste",
        roleManager: "Manager",
        roleModerator: "Modérateur",
        markAllRead: "Tout lu",
        noNotifications: "Aucune notification",
        noNotificationsHint: "Tes alertes apparaîtront ici.",
        yourReferralCode: "Ton code de parrainage",
        copyCode: "Copier le code",
        codeCopied: "✅ Code copié.",
        yourReferrals: "Tes filleuls",
        referee: "Filleul",
        claim: "Réclamer",
        shareReferralTitle: "Code de parrainage Dual Music",
        stripeSubscriptionHint: "Paiement par carte (Stripe). Ton abonnement est activé automatiquement après le paiement.",
        offer: "Offre",
        subscribeByCard: "S'abonner par carte",
        history: "Historique",
        createWithdrawPin: "Crée ton code PIN de retrait (6 chiffres)",
        createPin: "Créer le code",
        noWithdrawMethod: "Aucune méthode de retrait. Ajoute-en une depuis le site pour l'instant.",
        method: "Méthode",
        fees: "Frais",
        net: "Net",
        withdrawPin: "Code PIN de retrait",
        requestWithdraw: "Demander le retrait",
        statusPending: "En attente",
        statusApproved: "Approuvé",
        statusProcessing: "Versement en cours",
        statusPaid: "Payé",
        statusRejected: "Rejeté",
        statusFailed: "Échec — crédits rendus",
        pendingRewards: "crédits de récompense en attente",
        claimed: "✅ Réclamé",
        subscriptionActive: "✅ Abonnement actif :",
        withdrawSubmitted: "✅ Demande de retrait envoyée.",
        credits: "crédits",
        errPinWrong: "Code PIN incorrect.",
        errPinLocked: "PIN bloqué après trop de tentatives. Réessaie plus tard.",
        errBalanceTooLow: "Solde insuffisant pour ce retrait.",
        errOperationFailed: "Opération impossible.",
        blog: "Blog",
        video: "Vidéo",
        article: "Article",
        videoUnavailable: "Vidéo indisponible.",
        by: "Par",
        noArtists: "Aucun artiste",
        noArtistsHint: "Les artistes de la plateforme apparaîtront ici.",
        followed: "Suivi",
        follow: "Suivre",
        donors: "Donateurs",
        emptyRanking: "Classement vide",
        emptyRankingHint: "Le classement se remplira avec l'activité de la plateforme.",
        periodic: "Périodique",
        seasonActive: "En cours",
        seasonEnded: "Terminée",
        mysteryReward: "🎁 Récompense mystère",
        noReplays: "Aucune rediffusion disponible",
        noReplaysHint: "Les rediffusions débloquées apparaîtront ici.",
        replay: "Rediffusion",
        free: "Gratuit",
        replayPremium: "Cette rediffusion est premium.",
        unlocking: "Déblocage…",
        unlockFor: "Débloquer pour",
        unlockFailed: "Déblocage impossible (solde insuffisant ?).",
        back: "Retour",
        backToReplays: "← Liste des replays",
        likeAction: "J'aime",
        followers: "abonnés",
        views: "vues",
        tabChallenges: "Défis",
        myConcerts: "Mes concerts",
        create: "Créer",
        noChallenges: "Aucun défi",
        noChallengesHint: "Les défis de duel reçus apparaîtront ici.",
        noConcerts: "Aucun concert",
        noConcertsHint: "Crée ton premier concert depuis l'onglet « Créer ».",
        titleRequired: "Titre *",
        description: "Description",
        dateFormatLabel: "Date * (AAAA-MM-JJTHH:MM)",
        ticketPriceLabel: "Prix du billet (crédits)",
        maxTicketsLabel: "Places max (optionnel)",
        ticketRequired: "Ce concert nécessite un billet pour être regardé.",
        buyTicket: "Acheter le billet",
        allowDedications: "Autoriser les dédicaces",
        allowSponsorAds: "Autoriser les pubs sponsors",
        uploadingCover: "Upload de la pochette…",
        noCover: "Aucune pochette.",
        changeCover: "Changer la pochette",
        chooseCover: "Choisir une pochette",
        creating: "Création…",
        createConcert: "Créer le concert",
        duelChallenge: "Défi de duel",
        proposed: "Proposé",
        accept: "Accepter",
        decline: "Refuser",
        statusAccepted: "Accepté",
        statusDeclined: "Refusé",
        myRequests: "Mes demandes",
        newTab: "Nouvelle",
        tiersByDuration: "Tarifs (par durée)",
        tier: "Palier",
        noSponsorRequests: "Aucune demande de sponsoring",
        noSponsorRequestsHint: "Crée une demande depuis l'onglet « Nouvelle ».",
        unsupportedMedia: "Type de média non supporté.",
        noEvents: "Aucun événement disponible",
        noEventsHint: "Reviens quand des événements seront programmés.",
        noMedia: "Aucun média.",
        changeMedia: "Changer le média",
        chooseMedia: "Choisir un média",
        descriptionOptional: "Description (optionnel)",
        submitRequest: "Envoyer la demande",
        selectEventError: "Sélectionnez un événement.",
        event: "Événement",
        pay: "Payer",
        sponsorStatusPending: "En attente de validation",
        sponsorStatusApproved: "Approuvé — à payer",
        coverReady: "✅ Pochette prête.",
        mediaReady: "✅ Média prêt",
        step1ChooseEvent: "1. Choisir l'événement",
        step2Media: "2. Média de la pub",
        artist1: "Artiste 1",
        artist2: "Artiste 2",
        vote: "Voter",
        oneVote: "Un vote",
        noCandidates: "Aucun candidat approuvé",
        noCandidatesHint: "Le classement s'affichera dès les premières candidatures.",
        timerRunning: "⏱ Minuteur en cours",
        artistSingular: "Artiste",
        noLives: "Aucun live en cours",
        noLivesHint: "Reviens bientôt : les lives des artistes apparaîtront ici dès qu'ils démarrent.",
        noDuels: "Aucun duel pour le moment",
        noDuelsHint: "Les duels à venir s'afficheront ici.",
        noConcertsScheduled: "Aucun concert programmé",
        noConcertsScheduledHint: "Les concerts à venir apparaîtront ici.",
        noCompetitions: "Aucune compétition pour le moment",
        noCompetitionsHint: "Les compétitions ouvertes apparaîtront ici.",
        statusLiveNow: "🔴 EN DIRECT",
        statusUpcoming: "À venir",
        statusEnded: "Terminé",
        statusCancelled: "Annulé",
        freeLabel: "Gratuit",
        dedicationsOpen: "💌 Dédicaces ouvertes",
        saySomething: "Dis quelque chose…",
        sendGift: "Envoyer un cadeau",
        adminSpace: "Espace admin",
        adminRoleRequests: "Candidatures de rôle",
        adminRoleRequestsHint: "Fermé : les fans ne voient plus le formulaire ; tu assignes le rôle manuellement ci-dessous.",
        adminArtistRequests: "Demandes « Devenir artiste »",
        adminManagerRequests: "Demandes « Devenir manager »",
        adminAssignRole: "Assigner un rôle",
        adminSearchPlaceholder: "Rechercher (nom ou email)",
        adminSearch: "Rechercher",
        adminSearching: "Recherche…",
        adminAddArtist: "+ Artiste",
        adminAddManager: "+ Manager",
        adminRemoveArtist: "– Artiste",
        adminRemoveManager: "– Manager",
        adminRoleAssigned: "✅ Rôle assigné.",
        adminRoleRevoked: "Rôle révoqué.",
        adminUpdateFailed: "Modification impossible.",
        adminSearchFailed: "Recherche impossible.",
        deletionScheduledPrefix: "⚠️ Ton compte sera supprimé le",
        deletionConfirmHint: "Un délai de 20 jours te permettra d'annuler avant la suppression définitive. Confirmer ?",
        errInvalidAmount: "Montant invalide",
        errChooseMethod: "Choisis une méthode de retrait",
        errPin6: "Le code PIN doit contenir 6 chiffres",
        errPurchaseFailed: "Achat impossible (solde insuffisant ?).",
        errEnterValidAmount: "Saisis un montant valide.",
        errChooseCountry: "Choisis un pays.",
        openingPayment: "Ouverture du paiement…",
        errRechargeFailed: "Recharge impossible.",
        errVoteFailed: "Vote impossible",
        errSubscriptionUnavailable: "Abonnement indisponible.",
        errAddMediaFirst: "Ajoutez d'abord un média.",
        errTitleDateRequired: "Titre et date sont requis.",
        errCreateFailed: "Création impossible.",
        errCodeInvalid: "Code incorrect ou expiré.",
        errGoogleSignInFailed: "Connexion Google impossible.",
        errAppleSignInFailed: "Connexion Apple impossible.",
        errNameRequired: "Le nom est requis.",
        errPaymentFailed: "Paiement impossible.",
        errWalletLoadFailed: "Impossible de charger le portefeuille.",
        errSessionExpired: "Session expirée — reconnecte-toi.",
        sponsorPaid: "✅ Sponsoring payé.",
        requestSent: "✅ Demande envoyée — en attente de validation.",
        concertCreated: "✅ Concert créé — en attente de validation.",
        live: "Live",
        chooseDots: "Choisir…",
        profileUpdated: "✅ Profil mis à jour.",
        passwordChanged: "✅ Mot de passe changé.",
        purchased: "acheté !",
        fan: "Fan",
        reportAction: "Signaler",
        reportInappropriate: "Contenu inapproprié",
        reportHarassment: "Harcèlement",
        reportSpam: "Spam",
        reportViolence: "Violence",
        reportSent: "Signalement envoyé",
        banAction: "Bannir",
        banConfirmMessage: "Cette personne ne pourra plus écrire ni rejoindre, et ses messages seront masqués pour tout le monde.",
        myLives: "Mes Lives",
        myLivesHint: "Gérez vos lives passés et lancez-en un nouveau",
        liveTitle: "Titre du live",
        startLive: "Lancer un Live",
        liveActive: "Live en cours",
        endLive: "Terminer",
        dedicationsLabel: "Dédicaces",
        guestsLabel: "Invités",
        minDedicationPrice: "Prix minimum dédicace (vide = défaut)",
        dedication: "Dédicace",
        dedicationHint: "Envoyez un message dédié à l'artiste",
        send: "Envoyer",
        noDedicationsYet: "Aucune dédicace pour l'instant.",
        pendingLabel: "En attente",
        dedicationsAcceptedDelivered: "Acceptées / livrées",
        rejectAction: "Rejeter",
        markDelivered: "Marquer comme livrée",
        delivered: "Livrée",
        dedicationsEnabledOn: "Dédicaces activées",
        dedicationsEnabledOff: "Dédicaces coupées",
        dedicationMinPriceLive: "Prix minimum dédicace",
        noGuestRequests: "Aucune demande pour le moment",
        pendingRequests: "Demandes en attente",
        activeGuests: "Invités actifs",
        viewerFallback: "Spectateur",
        raiseHand: "Lever la main",
        removeGuestAction: "Retirer l'invité",
        leaveStageAction: "Descendre de scène",
        guestsEnabledOn: "Invités activés",
        guestsEnabledOff: "Invités coupés",
        moderators: "Modérateurs",
        moderatorsHint: "Un modérateur peut bannir un spectateur ou masquer un message, comme vous.",
        noModeratorsYet: "Aucun modérateur désigné.",
        designateViewer: "Désigner un spectateur",
        atModeratorLimit: "Nombre maximum de modérateurs atteint (2).",
        noViewersConnected: "Aucun spectateur connecté pour le moment.",
        appointAction: "Nommer",
        revokeAction: "Révoquer",
        chatDisabled: "Chat désactivé",
        chatEnabledOn: "Chat activé",
        chatEnabledOff: "Chat coupé",
        chooseCreditsPack: "Choisis un pack de crédits.",
        buyAction: "Acheter",
        purchasing: "Achat…",
        purchasePending: "Achat en attente d'approbation.",
        noCreditPacksAvailable: "Aucun pack disponible pour le moment.",
        creditsAdded: "crédits ajoutés !"
    )

    /// Chaînes **anglaises**.
    static let en = DMStrings(
        navHome: "Home",
        navLives: "Lives",
        navDuels: "Duels",
        navConcerts: "Concerts",
        navCompetitions: "Contests",
        homeTitle: "Join live music duels",
        homeSubtitle: "Vote for your artists, send gifts and live the musical competition.",
        lifestyle: "Lifestyle",
        ranking: "Ranking",
        artists: "Artists",
        notifications: "Notifications",
        profile: "Profile",
        preferences: "Preferences",
        appearance: "Appearance",
        themeLight: "Light",
        themeDark: "Dark",
        themeSystem: "System",
        language: "Language",
        french: "French",
        english: "English",
        account: "Account",
        deleteAccount: "Delete my account",
        cancelDeletion: "Cancel deletion",
        confirm: "Confirm",
        cancel: "Cancel",
        screenDuels: "Duels",
        screenConcerts: "Concerts",
        screenCompetitions: "Competitions",
        screenArtists: "Artists",
        screenDiscover: "Discover",
        screenRankings: "Rankings",
        loading: "Loading…",
        retry: "Retry",
        save: "Save",
        emptyGeneric: "Nothing to show yet.",
        menuMySpace: "My space",
        menuDashboard: "Dashboard",
        menuFollowing: "Following",
        menuSubscription: "Subscription",
        menuTransactions: "My transactions",
        menuReferral: "Referral program",
        menuSponsor: "Sponsor",
        menuBecomeArtist: "Become an artist",
        menuBecomeManager: "Become a manager",
        menuCreatorSpace: "Creator space",
        menuWithdraw: "Withdraw credits",
        menuReplays: "Replays",
        menuGiftShop: "Gift shop",
        menuEditProfile: "Edit profile",
        menuSignOut: "Sign out",
        menuAdminSpace: "Admin space",
        walletExpenses: "Expenses",
        walletIncome: "Income",
        creditPurchases: "Credit purchases",
        noPurchases: "No credit purchases yet.",
        rechargeCredits: "Top up credits",
        amountCredits: "Amount (credits)",
        mobileMoneyNumber: "Mobile Money number (optional)",
        operatorLabel: "Operator",
        country: "Country",
        buy: "Buy",
        myBalance: "My balance",
        recharge: "Top up",
        rechargeHint: "Pay with Mobile Money. Your account is credited automatically after payment.",
        payByMobileMoney: "Pay with Mobile Money",
        initializing: "Initializing…",
        giftShopHint: "Buy virtual gifts to send to artists during duels.",
        emptyShop: "Empty shop",
        emptyShopHint: "Gifts will be available soon.",
        authLoginSubtitle: "Sign in to join the lives",
        authRegisterSubtitle: "Create your account to join the lives",
        authResetSubtitle: "Enter the code you received and your new password",
        resetPasswordTitle: "Reset password",
        pleaseWait: "Please wait…",
        signIn: "Sign in",
        createAccount: "Create my account",
        sendCode: "Send code",
        reset: "Reset",
        email: "Email",
        emailPlaceholder: "youremail@example.com",
        password: "Password",
        passwordStar: "Password *",
        confirmPasswordStar: "Confirm password *",
        newPassword: "New password",
        atLeast8: "At least 8 characters",
        passwordsDontMatch: "Passwords do not match",
        codeFromEmail: "Code received by email",
        forgotPassword: "Forgot password?",
        noAccountSignUp: "No account? Sign up",
        alreadyAccountSignIn: "Already have an account? Sign in",
        backToLogin: "Back to login",
        chooseCountry: "Choose a country",
        countryStar: "Country *",
        continueWithGoogle: "Continue with Google",
        googleCancelled: "Google sign-in cancelled.",
        forgotHint: "Get a code by email to reset your password.",
        verifyEmailTitle: "Verify your email",
        verifyEmailSentTo: "A verification code was sent to",
        verifying: "Verifying…",
        validate: "Validate",
        resendCode: "Resend code",
        skipForNow: "Skip for now",
        completeProfileTitle: "Complete your profile",
        completeProfileSubtitle: "Your email is verified ✅. Tell us a bit more to finish your account.",
        fullNameStar: "Full name *",
        phoneOptional: "Phone (optional)",
        finish: "Finish",
        later: "Later",
        resetCodeSent: "If an account exists, a code has been sent by email.",
        passwordResetDone: "Password reset. Please sign in.",
        newCodeSent: "New code sent.",
        errWrongCredentials: "Wrong email or password.",
        errEmailTaken: "This email is already in use.",
        errInvalidFields: "Invalid fields (valid email + password ≥ 8 characters).",
        errUnstableConnection: "Unstable connection. Try again.",
        errGeneric: "Something went wrong. Try again.",
        bio: "Bio",
        fullName: "Full name",
        phoneNumber: "Phone number",
        avatar: "Avatar",
        changePhoto: "Change photo",
        uploading: "Uploading…",
        saving: "Saving…",
        changePassword: "Change password",
        currentPassword: "Current password",
        changing: "Changing…",
        uploadFailed: "Upload failed.",
        saveFailed: "Could not save.",
        changeFailed: "Change failed.",
        fileUnreadable: "Unreadable file.",
        newPasswordTooShort: "The new password must be at least 8 characters.",
        artistProjectLabel: "Present your musical project *",
        justificationDocLabel: "Link to a supporting document (optional)",
        socialNetworksOptional: "Social networks (optional)",
        sending: "Sending…",
        sendApplication: "Submit my application",
        managerExpLabel: "Experience",
        artistDescMinError: "Describe your project (at least 10 characters).",
        managerFieldsRequired: "Fill in your bio and experience.",
        sendFailed: "Could not send.",
        applicationsClosed: "Applications are currently closed. The administrator designates promotions directly.",
        applicationPending: "⏳ Your application is pending review.",
        spaceArtist: "Artist space",
        spaceManager: "Manager space",
        spaceFan: "Fan space",
        statVotesReceived: "Votes received",
        statDuelsWon: "Duels won",
        statGiftsReceived: "Gifts received",
        statDuelsManaged: "Duels managed",
        statActive: "Active",
        statVotesCast: "Votes cast",
        statGiftsSent: "Gifts sent",
        statTicketsBought: "Tickets bought",
        roleAdmin: "Admin",
        roleArtist: "Artist",
        roleManager: "Manager",
        roleModerator: "Moderator",
        markAllRead: "Mark all read",
        noNotifications: "No notifications",
        noNotificationsHint: "Your alerts will appear here.",
        yourReferralCode: "Your referral code",
        copyCode: "Copy code",
        codeCopied: "✅ Code copied.",
        yourReferrals: "Your referrals",
        referee: "Referee",
        claim: "Claim",
        shareReferralTitle: "Dual Music referral code",
        stripeSubscriptionHint: "Card payment (Stripe). Your subscription activates automatically after payment.",
        offer: "Plan",
        subscribeByCard: "Subscribe by card",
        history: "History",
        createWithdrawPin: "Create your withdrawal PIN (6 digits)",
        createPin: "Create PIN",
        noWithdrawMethod: "No withdrawal method. Add one from the website for now.",
        method: "Method",
        fees: "Fees",
        net: "Net",
        withdrawPin: "Withdrawal PIN",
        requestWithdraw: "Request withdrawal",
        statusPending: "Pending",
        statusApproved: "Approved",
        statusProcessing: "Payout in progress",
        statusPaid: "Paid",
        statusRejected: "Rejected",
        statusFailed: "Failed — credits returned",
        pendingRewards: "reward credits pending",
        claimed: "✅ Claimed",
        subscriptionActive: "✅ Active subscription:",
        withdrawSubmitted: "✅ Withdrawal request sent.",
        credits: "credits",
        errPinWrong: "Wrong PIN.",
        errPinLocked: "PIN locked after too many attempts. Try again later.",
        errBalanceTooLow: "Balance too low for this withdrawal.",
        errOperationFailed: "Operation failed.",
        blog: "Blog",
        video: "Video",
        article: "Article",
        videoUnavailable: "Video unavailable.",
        by: "By",
        noArtists: "No artists",
        noArtistsHint: "Platform artists will appear here.",
        followed: "Following",
        follow: "Follow",
        donors: "Donors",
        emptyRanking: "Empty ranking",
        emptyRankingHint: "The ranking will fill with platform activity.",
        periodic: "Periodic",
        seasonActive: "Active",
        seasonEnded: "Ended",
        mysteryReward: "🎁 Mystery reward",
        noReplays: "No replays available",
        noReplaysHint: "Unlocked replays will appear here.",
        replay: "Replay",
        free: "Free",
        replayPremium: "This replay is premium.",
        unlocking: "Unlocking…",
        unlockFor: "Unlock for",
        unlockFailed: "Unlock failed (insufficient balance?).",
        back: "Back",
        backToReplays: "← Replay list",
        likeAction: "Like",
        followers: "followers",
        views: "views",
        tabChallenges: "Challenges",
        myConcerts: "My concerts",
        create: "Create",
        noChallenges: "No challenge",
        noChallengesHint: "Received duel challenges will appear here.",
        noConcerts: "No concert",
        noConcertsHint: "Create your first concert from the “Create” tab.",
        titleRequired: "Title *",
        description: "Description",
        dateFormatLabel: "Date * (YYYY-MM-DDTHH:MM)",
        ticketPriceLabel: "Ticket price (credits)",
        maxTicketsLabel: "Max seats (optional)",
        ticketRequired: "This concert requires a ticket to watch.",
        buyTicket: "Buy ticket",
        allowDedications: "Allow dedications",
        allowSponsorAds: "Allow sponsor ads",
        uploadingCover: "Uploading cover…",
        noCover: "No cover.",
        changeCover: "Change cover",
        chooseCover: "Choose a cover",
        creating: "Creating…",
        createConcert: "Create concert",
        duelChallenge: "Duel challenge",
        proposed: "Proposed",
        accept: "Accept",
        decline: "Decline",
        statusAccepted: "Accepted",
        statusDeclined: "Declined",
        myRequests: "My requests",
        newTab: "New",
        tiersByDuration: "Rates (by duration)",
        tier: "Tier",
        noSponsorRequests: "No sponsor request",
        noSponsorRequestsHint: "Create a request from the “New” tab.",
        unsupportedMedia: "Unsupported media type.",
        noEvents: "No event available",
        noEventsHint: "Come back when events are scheduled.",
        noMedia: "No media.",
        changeMedia: "Change media",
        chooseMedia: "Choose media",
        descriptionOptional: "Description (optional)",
        submitRequest: "Submit request",
        selectEventError: "Select an event.",
        event: "Event",
        pay: "Pay",
        sponsorStatusPending: "Pending review",
        sponsorStatusApproved: "Approved — to pay",
        coverReady: "✅ Cover ready.",
        mediaReady: "✅ Media ready",
        step1ChooseEvent: "1. Choose the event",
        step2Media: "2. Ad media",
        artist1: "Artist 1",
        artist2: "Artist 2",
        vote: "Vote",
        oneVote: "One vote",
        noCandidates: "No approved candidate",
        noCandidatesHint: "The ranking will appear once candidacies start.",
        timerRunning: "⏱ Timer running",
        artistSingular: "Artist",
        noLives: "No live right now",
        noLivesHint: "Come back soon: artist lives will appear here as soon as they start.",
        noDuels: "No duel yet",
        noDuelsHint: "Upcoming duels will show up here.",
        noConcertsScheduled: "No concert scheduled",
        noConcertsScheduledHint: "Upcoming concerts will appear here.",
        noCompetitions: "No competition yet",
        noCompetitionsHint: "Open competitions will appear here.",
        statusLiveNow: "🔴 LIVE",
        statusUpcoming: "Upcoming",
        statusEnded: "Ended",
        statusCancelled: "Cancelled",
        freeLabel: "Free",
        dedicationsOpen: "💌 Dedications open",
        saySomething: "Say something…",
        sendGift: "Send a gift",
        adminSpace: "Admin space",
        adminRoleRequests: "Role applications",
        adminRoleRequestsHint: "Closed: fans no longer see the form; you assign the role manually below.",
        adminArtistRequests: "“Become an artist” requests",
        adminManagerRequests: "“Become a manager” requests",
        adminAssignRole: "Assign a role",
        adminSearchPlaceholder: "Search (name or email)",
        adminSearch: "Search",
        adminSearching: "Searching…",
        adminAddArtist: "+ Artist",
        adminAddManager: "+ Manager",
        adminRemoveArtist: "– Artist",
        adminRemoveManager: "– Manager",
        adminRoleAssigned: "✅ Role assigned.",
        adminRoleRevoked: "Role revoked.",
        adminUpdateFailed: "Update failed.",
        adminSearchFailed: "Search failed.",
        deletionScheduledPrefix: "⚠️ Your account will be deleted on",
        deletionConfirmHint: "A 20-day grace period lets you cancel before permanent deletion. Confirm?",
        errInvalidAmount: "Invalid amount",
        errChooseMethod: "Choose a withdrawal method",
        errPin6: "The PIN must be 6 digits",
        errPurchaseFailed: "Purchase failed (insufficient balance?).",
        errEnterValidAmount: "Enter a valid amount.",
        errChooseCountry: "Choose a country.",
        openingPayment: "Opening payment…",
        errRechargeFailed: "Top-up failed.",
        errVoteFailed: "Vote failed",
        errSubscriptionUnavailable: "Subscription unavailable.",
        errAddMediaFirst: "Add media first.",
        errTitleDateRequired: "Title and date are required.",
        errCreateFailed: "Creation failed.",
        errCodeInvalid: "Wrong or expired code.",
        errGoogleSignInFailed: "Google sign-in failed.",
        errAppleSignInFailed: "Apple sign-in failed.",
        errNameRequired: "Name is required.",
        errPaymentFailed: "Payment failed.",
        errWalletLoadFailed: "Could not load the wallet.",
        errSessionExpired: "Session expired — please sign in again.",
        sponsorPaid: "✅ Sponsorship paid.",
        requestSent: "✅ Request sent — pending review.",
        concertCreated: "✅ Concert created — pending review.",
        live: "Live",
        chooseDots: "Choose…",
        profileUpdated: "✅ Profile updated.",
        passwordChanged: "✅ Password changed.",
        purchased: "purchased!",
        fan: "Fan",
        reportAction: "Report",
        reportInappropriate: "Inappropriate content",
        reportHarassment: "Harassment",
        reportSpam: "Spam",
        reportViolence: "Violence",
        reportSent: "Report sent",
        banAction: "Ban",
        banConfirmMessage: "This person won't be able to write or rejoin anymore, and their messages will be hidden for everyone.",
        myLives: "My Lives",
        myLivesHint: "Manage your past lives and start a new one",
        liveTitle: "Live title",
        startLive: "Start a Live",
        liveActive: "Live in progress",
        endLive: "End",
        dedicationsLabel: "Dedications",
        guestsLabel: "Guests",
        minDedicationPrice: "Minimum dedication price (blank = default)",
        dedication: "Dedication",
        dedicationHint: "Send a dedicated message to the artist",
        send: "Send",
        noDedicationsYet: "No dedications yet.",
        pendingLabel: "Pending",
        dedicationsAcceptedDelivered: "Accepted / delivered",
        rejectAction: "Reject",
        markDelivered: "Mark as delivered",
        delivered: "Delivered",
        dedicationsEnabledOn: "Dedications on",
        dedicationsEnabledOff: "Dedications off",
        dedicationMinPriceLive: "Minimum dedication price",
        noGuestRequests: "No request yet",
        pendingRequests: "Pending requests",
        activeGuests: "Active guests",
        viewerFallback: "Viewer",
        raiseHand: "Raise hand",
        removeGuestAction: "Remove guest",
        leaveStageAction: "Leave stage",
        guestsEnabledOn: "Guests on",
        guestsEnabledOff: "Guests off",
        moderators: "Moderators",
        moderatorsHint: "A moderator can ban a viewer or hide a message, just like you.",
        noModeratorsYet: "No moderator designated yet.",
        designateViewer: "Designate a viewer",
        atModeratorLimit: "Maximum number of moderators reached (2).",
        noViewersConnected: "No viewers connected right now.",
        appointAction: "Appoint",
        revokeAction: "Revoke",
        chatDisabled: "Chat disabled",
        chatEnabledOn: "Chat on",
        chatEnabledOff: "Chat off",
        chooseCreditsPack: "Choose a credits pack.",
        buyAction: "Buy",
        purchasing: "Purchasing…",
        purchasePending: "Purchase pending approval.",
        noCreditPacksAvailable: "No packs available right now.",
        creditsAdded: "credits added!"
    )

    /// Renvoie la table de chaînes d'une langue.
    /// - Parameter language: langue demandée.
    static func of(_ language: AppLanguage) -> DMStrings {
        switch language {
        case .fr: return .fr
        case .en: return .en
        }
    }
}

// MARK: - Accès depuis les vues (environnement)

private struct DMStringsKey: EnvironmentKey {
    static let defaultValue: DMStrings = .fr
}

public extension EnvironmentValues {
    /// Chaînes localisées courantes. Usage : `@Environment(\.dmStrings) private var s`.
    var dmStrings: DMStrings {
        get { self[DMStringsKey.self] }
        set { self[DMStringsKey.self] = newValue }
    }
}

public extension View {
    /// Fournit la table de chaînes correspondant à la langue au sous-arbre de vues.
    /// - Parameter language: langue de l'interface.
    func dualMusicStrings(_ language: AppLanguage) -> some View {
        environment(\.dmStrings, DMStrings.of(language))
    }
}

// MARK: - Accès hors vue (ViewModels)

/// Chaînes courantes accessibles **hors SwiftUI** (ViewModels, pour les messages d'erreur).
///
/// Équivalent du `appStrings` global d'Android. Mises à jour par ``setLanguage(_:)`` au
/// démarrage et à chaque changement de langue ; toujours cohérentes avec `\.dmStrings`.
///
/// Isolé sur le `MainActor` : les ViewModels de l'app y sont déjà, donc l'accès est
/// synchrone et sans verrou.
@MainActor
public enum AppStrings {
    /// Table courante (français par défaut).
    public private(set) static var current: DMStrings = .fr
    /// Langue courante.
    public private(set) static var language: AppLanguage = .fr

    /// Met à jour la langue globale. Appelé par le contrôleur de langue de l'app.
    /// - Parameter language: nouvelle langue.
    public static func setLanguage(_ language: AppLanguage) {
        self.language = language
        self.current = DMStrings.of(language)
    }
}

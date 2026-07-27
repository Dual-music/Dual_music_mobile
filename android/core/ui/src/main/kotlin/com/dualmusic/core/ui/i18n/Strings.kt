package com.dualmusic.core.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** Langue de l'interface. */
enum class Language { FR, EN }

/**
 * Table de chaînes localisées de l'interface.
 *
 * Chaque écran lit ses libellés via `LocalStrings.current`. Pour ajouter une langue, fournir
 * une nouvelle instance ci-dessous ; pour traduire un écran, remplacer ses littéraux par le
 * champ correspondant. La traduction est incrémentale : les écrans non encore migrés restent
 * en français (valeur par défaut).
 */
interface Strings {
    // Navigation basse.
    val navHome: String
    val navLives: String
    val navDuels: String
    val navConcerts: String
    val navCompetitions: String
    // Accueil.
    val homeTitle: String
    val homeSubtitle: String
    val lifestyle: String
    val ranking: String
    val artists: String
    // Barre supérieure.
    val notifications: String
    val profile: String
    // Préférences.
    val preferences: String
    val appearance: String
    val themeLight: String
    val themeDark: String
    val themeSystem: String
    val language: String
    val french: String
    val english: String
    val account: String
    val deleteAccount: String
    val cancelDeletion: String
    val confirm: String
    val cancel: String
    // Titres d'écrans catalogue.
    val screenDuels: String
    val screenConcerts: String
    val screenCompetitions: String
    val screenArtists: String
    val screenDiscover: String
    val screenRankings: String
    // Communs.
    val loading: String
    val retry: String
    val save: String
    val emptyGeneric: String
    // Menu profil (pop-up).
    val menuMySpace: String
    val menuDashboard: String
    val menuFollowing: String
    val menuSubscription: String
    val menuTransactions: String
    val menuReferral: String
    val menuSponsor: String
    val menuBecomeArtist: String
    val menuBecomeManager: String
    val menuCreatorSpace: String
    val menuWithdraw: String
    val menuReplays: String
    val menuGiftShop: String
    val menuEditProfile: String
    val menuSignOut: String
    // Portefeuille / recharge / boutique.
    val walletExpenses: String
    val walletIncome: String
    val creditPurchases: String
    val noPurchases: String
    val rechargeCredits: String
    val amountCredits: String
    val mobileMoneyNumber: String
    val operator: String
    val country: String
    val buy: String
    val myBalance: String
    val recharge: String
    // Authentification.
    val authLoginSubtitle: String
    val authRegisterSubtitle: String
    val authResetSubtitle: String
    val resetPasswordTitle: String
    val pleaseWait: String
    val signIn: String
    val createAccount: String
    val sendCode: String
    val reset: String
    val email: String
    val emailPlaceholder: String
    val password: String
    val passwordStar: String
    val confirmPasswordStar: String
    val newPassword: String
    val atLeast8: String
    val passwordsDontMatch: String
    val codeFromEmail: String
    val forgotPassword: String
    val noAccountSignUp: String
    val alreadyAccountSignIn: String
    val backToLogin: String
    val chooseCountry: String
    val countryStar: String
    val continueWithGoogle: String
    val googleCancelled: String
    val forgotHint: String
    // Profil : édition + candidatures de rôle.
    val bio: String
    val fullName: String
    val phoneNumber: String
    val avatar: String
    val changePhoto: String
    val uploading: String
    val saving: String
    val changePassword: String
    val currentPassword: String
    val changing: String
    val uploadFailed: String
    val saveFailed: String
    val changeFailed: String
    val fileUnreadable: String
    val newPasswordTooShort: String
    val artistProjectLabel: String
    val justificationDocLabel: String
    val socialNetworksOptional: String
    val sending: String
    val sendApplication: String
    val managerExpLabel: String
    val artistDescMinError: String
    val managerFieldsRequired: String
    val sendFailed: String
    val applicationsClosed: String
    val applicationPending: String
    // Profil éditable + dashboard + profil public créateur.
    val profileTitle: String
    val edit: String
    val changePasswordSectionHint: String
    val quickActions: String
    val statArtistSpace: String
    val statManagerSpace: String
    val statFanSpace: String
    val statVotesReceived: String
    val statDuelsWon: String
    val statGiftsReceived: String
    val statDuelsManaged: String
    val statActiveDuels: String
    val statVotesCast: String
    val statGiftsSent: String
    val statTickets: String
    val statWinRate: String
    val publicProfile: String
    val artisticProfile: String
    val managerProfileTitle: String
    val stageName: String
    val coverImage: String
    val makeProfilePublic: String
    val socialLinks: String
    val socialLinksHint: String
    // Suppression de compte (zone sensible) + acceptation des documents légaux.
    val deleteAccountGrace: String
    val accountDeletionScheduledOn: String
    val deletionCanStillCancel: String
    val iAcceptThe: String
    val termsOfUse: String
    val privacyPolicy: String
    /** Étiquette de langue technique ("fr"/"en") pour sélectionner le contenu légal. */
    val langTag: String
    // Notifications / parrainage / abonnement / retrait.
    val markAllRead: String
    val noNotifications: String
    val noNotificationsHint: String
    val yourReferralCode: String
    val copyCode: String
    val yourReferrals: String
    val referee: String
    val claim: String
    val shareReferralTitle: String
    val stripeSubscriptionHint: String
    val offer: String
    val subscribeByCard: String
    val history: String
    val createWithdrawPin: String
    val createPin: String
    val noWithdrawMethod: String
    val method: String
    val fees: String
    val net: String
    val withdrawPin: String
    val requestWithdraw: String
    val statusPending: String
    val statusApproved: String
    val statusPaid: String
    val statusRejected: String
    val pendingRewards: String
    val claimed: String
    val subscriptionActive: String
    val withdrawSubmitted: String
    val credits: String
    // Contenu / artistes / classement / replays.
    val blog: String
    val video: String
    val article: String
    val videoUnavailable: String
    val by: String
    val noArtists: String
    val noArtistsHint: String
    val followed: String
    val follow: String
    val donors: String
    val emptyRanking: String
    val emptyRankingHint: String
    val periodic: String
    val seasonActive: String
    val seasonEnded: String
    val mysteryReward: String
    val noReplays: String
    val noReplaysHint: String
    val replay: String
    val free: String
    val replayPremium: String
    val unlocking: String
    val unlockFor: String
    val back: String
    val likeAction: String
    val followers: String
    val views: String
    // Espace créateur + sponsor.
    val tabChallenges: String
    val myConcerts: String
    val create: String
    val noChallenges: String
    val noChallengesHint: String
    val noConcerts: String
    val noConcertsHint: String
    val titleRequired: String
    val description: String
    val dateFormatLabel: String
    val ticketPriceLabel: String
    val maxTicketsLabel: String
    val allowDedications: String
    val allowSponsorAds: String
    val uploadingCover: String
    val noCover: String
    val changeCover: String
    val chooseCover: String
    val creating: String
    val createConcert: String
    val duelChallenge: String
    val proposed: String
    val accept: String
    val decline: String
    val statusAccepted: String
    val statusDeclined: String
    val myRequests: String
    val newTab: String
    val tiersByDuration: String
    val tier: String
    val noSponsorRequests: String
    val noSponsorRequestsHint: String
    val unsupportedMedia: String
    val noEvents: String
    val noEventsHint: String
    val noMedia: String
    val changeMedia: String
    val chooseMedia: String
    val descriptionOptional: String
    val submitRequest: String
    val selectEventError: String
    val event: String
    val pay: String
    val sponsorStatusPending: String
    val sponsorStatusApproved: String
    val coverReady: String
    val mediaReady: String
    val step1ChooseEvent: String
    val step2Media: String
    val artist1: String
    val artist2: String
    val vote: String
    val oneVote: String
    val noCandidates: String
    val noCandidatesHint: String
    val timerRunning: String
    val artistSingular: String
    // Messages d'erreur / succès (ViewModels).
    val errInvalidAmount: String
    val errChooseMethod: String
    val errPin6: String
    val errPurchaseFailed: String
    val errEnterValidAmount: String
    val errChooseCountry: String
    val openingPayment: String
    val errRechargeFailed: String
    val errVoteFailed: String
    val errSubscriptionUnavailable: String
    val errAddMediaFirst: String
    val errTitleDateRequired: String
    val errCreateFailed: String
    val errCodeInvalid: String
    val errGoogleSignInFailed: String
    val errNameRequired: String
    val errPaymentFailed: String
    val sponsorPaid: String
    val requestSent: String
    val concertCreated: String
    val live: String
    val chooseDots: String
    val profileUpdated: String
    val passwordChanged: String
    val fan: String
    // Menu profil (entrées artiste/manager).
    val menuArtistProfile: String
    val menuMyCompetitions: String
    val menuContent: String
    val menuEarnings: String
    val comingSoon: String
    val comingSoonHint: String
    // Mes compétitions (candidatures artiste).
    val candStatusPending: String
    val candStatusApproved: String
    val candStatusRejected: String
    val candScore: String
    val noCandidacies: String
    val noCandidaciesHint: String
    // Duels (demander un duel / invitations reçues).
    val requestDuel: String
    val requestDuelHint: String
    val searchArtist: String
    val noArtistAvailable: String
    val challenge: String
    val receivedInvitations: String
    val duelRequestSent: String
    // Suivis (artistes suivis uniquement).
    val followedTitle: String
    val notFollowingAny: String
    val followedEmptyHint: String
    // Transactions (onglet Retraits).
    val walletWithdrawals: String
    val noWithdrawals: String
    // Préférences : devise d'affichage.
    val displayCurrency: String
    val displayCurrencyHint: String
    // Mes Lives (gestion + lancement).
    val myLives: String
    val myLivesHint: String
    val liveTitle: String
    val startLive: String
    val liveActive: String
    val endLive: String
    val noLives: String
    val noLivesHint: String
}

/** Chaînes françaises (langue par défaut). */
object FrStrings : Strings {
    override val navHome = "Accueil"
    override val navLives = "Lives"
    override val navDuels = "Duels"
    override val navConcerts = "Concerts"
    override val navCompetitions = "Compét."
    override val homeTitle = "Participez aux duels musicaux en direct"
    override val homeSubtitle = "Votez pour vos artistes, offrez des cadeaux et vivez la compétition musicale."
    override val lifestyle = "Lifestyle"
    override val ranking = "Classement"
    override val artists = "Artistes"
    override val notifications = "Notifications"
    override val profile = "Profil"
    override val preferences = "Préférences"
    override val appearance = "Apparence"
    override val themeLight = "Clair"
    override val themeDark = "Sombre"
    override val themeSystem = "Système"
    override val language = "Langue"
    override val french = "Français"
    override val english = "Anglais"
    override val account = "Compte"
    override val deleteAccount = "Supprimer mon compte"
    override val cancelDeletion = "Annuler la suppression"
    override val confirm = "Confirmer"
    override val cancel = "Annuler"
    override val screenDuels = "Duels"
    override val screenConcerts = "Concerts"
    override val screenCompetitions = "Compétitions"
    override val screenArtists = "Artistes"
    override val screenDiscover = "Découvrir"
    override val screenRankings = "Classements"
    override val loading = "Chargement…"
    override val retry = "Réessayer"
    override val save = "Enregistrer"
    override val emptyGeneric = "Rien à afficher pour le moment."
    override val menuMySpace = "Mon espace"
    override val menuDashboard = "Tableau de bord"
    override val menuFollowing = "Suivis"
    override val menuSubscription = "Abonnement"
    override val menuTransactions = "Mes transactions"
    override val menuReferral = "Programme de parrainage"
    override val menuSponsor = "Sponsor"
    override val menuBecomeArtist = "Devenir artiste"
    override val menuBecomeManager = "Devenir manager"
    override val menuCreatorSpace = "Espace créateur"
    override val menuWithdraw = "Retirer mes crédits"
    override val menuReplays = "Replays"
    override val menuGiftShop = "Boutique de cadeaux"
    override val menuEditProfile = "Modifier le profil"
    override val menuSignOut = "Déconnexion"
    override val walletExpenses = "Dépenses"
    override val walletIncome = "Revenus"
    override val creditPurchases = "Achats de crédits"
    override val noPurchases = "Aucun achat de crédits pour le moment."
    override val rechargeCredits = "Recharger des crédits"
    override val amountCredits = "Montant (crédits)"
    override val mobileMoneyNumber = "Numéro Mobile Money (optionnel)"
    override val operator = "Opérateur"
    override val country = "Pays"
    override val buy = "Acheter"
    override val myBalance = "Mon solde"
    override val recharge = "Recharger"
    override val authLoginSubtitle = "Connecte-toi pour rejoindre les lives"
    override val authRegisterSubtitle = "Crée ton compte pour rejoindre les lives"
    override val authResetSubtitle = "Saisis le code reçu et ton nouveau mot de passe"
    override val resetPasswordTitle = "Réinitialiser le mot de passe"
    override val pleaseWait = "Veuillez patienter…"
    override val signIn = "Se connecter"
    override val createAccount = "Créer mon compte"
    override val sendCode = "Envoyer le code"
    override val reset = "Réinitialiser"
    override val email = "Email"
    override val emailPlaceholder = "votremail@exemple.com"
    override val password = "Mot de passe"
    override val passwordStar = "Mot de passe *"
    override val confirmPasswordStar = "Confirmer le mot de passe *"
    override val newPassword = "Nouveau mot de passe"
    override val atLeast8 = "Au moins 8 caractères"
    override val passwordsDontMatch = "Les mots de passe ne correspondent pas"
    override val codeFromEmail = "Code reçu par email"
    override val forgotPassword = "Mot de passe oublié ?"
    override val noAccountSignUp = "Pas de compte ? S'inscrire"
    override val alreadyAccountSignIn = "Déjà un compte ? Se connecter"
    override val backToLogin = "Retour à la connexion"
    override val chooseCountry = "Choisir un pays"
    override val countryStar = "Pays *"
    override val continueWithGoogle = "Continuer avec Google"
    override val googleCancelled = "Connexion Google annulée."
    override val forgotHint = "Reçois un code par email pour réinitialiser ton mot de passe."
    override val bio = "Bio"
    override val fullName = "Nom complet"
    override val phoneNumber = "Numéro de téléphone"
    override val avatar = "Avatar"
    override val changePhoto = "Changer la photo"
    override val uploading = "Upload…"
    override val saving = "Enregistrement…"
    override val changePassword = "Changer le mot de passe"
    override val currentPassword = "Mot de passe actuel"
    override val changing = "Changement…"
    override val uploadFailed = "Échec de l'upload."
    override val saveFailed = "Enregistrement impossible."
    override val changeFailed = "Changement impossible."
    override val fileUnreadable = "Fichier illisible."
    override val newPasswordTooShort = "Le nouveau mot de passe doit faire au moins 8 caractères."
    override val artistProjectLabel = "Présente ton projet musical *"
    override val justificationDocLabel = "Lien d'un document justificatif (optionnel)"
    override val socialNetworksOptional = "Réseaux sociaux (optionnel)"
    override val sending = "Envoi…"
    override val sendApplication = "Envoyer ma candidature"
    override val managerExpLabel = "Expérience"
    override val artistDescMinError = "Décris ton projet (au moins 10 caractères)."
    override val managerFieldsRequired = "Renseigne ta bio et ton expérience."
    override val sendFailed = "Envoi impossible."
    override val applicationsClosed = "Les candidatures sont actuellement fermées. L'administrateur désigne directement les promotions."
    override val applicationPending = "⏳ Ta demande est en attente de validation."
    override val profileTitle = "Mon profil"
    override val edit = "Modifier"
    override val changePasswordSectionHint = "Masqué — touchez pour changer votre mot de passe"
    override val quickActions = "Raccourcis"
    override val statArtistSpace = "Espace Artiste"
    override val statManagerSpace = "Espace Manager"
    override val statFanSpace = "Espace Fan"
    override val statVotesReceived = "Votes reçus"
    override val statDuelsWon = "Duels gagnés"
    override val statGiftsReceived = "Cadeaux reçus"
    override val statDuelsManaged = "Duels gérés"
    override val statActiveDuels = "En cours"
    override val statVotesCast = "Votes effectués"
    override val statGiftsSent = "Cadeaux envoyés"
    override val statTickets = "Billets achetés"
    override val statWinRate = "Taux de victoire"
    override val publicProfile = "Profil public"
    override val artisticProfile = "Profil artistique"
    override val managerProfileTitle = "Profil manager"
    override val stageName = "Nom de scène"
    override val coverImage = "Image de couverture"
    override val makeProfilePublic = "Profil visible publiquement"
    override val socialLinks = "Liens sociaux"
    override val socialLinksHint = "Ajoutez les URL complètes de vos réseaux (https://…)"
    override val deleteAccountGrace = "Un délai de 20 jours te permettra d'annuler avant la suppression définitive. Confirmer ?"
    override val accountDeletionScheduledOn = "⚠️ Ton compte sera supprimé le"
    override val deletionCanStillCancel = "Tu peux encore l'annuler."
    override val iAcceptThe = "J'accepte les"
    override val termsOfUse = "Conditions d'utilisation"
    override val privacyPolicy = "Politique de confidentialité"
    override val langTag = "fr"
    override val markAllRead = "Tout lu"
    override val noNotifications = "Aucune notification"
    override val noNotificationsHint = "Tes alertes apparaîtront ici."
    override val yourReferralCode = "Ton code de parrainage"
    override val copyCode = "Copier le code"
    override val yourReferrals = "Tes filleuls"
    override val referee = "Filleul"
    override val claim = "Réclamer"
    override val shareReferralTitle = "Code de parrainage Dual Music"
    override val stripeSubscriptionHint = "Paiement par carte (Stripe). Ton abonnement est activé automatiquement après le paiement."
    override val offer = "Offre"
    override val subscribeByCard = "S'abonner par carte"
    override val history = "Historique"
    override val createWithdrawPin = "Crée ton code PIN de retrait (6 chiffres)"
    override val createPin = "Créer le code"
    override val noWithdrawMethod = "Aucune méthode de retrait. Ajoute-en une depuis le site pour l'instant."
    override val method = "Méthode"
    override val fees = "Frais"
    override val net = "Net"
    override val withdrawPin = "Code PIN de retrait"
    override val requestWithdraw = "Demander le retrait"
    override val statusPending = "En attente"
    override val statusApproved = "Approuvé"
    override val statusPaid = "Payé"
    override val statusRejected = "Rejeté"
    override val pendingRewards = "crédits de récompense en attente"
    override val claimed = "✅ Réclamé"
    override val subscriptionActive = "✅ Abonnement actif :"
    override val withdrawSubmitted = "✅ Demande de retrait envoyée."
    override val credits = "crédits"
    override val blog = "Blog"
    override val video = "Vidéo"
    override val article = "Article"
    override val videoUnavailable = "Vidéo indisponible."
    override val by = "Par"
    override val noArtists = "Aucun artiste"
    override val noArtistsHint = "Les artistes de la plateforme apparaîtront ici."
    override val followed = "Suivi"
    override val follow = "Suivre"
    override val donors = "Donateurs"
    override val emptyRanking = "Classement vide"
    override val emptyRankingHint = "Le classement se remplira avec l'activité de la plateforme."
    override val periodic = "Périodique"
    override val seasonActive = "En cours"
    override val seasonEnded = "Terminée"
    override val mysteryReward = "🎁 Récompense mystère"
    override val noReplays = "Aucune rediffusion disponible"
    override val noReplaysHint = "Les rediffusions débloquées apparaîtront ici."
    override val replay = "Rediffusion"
    override val free = "Gratuit"
    override val replayPremium = "Cette rediffusion est premium."
    override val unlocking = "Déblocage…"
    override val unlockFor = "Débloquer pour"
    override val back = "Retour"
    override val likeAction = "J'aime"
    override val followers = "abonnés"
    override val views = "vues"
    override val tabChallenges = "Défis"
    override val myConcerts = "Mes concerts"
    override val create = "Créer"
    override val noChallenges = "Aucun défi"
    override val noChallengesHint = "Les défis de duel reçus apparaîtront ici."
    override val noConcerts = "Aucun concert"
    override val noConcertsHint = "Crée ton premier concert depuis l'onglet « Créer »."
    override val titleRequired = "Titre *"
    override val description = "Description"
    override val dateFormatLabel = "Date * (AAAA-MM-JJTHH:MM)"
    override val ticketPriceLabel = "Prix du billet (crédits)"
    override val maxTicketsLabel = "Places max (optionnel)"
    override val allowDedications = "Autoriser les dédicaces"
    override val allowSponsorAds = "Autoriser les pubs sponsors"
    override val uploadingCover = "Upload de la pochette…"
    override val noCover = "Aucune pochette."
    override val changeCover = "Changer la pochette"
    override val chooseCover = "Choisir une pochette"
    override val creating = "Création…"
    override val createConcert = "Créer le concert"
    override val duelChallenge = "Défi de duel"
    override val proposed = "Proposé"
    override val accept = "Accepter"
    override val decline = "Refuser"
    override val statusAccepted = "Accepté"
    override val statusDeclined = "Refusé"
    override val myRequests = "Mes demandes"
    override val newTab = "Nouvelle"
    override val tiersByDuration = "Tarifs (par durée)"
    override val tier = "Palier"
    override val noSponsorRequests = "Aucune demande de sponsoring"
    override val noSponsorRequestsHint = "Crée une demande depuis l'onglet « Nouvelle »."
    override val unsupportedMedia = "Type de média non supporté."
    override val noEvents = "Aucun événement disponible"
    override val noEventsHint = "Reviens quand des événements seront programmés."
    override val noMedia = "Aucun média."
    override val changeMedia = "Changer le média"
    override val chooseMedia = "Choisir un média"
    override val descriptionOptional = "Description (optionnel)"
    override val submitRequest = "Envoyer la demande"
    override val selectEventError = "Sélectionnez un événement."
    override val event = "Événement"
    override val pay = "Payer"
    override val sponsorStatusPending = "En attente de validation"
    override val sponsorStatusApproved = "Approuvé — à payer"
    override val coverReady = "✅ Pochette prête."
    override val mediaReady = "✅ Média prêt"
    override val step1ChooseEvent = "1. Choisir l'événement"
    override val step2Media = "2. Média de la pub"
    override val artist1 = "Artiste 1"
    override val artist2 = "Artiste 2"
    override val vote = "Voter"
    override val oneVote = "Un vote"
    override val noCandidates = "Aucun candidat approuvé"
    override val noCandidatesHint = "Le classement s'affichera dès les premières candidatures."
    override val timerRunning = "⏱ Minuteur en cours"
    override val artistSingular = "Artiste"
    override val errInvalidAmount = "Montant invalide"
    override val errChooseMethod = "Choisis une méthode de retrait"
    override val errPin6 = "Le code PIN doit contenir 6 chiffres"
    override val errPurchaseFailed = "Achat impossible (solde insuffisant ?)."
    override val errEnterValidAmount = "Saisis un montant valide."
    override val errChooseCountry = "Choisis un pays."
    override val openingPayment = "Ouverture du paiement…"
    override val errRechargeFailed = "Recharge impossible."
    override val errVoteFailed = "Vote impossible"
    override val errSubscriptionUnavailable = "Abonnement indisponible."
    override val errAddMediaFirst = "Ajoutez d'abord un média."
    override val errTitleDateRequired = "Titre et date sont requis."
    override val errCreateFailed = "Création impossible."
    override val errCodeInvalid = "Code incorrect ou expiré."
    override val errGoogleSignInFailed = "Connexion Google impossible."
    override val errNameRequired = "Le nom est requis."
    override val errPaymentFailed = "Paiement impossible."
    override val sponsorPaid = "✅ Sponsoring payé."
    override val requestSent = "✅ Demande envoyée — en attente de validation."
    override val concertCreated = "✅ Concert créé — en attente de validation."
    override val live = "Live"
    override val chooseDots = "Choisir…"
    override val profileUpdated = "✅ Profil mis à jour."
    override val passwordChanged = "✅ Mot de passe changé."
    override val fan = "Fan"
    override val menuArtistProfile = "Profil"
    override val menuMyCompetitions = "Mes compétitions"
    override val menuContent = "Contenu"
    override val menuEarnings = "Revenus"
    override val comingSoon = "Section en construction"
    override val comingSoonHint = "Cette section arrive très bientôt sur mobile."
    override val candStatusPending = "En attente"
    override val candStatusApproved = "Approuvée"
    override val candStatusRejected = "Rejetée"
    override val candScore = "Score"
    override val noCandidacies = "Aucune candidature"
    override val noCandidaciesHint = "Tu n'as encore participé à aucune compétition."
    override val requestDuel = "Demander un Duel"
    override val requestDuelHint = "Défiez un autre artiste pour un duel musical"
    override val searchArtist = "Rechercher un artiste…"
    override val noArtistAvailable = "Aucun artiste disponible"
    override val challenge = "Défier"
    override val receivedInvitations = "Invitations reçues"
    override val duelRequestSent = "✅ Invitation envoyée."
    override val followedTitle = "Artistes suivis"
    override val notFollowingAny = "Vous ne suivez aucun artiste"
    override val followedEmptyHint = "Découvrez des artistes depuis l'accueil pour les suivre."
    override val walletWithdrawals = "Retraits"
    override val noWithdrawals = "Aucun retrait pour le moment."
    override val displayCurrency = "Devise d'affichage"
    override val displayCurrencyHint = "La valeur de tes crédits s'affiche dans cette devise."
    override val myLives = "Mes Lives"
    override val myLivesHint = "Gérez vos lives passés et lancez-en un nouveau"
    override val liveTitle = "Titre du live"
    override val startLive = "Lancer un Live"
    override val liveActive = "Live en cours"
    override val endLive = "Terminer"
    override val noLives = "Aucun live en cours"
    override val noLivesHint = "Lancez un live pour connecter avec vos fans en temps réel"
}

/** Chaînes anglaises. */
object EnStrings : Strings {
    override val navHome = "Home"
    override val navLives = "Lives"
    override val navDuels = "Duels"
    override val navConcerts = "Concerts"
    override val navCompetitions = "Contests"
    override val homeTitle = "Join live music duels"
    override val homeSubtitle = "Vote for your artists, send gifts and live the musical competition."
    override val lifestyle = "Lifestyle"
    override val ranking = "Ranking"
    override val artists = "Artists"
    override val notifications = "Notifications"
    override val profile = "Profile"
    override val preferences = "Preferences"
    override val appearance = "Appearance"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeSystem = "System"
    override val language = "Language"
    override val french = "French"
    override val english = "English"
    override val account = "Account"
    override val deleteAccount = "Delete my account"
    override val cancelDeletion = "Cancel deletion"
    override val confirm = "Confirm"
    override val cancel = "Cancel"
    override val screenDuels = "Duels"
    override val screenConcerts = "Concerts"
    override val screenCompetitions = "Competitions"
    override val screenArtists = "Artists"
    override val screenDiscover = "Discover"
    override val screenRankings = "Rankings"
    override val loading = "Loading…"
    override val retry = "Retry"
    override val save = "Save"
    override val emptyGeneric = "Nothing to show yet."
    override val menuMySpace = "My space"
    override val menuDashboard = "Dashboard"
    override val menuFollowing = "Following"
    override val menuSubscription = "Subscription"
    override val menuTransactions = "My transactions"
    override val menuReferral = "Referral program"
    override val menuSponsor = "Sponsor"
    override val menuBecomeArtist = "Become an artist"
    override val menuBecomeManager = "Become a manager"
    override val menuCreatorSpace = "Creator space"
    override val menuWithdraw = "Withdraw credits"
    override val menuReplays = "Replays"
    override val menuGiftShop = "Gift shop"
    override val menuEditProfile = "Edit profile"
    override val menuSignOut = "Sign out"
    override val walletExpenses = "Expenses"
    override val walletIncome = "Income"
    override val creditPurchases = "Credit purchases"
    override val noPurchases = "No credit purchases yet."
    override val rechargeCredits = "Top up credits"
    override val amountCredits = "Amount (credits)"
    override val mobileMoneyNumber = "Mobile Money number (optional)"
    override val operator = "Operator"
    override val country = "Country"
    override val buy = "Buy"
    override val myBalance = "My balance"
    override val recharge = "Top up"
    override val authLoginSubtitle = "Sign in to join the lives"
    override val authRegisterSubtitle = "Create your account to join the lives"
    override val authResetSubtitle = "Enter the code you received and your new password"
    override val resetPasswordTitle = "Reset password"
    override val pleaseWait = "Please wait…"
    override val signIn = "Sign in"
    override val createAccount = "Create my account"
    override val sendCode = "Send code"
    override val reset = "Reset"
    override val email = "Email"
    override val emailPlaceholder = "youremail@example.com"
    override val password = "Password"
    override val passwordStar = "Password *"
    override val confirmPasswordStar = "Confirm password *"
    override val newPassword = "New password"
    override val atLeast8 = "At least 8 characters"
    override val passwordsDontMatch = "Passwords do not match"
    override val codeFromEmail = "Code received by email"
    override val forgotPassword = "Forgot password?"
    override val noAccountSignUp = "No account? Sign up"
    override val alreadyAccountSignIn = "Already have an account? Sign in"
    override val backToLogin = "Back to login"
    override val chooseCountry = "Choose a country"
    override val countryStar = "Country *"
    override val continueWithGoogle = "Continue with Google"
    override val googleCancelled = "Google sign-in cancelled."
    override val forgotHint = "Get a code by email to reset your password."
    override val bio = "Bio"
    override val fullName = "Full name"
    override val phoneNumber = "Phone number"
    override val avatar = "Avatar"
    override val changePhoto = "Change photo"
    override val uploading = "Uploading…"
    override val saving = "Saving…"
    override val changePassword = "Change password"
    override val currentPassword = "Current password"
    override val changing = "Changing…"
    override val uploadFailed = "Upload failed."
    override val saveFailed = "Could not save."
    override val changeFailed = "Change failed."
    override val fileUnreadable = "Unreadable file."
    override val newPasswordTooShort = "The new password must be at least 8 characters."
    override val artistProjectLabel = "Present your musical project *"
    override val justificationDocLabel = "Link to a supporting document (optional)"
    override val socialNetworksOptional = "Social networks (optional)"
    override val sending = "Sending…"
    override val sendApplication = "Submit my application"
    override val managerExpLabel = "Experience"
    override val artistDescMinError = "Describe your project (at least 10 characters)."
    override val managerFieldsRequired = "Fill in your bio and experience."
    override val sendFailed = "Could not send."
    override val applicationsClosed = "Applications are currently closed. The administrator designates promotions directly."
    override val applicationPending = "⏳ Your application is pending review."
    override val profileTitle = "My profile"
    override val edit = "Edit"
    override val changePasswordSectionHint = "Hidden — tap to change your password"
    override val quickActions = "Quick actions"
    override val statArtistSpace = "Artist space"
    override val statManagerSpace = "Manager space"
    override val statFanSpace = "Fan space"
    override val statVotesReceived = "Votes received"
    override val statDuelsWon = "Duels won"
    override val statGiftsReceived = "Gifts received"
    override val statDuelsManaged = "Duels managed"
    override val statActiveDuels = "Active"
    override val statVotesCast = "Votes cast"
    override val statGiftsSent = "Gifts sent"
    override val statTickets = "Tickets bought"
    override val statWinRate = "Win rate"
    override val publicProfile = "Public profile"
    override val artisticProfile = "Artist profile"
    override val managerProfileTitle = "Manager profile"
    override val stageName = "Stage name"
    override val coverImage = "Cover image"
    override val makeProfilePublic = "Publicly visible profile"
    override val socialLinks = "Social links"
    override val socialLinksHint = "Add the full URLs of your networks (https://…)"
    override val deleteAccountGrace = "A 20-day grace period lets you cancel before permanent deletion. Confirm?"
    override val accountDeletionScheduledOn = "⚠️ Your account will be deleted on"
    override val deletionCanStillCancel = "You can still cancel."
    override val iAcceptThe = "I accept the"
    override val termsOfUse = "Terms of Use"
    override val privacyPolicy = "Privacy Policy"
    override val langTag = "en"
    override val markAllRead = "Mark all read"
    override val noNotifications = "No notifications"
    override val noNotificationsHint = "Your alerts will appear here."
    override val yourReferralCode = "Your referral code"
    override val copyCode = "Copy code"
    override val yourReferrals = "Your referrals"
    override val referee = "Referee"
    override val claim = "Claim"
    override val shareReferralTitle = "Dual Music referral code"
    override val stripeSubscriptionHint = "Card payment (Stripe). Your subscription activates automatically after payment."
    override val offer = "Plan"
    override val subscribeByCard = "Subscribe by card"
    override val history = "History"
    override val createWithdrawPin = "Create your withdrawal PIN (6 digits)"
    override val createPin = "Create PIN"
    override val noWithdrawMethod = "No withdrawal method. Add one from the website for now."
    override val method = "Method"
    override val fees = "Fees"
    override val net = "Net"
    override val withdrawPin = "Withdrawal PIN"
    override val requestWithdraw = "Request withdrawal"
    override val statusPending = "Pending"
    override val statusApproved = "Approved"
    override val statusPaid = "Paid"
    override val statusRejected = "Rejected"
    override val pendingRewards = "reward credits pending"
    override val claimed = "✅ Claimed"
    override val subscriptionActive = "✅ Active subscription:"
    override val withdrawSubmitted = "✅ Withdrawal request sent."
    override val credits = "credits"
    override val blog = "Blog"
    override val video = "Video"
    override val article = "Article"
    override val videoUnavailable = "Video unavailable."
    override val by = "By"
    override val noArtists = "No artists"
    override val noArtistsHint = "Platform artists will appear here."
    override val followed = "Following"
    override val follow = "Follow"
    override val donors = "Donors"
    override val emptyRanking = "Empty ranking"
    override val emptyRankingHint = "The ranking will fill with platform activity."
    override val periodic = "Periodic"
    override val seasonActive = "Active"
    override val seasonEnded = "Ended"
    override val mysteryReward = "🎁 Mystery reward"
    override val noReplays = "No replays available"
    override val noReplaysHint = "Unlocked replays will appear here."
    override val replay = "Replay"
    override val free = "Free"
    override val replayPremium = "This replay is premium."
    override val unlocking = "Unlocking…"
    override val unlockFor = "Unlock for"
    override val back = "Back"
    override val likeAction = "Like"
    override val followers = "followers"
    override val views = "views"
    override val tabChallenges = "Challenges"
    override val myConcerts = "My concerts"
    override val create = "Create"
    override val noChallenges = "No challenge"
    override val noChallengesHint = "Received duel challenges will appear here."
    override val noConcerts = "No concert"
    override val noConcertsHint = "Create your first concert from the “Create” tab."
    override val titleRequired = "Title *"
    override val description = "Description"
    override val dateFormatLabel = "Date * (YYYY-MM-DDTHH:MM)"
    override val ticketPriceLabel = "Ticket price (credits)"
    override val maxTicketsLabel = "Max seats (optional)"
    override val allowDedications = "Allow dedications"
    override val allowSponsorAds = "Allow sponsor ads"
    override val uploadingCover = "Uploading cover…"
    override val noCover = "No cover."
    override val changeCover = "Change cover"
    override val chooseCover = "Choose a cover"
    override val creating = "Creating…"
    override val createConcert = "Create concert"
    override val duelChallenge = "Duel challenge"
    override val proposed = "Proposed"
    override val accept = "Accept"
    override val decline = "Decline"
    override val statusAccepted = "Accepted"
    override val statusDeclined = "Declined"
    override val myRequests = "My requests"
    override val newTab = "New"
    override val tiersByDuration = "Rates (by duration)"
    override val tier = "Tier"
    override val noSponsorRequests = "No sponsor request"
    override val noSponsorRequestsHint = "Create a request from the “New” tab."
    override val unsupportedMedia = "Unsupported media type."
    override val noEvents = "No event available"
    override val noEventsHint = "Come back when events are scheduled."
    override val noMedia = "No media."
    override val changeMedia = "Change media"
    override val chooseMedia = "Choose media"
    override val descriptionOptional = "Description (optional)"
    override val submitRequest = "Submit request"
    override val selectEventError = "Select an event."
    override val event = "Event"
    override val pay = "Pay"
    override val sponsorStatusPending = "Pending review"
    override val sponsorStatusApproved = "Approved — to pay"
    override val coverReady = "✅ Cover ready."
    override val mediaReady = "✅ Media ready"
    override val step1ChooseEvent = "1. Choose the event"
    override val step2Media = "2. Ad media"
    override val artist1 = "Artist 1"
    override val artist2 = "Artist 2"
    override val vote = "Vote"
    override val oneVote = "One vote"
    override val noCandidates = "No approved candidate"
    override val noCandidatesHint = "The ranking will appear once candidacies start."
    override val timerRunning = "⏱ Timer running"
    override val artistSingular = "Artist"
    override val errInvalidAmount = "Invalid amount"
    override val errChooseMethod = "Choose a withdrawal method"
    override val errPin6 = "The PIN must be 6 digits"
    override val errPurchaseFailed = "Purchase failed (insufficient balance?)."
    override val errEnterValidAmount = "Enter a valid amount."
    override val errChooseCountry = "Choose a country."
    override val openingPayment = "Opening payment…"
    override val errRechargeFailed = "Top-up failed."
    override val errVoteFailed = "Vote failed"
    override val errSubscriptionUnavailable = "Subscription unavailable."
    override val errAddMediaFirst = "Add media first."
    override val errTitleDateRequired = "Title and date are required."
    override val errCreateFailed = "Creation failed."
    override val errCodeInvalid = "Wrong or expired code."
    override val errGoogleSignInFailed = "Google sign-in failed."
    override val errNameRequired = "Name is required."
    override val errPaymentFailed = "Payment failed."
    override val sponsorPaid = "✅ Sponsorship paid."
    override val requestSent = "✅ Request sent — pending review."
    override val concertCreated = "✅ Concert created — pending review."
    override val live = "Live"
    override val chooseDots = "Choose…"
    override val profileUpdated = "✅ Profile updated."
    override val passwordChanged = "✅ Password changed."
    override val fan = "Fan"
    override val menuArtistProfile = "Profile"
    override val menuMyCompetitions = "My competitions"
    override val menuContent = "Content"
    override val menuEarnings = "Earnings"
    override val comingSoon = "Coming soon"
    override val comingSoonHint = "This section is coming soon on mobile."
    override val candStatusPending = "Pending"
    override val candStatusApproved = "Approved"
    override val candStatusRejected = "Rejected"
    override val candScore = "Score"
    override val noCandidacies = "No candidacies"
    override val noCandidaciesHint = "You haven't entered any competition yet."
    override val requestDuel = "Request a Duel"
    override val requestDuelHint = "Challenge another artist to a music duel"
    override val searchArtist = "Search an artist…"
    override val noArtistAvailable = "No artist available"
    override val challenge = "Challenge"
    override val receivedInvitations = "Received invitations"
    override val duelRequestSent = "✅ Invitation sent."
    override val followedTitle = "Followed artists"
    override val notFollowingAny = "You don't follow any artist"
    override val followedEmptyHint = "Discover artists from home to follow them."
    override val walletWithdrawals = "Withdrawals"
    override val noWithdrawals = "No withdrawal yet."
    override val displayCurrency = "Display currency"
    override val displayCurrencyHint = "Your credits value is shown in this currency."
    override val myLives = "My Lives"
    override val myLivesHint = "Manage your past lives and start a new one"
    override val liveTitle = "Live title"
    override val startLive = "Start a Live"
    override val liveActive = "Live in progress"
    override val endLive = "End"
    override val noLives = "No live in progress"
    override val noLivesHint = "Start a live to connect with your fans in real time"
}

/** Renvoie la table de chaînes d'une langue. */
fun stringsFor(language: Language): Strings = when (language) {
    Language.FR -> FrStrings
    Language.EN -> EnStrings
}

/**
 * Chaînes localisées courantes, fournies au sommet de l'app via `CompositionLocalProvider`.
 * Par défaut : français.
 */
val LocalStrings = staticCompositionLocalOf<Strings> { FrStrings }

/**
 * Chaînes courantes accessibles **hors contexte @Composable** (ex. ViewModels, pour les
 * messages d'erreur). Mises à jour par [setAppLanguage] au démarrage et à chaque changement
 * de langue. Toujours cohérentes avec [LocalStrings] côté UI.
 */
@Volatile
var appStrings: Strings = FrStrings
    private set

/** Met à jour la langue globale (UI hors composable). Appelé par le contrôleur de langue. */
fun setAppLanguage(language: Language) {
    appStrings = stringsFor(language)
}

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
data class Strings(
    // Navigation basse.
    val navHome: String,
    val navLives: String,
    val navDuels: String,
    val navConcerts: String,
    val navCompetitions: String,
    // Accueil.
    val homeTitle: String,
    val homeSubtitle: String,
    val lifestyle: String,
    val ranking: String,
    val artists: String,
    // Barre supérieure.
    val notifications: String,
    val profile: String,
    // Préférences.
    val preferences: String,
    val appearance: String,
    val themeLight: String,
    val themeDark: String,
    val themeSystem: String,
    val language: String,
    val french: String,
    val english: String,
    val account: String,
    val deleteAccount: String,
    val cancelDeletion: String,
    val confirm: String,
    val cancel: String,
)

/** Chaînes françaises (langue par défaut). */
val FrStrings = Strings(
    navHome = "Accueil",
    navLives = "Lives",
    navDuels = "Duels",
    navConcerts = "Concerts",
    navCompetitions = "Compét.",
    homeTitle = "Participez aux duels musicaux en direct",
    homeSubtitle = "Votez pour vos artistes, offrez des cadeaux et vivez la compétition musicale.",
    lifestyle = "Lifestyle",
    ranking = "Classement",
    artists = "Artistes",
    notifications = "Notifications",
    profile = "Profil",
    preferences = "Préférences",
    appearance = "Apparence",
    themeLight = "Clair",
    themeDark = "Sombre",
    themeSystem = "Système",
    language = "Langue",
    french = "Français",
    english = "Anglais",
    account = "Compte",
    deleteAccount = "Supprimer mon compte",
    cancelDeletion = "Annuler la suppression",
    confirm = "Confirmer",
    cancel = "Annuler",
)

/** Chaînes anglaises. */
val EnStrings = Strings(
    navHome = "Home",
    navLives = "Lives",
    navDuels = "Duels",
    navConcerts = "Concerts",
    navCompetitions = "Contests",
    homeTitle = "Join live music duels",
    homeSubtitle = "Vote for your artists, send gifts and live the musical competition.",
    lifestyle = "Lifestyle",
    ranking = "Ranking",
    artists = "Artists",
    notifications = "Notifications",
    profile = "Profile",
    preferences = "Preferences",
    appearance = "Appearance",
    themeLight = "Light",
    themeDark = "Dark",
    themeSystem = "System",
    language = "Language",
    french = "French",
    english = "English",
    account = "Account",
    deleteAccount = "Delete my account",
    cancelDeletion = "Cancel deletion",
    confirm = "Confirm",
    cancel = "Cancel",
)

/** Renvoie la table de chaînes d'une langue. */
fun stringsFor(language: Language): Strings = when (language) {
    Language.FR -> FrStrings
    Language.EN -> EnStrings
}

/**
 * Chaînes localisées courantes, fournies au sommet de l'app via `CompositionLocalProvider`.
 * Par défaut : français.
 */
val LocalStrings = staticCompositionLocalOf { FrStrings }

/*
 * Projet Gradle racine — Dual Music (Android).
 *
 * Pour le PREMIER test sur téléphone, on n'inclut que l'app de démo + le design system
 * (core:ui), afin de minimiser les dépendances et garantir une compilation simple.
 * Les autres modules (core:network, feature:*, …) seront ajoutés au fur et à mesure.
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DualMusicMobile"

include(":app")
include(":core:ui")

// Lot « connexion » : fondation réseau/auth + shared-domain + écrans d'auth.
// shared-domain vit HORS du dossier android/ (mono-repo) → on mappe son projectDir.
include(":shared-domain")
project(":shared-domain").projectDir = file("../shared-domain")
include(":core:network")
include(":core:auth")
include(":feature:auth")

// À activer aux lots suivants (feed / live / vidéo) :
// include(":core:realtime", ":core:media", ":feature:live", ":feature:feed")

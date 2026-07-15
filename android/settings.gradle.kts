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

// Modules disponibles, à activer quand on branche les écrans réels :
// include(":shared-domain") ; project(":shared-domain").projectDir = file("../shared-domain")
// include(":core:network", ":core:realtime", ":core:auth", ":core:media")
// include(":feature:auth", ":feature:live", ":feature:feed")

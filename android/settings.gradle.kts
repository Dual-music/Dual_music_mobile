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
        // Requis par LiveKit : ses dépendances transitives `noise` et `audioswitch`
        // (com.github.*) sont publiées sur JitPack, pas sur Maven Central.
        maven { url = uri("https://jitpack.io") }
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

// Lot « feed + live » : temps réel (Socket.IO), média (LiveKit) et les écrans.
include(":core:realtime")
include(":core:media")
include(":feature:live")
include(":feature:feed")

// Lot « portefeuille » : solde, historiques dépenses/revenus, débits (vote/cadeau).
include(":feature:wallet")

// Lot « duels » : catalogue + room (vidéo, votes payants, minuteur, chat, cadeaux).
include(":feature:duel")

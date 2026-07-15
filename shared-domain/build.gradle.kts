/*
 * shared-domain — Kotlin Multiplatform Mobile (KMM)
 *
 * Couche « domain » PARTAGÉE entre iOS et Android : modèles, DTOs, enums, contrat
 * temps réel (Socket.IO), aperçus économie et validation d'entrée.
 *
 * RÈGLE ABSOLUE : ce module ne contient AUCUNE UI, AUCUN accès réseau, AUCUN média.
 * Il compile en Kotlin/Native (framework `SharedDomain` pour iOS) et Kotlin/JVM (Android).
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    // Cible Android (Kotlin/JVM).
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    // Cibles iOS → un framework Objective-C consommable depuis Swift.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SharedDomain"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Sérialisation JSON alignée sur l'enveloppe du backend.
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            // Dates ISO-8601 multiplateformes (createdAt, scheduledTime, …).
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.dualmusic.domain"
    compileSdk = 35
    defaultConfig {
        minSdk = 26 // Android 8.0 — aligné sur les besoins Vulkan/HW decode du pipeline vidéo.
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

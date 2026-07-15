/*
 * core:auth (Android) — authentification & stockage sécurisé.
 *
 * Implémente `TokenStore` (EncryptedSharedPreferences + StrongBox) et `TokenRefresher`
 * (`POST /auth/refresh`) attendus par `core:network`, + garde biométrique (BiometricPrompt).
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.dualmusic.core.auth"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":shared-domain"))
    implementation(project(":core:network"))

    // Stockage chiffré des jetons (clé maître StrongBox si dispo).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Biométrie (empreinte / visage).
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Client minimal pour l'appel de refresh (indépendant de core:network → pas de cycle).
    val ktor = "3.0.1"
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

/*
 * core:upload (Android) — upload de média vers le stockage objet (flux presign → PUT → confirm).
 *
 * Réutilisé par feature:sponsor (média de pub) et feature:creator (pochette de concert).
 * Le PUT des octets se fait vers une URL signée ABSOLUE (hors API : pas de Bearer, pas de
 * préfixe /api/v1) → on instancie un HttpClient Ktor dédié, distinct de l'ApiClient.
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.dualmusic.core.upload"
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
    // ApiClient (presign/confirm) + ktor-client-core exposé transitivement (api).
    implementation(project(":core:network"))

    val ktor = "3.0.1"
    // Moteur pour le HttpClient dédié au PUT brut (non exposé par core:network).
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

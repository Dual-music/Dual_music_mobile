/*
 * :app — application Android de démonstration.
 *
 * Premier jalon : afficher l'identité visuelle Dual Music sur le téléphone (thème,
 * boutons, cadeau animé) avec un minimum de dépendances (uniquement :core:ui).
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dualmusic.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dualmusic.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:ui"))
    // Lot connexion : écran d'auth + fondation (tirée transitivement).
    implementation(project(":feature:auth"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":shared-domain"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    // ViewModel + collecte d'état lifecycle-aware dans Compose.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

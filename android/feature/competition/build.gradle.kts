/*
 * feature:competition (Android) — compétitions musicales.
 *
 * Catalogue, room (candidats + classement en direct), vote payant et billetterie.
 * Les DÉBITS (vote, cadeau, billet) passent par des procédures atomiques serveur.
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dualmusic.feature.competition"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared-domain"))
    implementation(project(":core:network"))
    implementation(project(":core:realtime"))
    // Room vidéo LiveKit (expose transitivement le SDK LiveKit : SurfaceViewRenderer/VideoTrack).
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    // Diffusion pub sponsor (overlay vidéo + contrôle organisateur).
    implementation(project(":feature:sponsor"))
    // Upload de l'image de couverture (presign → PUT → confirm).
    implementation(project(":core:upload"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    // Sélecteur média système (PickVisualMedia).
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

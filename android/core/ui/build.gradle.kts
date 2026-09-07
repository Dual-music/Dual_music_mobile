/*
 * core:ui (Android) — design system Compose de Dual Music.
 *
 * Reprend à l'identique les jetons du web (docs/DESIGN-TOKENS.md) : palette violet néon +
 * rose + cyan, thème sombre par défaut, dégradés, halos, rayon 12dp. Les features
 * consomment uniquement les jetons sémantiques (DualMusicTheme.colors / .gradients).
 */
plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dualmusic.core.ui"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    // Jeu d'icônes complet (Visibility, Share, EmojiEvents, CardGiftcard, Timer…) pour le
    // header/rail de direct partagé, aligné sur les icônes lucide du web.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Chargement d'images distantes avec cache mémoire + disque (voir DMRemoteImage.kt) —
    // remplace un chargeur maison sans cache qui re-téléchargeait à chaque recomposition.
    implementation("io.coil-kt:coil-compose:2.7.0")
}

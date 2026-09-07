/*
 * feature:content (Android) — contenu : vidéos lifestyle + blog.
 *
 * Lifestyle = courtes vidéos d'artistes (lecteur ExoPlayer + like). Blog = articles.
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dualmusic.feature.content"
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
    implementation(project(":core:ui"))
    // Publication lifestyle : upload vidéo (presign → PUT → confirm).
    implementation(project(":core:upload"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    // Icônes du lecteur moderne (Replay10/Forward10/Fullscreen/VolumeUp…), hors du jeu "Filled" de base.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    // Picker média système (rememberLauncherForActivityResult + PickVisualMedia).
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Lecture des vidéos lifestyle.
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

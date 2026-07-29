/*
 * core:media (Android) — pipeline vidéo live de Dual Music.
 *
 * Enveloppe le LiveKit Android SDK (WebRTC + décodage matériel MediaCodec). Obtention du
 * jeton via le backend, connexion à la room SFU, souscription aux pistes, exposition en
 * Flow pour l'UI Compose (rendu via les composants LiveKit Compose dans la feature).
 */
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.dualmusic.core.media"
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

    // LiveKit Android (WebRTC + décodage HW) + composants Compose de rendu vidéo.
    implementation("io.livekit:livekit-android:2.11.0")
    implementation("io.livekit:livekit-android-compose-components:1.3.1")
    // Traitement vidéo temps réel (flou d'arrière-plan / fond virtuel) — version = SDK.
    implementation("io.livekit:livekit-android-track-processors:2.11.0")
    // CameraX (requis par le provider caméra du processor) + ProcessLifecycleOwner.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

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

    // LiveKit Android (WebRTC + décodage HW). Bump 2.11→2.15 requis par track-processors.
    implementation("io.livekit:livekit-android:2.15.0")
    // Traitement vidéo temps réel (flou d'arrière-plan / fond virtuel) — même version que le SDK.
    implementation("io.livekit:livekit-android-track-processors:2.15.0")
    // Fournit CameraXHelper (livekit.org.webrtc) requis par le processor.
    implementation("io.livekit:livekit-android-camerax:2.15.0")
    // CameraX (requis par le provider caméra du processor) + ProcessLifecycleOwner.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

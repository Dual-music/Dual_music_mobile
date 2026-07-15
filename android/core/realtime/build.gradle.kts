/*
 * core:realtime (Android) — client Socket.IO de Dual Music.
 *
 * Enveloppe `socket.io-client-java` : namespaces `/chat` `/live` `/notifications`,
 * handshake JWT, rooms `type:id`, écoute typée d'événements, reconnexion.
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.dualmusic.core.realtime"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":shared-domain")) // contrat realtime + payloads partagés

    // SDK Socket.IO officiel (client Java, compatible serveur Socket.IO v4).
    implementation("io.socket:socket.io-client:2.1.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))
}

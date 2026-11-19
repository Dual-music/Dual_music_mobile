/*
 * :app — application Android de démonstration.
 *
 * Premier jalon : afficher l'identité visuelle Dual Music sur le téléphone (thème,
 * boutons, cadeau animé) avec un minimum de dépendances (uniquement :core:ui).
 */
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Firebase Cloud Messaging (push) — lit android/app/google-services.json.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dualmusic.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dualmusic.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0"
    }

    // Signature release : lue depuis android/keystore.properties (non versionné). Absent → la
    // build release reste non signée, sans casser la compilation debug.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // WiFi : IP LAN du PC (à mettre à jour si elle change — `ipconfig`).
            // Émulateur : "http://10.0.2.2:4000". USB : "http://127.0.0.1:4000" + `adb reverse tcp:4000 tcp:4000`.
            buildConfigField("String", "API_BASE_URL", "\"http://172.17.10.135:4000\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://api.dualmusic.pro\"")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // buildConfig : expose BuildConfig.DEBUG / VERSION_NAME (utilisés par l'init Sentry).
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // LiveKit + Ktor/OkHttp embarquent des métadonnées qui entrent en conflit au
    // packaging : on exclut les doublons courants pour éviter les erreurs de build.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    // Lot connexion : écran d'auth + fondation (tirée transitivement).
    implementation(project(":feature:auth"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":shared-domain"))
    // Lot feed + live : écrans + infra temps réel/média (pour le wiring DI dans l'app).
    implementation(project(":feature:feed"))
    implementation(project(":feature:live"))
    implementation(project(":core:media"))
    implementation(project(":core:realtime"))
    // Lot portefeuille.
    implementation(project(":feature:wallet"))
    // Lot duels.
    implementation(project(":feature:duel"))
    // Lot concerts + compétitions.
    implementation(project(":feature:concert"))
    implementation(project(":feature:competition"))
    // Lot profil.
    implementation(project(":feature:profile"))
    // Lot notifications (in-app).
    implementation(project(":feature:notifications"))
    // Lot retrait.
    implementation(project(":feature:withdrawal"))
    // Lot replays.
    implementation(project(":feature:replay"))
    // Lot boutique cadeaux.
    implementation(project(":feature:giftshop"))
    // Lots classements / parrainage / abonnements.
    implementation(project(":feature:leaderboard"))
    implementation(project(":feature:referral"))
    implementation(project(":feature:subscription"))
    // Lot contenu.
    implementation(project(":feature:content"))
    // Lot artistes.
    implementation(project(":feature:artists"))
    // Lots sponsors + créateur (+ upload média partagé).
    implementation(project(":core:upload"))
    implementation(project(":feature:sponsor"))
    implementation(project(":feature:creator"))
    implementation("androidx.compose.material:material-icons-extended")

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
    // Observabilité : crashs, ANR, perf (auto-capture des exceptions non gérées).
    implementation("io.sentry:sentry-android:7.14.0")
    // Firebase Cloud Messaging (notifications push).
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

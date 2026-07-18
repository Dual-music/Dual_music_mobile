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
    debugImplementation("androidx.compose.ui:ui-tooling")
}

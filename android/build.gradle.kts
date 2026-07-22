/*
 * Build racine — déclare les versions des plugins pour tous les modules.
 * `apply false` : les versions sont fixées ici, chaque module applique le plugin sans version.
 */
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    // Firebase (Google Services) — traite google-services.json (FCM, etc.).
    id("com.google.gms.google-services") version "4.4.2" apply false
}

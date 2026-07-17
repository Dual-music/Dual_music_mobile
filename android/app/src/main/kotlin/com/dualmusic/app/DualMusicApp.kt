package com.dualmusic.app

import android.app.Application
import io.sentry.android.core.SentryAndroid

/**
 * DSN Sentry du projet.
 *
 * ⚠️ À renseigner depuis le dashboard Sentry (Settings → Projects → Client Keys). Laissé
 * VIDE par défaut : Sentry se désactive alors proprement (aucun crash, aucun envoi) — le
 * build et l'app fonctionnent sans configuration. En production, injecte-le plutôt via
 * une variable de build / un secret CI que de le committer en clair.
 */
private const val SENTRY_DSN = ""

/**
 * Application Dual Music.
 *
 * Initialise l'observabilité (Sentry) au démarrage : capture automatique des exceptions
 * non gérées, des ANR et des traces de performance. Sans DSN, l'init est un no-op sûr.
 */
class DualMusicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initObservability()
    }

    /** Configure Sentry (crashs + ANR + performance). Sûr si le DSN est vide. */
    private fun initObservability() {
        if (SENTRY_DSN.isBlank()) return // Désactivé tant que le DSN n'est pas fourni.
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
            // Échantillonnage des traces de perf : 20 % en prod (ajuster selon le volume).
            options.tracesSampleRate = 0.2
            options.isEnableAppStartProfiling = true
            // Ne jamais envoyer d'infos sensibles (PII) par défaut.
            options.isSendDefaultPii = false
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.release = BuildConfig.VERSION_NAME
        }
    }
}

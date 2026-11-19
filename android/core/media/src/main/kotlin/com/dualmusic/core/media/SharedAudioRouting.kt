package com.dualmusic.core.media

import android.content.Context
import android.media.AudioManager
import io.livekit.android.audio.AudioHandler
import io.livekit.android.audio.AudioSwitchHandler

/**
 * Routage audio UNIQUE pour toutes les rooms LiveKit de l'app.
 *
 * Par défaut, chaque `LiveKit.create()` embarque SON propre `AudioSwitchHandler` : il sauvegarde
 * l'état audio du téléphone à la connexion et le RESTAURE à la déconnexion (mode normal, haut-
 * parleur coupé, focus abandonné). Avec un live multi-room (room de l'artiste + une room par
 * invité), la fermeture de n'importe laquelle — un invité qui quitte la scène — restaurait l'état
 * « d'avant » alors que les autres rooms jouaient encore : son qui bascule sur l'écouteur, baisse
 * ou se coupe, différemment selon l'ordre de connexion de chaque appareil.
 *
 * Ici un seul [AudioSwitchHandler] partagé, démarré à la première room connectée et arrêté à la
 * dernière déconnexion (compteur de références). Chaque room reçoit un [AudioHandler] délégué.
 */
object SharedAudioRouting {

    @Volatile private var inner: AudioSwitchHandler? = null
    private var refCount = 0

    private fun handler(context: Context): AudioSwitchHandler =
        inner ?: synchronized(this) {
            inner ?: AudioSwitchHandler(context.applicationContext).apply {
                // Appel/visio : écho annulé, volume géré comme un appel, haut-parleur par défaut.
                audioMode = AudioManager.MODE_IN_COMMUNICATION
                manageAudioFocus = true
                inner = this
            }
        }

    /** [AudioHandler] à fournir à `LiveKitOverrides(audioOptions = AudioOptions(audioHandler = …))`. */
    fun forRoom(context: Context): AudioHandler = object : AudioHandler {
        private var started = false

        override fun start() {
            synchronized(SharedAudioRouting) {
                if (started) return
                started = true
                refCount += 1
                if (refCount == 1) runCatching { handler(context).start() }
            }
        }

        override fun stop() {
            synchronized(SharedAudioRouting) {
                if (!started) return
                started = false
                refCount = (refCount - 1).coerceAtLeast(0)
                if (refCount == 0) runCatching { inner?.stop() }
            }
        }
    }
}

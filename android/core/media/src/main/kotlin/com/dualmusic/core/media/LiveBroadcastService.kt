package com.dualmusic.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Service de premier plan maintenu PENDANT que l'utilisateur publie son micro/caméra
 * (artiste hôte, invité sur scène, candidat de compétition, artiste de duel).
 *
 * Sans lui, Android coupe la capture micro dès que l'app passe en arrière-plan (écran
 * verrouillé, notification ouverte, autre app) : l'orateur continue d'entendre tout le
 * monde mais plus personne ne l'entend — le « je t'entends mais tu ne m'entends pas »
 * rapporté par les testeurs mobiles. La notification persistante est obligatoire (et utile :
 * elle ramène dans l'app d'un tap).
 *
 * Compteur de références : plusieurs rooms peuvent publier en même temps (duel = 1 room par
 * slot) ; le service ne s'arrête que lorsque la dernière publication cesse.
 */
class LiveBroadcastService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        // Pas de redémarrage automatique : si le système nous tue, la room est de toute façon
        // perdue et l'écran relancera la publication (et donc ce service) lui-même.
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        ensureChannel(this)
        val notification = buildNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Types « microphone » et « camera » : API 30+. Obligatoires à partir d'API 34.
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Ex. permission caméra/micro absente au moment de l'appel (API 34 lève une
            // SecurityException) : on ne doit JAMAIS faire planter la diffusion pour ça.
            Log.w(TAG, "startForeground a échoué — diffusion sans service de premier plan", e)
            stopSelf()
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "LiveBroadcastService"
        private const val CHANNEL_ID = "dualmusic_live_broadcast"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_STOP = "com.dualmusic.core.media.action.STOP_BROADCAST"

        private var refCount = 0

        /** À appeler quand une room commence à publier micro/caméra. */
        @Synchronized
        fun acquire(context: Context) {
            refCount += 1
            if (refCount > 1) return
            val intent = Intent(context, LiveBroadcastService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de démarrer le service de diffusion", e)
                refCount = 0
            }
        }

        /** À appeler quand une room cesse de publier. Le service s'arrête à la dernière libération. */
        @Synchronized
        fun release(context: Context) {
            if (refCount == 0) return
            refCount -= 1
            if (refCount > 0) return
            runCatching { context.startService(Intent(context, LiveBroadcastService::class.java).setAction(ACTION_STOP)) }
                .onFailure { runCatching { context.stopService(Intent(context, LiveBroadcastService::class.java)) } }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, "Diffusion en direct", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affichée pendant que vous diffusez votre micro ou votre caméra."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context): Notification {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = launch?.let {
                PendingIntent.getActivity(
                    context, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            // minSdk 26 : le canal de notification existe toujours.
            return Notification.Builder(context, CHANNEL_ID)
                .setContentTitle("Dual Music — en direct")
                .setContentText("Votre micro/caméra sont diffusés. Touchez pour revenir au direct.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .apply { pending?.let { setContentIntent(it) } }
                .build()
        }
    }
}

package com.dualmusic.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service de réception des notifications push (Firebase Cloud Messaging).
 *
 * - [onMessageReceived] : affiche la notification système (titre + corps) et ouvre l'app au tap.
 * - [onNewToken] : le jeton d'appareil a changé. L'enregistrement auprès du backend se fait
 *   lorsque l'utilisateur est connecté (voir `AppContainer.registerPushToken` appelé au login) ;
 *   ici on ne fait que journaliser, le nouveau jeton sera repris au prochain enregistrement.
 */
class DualMusicMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Dual Music"
        val body = message.notification?.body ?: message.data["message"] ?: ""
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        // Réenregistré au prochain login (l'appel backend nécessite le Bearer de l'utilisateur).
    }

    /** Construit + affiche une notification dans le canal par défaut, ouvrant l'app au tap. */
    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Général", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "dualmusic_default"
    }
}

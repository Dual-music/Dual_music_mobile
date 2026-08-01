package com.dualmusic.domain.realtime

import com.dualmusic.domain.model.EventContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contrat temps réel Dual Music — **Socket.IO** (et NON Supabase).
 *
 * Référence unique partagée iOS/Android : namespaces, nommage des rooms, catalogue
 * d'événements et formes de payloads. Les clients `core-realtime` (un par plateforme)
 * implémentent ce contrat ; toute évolution se fait ICI d'abord.
 *
 * Modèle backend :
 *  - 3 namespaces : `/chat`, `/live`, `/notifications`.
 *  - handshake authentifié par JWT (le token est fourni à la connexion).
 *  - on rejoint une room en émettant `join { type, id }` ; le nom interne = `type:id`.
 *  - le serveur pousse des événements dans la room (voir [RealtimeEvent]) et à
 *    l'utilisateur (événements `tx:*`).
 */
object Realtime {

    /** Namespaces Socket.IO exposés par le backend. */
    object Namespace {
        const val CHAT = "/chat"
        const val LIVE = "/live"
        const val NOTIFICATIONS = "/notifications"
    }

    /**
     * Types de room joignables (correspondent à [EventContext] + `leaderboard`).
     * La valeur wire est utilisée dans le payload `join { type, id }`.
     */
    enum class RoomType(val wire: String) {
        DUEL("duel"),
        CONCERT("concert"),
        COMPETITION("competition"),
        LIVE("live"),
        LEADERBOARD("leaderboard");

        companion object {
            /** Convertit un [EventContext] métier en [RoomType] realtime. */
            fun from(context: EventContext): RoomType = when (context) {
                EventContext.DUEL -> DUEL
                EventContext.CONCERT -> CONCERT
                EventContext.COMPETITION -> COMPETITION
                EventContext.LIVE -> LIVE
            }
        }
    }

    /** Nom interne d'une room : `type:id` (identique au backend `roomName`). */
    fun roomName(type: RoomType, id: String): String = "${type.wire}:$id"

    /** Événement client → serveur pour rejoindre/quitter une room. */
    const val EVENT_JOIN = "join"
    const val EVENT_LEAVE = "leave"

    /**
     * Catalogue des événements serveur → client.
     * Documenté avec la room/namespace d'origine pour brancher les bons listeners.
     */
    object RealtimeEvent {
        /** `/live` · room duel — un vote payant vient d'être enregistré. Payload [VotePayload]. */
        const val VOTE = "vote"

        /** `/live` · room event — un cadeau a été envoyé dans l'événement. Payload [GiftPayload]. */
        const val GIFT = "gift"

        /** par-utilisateur — le destinataire d'un cadeau est notifié (toast). Payload [TxGiftPayload]. */
        const val TX_GIFT = "tx:gift"

        /** par-utilisateur — un crédit (recharge/gain) a été porté au solde. Payload [TxCreditPayload]. */
        const val TX_CREDIT = "tx:credit"

        /** par-utilisateur — un retrait a changé d'état. Payload [TxWithdrawalPayload]. */
        const val TX_WITHDRAWAL = "tx:withdrawal"

        /** `/live` · room event — changement de statut (upcoming/live/ended/winner). Payload [StatusPayload]. */
        const val STATUS = "status"

        /** `/live` · room duel — minuteur (start/stop, cible). Payload [TimerPayload]. */
        const val TIMER = "timer"

        /** `/live` · room event — présence (viewers). Payload [PresencePayload]. */
        const val PRESENCE = "presence"

        /** `/live` · room event — diffusion d'une pub sponsor (start/stop). Payload [SponsorAdPayload]. */
        const val SPONSOR_AD = "sponsor:ad"

        /** `/chat` · room event — nouveau message de chat. Payload [ChatMessagePayload]. */
        const val CHAT_MESSAGE = "message"

        /** `/notifications` — nouvelle notification in-app. Payload libre (voir feature notifications). */
        const val NOTIFICATION = "notification"
    }
}

/** Payload de l'événement `join`/`leave`. */
@Serializable
data class JoinPayload(val type: String, val id: String)

/** `vote` — nouveau vote sur un duel. */
@Serializable
data class VotePayload(
    @SerialName("duel_id") val duelId: String,
    @SerialName("artist_id") val artistId: String,
    val amount: Double,
    @SerialName("user_id") val userId: String? = null,
)

/** `gift` — cadeau envoyé dans une room d'événement. */
@Serializable
data class GiftPayload(
    @SerialName("to_user_id") val toUserId: String? = null,
    @SerialName("from_user_id") val fromUserId: String? = null,
    val value: Double = 0.0,
    @SerialName("gift_id") val giftId: String? = null,
    @SerialName("gift_name") val giftName: String? = null,
    @SerialName("gift_image") val giftImage: String? = null,
)

/** Vidéo publicitaire sponsor diffusable dans un événement. */
@Serializable
data class SponsorAdVideo(
    val id: String,
    @SerialName("video_url") val videoUrl: String,
    val title: String = "",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("play_count") val playCount: Int = 0,
)

/** `sponsor:ad` — début/fin de diffusion d'une pub dans la room (start/stop). */
@Serializable
data class SponsorAdPayload(
    val action: String, // "start" | "stop"
    @SerialName("play_id") val playId: String? = null,
    val ad: SponsorAdVideo? = null,
)

/** Réponse de `POST /sponsors/ads/play` : l'enregistrement de diffusion créé. */
@Serializable
data class SponsorAdPlay(val id: String)

/** `tx:gift` — notification au destinataire d'un cadeau. */
@Serializable
data class TxGiftPayload(val amount: Double)

/** `tx:credit` — crédit porté au solde de l'utilisateur. */
@Serializable
data class TxCreditPayload(val amount: Double, val currency: String? = null)

/** `tx:withdrawal` — mise à jour d'un retrait. */
@Serializable
data class TxWithdrawalPayload(
    val amount: Double,
    val status: String,
)

/** `status` — changement d'état d'un duel/live/concert/compétition. */
@Serializable
data class StatusPayload(
    @SerialName("duel_id") val duelId: String? = null,
    @SerialName("live_id") val liveId: String? = null,
    val status: String,
    @SerialName("winner_id") val winnerId: String? = null,
    @SerialName("room_id") val roomId: String? = null,
)

/** `timer` — minuteur d'un duel (arrivants tardifs inclus). */
@Serializable
data class TimerPayload(
    @SerialName("duel_id") val duelId: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("target_id") val targetId: String? = null,
)

/** `presence` — compteur de spectateurs d'un live. */
@Serializable
data class PresencePayload(
    @SerialName("live_id") val liveId: String? = null,
    val count: Int,
)

/** `message` — message de chat d'une room. */
@Serializable
data class ChatMessagePayload(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    // Le backend utilise la clé `message` (colonne DB), pas `content`.
    @SerialName("message") val content: String,
    @SerialName("created_at") val createdAt: String? = null,
    // Le backend diffuse l'auteur sous la clé `author` (chat.service.js) — pas `user`.
    @SerialName("author") val user: com.dualmusic.domain.model.DisplayProfile? = null,
)

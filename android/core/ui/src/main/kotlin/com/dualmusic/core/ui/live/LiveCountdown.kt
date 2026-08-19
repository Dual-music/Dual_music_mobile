package com.dualmusic.core.ui.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Décompte visuel `MM:SS` qui égrène jusqu'à [endsAtIso] — équivalent Android du minuteur
 * visuel du web (`DuelVideoTimer` / `CompetitionPerformerTimer`), au lieu d'un simple texte
 * « minuteur en cours ». Rien n'est affiché tant que [endsAtIso] est vide ou déjà dépassé.
 *
 * La pastille vire au **rouge** dans les 10 dernières secondes pour signaler l'urgence.
 *
 * @param endsAtIso instant de fin ISO-8601 (ex. `2026-08-07T12:34:56Z`), ou null.
 * @param label libellé optionnel affiché avant le temps (ex. nom du performeur).
 */
@Composable
fun LiveCountdown(
    endsAtIso: String?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val endMillis = remember(endsAtIso) { parseInstantMillis(endsAtIso) } ?: return
    var now by remember(endsAtIso) { mutableLongStateOf(System.currentTimeMillis()) }
    // Tick chaque seconde tant que le décompte n'est pas terminé.
    androidx.compose.runtime.LaunchedEffect(endsAtIso) {
        while (System.currentTimeMillis() < endMillis) {
            now = System.currentTimeMillis()
            delay(500L)
        }
        now = endMillis
    }
    val remainingSec = ((endMillis - now) / 1000L).coerceAtLeast(0L)
    val urgent = remainingSec in 1..10
    val bg by animateColorAsState(
        if (urgent) Color(0xFFDC2626) else Color.Black.copy(alpha = 0.55f),
        label = "countdown-bg",
    )
    val mm = remainingSec / 60L
    val ss = remainingSec % 60L
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        if (!label.isNullOrBlank()) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "%02d:%02d".format(mm, ss),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Parse un instant ISO-8601 en millis epoch ; null si vide/illisible. */
private fun parseInstantMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(iso).toEpochMilli() }
        .getOrElse {
            // Repli tolérant : chaîne naïve « yyyy-MM-ddTHH:mm[:ss] » traitée comme UTC.
            runCatching {
                val clean = iso.trim().substringBefore('.').removeSuffix("Z")
                val fmt = java.text.SimpleDateFormat(
                    if (clean.length <= 16) "yyyy-MM-dd'T'HH:mm" else "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.US,
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                fmt.parse(clean)?.time
            }.getOrNull()
        }
}

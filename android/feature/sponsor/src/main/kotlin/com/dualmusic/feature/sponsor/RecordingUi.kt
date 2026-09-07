package com.dualmusic.feature.sponsor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.i18n.LocalStrings

/**
 * Contrôle d'enregistrement pour l'hôte/manager, piloté par le mode admin :
 *  - `manual` : bouton Enregistrer / Arrêter.
 *  - `auto` (+ actif) : simple indicateur « REC » (l'enregistrement se déclenche seul).
 *  - sinon : rien.
 *
 * @param mode 'off' | 'auto' | 'manual'.
 * @param active un enregistrement est en cours.
 * @param busy verrou anti double-clic.
 * @param onToggle démarre / arrête (mode manual).
 */
@Composable
fun RecordingHostButton(
    mode: String,
    active: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    when {
        mode == "manual" -> DMButton(
            if (active) s.recordStop else s.recordStart,
            style = if (active) DMButtonStyle.DESTRUCTIVE else DMButtonStyle.SECONDARY,
            enabled = !busy,
            modifier = modifier,
            onClick = onToggle,
        )
        mode == "auto" && active -> Box(
            modifier
                .background(Color(0xFFDC2626), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("● ${s.recording}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

/** `125` → `"02:05"` ; `4000` → `"1:06:40"`. */
private fun formatRecDuration(totalSeconds: Int): String {
    val sec = totalSeconds.coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Petite pastille rouge « ● REC » à poser sur l'icône de rail dédiée à l'enregistrement, pour
 * voir d'un coup d'œil qu'un segment tourne sans avoir besoin d'ouvrir la feuille complète.
 * @param active un segment est en cours d'enregistrement.
 * @param paused en pause (session ouverte, rien n'enregistre).
 */
@Composable
fun RecordingRailBadge(active: Boolean, paused: Boolean, modifier: Modifier = Modifier) {
    if (!active && !paused) return
    Box(
        modifier.size(14.dp).background(if (active) Color(0xFFDC2626) else Color(0xFFEAB308), CircleShape),
    )
}

/**
 * Contrôles complets d'enregistrement (mode `manual`), pensés pour une feuille DÉDIÉE (et non
 * mêlés aux réglages caméra/micro, qui ont LEUR PROPRE bouton « Pause » sans rapport — les
 * confondre était source de confusion) : gros chrono + Pause/Reprendre + Annuler + Sauvegarder,
 * chacun en PLEINE LARGEUR l'un sous l'autre pour ne jamais déborder de l'écran.
 *
 * L'egress LiveKit n'a pas de vraie pause serveur : « Pause l'enregistrement » arrête le
 * segment en cours, « Reprendre » en démarre un nouveau sous la même session — le serveur les
 * recolle en une seule vidéo à la sauvegarde ; ce détail est invisible ici, le chrono continue
 * juste d'avancer (ou se fige pendant une pause).
 *
 * @param mode 'off' | 'auto' | 'manual' — rien n'est rendu hors `manual`.
 * @param active un segment est en cours d'enregistrement.
 * @param paused en pause (session ouverte, rien n'enregistre).
 * @param finalizing sauvegarde en cours (dernier segment en clôture / recollage) — tout est
 *   désactivé le temps que ça se termine.
 * @param accumulatedSeconds durée cumulée des segments déjà clos.
 * @param runStartedAt horodatage ISO du segment en cours (null si rien n'est actif).
 * @param busy verrou anti double-clic pendant un appel réseau.
 * @param onStart / onPause / onResume / onCancel / onSave actions hôte.
 */
@Composable
fun RecordingSessionControls(
    mode: String,
    active: Boolean,
    paused: Boolean,
    finalizing: Boolean,
    accumulatedSeconds: Int,
    runStartedAt: String?,
    busy: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    if (mode != "manual") return

    if (!active && !paused && !finalizing) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Le direct n'est pas encore enregistré. Démarre l'enregistrement pour pouvoir en publier le replay ensuite.",
                color = Color(0xFF94A3B8), fontSize = 12.sp,
            )
            DMButton(s.recordStart, style = DMButtonStyle.SECONDARY, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = onStart)
        }
        return
    }

    if (finalizing) {
        Box(
            modifier.fillMaxWidth().background(Color(0xFF64748B), RoundedCornerShape(999.dp)).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("⏳ Finalisation…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        return
    }

    // Chrono local : additionne les segments déjà clos + le temps écoulé depuis le début du
    // segment en cours, recalculé chaque seconde tant qu'un segment enregistre réellement.
    var liveSeconds by remember(accumulatedSeconds, runStartedAt, active) {
        mutableIntStateOf(
            accumulatedSeconds + if (active && runStartedAt != null) {
                ((System.currentTimeMillis() - java.time.Instant.parse(runStartedAt).toEpochMilli()) / 1000).toInt().coerceAtLeast(0)
            } else 0,
        )
    }
    LaunchedEffect(active, runStartedAt) {
        if (!active) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            liveSeconds += 1
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().background(Color(0xFFDC2626), RoundedCornerShape(999.dp)).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "● ${formatRecDuration(liveSeconds)}${if (paused) "  ·  en pause" else ""}",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (active) {
            DMButton("Pause l'enregistrement", style = DMButtonStyle.SECONDARY, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = onPause)
        } else {
            DMButton("Reprendre l'enregistrement", style = DMButtonStyle.SECONDARY, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = onResume)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DMButton("Annuler", style = DMButtonStyle.DESTRUCTIVE, enabled = !busy, modifier = Modifier.weight(1f), onClick = onCancel)
            DMButton("Sauvegarder", style = DMButtonStyle.PRIMARY, enabled = !busy, modifier = Modifier.weight(1f), onClick = onSave)
        }
    }
}

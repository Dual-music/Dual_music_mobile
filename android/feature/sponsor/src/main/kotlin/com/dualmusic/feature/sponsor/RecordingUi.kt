package com.dualmusic.feature.sponsor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

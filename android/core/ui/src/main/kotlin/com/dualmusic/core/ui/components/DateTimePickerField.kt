package com.dualmusic.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.theme.DualMusicTheme
import java.util.Calendar
import java.util.TimeZone

/**
 * Champ date+heure sélectionnable (Material3) — équivalent Android du `datetime-local` du web.
 *
 * Émet une chaîne d'horloge locale `"yyyy-MM-ddTHH:mm"` (sans fuseau, comme le web) via
 * [onValueChange], ou `""` si vidé. Optionnel : bouton d'effacement quand une valeur est posée.
 * Le tap ouvre un sélecteur de date, puis un sélecteur d'heure.
 *
 * @param value valeur courante (`"yyyy-MM-ddTHH:mm"` ou vide).
 * @param label libellé au-dessus du champ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "jj/mm/aaaa --:--",
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    val colors = DualMusicTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.mutedForeground, fontSize = 12.sp)
        // Champ bordé cliquable (le TextField readOnly intercepterait les clics).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.muted, RoundedCornerShape(8.dp))
                .clickable { showDate = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = prettyDateTime(value)
            Text(
                shown.ifBlank { placeholder },
                color = if (shown.isBlank()) colors.mutedForeground else colors.foreground,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                // clickable enfant : efface sans ouvrir le sélecteur.
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = null,
                    tint = colors.mutedForeground,
                    modifier = Modifier.clickable { onValueChange("") },
                )
            } else {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = colors.mutedForeground)
            }
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = dateState.selectedDateMillis
                    showDate = false
                    if (pickedDateMillis != null) showTime = true
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler") } },
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis?.let { millis ->
                        onValueChange(composeLocalDateTime(millis, timeState.hour, timeState.minute))
                    }
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annuler") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

/** Combine une date (millis UTC minuit du DatePicker) + une heure locale → `"yyyy-MM-ddTHH:mm"`. */
private fun composeLocalDateTime(dateMillis: Long, hour: Int, minute: Int): String {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis }
    val y = utc.get(Calendar.YEAR)
    val mo = utc.get(Calendar.MONTH) + 1
    val d = utc.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02dT%02d:%02d".format(y, mo, d, hour, minute)
}

/** `"2026-07-31T14:30"` → `"31/07/2026 14:30"` pour l'affichage ; vide → vide. */
private fun prettyDateTime(value: String): String {
    if (value.isBlank()) return ""
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""").find(value) ?: return value
    val (y, mo, d, h, mi) = m.destructured
    return "$d/$mo/$y $h:$mi"
}

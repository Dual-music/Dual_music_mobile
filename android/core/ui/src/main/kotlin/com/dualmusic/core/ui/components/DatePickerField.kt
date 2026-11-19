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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualmusic.core.ui.theme.DualMusicTheme
import java.util.Calendar
import java.util.TimeZone

/**
 * Champ date sélectionnable (Material3, sans heure) — équivalent Android du `date` du web.
 * Pensé pour la date de naissance : n'autorise que le passé (pas de date future).
 *
 * Émet `"yyyy-MM-dd"` via [onValueChange], ou `""` si vidé.
 *
 * @param value valeur courante (`"yyyy-MM-dd"` ou vide).
 * @param label libellé au-dessus du champ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "jj/mm/aaaa",
) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = DualMusicTheme.colors

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.mutedForeground, fontSize = 12.sp)
        // Champ bordé cliquable (le TextField readOnly intercepterait les clics).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.muted, RoundedCornerShape(8.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = prettyDate(value)
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

    if (showPicker) {
        val dateState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { onValueChange(composeIsoDate(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
        ) { DatePicker(state = dateState) }
    }
}

/** Millis UTC minuit du DatePicker → `"yyyy-MM-dd"`. */
private fun composeIsoDate(dateMillis: Long): String {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis }
    return "%04d-%02d-%02d".format(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH) + 1, utc.get(Calendar.DAY_OF_MONTH))
}

/** `"2026-07-31"` → `"31/07/2026"` pour l'affichage ; vide/illisible → tel quel. */
private fun prettyDate(value: String): String {
    if (value.isBlank()) return ""
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(value) ?: return value
    val (y, mo, d) = m.destructured
    return "$d/$mo/$y"
}

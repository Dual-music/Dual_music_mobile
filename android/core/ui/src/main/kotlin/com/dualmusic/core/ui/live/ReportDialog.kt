package com.dualmusic.core.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialogue de **signalement** d'un direct — parité web (`LiveReportButton`). Mêmes motifs :
 * contenu inapproprié / violence / harcèlement / spam / autre. La valeur transmise à
 * [onSubmit] est la clé backend (`inappropriate`, `violence`, …) attendue par
 * `POST /moderation/reports/{live|competition}`.
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String) -> Unit,
) {
    // (clé backend, libellé affiché) — mêmes clés que le web.
    val reasons = listOf(
        "inappropriate" to "Contenu inapproprié",
        "violence" to "Violence",
        "harassment" to "Harcèlement",
        "spam" to "Spam",
        "other" to "Autre",
    )
    var selected by remember { mutableStateOf(reasons.first().first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signaler ce direct") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { (value, label) ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == value, onClick = { selected = value })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected); onDismiss() }) { Text("Envoyer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

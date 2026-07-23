package com.dualmusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Écran **Préférences** : choix du thème (clair / sombre / système), appliqué immédiatement
 * et persistant. La langue est affichée pour information (i18n à venir).
 *
 * @param currentMode mode de thème courant.
 * @param onSelectMode change le thème.
 */
@Composable
fun PreferencesScreen(currentMode: ThemeMode, onSelectMode: (ThemeMode) -> Unit) {
    val colors = DualMusicTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        // --- Thème ---
        Text("Thème", color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                ThemeRow("Système (auto)", ThemeMode.SYSTEM, currentMode, onSelectMode)
                ThemeRow("Clair", ThemeMode.LIGHT, currentMode, onSelectMode)
                ThemeRow("Sombre", ThemeMode.DARK, currentMode, onSelectMode)
            }
        }

        // --- Langue ---
        Text("Langue", color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary)
                Text(
                    "  Français",
                    color = colors.foreground,
                    modifier = Modifier.padding(start = DualMusicTheme.spacing.xs),
                )
            }
        }
        Text(
            "D'autres langues arriveront prochainement.",
            color = colors.mutedForeground,
        )
    }
}

/** Ligne de sélection d'un mode de thème (radio + libellé). */
@Composable
private fun ThemeRow(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = DualMusicTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
        Text(label, color = colors.foreground, modifier = Modifier.padding(start = DualMusicTheme.spacing.sm))
    }
}

package com.dualmusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dualmusic.core.ui.components.DMButton
import com.dualmusic.core.ui.components.DMButtonStyle
import com.dualmusic.core.ui.components.DMCard
import com.dualmusic.core.ui.i18n.Language
import com.dualmusic.core.ui.i18n.LocalStrings
import com.dualmusic.core.ui.theme.DualMusicTheme

/**
 * Écran **Préférences** : choix du thème (clair / sombre / système), appliqué immédiatement
 * et persistant. La langue est affichée pour information (i18n à venir).
 *
 * @param currentMode mode de thème courant.
 * @param onSelectMode change le thème.
 */
@Composable
fun PreferencesScreen(
    currentMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
    currentLanguage: Language = Language.FR,
    onSelectLanguage: (Language) -> Unit = {},
    deletionScheduledAt: String? = null,
    onRequestDeletion: () -> Unit = {},
    onCancelDeletion: () -> Unit = {},
) {
    val colors = DualMusicTheme.colors
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DualMusicTheme.gradients.hero)
            .padding(DualMusicTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.lg),
    ) {
        // --- Thème ---
        Text(s.appearance, color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                ThemeRow(s.themeSystem, ThemeMode.SYSTEM, currentMode, onSelectMode)
                ThemeRow(s.themeLight, ThemeMode.LIGHT, currentMode, onSelectMode)
                ThemeRow(s.themeDark, ThemeMode.DARK, currentMode, onSelectMode)
            }
        }

        // --- Langue (appliquée immédiatement, persistée) ---
        Text(s.language, color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                LanguageRow(s.french, Language.FR, currentLanguage, onSelectLanguage)
                LanguageRow(s.english, Language.EN, currentLanguage, onSelectLanguage)
            }
        }

        // --- Compte (zone sensible) ---
        Text(s.account, color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                if (deletionScheduledAt != null) {
                    Text(
                        "⚠️ Ton compte sera supprimé le ${deletionScheduledAt.take(10)}. " +
                            "Tu peux encore l'annuler.",
                        color = colors.destructive,
                    )
                    DMButton(s.cancelDeletion, onClick = onCancelDeletion)
                } else {
                    var confirm by remember { mutableStateOf(false) }
                    if (!confirm) {
                        DMButton(s.deleteAccount, style = DMButtonStyle.OUTLINE, onClick = { confirm = true })
                    } else {
                        Text(
                            "Un délai de 20 jours te permettra d'annuler avant la suppression définitive. Confirmer ?",
                            color = colors.mutedForeground,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                            DMButton(
                                s.confirm,
                                style = DMButtonStyle.OUTLINE,
                                modifier = Modifier.weight(1f),
                                onClick = { confirm = false; onRequestDeletion() },
                            )
                            DMButton(
                                s.cancel,
                                style = DMButtonStyle.SECONDARY,
                                modifier = Modifier.weight(1f),
                                onClick = { confirm = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ligne de sélection de langue (radio + libellé). */
@Composable
private fun LanguageRow(
    label: String,
    language: Language,
    current: Language,
    onSelect: (Language) -> Unit,
) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(language) }
            .padding(vertical = DualMusicTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == language, onClick = { onSelect(language) })
        Text(label, color = colors.foreground, modifier = Modifier.padding(start = DualMusicTheme.spacing.sm))
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

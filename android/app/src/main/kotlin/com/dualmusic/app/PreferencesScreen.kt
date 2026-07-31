package com.dualmusic.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    currentCurrency: com.dualmusic.core.ui.currency.DisplayCurrency = com.dualmusic.core.ui.currency.DisplayCurrency(),
    currencyOptions: List<com.dualmusic.domain.settings.ExchangeRate> = emptyList(),
    onSelectCurrency: (com.dualmusic.core.ui.currency.DisplayCurrency) -> Unit = {},
    uiPrefs: com.dualmusic.core.ui.prefs.UiPrefs = com.dualmusic.core.ui.prefs.UiPrefs(),
    onUiPrefsChange: (com.dualmusic.core.ui.prefs.UiPrefs) -> Unit = {},
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
            .verticalScroll(rememberScrollState())
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

        // --- Devise d'affichage (persistée ; convertit la valeur des crédits) ---
        Text(s.displayCurrency, color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                var expanded by remember { mutableStateOf(false) }
                val eurRate = currencyOptions.firstOrNull { it.currencyCode == "EUR" }?.ratePerUsd ?: 1.0
                androidx.compose.foundation.layout.Box {
                    DMButton(
                        "${currentCurrency.code} (${currentCurrency.symbol})",
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (currencyOptions.isNotEmpty()) expanded = true },
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        currencyOptions.forEach { rate ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${rate.currencyCode} — ${rate.name ?: rate.currencyCode}") },
                                onClick = {
                                    expanded = false
                                    val factor = if (eurRate != 0.0) rate.ratePerUsd / eurRate else 1.0
                                    onSelectCurrency(
                                        com.dualmusic.core.ui.currency.DisplayCurrency(
                                            code = rate.currencyCode,
                                            symbol = rate.symbol ?: rate.currencyCode,
                                            eurToTarget = factor,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                Text(s.displayCurrencyHint, color = colors.mutedForeground)
            }
        }

        // --- Notifications visuelles (carte top donateur + animations + fuseau) ---
        Text(s.vnpTitle, color = colors.foreground, fontWeight = FontWeight.Bold)
        Text(s.vnpDesc, color = colors.mutedForeground)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                // Carte du top donateur.
                Text(s.vnpTopDonorCard, color = colors.foreground, fontWeight = FontWeight.Bold)
                RadioOption(s.vnpFull, s.vnpFullDesc, uiPrefs.topDonorMode == "full") { onUiPrefsChange(uiPrefs.copy(topDonorMode = "full")) }
                RadioOption(s.vnpReduced, s.vnpReducedDesc, uiPrefs.topDonorMode == "reduced") { onUiPrefsChange(uiPrefs.copy(topDonorMode = "reduced")) }
                RadioOption(s.vnpOff, s.vnpOffDesc, uiPrefs.topDonorMode == "off") { onUiPrefsChange(uiPrefs.copy(topDonorMode = "off")) }

                // Animation du top donateur (désactivée si carte off).
                Text(s.vnpTopDonorAnim, color = colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = DualMusicTheme.spacing.sm))
                val animEnabled = uiPrefs.topDonorMode != "off"
                RadioOption(s.vnpDefault, s.vnpDefaultDesc, uiPrefs.topDonorAnimation == "default", enabled = animEnabled) { onUiPrefsChange(uiPrefs.copy(topDonorAnimation = "default")) }
                RadioOption(s.vnpTraversing, s.vnpTraversingDesc, uiPrefs.topDonorAnimation == "traversing", enabled = animEnabled) { onUiPrefsChange(uiPrefs.copy(topDonorAnimation = "traversing")) }

                // Réduire les animations.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = DualMusicTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.vnpReduceAnim, color = colors.foreground)
                        Text(s.vnpReduceAnimDesc, color = colors.mutedForeground)
                    }
                    androidx.compose.material3.Switch(
                        checked = uiPrefs.reduceAnimations,
                        onCheckedChange = { onUiPrefsChange(uiPrefs.copy(reduceAnimations = it)) },
                    )
                }

                // Fuseau horaire.
                Text(s.vnpTimezone, color = colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = DualMusicTheme.spacing.sm))
                var tzExpanded by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    DMButton(
                        Timezones.firstOrNull { it.first == uiPrefs.timezone }?.second ?: uiPrefs.timezone,
                        style = DMButtonStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { tzExpanded = true },
                    )
                    androidx.compose.material3.DropdownMenu(expanded = tzExpanded, onDismissRequest = { tzExpanded = false }) {
                        Timezones.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { tzExpanded = false; onUiPrefsChange(uiPrefs.copy(timezone = value)) },
                            )
                        }
                    }
                }
                Text(s.vnpTimezoneDesc, color = colors.mutedForeground)
            }
        }

        // --- Compte (zone sensible) ---
        Text(s.account, color = colors.foreground, fontWeight = FontWeight.Bold)
        DMCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(DualMusicTheme.spacing.sm)) {
                if (deletionScheduledAt != null) {
                    Text(
                        "⚠️ Ton compte sera supprimé le ${com.dualmusic.core.ui.datetime.formatTz(deletionScheduledAt, "dd/MM/yyyy")}. " +
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

/** Fuseaux horaires proposés (valeur IANA, libellé) — identiques au web. */
private val Timezones = listOf(
    "GMT" to "GMT (UTC)",
    "Europe/Paris" to "Europe/Paris (CET/CEST)",
    "Europe/London" to "Europe/London",
    "Africa/Abidjan" to "Africa/Abidjan (GMT)",
    "Africa/Lagos" to "Africa/Lagos (WAT)",
    "Africa/Casablanca" to "Africa/Casablanca",
    "America/New_York" to "America/New_York",
    "America/Los_Angeles" to "America/Los_Angeles",
    "Asia/Dubai" to "Asia/Dubai",
    "Asia/Tokyo" to "Asia/Tokyo",
)

/** Option radio avec titre + description (préférences visuelles). */
@Composable
private fun RadioOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    val colors = DualMusicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = DualMusicTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(modifier = Modifier.padding(start = DualMusicTheme.spacing.sm)) {
            Text(label, color = if (enabled) colors.foreground else colors.mutedForeground, fontWeight = FontWeight.Bold)
            Text(description, color = colors.mutedForeground)
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

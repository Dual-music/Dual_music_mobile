package com.dualmusic.core.ui.components

import java.text.NumberFormat
import java.util.Locale

/**
 * Formatage de la devise interne, aligné sur le web : le nombre (séparateur de milliers,
 * sans décimale inutile) suivi du mot **« Crédit » / « Crédits »** (jamais « cr. » ni « 💎 »).
 *
 * ```
 * formatCredits(1250.0)  // "1 250 Crédits"
 * formatCredits(1.0)     // "1 Crédit"
 * formatCredits(12.5)    // "12,5 Crédits"
 * ```
 *
 * @param credits montant en crédits.
 * @param withUnit ajoute le mot « Crédit(s) » (défaut) ; `false` = nombre seul.
 */
fun formatCredits(credits: Double, withUnit: Boolean = true): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.maximumFractionDigits = if (credits % 1.0 == 0.0) 0 else 2
    val number = nf.format(credits)
    if (!withUnit) return number
    val unit = if (credits > 1.0) "Crédits" else "Crédit"
    return "$number $unit"
}

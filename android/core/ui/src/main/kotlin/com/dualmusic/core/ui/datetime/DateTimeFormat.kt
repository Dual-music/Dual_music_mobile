package com.dualmusic.core.ui.datetime

import com.dualmusic.core.ui.prefs.UiPreferencesStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formate une date/heure ISO dans le fuseau préféré de l'utilisateur — équivalent Android de
 * `formatTz` du web (`src/lib/datetime.ts`). Par défaut le fuseau vient de [UiPreferencesStore]
 * (défaut `GMT`/UTC), configurable dans Préférences.
 *
 * `GMT`/`UTC` : la valeur naïve (sans fuseau) est traitée comme UTC et un suffixe « GMT » est
 * ajouté. Sinon, l'instant (parsé en UTC) est rendu dans le fuseau IANA demandé.
 *
 * @param iso date ISO (`yyyy-MM-ddTHH:mm[:ss][Z|±hh:mm]`) ou vide.
 * @param pattern motif d'affichage (par défaut `dd/MM/yyyy HH:mm`).
 * @param timezone fuseau IANA ou `GMT` ; par défaut la préférence courante.
 */
fun formatTz(
    iso: String?,
    pattern: String = "dd/MM/yyyy HH:mm",
    timezone: String = UiPreferencesStore.current().timezone,
): String {
    if (iso.isNullOrBlank()) return ""
    val date = parseIsoAsUtc(iso) ?: return iso
    val tz = if (timezone == "GMT" || timezone == "UTC") "UTC" else timezone
    val hasTime = pattern.contains('H') || pattern.contains('h')
    val out = SimpleDateFormat(pattern, Locale.getDefault())
    out.timeZone = TimeZone.getTimeZone(tz)
    val label = if (tz == "UTC" && hasTime) " GMT" else ""
    return out.format(date) + label
}

/** Parse une chaîne ISO (avec ou sans secondes/fuseau) comme instant UTC. */
private fun parseIsoAsUtc(iso: String): Date? {
    // Normalise : espace→T, retire fraction/fuseau, garde yyyy-MM-ddTHH:mm[:ss].
    val clean = iso.trim()
        .replace(' ', 'T')
        .substringBefore('.')
        .substringBefore('+')
        .removeSuffix("Z")
        .let { if (it.length > 19) it.take(19) else it }
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")
    for (p in patterns) {
        runCatching {
            val src = SimpleDateFormat(p, Locale.US)
            src.timeZone = TimeZone.getTimeZone("UTC")
            src.isLenient = false
            return src.parse(clean)
        }
    }
    return null
}

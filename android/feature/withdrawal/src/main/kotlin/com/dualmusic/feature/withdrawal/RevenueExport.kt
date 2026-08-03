package com.dualmusic.feature.withdrawal

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dualmusic.domain.wallet.RevenueEvent

/**
 * Export des revenus — parité fonctionnelle avec le web (qui génère tout côté client) :
 *  - CSV : partage du contenu via le sélecteur système (ACTION_SEND).
 *  - PDF : impression système d'un tableau HTML (équivalent du `window.print()` web) →
 *    l'utilisateur choisit « Enregistrer au format PDF ».
 */

/** BOM UTF-8 (aide Excel à reconnaître l'encodage). */
private const val BOM = "﻿"

/** Partage un CSV des revenus. */
fun exportRevenuesCsv(context: Context, events: List<RevenueEvent>, period: String, label: (String) -> String) {
    val sb = StringBuilder()
    sb.append(BOM)
    sb.append("Événement,Source,Crédits,Versements,Dernière date\n")
    events.forEach { e ->
        val src = label(e.sourceType)
        sb.append("\"$src\",\"${e.sourceType}\",${e.totalReceived},${e.txCount},${e.lastAt ?: ""}\n")
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "revenues-$period.csv")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(share, "Export CSV"))
}

/** Génère et imprime un PDF (via WebView + PrintManager) — l'utilisateur enregistre en PDF. */
fun exportRevenuesPdf(context: Context, events: List<RevenueEvent>, totalCredits: Double, label: (String) -> String) {
    val rows = events.joinToString("") { e ->
        "<tr><td>${label(e.sourceType)}</td><td>${e.txCount}</td><td>${e.totalReceived}</td><td>${e.lastAt ?: ""}</td></tr>"
    }
    val html = """
        <html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;padding:16px;color:#111}
        h1{font-size:18px} table{width:100%;border-collapse:collapse;margin-top:12px}
        th,td{border:1px solid #ccc;padding:6px;text-align:left;font-size:12px}
        th{background:#f3f3f3}
        </style></head><body>
        <h1>Mes revenus</h1>
        <p>Total : $totalCredits credits</p>
        <table><thead><tr><th>Evenement</th><th>Versements</th><th>Credits</th><th>Derniere date</th></tr></thead>
        <tbody>$rows</tbody></table>
        </body></html>
    """.trimIndent()

    // La WebView doit survivre jusqu'à la fin de l'impression → référence conservée.
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter("revenues")
            printManager.print("Mes revenus", adapter, PrintAttributes.Builder().build())
            heldWebView = null // libère après lancement de l'impression
        }
    }
    heldWebView = webView
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

/** Garde en vie la WebView d'impression le temps du rendu (sinon GC → impression vide). */
private var heldWebView: WebView? = null

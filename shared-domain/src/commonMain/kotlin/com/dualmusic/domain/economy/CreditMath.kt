package com.dualmusic.domain.economy

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Calculs économiques d'**APERÇU** — affichage uniquement.
 *
 * ⚠️ RÈGLE CRITIQUE : ces fonctions servent à donner un aperçu instantané dans l'UI
 * (ex. « ≈ 5,00 € », « net estimé »). Elles ne font PAS foi. Les montants réels
 * (débits, splits, commissions, crédits portés) sont calculés et validés par le
 * backend via des procédures stockées atomiques. Toujours confirmer via l'API
 * (`/withdrawals/net`, endpoints pricing, solde) avant d'engager de l'argent.
 */
object CreditMath {

    /** Invariant métier : 1 crédit = 0,50 €. (Le backend reste la référence.) */
    const val CREDIT_EUR_VALUE: Double = 0.50

    /**
     * Aperçu de la contre-valeur en euros d'un montant de crédits.
     * @param credits nombre de crédits.
     * @return valeur affichable en euros (non contractuelle).
     */
    fun creditsToEurPreview(credits: Double): Double = credits * CREDIT_EUR_VALUE

    /**
     * Aperçu du nombre de crédits pour un montant en euros (arrondi plancher, comme le
     * backend qui plafonne au crédit entier).
     */
    fun eurToCreditsPreview(eur: Double): Long =
        if (CREDIT_EUR_VALUE <= 0) 0 else floor(eur / CREDIT_EUR_VALUE).roundToLong()

    /**
     * Aperçu du net d'un retrait après frais.
     * @param amountCredits montant brut demandé (crédits).
     * @param feePct pourcentage de frais applicable (obtenu de la config plateforme).
     * @return triple (frais, net, feePct) pour l'affichage — le net réel vient de
     *   `POST /withdrawals/net`.
     */
    fun withdrawalNetPreview(amountCredits: Double, feePct: Double): NetPreview {
        val fee = roundCredits(amountCredits * feePct / 100.0)
        val net = roundCredits(amountCredits - fee)
        return NetPreview(fee = fee, net = net, feePct = feePct)
    }

    /** Arrondi à 2 décimales (les crédits sont manipulés en DECIMAL(18,2) côté backend). */
    private fun roundCredits(value: Double): Double = (value * 100).roundToLong() / 100.0
}

/** Résultat d'un aperçu de net de retrait (affichage). */
data class NetPreview(val fee: Double, val net: Double, val feePct: Double)

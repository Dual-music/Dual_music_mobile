package com.dualmusic.domain.validation

/**
 * Validation d'entrée côté client (aperçu UX seulement).
 *
 * But : feedback immédiat dans les formulaires (activer/désactiver un bouton, message
 * inline). La validation **faisant foi** reste côté backend (Joi). Ces règles reflètent
 * celles du serveur pour éviter des allers-retours inutiles, sans les remplacer.
 */
object Validators {

    /** OTP : exactement 6 chiffres (aligné sur `OTP_LENGTH=6`). */
    fun isValidOtp(code: String): Boolean = code.matches(Regex("^\\d{6}$"))

    /** PIN de retrait : exactement 6 chiffres (aligné sur la validation backend). */
    fun isValidWithdrawalPin(pin: String): Boolean = pin.matches(Regex("^\\d{6}$"))

    /**
     * Téléphone au format E.164 approximatif : `+` suivi de 8 à 15 chiffres.
     * La normalisation stricte (indicatif pays, opérateur) est faite au moment de l'OTP.
     */
    fun isValidPhone(phone: String): Boolean =
        phone.trim().matches(Regex("^\\+?[1-9]\\d{7,14}$"))

    /** Email — vérification légère (le backend valide strictement). */
    fun isValidEmail(email: String): Boolean =
        email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))

    /** Montant positif à 2 décimales max (crédits/euros). */
    fun isValidAmount(amount: Double): Boolean = amount > 0.0 && amount <= 1_000_000

    /** Vrai si le montant demandé est couvert par le solde (aperçu avant appel API). */
    fun canAfford(amount: Double, balance: Double): Boolean = amount > 0.0 && amount <= balance
}

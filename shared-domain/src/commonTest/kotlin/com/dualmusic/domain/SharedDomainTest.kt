package com.dualmusic.domain

import com.dualmusic.domain.economy.CreditMath
import com.dualmusic.domain.model.EventContext
import com.dualmusic.domain.realtime.Realtime
import com.dualmusic.domain.validation.Validators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests du module shared-domain. Sert de patron pour la couverture ≥ 80 % attendue sur
 * domain + data. Purement logique (aucune dépendance plateforme).
 */
class SharedDomainTest {

    @Test
    fun roomName_matches_backend_convention() {
        assertEquals("duel:123", Realtime.roomName(Realtime.RoomType.DUEL, "123"))
        assertEquals(Realtime.RoomType.LIVE, Realtime.RoomType.from(EventContext.LIVE))
    }

    @Test
    fun credits_to_eur_preview_uses_business_invariant() {
        assertEquals(5.0, CreditMath.creditsToEurPreview(10.0)) // 10 crédits × 0,50 €
        assertEquals(20L, CreditMath.eurToCreditsPreview(10.0)) // 10 € / 0,50 €
    }

    @Test
    fun withdrawal_net_preview_deducts_fee() {
        val net = CreditMath.withdrawalNetPreview(amountCredits = 100.0, feePct = 10.0)
        assertEquals(10.0, net.fee)
        assertEquals(90.0, net.net)
    }

    @Test
    fun validators_enforce_backend_rules() {
        assertTrue(Validators.isValidWithdrawalPin("123456"))
        assertFalse(Validators.isValidWithdrawalPin("12ab56"))
        assertTrue(Validators.isValidOtp("000000"))
        assertTrue(Validators.canAfford(amount = 50.0, balance = 100.0))
        assertFalse(Validators.canAfford(amount = 150.0, balance = 100.0))
    }
}

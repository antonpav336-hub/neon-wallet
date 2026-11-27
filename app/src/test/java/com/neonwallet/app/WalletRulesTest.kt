package com.neonwallet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRulesTest {
    @Test fun rejectsZeroAndNegativeAmounts() {
        assertFalse(canSubmitMoneyOperation("topup", 0L, 100L, ""))
        assertFalse(canSubmitMoneyOperation("transfer", -1L, 100L, "Alex"))
    }

    @Test fun transferRequiresRecipientAndAvailableBalance() {
        assertFalse(canSubmitMoneyOperation("transfer", 10L, 100L, "  "))
        assertTrue(canSubmitMoneyOperation("transfer", 10L, 100L, "Alex"))
        assertFalse(canSubmitMoneyOperation("transfer", 101L, 100L, "Alex"))
        assertTrue(canSubmitMoneyOperation("transfer", 100L, 100L, "Alex"))
    }

    @Test fun withdrawalRequiresDestinationAndAvailableBalance() {
        assertFalse(canSubmitMoneyOperation("withdraw", 20L, 10L, "Bank"))
        assertFalse(canSubmitMoneyOperation("withdraw", 10L, 20L, "  "))
        assertTrue(canSubmitMoneyOperation("withdraw", 10L, 20L, "Bank"))
    }

    @Test fun topupRequiresPositiveAmountAndPreventsOverflow() {
        assertTrue(canSubmitMoneyOperation("topup", 1L, 0L, ""))
        assertFalse(canSubmitMoneyOperation("topup", 1L, Long.MAX_VALUE, ""))
        assertTrue(canSubmitMoneyOperation("topup", 1L, Long.MAX_VALUE - 1L, ""))
    }

    @Test fun frozenCardBlocksCardPayments() {
        assertFalse(canSubmitMoneyOperation("card_payment", 10L, 100L, "Merchant", true))
        assertTrue(canSubmitMoneyOperation("card_payment", 10L, 100L, "Merchant", false))
        assertFalse(canUseCard(true))
        assertTrue(canUseCard(false))
    }

    @Test fun duplicateSubmissionIsBlocked() {
        assertFalse(canStartOperation(true))
        assertTrue(canStartOperation(false))
    }

    @Test fun rejectsUnknownOperationAndNegativeBalance() {
        assertFalse(canSubmitMoneyOperation("unknown", 1L, 100L, "Alex"))
        assertFalse(canSubmitMoneyOperation("transfer", 1L, -1L, "Alex"))
    }
}

package com.neonwallet.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRulesTest {
    @Test fun rejectsZeroAndNegativeAmounts() {
        assertFalse(canSubmitMoneyOperation("topup", 0L, 100L, ""))
        assertFalse(canSubmitMoneyOperation("transfer", -1L, 100L, "Alex"))
    }

    @Test fun transferRequiresRecipient() {
        assertFalse(canSubmitMoneyOperation("transfer", 10L, 100L, "  "))
        assertTrue(canSubmitMoneyOperation("transfer", 10L, 100L, "Alex"))
    }

    @Test fun transferCannotExceedBalance() {
        assertFalse(canSubmitMoneyOperation("transfer", 101L, 100L, "Alex"))
        assertTrue(canSubmitMoneyOperation("transfer", 100L, 100L, "Alex"))
    }

    @Test fun topupRequiresPositiveAmountAndKnownOperation() {
        assertTrue(canSubmitMoneyOperation("topup", 1L, 0L, ""))
        assertFalse(canSubmitMoneyOperation("unknown", 1L, 100L, "Alex"))
    }

    @Test fun rejectsBalanceOverflow() {
        assertFalse(canSubmitMoneyOperation("topup", 1L, Long.MAX_VALUE, ""))
        assertTrue(canSubmitMoneyOperation("topup", 1L, Long.MAX_VALUE - 1L, ""))
        assertFalse(canSubmitMoneyOperation("transfer", 1L, -1L, "Alex"))
    }
}

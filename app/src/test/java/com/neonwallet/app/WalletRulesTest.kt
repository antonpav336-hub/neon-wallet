package com.neonwallet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRulesTest {
    @Test fun rejectsZeroAndNegativeAmounts() {
        assertFalse(canSubmitMoneyOperation("topup", 0L, 100L, ""))
        assertFalse(canSubmitMoneyOperation("transfer", -1L, 100L, "Alex"))
        assertEquals("Укажи сумму больше нуля.", operationFailureReason("topup", 0L, 100L, ""))
    }

    @Test fun transferRequiresRecipientAndAvailableBalance() {
        assertFalse(canSubmitMoneyOperation("transfer", 10L, 100L, "  "))
        assertTrue(canSubmitMoneyOperation("transfer", 10L, 100L, "Alex"))
        assertFalse(canSubmitMoneyOperation("transfer", 101L, 100L, "Alex"))
        assertTrue(canSubmitMoneyOperation("transfer", 100L, 100L, "Alex"))
        assertEquals("Недостаточно средств на демо-балансе.", operationFailureReason("transfer", 101L, 100L, "Alex"))
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
        assertEquals("Карта заморожена. Разморозь её перед оплатой.", operationFailureReason("card_payment", 10L, 100L, "Shop", true))
    }

    @Test fun cardLimitIsEnforced() {
        assertTrue(canSubmitMoneyOperation("card_payment", 50L, 100L, "Shop", false, 50L))
        assertFalse(canSubmitMoneyOperation("card_payment", 51L, 100L, "Shop", false, 50L))
        assertFalse(canSubmitMoneyOperation("card_payment", 10L, 100L, "Shop", false, -1L))
        assertEquals("Сумма превышает установленный лимит карты.", operationFailureReason("card_payment", 51L, 100L, "Shop", false, 50L))
    }

    @Test fun duplicateSubmissionIsBlocked() {
        assertFalse(canStartOperation(true))
        assertTrue(canStartOperation(false))
    }

    @Test fun rejectsUnknownOperationAndNegativeBalance() {
        assertFalse(canSubmitMoneyOperation("unknown", 1L, 100L, "Alex"))
        assertFalse(canSubmitMoneyOperation("transfer", 1L, -1L, "Alex"))
    }

    @Test fun requiredRecipientIsExplained() {
        assertEquals("Укажи корректного получателя или назначение.", operationFailureReason("withdraw", 1L, 100L, " "))
        assertFalse(validRecipient(" "))
        assertFalse(validRecipient("A".repeat(81)))
        assertTrue(validRecipient("+79991234567"))
        assertTrue(validRecipient("NEON-ID-2048"))
    }

    @Test fun transferRequiresSupportedDestinationBank() {
        assertTrue(canSubmitMoneyOperation("transfer", 500L, 1000L, "Alex", bank = "ВТБ"))
        assertFalse(canSubmitMoneyOperation("transfer", 500L, 1000L, "Alex", bank = "Unknown Bank"))
        assertEquals("Выбери банк назначения.", operationFailureReason("transfer", 500L, 1000L, "Alex", bank = "Unknown Bank"))
    }

    @Test fun transferLimitIsEnforced() {
        assertTrue(canSubmitMoneyOperation("transfer", 100000L, 150000L, "Alex"))
        assertFalse(canSubmitMoneyOperation("transfer", 100001L, 150000L, "Alex"))
        assertEquals("Сумма превышает лимит перевода.", operationFailureReason("transfer", 100001L, 150000L, "Alex"))
    }
}

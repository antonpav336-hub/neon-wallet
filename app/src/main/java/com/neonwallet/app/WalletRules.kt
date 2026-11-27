package com.neonwallet.app

internal fun canSubmitMoneyOperation(
    kind: String,
    amount: Long,
    balance: Long,
    recipient: String,
    cardFrozen: Boolean = false
): Boolean {
    if (amount <= 0L || balance < 0L) return false
    if (kind == "card_payment" && cardFrozen) return false
    return when (kind) {
        "transfer", "withdraw", "card_payment" -> amount <= balance && recipient.isNotBlank()
        "topup" -> balance <= Long.MAX_VALUE - amount
        else -> false
    }
}

internal fun canUseCard(cardFrozen: Boolean): Boolean = !cardFrozen

internal fun canStartOperation(isSubmitting: Boolean): Boolean = !isSubmitting

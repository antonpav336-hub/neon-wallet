package com.neonwallet.app

internal fun canSubmitMoneyOperation(
    kind: String,
    amount: Long,
    balance: Long,
    recipient: String
): Boolean {
    if (amount <= 0L || balance < 0L) return false
    if (kind == "transfer") return amount <= balance && recipient.isNotBlank()
    if (kind == "topup") return balance <= Long.MAX_VALUE - amount
    return false
}

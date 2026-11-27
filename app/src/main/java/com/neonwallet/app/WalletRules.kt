package com.neonwallet.app

internal fun canSubmitMoneyOperation(
    kind: String,
    amount: Long,
    balance: Long,
    recipient: String,
    cardFrozen: Boolean = false,
    cardLimit: Long = Long.MAX_VALUE
): Boolean {
    if (amount <= 0L || balance < 0L || cardLimit < 0L) return false
    if (kind == "card_payment" && cardFrozen) return false
    return when (kind) {
        "transfer", "withdraw" -> amount <= balance && recipient.isNotBlank()
        "card_payment" -> amount <= balance && amount <= cardLimit && recipient.isNotBlank()
        "topup" -> balance <= Long.MAX_VALUE - amount
        else -> false
    }
}

internal fun operationFailureReason(
    kind: String,
    amount: Long,
    balance: Long,
    recipient: String,
    cardFrozen: Boolean = false,
    cardLimit: Long = Long.MAX_VALUE
): String? = when {
    amount <= 0L -> "Укажи сумму больше нуля."
    balance < 0L -> "Баланс недоступен. Попробуй ещё раз."
    recipient.isBlank() && kind in setOf("transfer", "withdraw", "card_payment") -> "Заполни получателя или назначение."
    kind == "card_payment" && cardFrozen -> "Карта заморожена. Разморозь её перед оплатой."
    amount > balance && kind in setOf("transfer", "withdraw", "card_payment") -> "Недостаточно средств на демо-балансе."
    kind == "card_payment" && amount > cardLimit -> "Сумма превышает установленный лимит карты."
    kind == "topup" && balance > Long.MAX_VALUE - amount -> "Сумма слишком велика."
    kind !in setOf("topup", "transfer", "withdraw", "card_payment") -> "Неизвестный тип операции."
    else -> null
}

internal fun canUseCard(cardFrozen: Boolean): Boolean = !cardFrozen
internal fun canStartOperation(isSubmitting: Boolean): Boolean = !isSubmitting

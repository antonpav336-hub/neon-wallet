package com.neonwallet.app

internal fun canSubmitMoneyOperation(
    kind: String,
    amount: Long,
    balance: Long,
    recipient: String,
    cardFrozen: Boolean = false,
    cardLimit: Long = Long.MAX_VALUE,
    transferLimit: Long = 100000L,
    bank: String = "NEON"
): Boolean {
    if (amount <= 0L || balance < 0L || cardLimit < 0L || transferLimit < 0L) return false
    if (kind == "card_payment" && cardFrozen) return false
    if (kind == "transfer" && !validBank(bank)) return false
    return when (kind) {
        "transfer" -> amount <= balance && amount <= transferLimit && validRecipient(recipient)
        "withdraw" -> amount <= balance && validRecipient(recipient)
        "card_payment" -> amount <= balance && amount <= cardLimit && validRecipient(recipient)
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
    cardLimit: Long = Long.MAX_VALUE,
    transferLimit: Long = 100000L,
    bank: String = "NEON"
): String? = when {
    amount <= 0L -> "Укажи сумму больше нуля."
    balance < 0L -> "Баланс недоступен. Попробуй ещё раз."
    kind == "transfer" && amount > transferLimit -> "Сумма превышает лимит перевода."
    kind == "transfer" && !validBank(bank) -> "Выбери банк назначения."
    !validRecipient(recipient) && kind in setOf("transfer", "withdraw", "card_payment") -> "Укажи корректного получателя или назначение."
    kind == "card_payment" && cardFrozen -> "Карта заморожена. Разморозь её перед оплатой."
    amount > balance && kind in setOf("transfer", "withdraw", "card_payment") -> "Недостаточно средств на демо-балансе."
    kind == "card_payment" && amount > cardLimit -> "Сумма превышает установленный лимит карты."
    kind == "topup" && balance > Long.MAX_VALUE - amount -> "Сумма слишком велика."
    kind !in setOf("topup", "transfer", "withdraw", "card_payment") -> "Неизвестный тип операции."
    else -> null
}

internal fun canUseCard(cardFrozen: Boolean): Boolean = !cardFrozen
internal fun canStartOperation(isSubmitting: Boolean): Boolean = !isSubmitting

internal fun validBank(bank: String): Boolean = bank in setOf("NEON", "ВТБ", "Сбер", "Т-Банк", "Альфа-Банк", "По номеру / ID")

internal fun validRecipient(recipient: String): Boolean =
    recipient.trim().length in 2..80 && recipient.none { it.isISOControl() }

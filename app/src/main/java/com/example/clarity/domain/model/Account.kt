package com.example.clarity.domain.model

import java.util.UUID

enum class AccountError {
    SIGN_IN_REQUIRED, OWNER_CHANGE, NAME_BLANK, NAME_DUPLICATE, FIELD_TOO_LONG,
    CURRENCY_INVALID, DEFAULT_REQUIRED, NOT_FOUND, ALREADY_EXISTS
}

class AccountException(val error: AccountError, message: String? = null) : Exception(message)

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val bankName: String,
    val accountType: String? = null,
    val currency: String = Currencies.supported.first(),
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    val isCashWallet: Boolean
        get() = id.startsWith(CASH_WALLET_PREFIX)

    val isGoalAccount: Boolean
        get() = id.startsWith(GOAL_ACCOUNT_PREFIX) || accountType.equals("GOAL", ignoreCase = true)

    val displayLabel: String
        get() = if (bankName.isBlank() || bankName.equals(name, ignoreCase = true)) name else "$name ($bankName)"

    companion object {
        const val CASH_WALLET_PREFIX = "cash_wallet_"
        const val GOAL_ACCOUNT_PREFIX = "goal_account_"

        fun cashWalletId(userId: String): String = "$CASH_WALLET_PREFIX$userId"

        fun goalAccountId(goalId: String): String = "$GOAL_ACCOUNT_PREFIX$goalId"

        fun goalAccount(
            goalId: String,
            currency: String,
            userId: String = "",
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = createdAt
        ): Account = Account(
            id = goalAccountId(goalId),
            userId = userId,
            name = "Goal Account $goalId".take(100),
            bankName = "Clarity Goals",
            accountType = "GOAL",
            currency = currency,
            isDefault = false,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

        fun defaultCashWallet(currency: String, isDefault: Boolean = false, userId: String = ""): Account = Account(
            id = if (userId.isNotBlank()) cashWalletId(userId) else "$CASH_WALLET_PREFIX${UUID.randomUUID()}",
            name = "Cash Wallet",
            bankName = "Cash",
            accountType = "CASH",
            currency = currency,
            userId = userId,
            isDefault = isDefault
        )
    }
}

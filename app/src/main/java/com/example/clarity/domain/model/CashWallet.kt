package com.example.clarity.domain.model

import java.math.BigInteger

data class WalletBankAccount(
    val id: String, val name: String, val balanceMinor: BigInteger,
    val bankId: String = "other", val lastFour: String = "", val accountType: String = "OTHER",
    val currency: String = "LKR", val isDefault: Boolean = false, val isActive: Boolean = true,
    val needsBankReview: Boolean = true, val openingBalanceMinor: Long = 0
) {
    val bankName: String get() = BankCatalog.find(bankId)?.name ?: "Other Bank / Financial Institution"
}

data class WalletBankAccountInput(
    val id: String, val bankId: String, val nickname: String, val lastFour: String = "",
    val accountType: String = "SAVINGS", val openingBalanceMinor: Long = 0,
    val currency: String = "LKR", val isDefault: Boolean = false
)

data class WalletActivity(
    val id: String,
    val title: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val dateEpochMillis: Long,
    val transactionId: String? = null,
    val kind: String = "CASH",
    val bankAccountId: String? = null
)

data class WalletTopUpOption(
    val bankAccountId: String,
    val enabled: Boolean = false,
    val thresholdMinor: Long = 0,
    val amountMinor: Long = 0
)

data class CashWallet(
    val balanceMinor: BigInteger = BigInteger.ZERO,
    val accounts: List<WalletBankAccount> = emptyList(),
    val activity: List<WalletActivity> = emptyList(),
    val topUpOption: WalletTopUpOption? = null,
    val topUpHistory: List<WalletActivity> = emptyList(),
    val ownerId: String? = null
)

/** Reminders suggest a review; they never authorize a transfer or cover an expense. */
object CashWalletRules {
    fun needsReminder(balance: BigInteger, option: WalletTopUpOption?) =
        option?.enabled == true && balance < BigInteger.valueOf(option.thresholdMinor)
}

class WalletValidationException(message: String) : IllegalArgumentException(message)
fun walletRequire(condition: Boolean, message: String) {
    if (!condition) throw WalletValidationException(message)
}

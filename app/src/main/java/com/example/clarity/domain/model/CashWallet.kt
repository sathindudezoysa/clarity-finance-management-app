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
    val amountMinor: Long = 0,
    val automaticExecution: Boolean = false
)

data class CashWallet(
    val balanceMinor: BigInteger = BigInteger.ZERO,
    val accounts: List<WalletBankAccount> = emptyList(),
    val activity: List<WalletActivity> = emptyList(),
    val topUpOption: WalletTopUpOption? = null,
    val topUpHistory: List<WalletActivity> = emptyList(),
    val ownerId: String? = null
)

/** Automatic execution requires a separately saved opt-in; legacy settings remain reminders. */
object CashWalletRules {
    fun needsReminder(balance: BigInteger, option: WalletTopUpOption?) =
        option?.enabled == true && !option.automaticExecution && balance < BigInteger.valueOf(option.thresholdMinor)

    fun needsAutomaticTopUp(balance: BigInteger, option: WalletTopUpOption?) =
        option?.enabled == true && option.automaticExecution && balance < BigInteger.valueOf(option.thresholdMinor)

    fun automaticTopUpAmount(balance: BigInteger, option: WalletTopUpOption?, bankBalance: BigInteger?, bankActive: Boolean): Long? {
        if (!needsAutomaticTopUp(balance, option)) return null
        val amount = requireNotNull(option).amountMinor
        walletRequire(amount > 0, "Choose an automatic top-up amount greater than zero.")
        walletRequire(bankActive && bankBalance != null, "The automatic top-up account is unavailable. Choose another account or turn off automatic top-ups.")
        val transfer = BigInteger.valueOf(amount)
        walletRequire(requireNotNull(bankBalance) >= transfer, "Your bank balance is not enough for the automatic top-up. Reduce the amount or change your top-up settings.")
        val after = balance + transfer
        walletRequire(after >= BigInteger.ZERO, "Your cash balance is not enough. Add cash or reduce the amount.")
        walletRequire(after <= BigInteger.valueOf(Long.MAX_VALUE), "The wallet balance is too large. Use a smaller amount.")
        return amount
    }
}

class WalletValidationException(message: String) : IllegalArgumentException(message)
fun walletRequire(condition: Boolean, message: String) {
    if (!condition) throw WalletValidationException(message)
}

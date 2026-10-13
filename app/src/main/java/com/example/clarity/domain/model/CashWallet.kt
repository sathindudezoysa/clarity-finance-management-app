package com.example.clarity.domain.model

import java.math.BigInteger

data class WalletBankAccount(val id: String, val name: String, val balanceMinor: BigInteger)

data class WalletActivity(
    val id: String,
    val title: String,
    val amountMinor: Long,
    val isCredit: Boolean,
    val dateEpochMillis: Long,
    val transactionId: String? = null
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
    val topUpOption: WalletTopUpOption? = null
)

/** Integer arithmetic avoids rounding and overflow in accumulated balances. */
object CashWalletRules {
    fun topUpNeeded(balance: BigInteger, expenseMinor: Long, option: WalletTopUpOption?): Long {
        require(expenseMinor > 0) { "Amount must be greater than zero" }
        val remaining = balance - BigInteger.valueOf(expenseMinor)
        if (option?.enabled != true) return 0
        require(option.thresholdMinor >= 0 && option.amountMinor > 0) { "Invalid automatic top-up" }
        if (remaining >= BigInteger.valueOf(option.thresholdMinor)) return 0
        // A single withdrawal per cash save. Larger purchases must be topped up explicitly.
        require(remaining + BigInteger.valueOf(option.amountMinor) >= BigInteger.ZERO) {
            "Automatic top-up is too small for this expense. Top up the wallet first."
        }
        return option.amountMinor
    }
}

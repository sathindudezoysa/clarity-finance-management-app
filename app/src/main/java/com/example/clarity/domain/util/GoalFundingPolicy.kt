package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigInteger

enum class GoalFundingLimitFactor {
    CASH_WALLET,
    NET_WORTH,
    BOTH
}

data class GoalFundingAvailability(
    val currency: String,
    val cashWallet: Account?,
    val cashBalanceMinor: BigInteger,
    val netWorthMinor: BigInteger,
    val availableToContributeMinor: BigInteger,
    val limitingFactor: GoalFundingLimitFactor
) {
    fun canAfford(amountMinor: Long): Boolean =
        amountMinor > 0L && BigInteger.valueOf(amountMinor) <= availableToContributeMinor
}

object GoalFundingPolicy {

    fun filterVisibleAccounts(accounts: List<Account>): List<Account> =
        accounts.filterNot { it.isGoalAccount }

    fun resolveTransactionMinor(
        tx: Transaction,
        targetCurrency: String
    ): BigInteger? {
        if (tx.amountMinor <= 0L) return BigInteger.ZERO
        if (tx.currency.equals(targetCurrency, ignoreCase = true)) {
            return BigInteger.valueOf(tx.amountMinor)
        }
        val snapshot = tx.conversion
        if (tx.targetCurrency.equals(targetCurrency, ignoreCase = true) &&
            !tx.pendingConversion &&
            snapshot != null &&
            snapshot.amountMinor > 0L
        ) {
            return BigInteger.valueOf(snapshot.amountMinor)
        }
        return null
    }

    fun evaluate(
        transactions: List<Transaction>,
        accounts: List<Account>,
        currency: String
    ): GoalFundingAvailability {
        val cashAccount = accounts.firstOrNull { it.isCashWallet }
        var cashBalanceMinor = BigInteger.ZERO
        var netWorthMinor = BigInteger.ZERO

        for (tx in transactions) {
            val isCashTx = (cashAccount != null && tx.accountId == cashAccount.id) || tx.isCash
            val resolved = resolveTransactionMinor(tx, currency)

            if (tx.type == TransactionType.INCOME) {
                val amount = resolved ?: BigInteger.ZERO
                netWorthMinor = netWorthMinor.add(amount)
                if (isCashTx) {
                    cashBalanceMinor = cashBalanceMinor.add(amount)
                }
            } else {
                val amount = resolved ?: BigInteger.ZERO
                netWorthMinor = netWorthMinor.subtract(amount)
                if (isCashTx) {
                    cashBalanceMinor = cashBalanceMinor.subtract(amount)
                }
            }
        }

        val minRaw = minOf(cashBalanceMinor, netWorthMinor)
        val availableMinor = minRaw.coerceAtLeast(BigInteger.ZERO)
        val limitingFactor = when {
            cashBalanceMinor < netWorthMinor -> GoalFundingLimitFactor.CASH_WALLET
            netWorthMinor < cashBalanceMinor -> GoalFundingLimitFactor.NET_WORTH
            else -> GoalFundingLimitFactor.BOTH
        }

        return GoalFundingAvailability(
            currency = currency,
            cashWallet = cashAccount,
            cashBalanceMinor = cashBalanceMinor,
            netWorthMinor = netWorthMinor,
            availableToContributeMinor = availableMinor,
            limitingFactor = limitingFactor
        )
    }
}


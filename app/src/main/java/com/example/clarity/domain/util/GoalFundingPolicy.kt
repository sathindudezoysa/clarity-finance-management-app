package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigInteger

enum class GoalFundingLimitFactor {
    ACCOUNT_BALANCE,
    NET_WORTH,
    BOTH
}

data class GoalFundingAccountOption(
    val account: Account,
    val balanceMinor: BigInteger
)

data class GoalFundingAvailability(
    val currency: String,
    val matchingCurrencyAccounts: List<Account> = emptyList(),
    val eligibleAccounts: List<GoalFundingAccountOption> = emptyList(),
    val selectedAccount: Account? = null,
    val accountBalanceMinor: BigInteger = BigInteger.ZERO,
    val netWorthMinor: BigInteger = BigInteger.ZERO,
    val availableToContributeMinor: BigInteger = BigInteger.ZERO,
    val limitingFactor: GoalFundingLimitFactor = GoalFundingLimitFactor.BOTH
) {
    fun canAfford(amountMinor: Long): Boolean =
        selectedAccount != null &&
            amountMinor > 0L &&
            BigInteger.valueOf(amountMinor) <= availableToContributeMinor
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

    fun accountBalanceMinor(
        account: Account,
        transactions: List<Transaction>,
        currency: String = account.currency
    ): BigInteger {
        var balanceMinor = BigInteger.ZERO
        for (tx in transactions) {
            val belongsToAccount = if (account.isCashWallet) {
                tx.accountId == account.id || tx.isCash
            } else {
                tx.accountId == account.id
            }
            if (!belongsToAccount) continue
            val amount = resolveTransactionMinor(tx, currency) ?: BigInteger.ZERO
            balanceMinor = if (tx.type == TransactionType.INCOME) {
                balanceMinor.add(amount)
            } else {
                balanceMinor.subtract(amount)
            }
        }
        return balanceMinor
    }

    fun filterEligibleFundingAccounts(
        accounts: List<Account>,
        transactions: List<Transaction>,
        goalCurrency: String
    ): List<GoalFundingAccountOption> {
        return filterVisibleAccounts(accounts)
            .filter { it.currency.equals(goalCurrency, ignoreCase = true) }
            .mapNotNull { account ->
                val balanceMinor = accountBalanceMinor(account, transactions, goalCurrency)
                if (balanceMinor > BigInteger.ZERO) {
                    GoalFundingAccountOption(account = account, balanceMinor = balanceMinor)
                } else {
                    null
                }
            }
    }

    fun evaluate(
        transactions: List<Transaction>,
        accounts: List<Account>,
        currency: String,
        selectedAccountId: String? = null
    ): GoalFundingAvailability {
        val visibleAccounts = filterVisibleAccounts(accounts)
        val matchingCurrencyAccounts = visibleAccounts.filter {
            it.currency.equals(currency, ignoreCase = true)
        }
        val balancesById = matchingCurrencyAccounts.associate { account ->
            account.id to accountBalanceMinor(account, transactions, currency)
        }
        val eligibleAccounts = matchingCurrencyAccounts.mapNotNull { account ->
            val balance = balancesById[account.id] ?: BigInteger.ZERO
            if (balance > BigInteger.ZERO) {
                GoalFundingAccountOption(account = account, balanceMinor = balance)
            } else {
                null
            }
        }

        val selectedAccount = if (!selectedAccountId.isNullOrBlank()) {
            matchingCurrencyAccounts.firstOrNull { it.id == selectedAccountId }
        } else {
            eligibleAccounts.firstOrNull { it.account.isDefault }?.account
                ?: eligibleAccounts.firstOrNull()?.account
        }

        val selectedBalanceMinor = if (selectedAccount != null) {
            balancesById[selectedAccount.id] ?: BigInteger.ZERO
        } else {
            matchingCurrencyAccounts.firstOrNull()?.let { balancesById[it.id] } ?: BigInteger.ZERO
        }

        var netWorthMinor = BigInteger.ZERO
        for (tx in transactions) {
            val amount = resolveTransactionMinor(tx, currency) ?: BigInteger.ZERO
            netWorthMinor = if (tx.type == TransactionType.INCOME) {
                netWorthMinor.add(amount)
            } else {
                netWorthMinor.subtract(amount)
            }
        }

        val minRaw = minOf(selectedBalanceMinor, netWorthMinor)
        val availableMinor = if (selectedAccount != null) {
            minRaw.coerceAtLeast(BigInteger.ZERO)
        } else {
            BigInteger.ZERO
        }
        val limitingFactor = when {
            selectedBalanceMinor < netWorthMinor -> GoalFundingLimitFactor.ACCOUNT_BALANCE
            netWorthMinor < selectedBalanceMinor -> GoalFundingLimitFactor.NET_WORTH
            else -> GoalFundingLimitFactor.BOTH
        }

        return GoalFundingAvailability(
            currency = currency,
            matchingCurrencyAccounts = matchingCurrencyAccounts,
            eligibleAccounts = eligibleAccounts,
            selectedAccount = selectedAccount,
            accountBalanceMinor = selectedBalanceMinor,
            netWorthMinor = netWorthMinor,
            availableToContributeMinor = availableMinor,
            limitingFactor = limitingFactor
        )
    }
}

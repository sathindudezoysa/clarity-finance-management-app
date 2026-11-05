package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object AccountBalancePolicy {

    suspend fun calculateBalanceMinor(
        account: Account,
        transactions: List<Transaction>,
        resolveRate: (suspend (sourceCurrency: String, targetCurrency: String) -> BigDecimal?)? = null
    ): BigInteger {
        val safeCurrency = account.currency.ifBlank { Currencies.supported.first() }
        val accountTx = transactions.filter { it.accountId == account.id }
        var balance = BigInteger.ZERO
        for (tx in accountTx) {
            val amount = resolveAmountMinor(tx, safeCurrency, resolveRate)
            if (tx.type == TransactionType.INCOME) {
                balance += amount
            } else {
                balance -= amount
            }
        }
        return balance
    }

    fun calculateBalanceMinorSync(
        account: Account,
        transactions: List<Transaction>
    ): BigInteger {
        val safeCurrency = account.currency.ifBlank { Currencies.supported.first() }
        val accountTx = transactions.filter { it.accountId == account.id }
        var balance = BigInteger.ZERO
        for (tx in accountTx) {
            val amount = resolveAmountMinorSync(tx, safeCurrency)
            if (tx.type == TransactionType.INCOME) {
                balance += amount
            } else {
                balance -= amount
            }
        }
        return balance
    }

    fun formatAccountBalance(
        account: Account,
        balanceMinor: BigInteger
    ): String {
        val safeCurrency = account.currency.ifBlank { Currencies.supported.first() }
        return if (balanceMinor < BigInteger.ZERO) {
            "-${safeCurrency} ${MoneyUtils.formatMinor(balanceMinor.abs(), safeCurrency)}"
        } else {
            "${safeCurrency} ${MoneyUtils.formatMinor(balanceMinor, safeCurrency)}"
        }
    }

    private suspend fun resolveAmountMinor(
        tx: Transaction,
        targetCurrency: String,
        resolveRate: (suspend (sourceCurrency: String, targetCurrency: String) -> BigDecimal?)?
    ): BigInteger {
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
        val rate = resolveRate?.invoke(tx.currency, targetCurrency)
        if (rate != null) {
            return runCatching {
                BigDecimal(BigInteger.valueOf(tx.amountMinor), Currencies.digits(tx.currency))
                    .multiply(rate)
                    .movePointRight(Currencies.digits(targetCurrency))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toBigInteger()
            }.getOrDefault(BigInteger.valueOf(tx.amountMinor))
        }
        return BigInteger.valueOf(tx.amountMinor)
    }

    private fun resolveAmountMinorSync(
        tx: Transaction,
        targetCurrency: String
    ): BigInteger {
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
        return BigInteger.valueOf(tx.amountMinor)
    }
}

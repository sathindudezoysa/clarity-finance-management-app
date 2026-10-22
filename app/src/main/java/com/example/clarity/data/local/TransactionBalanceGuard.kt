package com.example.clarity.data.local

import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import java.math.BigInteger

internal object TransactionBalanceGuard {
    fun check(
        before: List<TransactionEntity>,
        after: List<TransactionEntity>,
        previousAccountId: String?,
        newAccountId: String?,
        reservedByAccount: Map<String, BigInteger> = emptyMap()
    ) {
        val beforeTotal = netMinor(before)
        val afterTotal = netMinor(after)
        if (afterTotal < BigInteger.ZERO && afterTotal < beforeTotal) {
            throw TransactionException(TransactionError.INSUFFICIENT_BALANCE, "Insufficient net worth balance")
        }
        listOfNotNull(previousAccountId, newAccountId).distinct().forEach { accountId ->
            val reserved = reservedByAccount[accountId]?.max(BigInteger.ZERO) ?: BigInteger.ZERO
            val beforeAccount = netMinor(before.filter { it.accountId == accountId }) - reserved
            val afterAccount = netMinor(after.filter { it.accountId == accountId }) - reserved
            if (afterAccount < BigInteger.ZERO && afterAccount < beforeAccount) {
                throw TransactionException(TransactionError.INSUFFICIENT_BALANCE, "Insufficient account balance")
            }
        }
    }

    fun netMinor(transactions: List<TransactionEntity>): BigInteger {
        var total = BigInteger.ZERO
        for (tx in transactions) {
            val converted = tx.conversion?.amountMinor ?: continue
            val amount = BigInteger.valueOf(converted)
            if (tx.type == "INCOME") total += amount else total -= amount
        }
        return total
    }

    fun hasConsistentCurrencyUnits(
        accountCurrency: String,
        accountId: String,
        transactions: List<TransactionEntity>
    ): Boolean {
        if (accountCurrency !in Currencies.supported) return false
        for (tx in transactions) {
            if (tx.accountId != accountId) continue
            if (tx.pendingConversion || tx.conversion == null || tx.targetCurrency != accountCurrency) {
                return false
            }
        }
        return true
    }

    fun availableMinorForAccount(
        accountId: String,
        transactions: List<TransactionEntity>,
        reservedMinor: BigInteger
    ): BigInteger = netMinor(transactions.filter { it.accountId == accountId }) - reservedMinor.max(BigInteger.ZERO)
}

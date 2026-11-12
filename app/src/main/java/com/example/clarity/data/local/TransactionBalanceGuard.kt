package com.example.clarity.data.local

import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import java.math.BigInteger

internal object TransactionBalanceGuard {
    fun check(
        before: List<TransactionEntity>,
        after: List<TransactionEntity>,
        previousAccountId: String?,
        newAccountId: String?
    ) {
        val beforeTotal = netMinor(before)
        val afterTotal = netMinor(after)
        if (afterTotal < BigInteger.ZERO && afterTotal < beforeTotal) {
            throw TransactionException(TransactionError.INSUFFICIENT_BALANCE, "Insufficient net worth balance")
        }
        listOfNotNull(previousAccountId, newAccountId).distinct().forEach { accountId ->
            val beforeForAccount = before.filter { it.accountId == accountId }
            val afterForAccount = after.filter { it.accountId == accountId }
            val accountCurrency = (beforeForAccount.firstOrNull() ?: afterForAccount.firstOrNull())?.currency
                ?: return@forEach
            val beforeAccount = accountNetMinor(beforeForAccount, accountCurrency)
            val afterAccount = accountNetMinor(afterForAccount, accountCurrency)
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

    private fun accountNetMinor(transactions: List<TransactionEntity>, currency: String): BigInteger {
        var total = BigInteger.ZERO
        for (tx in transactions) {
            if (!tx.currency.equals(currency, ignoreCase = true)) continue
            val amount = BigInteger.valueOf(tx.amountMinor)
            if (tx.type == "INCOME") total += amount else total -= amount
        }
        return total
    }
}



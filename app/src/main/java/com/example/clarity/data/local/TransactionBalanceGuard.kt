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
            val beforeAccount = netMinor(before.filter { it.accountId == accountId })
            val afterAccount = netMinor(after.filter { it.accountId == accountId })
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
}



package com.example.clarity.presentation.transactions.list

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigInteger

data class TransactionListState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val error: String? = null,
    val accountNames: Map<String, String> = emptyMap()
) {
    val pendingCount: Int get() = transactions.count { it.pendingConversion }
    val totalsByCurrency: Map<String, CurrencyTotals> get() = transactions.filter { !it.pendingConversion }
        .groupBy { it.targetCurrency }.mapValues { (_, rows) ->
            fun sum(type: TransactionType) = rows.filter { it.type == type }.fold(BigInteger.ZERO) { total, t ->
                total + BigInteger.valueOf(checkNotNull(t.conversion).amountMinor)
            }
            CurrencyTotals(sum(TransactionType.INCOME), sum(TransactionType.EXPENSE))
        }
    val incomeMinor: BigInteger get() = total(TransactionType.INCOME)
    val expenseMinor: BigInteger get() = total(TransactionType.EXPENSE)
    val netMinor: BigInteger get() = incomeMinor - expenseMinor

    private fun total(type: TransactionType): BigInteger = transactions
        .filter { it.type == type && it.targetCurrency == "LKR" && !it.pendingConversion }.fold(BigInteger.ZERO) { total, transaction ->
            total + BigInteger.valueOf(checkNotNull(transaction.conversion).amountMinor)
        }
}

data class CurrencyTotals(val income: BigInteger, val expense: BigInteger) {
    val net: BigInteger get() = income - expense
}

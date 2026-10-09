package com.example.clarity.presentation.transactions.list

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigInteger

data class TransactionListState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val error: Int? = null
) {
    val incomeMinor: BigInteger get() = total(TransactionType.INCOME)
    val expenseMinor: BigInteger get() = total(TransactionType.EXPENSE)
    val netMinor: BigInteger get() = incomeMinor - expenseMinor

    private fun total(type: TransactionType): BigInteger = transactions
        .filter { it.type == type }.fold(BigInteger.ZERO) { total, transaction ->
            total + BigInteger.valueOf(transaction.amountMinor)
        }
}

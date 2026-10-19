package com.example.clarity.presentation.budgets

import com.example.clarity.R
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.domain.model.Currencies
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat

fun budgetMoney(amount: BigInteger, currency: String): String = "$currency " + NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = Currencies.digits(currency)
    maximumFractionDigits = Currencies.digits(currency)
}.format(BigDecimal(amount, Currencies.digits(currency)))

fun budgetStatusResource(threshold: BudgetThreshold, incomplete: Boolean = false): Int = when (threshold) {
    BudgetThreshold.NORMAL -> if (incomplete) R.string.budget_incomplete_status else R.string.budget_normal
    BudgetThreshold.NEARING -> R.string.budget_nearing
    BudgetThreshold.REACHED -> R.string.budget_reached
    BudgetThreshold.EXCEEDED -> R.string.budget_exceeded
}
fun budgetFeedbackResource(threshold: BudgetThreshold): Int = when (threshold) {
    BudgetThreshold.NEARING -> R.string.budget_feedback_nearing
    BudgetThreshold.REACHED -> R.string.budget_feedback_reached
    else -> R.string.budget_feedback_exceeded
}

package com.example.clarity.presentation.budgets

import com.example.clarity.domain.model.BudgetSummary
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.TransactionTags
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat

fun budgetMoney(amount: BigInteger, currency: String): String = "$currency " + NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = Currencies.digits(currency)
    maximumFractionDigits = Currencies.digits(currency)
}.format(BigDecimal(amount, Currencies.digits(currency)))

fun budgetStatusText(threshold: BudgetThreshold, incomplete: Boolean = false): String = when (threshold) {
    BudgetThreshold.NORMAL -> if (incomplete) "Spending incomplete — status may increase" else "Within budget"
    BudgetThreshold.NEARING -> "Nearing budget limit"
    BudgetThreshold.REACHED -> "Budget reached"
    BudgetThreshold.EXCEEDED -> "Budget exceeded"
}
fun budgetStatusBadgeText(threshold: BudgetThreshold, incomplete: Boolean = false): String {
    val glyph = when (threshold) {
        BudgetThreshold.NORMAL -> if (incomplete) "⚠" else "✓"
        BudgetThreshold.NEARING -> "⚠"
        BudgetThreshold.REACHED -> "●"
        BudgetThreshold.EXCEEDED -> "▲"
    }
    return "$glyph ${budgetStatusText(threshold, incomplete)}"
}
fun budgetCategoryIcon(category: String): String = when (TransactionTags.key(category)) {
    "food" -> "🍽"
    "groceries" -> "🛒"
    "travel", "transport" -> "🚌"
    "savings" -> "💰"
    "general" -> "🏷"
    "utilities", "bills" -> "💡"
    "health" -> "🩺"
    "entertainment" -> "🎬"
    "shopping" -> "🛍"
    "education" -> "📚"
    else -> "📌"
}
fun budgetFeedbackText(threshold: BudgetThreshold, category: String): String = when (threshold) {
    BudgetThreshold.NEARING -> "You’re close to your $category budget. A small adjustment now can help you stay on track."
    BudgetThreshold.REACHED -> "You’ve reached your $category budget. Review your next expenses and make a fresh plan."
    else -> "You’ve passed this month’s $category budget. Review your next expenses and make a fresh plan."
}
fun budgetCategoryMonthText(category: String, month: String): String = "$category · $month"
fun budgetMonthLabel(month: String): String = BudgetSummary.formatMonth(month)
fun budgetSpendingText(spent: String, limit: String): String = "Counted $spent of $limit"
fun budgetRemainingText(amount: String): String = "Unspent from counted total: $amount"
fun budgetExceededByText(amount: String): String = "Exceeded by $amount"
fun budgetIncompleteText(uncounted: Int): String =
    String.format("%d expense(s) are uncounted because their currency is not yet comparable. This total is incomplete.", uncounted)
fun budgetDisableConfirmText(category: String, month: String): String =
    "Are you sure you want to disable the $category budget for ${budgetMonthLabel(month)}?"
fun budgetSummaryTitleText(currency: String, incomplete: Boolean): String =
    if (incomplete) "Monthly Summary ($currency) · Incomplete" else "Monthly Summary ($currency)"

object BudgetMessages {
    const val SCREEN_TITLE = "My Budgets"
    const val EMPTY_MONTH = "No budgets for this month. Create your first budget to start tracking spending."
    const val CATEGORY_REQUIRED = BudgetSummary.CATEGORY_REQUIRED
    const val AMOUNT_INVALID = BudgetSummary.AMOUNT_INVALID
    const val DUPLICATE_BUDGET = BudgetSummary.DUPLICATE_BUDGET
    const val DISABLE_TITLE = "Disable Budget?"
    const val SAVED_NOTICE = "Budget saved"
    const val UPDATED_NOTICE = "Budget updated"
    const val DISABLED_NOTICE = "Budget disabled"
    const val REENABLED_NOTICE = "Budget re-enabled"
    const val RESOLVED_NOTICE = "Uncounted currencies resolved"
    const val VALIDATION = "Enter a valid month and a positive amount with the currency’s exact decimal places. If this category and month already have a budget, edit or re-enable that budget."
    const val SAVE_ERROR = "Could not save the budget. Check your session and try again."
    const val LOAD_ERROR = "Could not load budgets. Reopen this screen to retry."
    const val CONVERSION_ERROR = "Some currency conversions are unavailable. Counted spending is unchanged; reconnect and retry."
}

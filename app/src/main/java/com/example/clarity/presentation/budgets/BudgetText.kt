package com.example.clarity.presentation.budgets

import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.domain.model.Currencies
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat

fun budgetMoney(amount: BigInteger, currency: String): String = "$currency " + NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = Currencies.digits(currency)
    maximumFractionDigits = Currencies.digits(currency)
}.format(BigDecimal(amount, Currencies.digits(currency)))

fun budgetStatusText(threshold: BudgetThreshold, incomplete: Boolean = false): String = when (threshold) {
    BudgetThreshold.NORMAL -> if (incomplete) "Spending incomplete — status may increase" else "Below warning threshold"
    BudgetThreshold.NEARING -> "Nearing budget limit"
    BudgetThreshold.REACHED -> "Budget reached"
    BudgetThreshold.EXCEEDED -> "Budget exceeded"
}
fun budgetFeedbackText(threshold: BudgetThreshold, category: String): String = when (threshold) {
    BudgetThreshold.NEARING -> "You’re close to your $category budget. A small adjustment now can help you stay on track."
    BudgetThreshold.REACHED -> "You’ve reached your $category budget. Review your next expenses and make a fresh plan."
    else -> "You’ve passed this month’s $category budget. Review your next expenses and make a fresh plan."
}
fun budgetCategoryMonthText(category: String, month: String): String = "$category · $month"
fun budgetSpendingText(spent: String, limit: String): String = "Counted $spent of $limit"
fun budgetRemainingText(amount: String): String = "Unspent from counted total: $amount"
fun budgetExceededByText(amount: String): String = "Exceeded by $amount"
fun budgetIncompleteText(uncounted: Int): String =
    String.format("%d expense(s) are uncounted because their currency is not yet comparable. This total is incomplete.", uncounted)

object BudgetMessages {
    const val VALIDATION = "Enter a valid month and a positive amount with the currency’s exact decimal places. If this category and month already have a budget, edit or re-enable that budget."
    const val SAVE_ERROR = "Could not save the budget. Check your session and try again."
    const val LOAD_ERROR = "Could not load budgets. Reopen this screen to retry."
    const val CONVERSION_ERROR = "Some currency conversions are unavailable. Counted spending is unchanged; reconnect and retry."
}

fun budgetLimitLabel(currency: String): String = "Limit ($currency)"

/** Screen-only labels for the budgets UI; wording of pre-existing labels is unchanged. */
object BudgetUiText {
    const val TITLE = "Budgets"
    const val BACK = "Back"
    const val CREATE = "Create monthly budget"
    const val EDIT_TITLE = "Edit budget"
    const val MONTH_POLICY = "Calendar months use your device’s current timezone, matching transaction dates. Changing timezone can move spending between months. Existing budgets keep their currency."
    const val SIGN_IN = "Sign in to manage your budgets."
    const val SYNC_ERROR = "Cloud sync is unavailable. Local changes are saved and will retry automatically."
    const val EMPTY_TITLE = "No budgets yet"
    const val EMPTY = "Create a category budget to follow your monthly spending."
    const val LOADING = "Loading budgets"
    const val NOTIFICATION_TITLE = "Budget notifications"
    const val NOTIFICATION_HELP = "Optional local notifications tell you when a current monthly budget crosses a threshold. Budget warnings remain available here if notifications are off."
    const val NOTIFICATION_ACTION = "Enable or manage budget notifications"
    const val RATIONALE_TITLE = "Budget update"
    const val CONTINUE = "Continue"
    const val CANCEL = "Cancel"
    const val SAVE = "Save"
    const val MONTH_LABEL = "Month (YYYY-MM)"
    const val RETRY = "Resolve uncounted currencies"
    const val PENDING_SYNC = "Saved on this device · Waiting to sync"
    const val EDIT = "Edit budget"
    const val DISABLE = "Disable"
    const val DISABLED_SECTION = "Disabled budgets"
    const val DISABLED_HINT = "Disabled — tap to enable"
    const val DASHBOARD_OPEN = "View all"
}

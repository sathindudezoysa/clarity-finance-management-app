package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.util.MoneyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GoalCardUiModel(
    val id: String,
    val name: String,
    val description: String,
    val currency: String,
    val targetFormatted: String,
    val targetDateFormatted: String,
    val daysRemainingLabel: String,
    val isOverdue: Boolean,
    val isSyncing: Boolean
)

data class GoalsState(
    val isLoading: Boolean = true,
    val ownerId: String? = null,
    val goals: List<GoalCardUiModel> = emptyList(),
    val error: String? = null
) {
    val isEmpty: Boolean
        get() = goals.isEmpty()
}

object GoalMessages {
    const val EMPTY_GOALS = "No goals yet. Create your first financial goal to start tracking your savings."
    const val SIGN_IN = "Please sign in to view and manage your financial goals."
    const val LOAD_ERROR = "Could not load your financial goals. Tap Retry to try again."
    const val NOT_FOUND = "Financial goal not found."
    const val OPEN_ERROR = "Could not open this financial goal. Please go back and try again."
    const val SAVE_ERROR = "Could not save the financial goal. Please check your inputs and try again."
    const val NAME_BLANK = "Enter a goal name."
    const val NAME_TOO_LONG = "Goal name must be 100 characters or fewer."
    const val DESCRIPTION_TOO_LONG = "Description must be 500 characters or fewer."
    const val INVALID_CURRENCY = "Unsupported currency code."
    const val INVALID_AMOUNT = "Enter a valid unsigned numeric amount matching the currency precision."
    const val TARGET_NON_POSITIVE = "Target amount must be greater than zero."
    const val INVALID_DATE = "Select a valid target date."
    const val DATE_NOT_IN_FUTURE = "Target date must be in the future."
}

fun formatGoalMoney(amountMinor: Long, currency: String): String {
    val safeCurrency = if (currency in Currencies.supported) currency else Currencies.supported.first()
    return "$safeCurrency ${MoneyUtils.formatMinor(amountMinor, safeCurrency)}"
}

private val GOAL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

fun formatGoalEpochDay(epochDay: Long): String =
    runCatching { LocalDate.ofEpochDay(epochDay).format(GOAL_DATE_FORMATTER) }
        .getOrElse { epochDay.toString() }

fun goalErrorMessage(error: Throwable, fallback: String): String {
    val goalEx = error as? GoalException ?: return fallback
    return when (goalEx.error) {
        GoalError.SIGN_IN_REQUIRED -> GoalMessages.SIGN_IN
        GoalError.NOT_FOUND -> GoalMessages.NOT_FOUND
        GoalError.INVALID_ID -> fallback
        GoalError.NAME_BLANK -> GoalMessages.NAME_BLANK
        GoalError.NAME_TOO_LONG -> GoalMessages.NAME_TOO_LONG
        GoalError.DESCRIPTION_TOO_LONG -> GoalMessages.DESCRIPTION_TOO_LONG
        GoalError.INVALID_CURRENCY -> GoalMessages.INVALID_CURRENCY
        GoalError.INVALID_AMOUNT -> GoalMessages.INVALID_AMOUNT
        GoalError.TARGET_NON_POSITIVE -> GoalMessages.TARGET_NON_POSITIVE
        GoalError.INVALID_DATE -> GoalMessages.INVALID_DATE
        GoalError.DATE_NOT_IN_FUTURE -> GoalMessages.DATE_NOT_IN_FUTURE
        GoalError.IMMUTABLE_FIELD_CHANGED -> fallback
    }
}

fun FinancialGoal.toCardUiModel(todayEpochDay: Long): GoalCardUiModel {
    val overdue = isOverdue(todayEpochDay)
    val daysLeft = daysRemaining(todayEpochDay)
    val daysLabel = if (overdue) {
        "Overdue"
    } else {
        "$daysLeft days left"
    }
    return GoalCardUiModel(
        id = id,
        name = name,
        description = description,
        currency = currency,
        targetFormatted = formatGoalMoney(targetMinor, currency),
        targetDateFormatted = formatGoalEpochDay(targetDateEpochDay),
        daysRemainingLabel = daysLabel,
        isOverdue = overdue,
        isSyncing = pendingSync
    )
}

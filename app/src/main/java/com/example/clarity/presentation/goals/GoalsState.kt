package com.example.clarity.presentation.goals

import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.util.GoalMilestoneThreshold
import com.example.clarity.domain.util.GoalProgressPolicy
import com.example.clarity.domain.util.GoalStreakSummary
import com.example.clarity.domain.util.MoneyUtils
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
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
    val isSyncing: Boolean,
    val isActive: Boolean = false,
    val monthlyRateFormatted: String? = null,
    val progressFraction: Float = 0f,
    val savedFormatted: String = formatGoalMoney(0L, currency),
    val percentLabel: String = "0% saved",
    val percentValueLabel: String = "0%",
    val timelineRemainingLabel: String = daysRemainingLabel,
    val nextMilestoneLabel: String = "Next milestone: 25%",
    val isCompleted: Boolean = false
)

data class MonthlyRateTotalUiModel(
    val currency: String,
    val totalFormatted: String
)

data class GoalsAggregateSummaryUiModel(
    val currency: String = Currencies.supported.first(),
    val monthlyRateFormatted: String = formatGoalMoney(0L, currency),
    val savedFormatted: String = formatGoalMoney(0L, currency),
    val targetFormatted: String = formatGoalMoney(0L, currency),
    val percentLabel: String = "0%",
    val progressFraction: Float = 0f
)

data class GoalsState(
    val isLoading: Boolean = true,
    val ownerId: String? = null,
    val goals: List<GoalCardUiModel> = emptyList(),
    val error: String? = null,
    val totalMonthlyRates: List<MonthlyRateTotalUiModel> = emptyList(),
    val aggregateSummary: GoalsAggregateSummaryUiModel = GoalsAggregateSummaryUiModel(),
    val activeGoalCount: Int = 0,
    val streakSummary: GoalStreakSummary = GoalStreakSummary()
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
    const val CONTRIBUTION_SAVE_ERROR = "Could not record your contribution. Please check the amount and try again."
    const val CONTRIBUTION_AMOUNT_POSITIVE = "Contribution amount must be greater than zero."
    const val ACCOUNT_REQUIRED = "Select a funded account matching the goal currency."
    const val ACCOUNT_NOT_FOUND = "Selected funding account was not found."
    const val CASH_WALLET_REQUIRED = "Set up your Cash Wallet first"
    const val CURRENCY_MISMATCH = "Selected account currency must match the goal currency."
    const val INSUFFICIENT_FUNDS = "Amount exceeds available funds in the selected account or Net Worth."
    const val NAME_BLANK = "Enter a goal name."
    const val NAME_TOO_LONG = "Goal name must be 100 characters or fewer."
    const val DESCRIPTION_TOO_LONG = "Description must be 500 characters or fewer."
    const val INVALID_CURRENCY = "Unsupported currency code."
    const val INVALID_AMOUNT = "Enter a valid unsigned numeric amount matching the currency precision."
    const val TARGET_NON_POSITIVE = "Target amount must be greater than zero."
    const val INVALID_DATE = "Select a valid target date."
    const val DATE_NOT_IN_FUTURE = "Target date must be in the future."

    fun noCurrencyAccounts(currency: String): String =
        "No $currency accounts found. Add a $currency account with funds to transfer to this goal."

    fun noFundedCurrencyAccounts(currency: String): String =
        "No $currency accounts have available balance. Add funds to a $currency account first."
}

fun formatGoalMoney(amountMinor: Long, currency: String): String {
    val safeCurrency = if (currency in Currencies.supported) currency else Currencies.supported.first()
    return "$safeCurrency ${MoneyUtils.formatMinor(amountMinor, safeCurrency)}"
}

fun formatGoalMoney(amountMinor: BigInteger, currency: String): String {
    val safeCurrency = if (currency in Currencies.supported) currency else Currencies.supported.first()
    return "$safeCurrency ${MoneyUtils.formatMinor(amountMinor, safeCurrency)}"
}

fun formatGoalMonthlyRate(rateMinor: Long, currency: String): String =
    "${formatGoalMoney(rateMinor, currency)} / month"

fun formatGoalMonthlyRate(rateMinor: BigInteger, currency: String): String {
    val safeCurrency = if (currency in Currencies.supported) currency else Currencies.supported.first()
    return "$safeCurrency ${MoneyUtils.formatMinor(rateMinor, safeCurrency)} / month"
}

fun calculateMonthlyRateTotals(
    goals: List<FinancialGoal>,
    todayEpochDay: Long
): List<MonthlyRateTotalUiModel> {
    val totalsByCurrency = linkedMapOf<String, BigInteger>()
    for (goal in goals) {
        val rateMinor = goal.requiredMonthlySavingsMinor(todayEpochDay) ?: continue
        val safeCurrency = if (goal.currency in Currencies.supported) {
            goal.currency
        } else {
            Currencies.supported.first()
        }
        val current = totalsByCurrency[safeCurrency] ?: BigInteger.ZERO
        totalsByCurrency[safeCurrency] = current.add(BigInteger.valueOf(rateMinor))
    }
    return Currencies.supported.mapNotNull { currency ->
        totalsByCurrency[currency]?.let { totalMinor ->
            MonthlyRateTotalUiModel(
                currency = currency,
                totalFormatted = formatGoalMonthlyRate(totalMinor, currency)
            )
        }
    }
}

private val GOAL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

fun formatGoalEpochDay(epochDay: Long): String =
    runCatching { LocalDate.ofEpochDay(epochDay).format(GOAL_DATE_FORMATTER) }
        .getOrElse { epochDay.toString() }

fun goalErrorMessage(error: Throwable, fallback: String): String {
    if (error is GoalContributionException) {
        return when (error.error) {
            GoalContributionError.SIGN_IN_REQUIRED -> GoalMessages.SIGN_IN
            GoalContributionError.GOAL_NOT_FOUND -> GoalMessages.NOT_FOUND
            GoalContributionError.INVALID_ID, GoalContributionError.INVALID_GOAL_ID -> fallback
            GoalContributionError.INVALID_CURRENCY -> GoalMessages.INVALID_CURRENCY
            GoalContributionError.INVALID_AMOUNT -> GoalMessages.INVALID_AMOUNT
            GoalContributionError.AMOUNT_NON_POSITIVE -> GoalMessages.CONTRIBUTION_AMOUNT_POSITIVE
            GoalContributionError.INVALID_DATE -> GoalMessages.INVALID_DATE
            GoalContributionError.ACCOUNT_REQUIRED -> GoalMessages.ACCOUNT_REQUIRED
            GoalContributionError.ACCOUNT_NOT_FOUND -> GoalMessages.ACCOUNT_NOT_FOUND
            GoalContributionError.CASH_WALLET_REQUIRED -> GoalMessages.CASH_WALLET_REQUIRED
            GoalContributionError.CURRENCY_MISMATCH -> GoalMessages.CURRENCY_MISMATCH
            GoalContributionError.INSUFFICIENT_FUNDS -> GoalMessages.INSUFFICIENT_FUNDS
        }
    }
    if (error is TransactionException && error.error == TransactionError.INSUFFICIENT_BALANCE) {
        return GoalMessages.INSUFFICIENT_FUNDS
    }
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

fun formatCompactPercent(
    savedMinor: BigInteger,
    targetMinor: BigInteger,
    isCompleted: Boolean = savedMinor >= targetMinor && targetMinor > BigInteger.ZERO
): String {
    if (targetMinor <= BigInteger.ZERO) return "0%"
    if (isCompleted || savedMinor >= targetMinor) return "100%"
    if (savedMinor <= BigInteger.ZERO) return "0%"
    val tenths = BigDecimal(savedMinor)
        .multiply(BigDecimal.valueOf(1_000L))
        .divide(BigDecimal(targetMinor), 0, RoundingMode.DOWN)
        .toInt()
        .coerceIn(0, 999)
    return if (tenths % 10 == 0) {
        "${tenths / 10}%"
    } else {
        "${tenths / 10}.${tenths % 10}%"
    }
}

fun formatTimelineRemaining(
    daysLeft: Long,
    isOverdue: Boolean,
    isCompleted: Boolean
): String = when {
    isCompleted -> "Goal reached"
    isOverdue -> "Overdue"
    daysLeft >= 14L -> "${daysLeft / 7L} weeks left"
    daysLeft == 1L -> "1 day left"
    else -> "$daysLeft days left"
}

fun buildGoalsAggregateSummary(
    currency: String,
    monthlyRateMinor: BigInteger,
    savedMinor: BigInteger,
    targetMinor: BigInteger
): GoalsAggregateSummaryUiModel {
    val safeCurrency = if (currency in Currencies.supported) currency else Currencies.supported.first()
    val safeRate = monthlyRateMinor.max(BigInteger.ZERO)
    val safeSaved = savedMinor.max(BigInteger.ZERO)
    val safeTarget = targetMinor.max(BigInteger.ZERO)
    val isCompleted = safeTarget > BigInteger.ZERO && safeSaved >= safeTarget
    val progressFraction = when {
        safeTarget <= BigInteger.ZERO -> 0f
        isCompleted -> 1f
        else -> BigDecimal(safeSaved)
            .divide(BigDecimal(safeTarget), 4, RoundingMode.DOWN)
            .toFloat()
            .coerceIn(0f, 1f)
    }
    return GoalsAggregateSummaryUiModel(
        currency = safeCurrency,
        monthlyRateFormatted = formatGoalMoney(safeRate, safeCurrency),
        savedFormatted = formatGoalMoney(safeSaved, safeCurrency),
        targetFormatted = formatGoalMoney(safeTarget, safeCurrency),
        percentLabel = formatCompactPercent(safeSaved, safeTarget, isCompleted),
        progressFraction = progressFraction
    )
}

fun FinancialGoal.toCardUiModel(
    todayEpochDay: Long,
    contributions: List<GoalContribution> = emptyList()
): GoalCardUiModel {
    val overdue = isOverdue(todayEpochDay)
    val active = isActive(todayEpochDay)
    val daysLeft = daysRemaining(todayEpochDay)
    val daysLabel = if (overdue) {
        "Overdue"
    } else {
        "$daysLeft days left"
    }
    val monthlyRate = requiredMonthlySavingsMinor(todayEpochDay)?.let { rateMinor ->
        formatGoalMonthlyRate(rateMinor, currency)
    }
    val progress = GoalProgressPolicy.evaluateGoalProgress(this, contributions)
    val savedFormatted = formatGoalMoney(progress.savedMinor, currency)
    val percentLabel = if (progress.isCompleted) {
        "100% — Goal Reached"
    } else {
        "${progress.displayPercent}% saved"
    }
    val percentValueLabel = formatCompactPercent(
        savedMinor = progress.savedMinor,
        targetMinor = BigInteger.valueOf(targetMinor.coerceAtLeast(0L)),
        isCompleted = progress.isCompleted
    )
    val timelineRemainingLabel = formatTimelineRemaining(
        daysLeft = daysLeft,
        isOverdue = overdue,
        isCompleted = progress.isCompleted
    )
    val nextMilestoneLabel = if (progress.isCompleted) {
        GoalMilestoneThreshold.COMPLETE.motivationText
    } else {
        val next = progress.nextMilestone
        if (next != null) {
            "Next milestone: ${next.threshold.percent}% (${formatGoalMoney(next.requiredMinor, currency)})"
        } else {
            "Next milestone: 25%"
        }
    }
    val anyContributionSyncing = contributions.any { it.goalId == id && it.pendingSync }
    return GoalCardUiModel(
        id = id,
        name = name,
        description = description,
        currency = currency,
        targetFormatted = formatGoalMoney(targetMinor, currency),
        targetDateFormatted = formatGoalEpochDay(targetDateEpochDay),
        daysRemainingLabel = daysLabel,
        isOverdue = overdue,
        isSyncing = pendingSync || anyContributionSyncing,
        isActive = active,
        monthlyRateFormatted = monthlyRate,
        progressFraction = progress.progressFraction,
        savedFormatted = savedFormatted,
        percentLabel = percentLabel,
        percentValueLabel = percentValueLabel,
        timelineRemainingLabel = timelineRemainingLabel,
        nextMilestoneLabel = nextMilestoneLabel,
        isCompleted = progress.isCompleted
    )
}

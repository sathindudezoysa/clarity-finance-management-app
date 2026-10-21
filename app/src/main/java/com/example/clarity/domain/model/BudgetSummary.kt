package com.example.clarity.domain.model

import com.example.clarity.domain.util.MoneyUtils
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BudgetCurrencySummary(
    val currency: String,
    val totalLimit: BigInteger,
    val totalSpent: BigInteger,
    val remaining: BigInteger,
    val percent: BigDecimal,
    val percentText: String,
    val progress: Float,
    val uncounted: Int,
    val budgetCount: Int
) {
    val incomplete: Boolean get() = uncounted > 0
}

data class BudgetMonthView(
    val month: String,
    val active: List<BudgetStatus>,
    val disabled: List<BudgetStatus>,
    val summaries: List<BudgetCurrencySummary>
)

data class BudgetSelectionState(
    val selectedMonth: String,
    val handledSelectedId: String? = null
)

data class BudgetEditorValidation(
    val budget: Budget? = null,
    val categoryError: String? = null,
    val monthError: String? = null,
    val amountError: String? = null,
    val duplicateError: String? = null
) {
    val isValid: Boolean
        get() = budget != null && categoryError == null && monthError == null &&
            amountError == null && duplicateError == null
    val firstError: String?
        get() = categoryError ?: monthError ?: amountError ?: duplicateError
}

val BudgetStatus.percent: BigDecimal
    get() = BudgetSummary.percent(spent, budget.limitMinor.toBigInteger())

val BudgetStatus.percentText: String
    get() = BudgetSummary.formatPercent(spent, budget.limitMinor.toBigInteger())

val BudgetStatus.progress: Float
    get() = BudgetSummary.progress(spent, budget.limitMinor.toBigInteger())

object BudgetSummary {
    const val CATEGORY_REQUIRED = "Choose a category"
    const val CATEGORY_TOO_LONG = "Category must be 100 characters or fewer."
    const val MONTH_INVALID = "Select a valid month."
    const val AMOUNT_INVALID = "Please enter a valid amount greater than zero."
    const val DUPLICATE_BUDGET = "A budget already exists for this category and month. Edit the existing budget instead."

    fun shiftMonth(month: String, delta: Long): String =
        YearMonth.parse(month).plusMonths(delta).toString()

    fun formatMonth(month: String, locale: Locale = Locale.ENGLISH): String =
        runCatching { YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)) }
            .getOrDefault(month)

    fun isValidMonth(month: String): Boolean =
        month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) &&
            runCatching { YearMonth.parse(month).year > 0 }.getOrDefault(false)

    fun percent(spent: BigInteger, limit: BigInteger): BigDecimal {
        require(limit > BigInteger.ZERO) { "Limit must be positive" }
        if (spent <= BigInteger.ZERO) return BigDecimal.ZERO
        return BigDecimal(spent)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal(limit), 2, RoundingMode.DOWN)
            .stripTrailingZeros()
            .let { if (it.scale() < 0) it.setScale(0) else it }
    }

    fun formatPercent(spent: BigInteger, limit: BigInteger): String =
        "${percent(spent, limit).toPlainString()}%"

    fun progress(spent: BigInteger, limit: BigInteger): Float {
        require(limit > BigInteger.ZERO) { "Limit must be positive" }
        if (spent <= BigInteger.ZERO) return 0f
        if (spent >= limit) return 1f
        return BigDecimal(spent)
            .divide(BigDecimal(limit), 4, RoundingMode.DOWN)
            .toFloat()
            .coerceIn(0f, 1f)
    }

    fun forMonth(statuses: List<BudgetStatus>, selectedMonth: String): BudgetMonthView {
        val inMonth = statuses.filter { it.budget.month == selectedMonth }
        val active = inMonth.filter { it.budget.active }
        val disabled = inMonth.filter { !it.budget.active }
        return BudgetMonthView(selectedMonth, active, disabled, summarize(active))
    }

    fun summarize(statuses: List<BudgetStatus>): List<BudgetCurrencySummary> =
        statuses.filter { it.budget.active }
            .groupBy { it.budget.currency }
            .map { (currency, items) ->
                val totalLimit = items.fold(BigInteger.ZERO) { acc, item -> acc + item.budget.limitMinor.toBigInteger() }
                val totalSpent = items.fold(BigInteger.ZERO) { acc, item -> acc + item.spent }
                val remaining = totalLimit - totalSpent
                val uncounted = items.sumOf { it.uncounted }
                val pct = percent(totalSpent, totalLimit)
                BudgetCurrencySummary(
                    currency = currency,
                    totalLimit = totalLimit,
                    totalSpent = totalSpent,
                    remaining = remaining,
                    percent = pct,
                    percentText = "${pct.toPlainString()}%",
                    progress = progress(totalSpent, totalLimit),
                    uncounted = uncounted,
                    budgetCount = items.size
                )
            }

    fun resolveSelection(
        selectedMonth: String,
        handledSelectedId: String?,
        selectedId: String?,
        statuses: List<BudgetStatus>
    ): BudgetSelectionState {
        if (selectedId == null) return BudgetSelectionState(selectedMonth, null)
        if (selectedId == handledSelectedId) return BudgetSelectionState(selectedMonth, handledSelectedId)
        val match = statuses.firstOrNull { it.budget.id == selectedId }
            ?: return BudgetSelectionState(selectedMonth, handledSelectedId)
        return BudgetSelectionState(match.budget.month, selectedId)
    }

    fun scrollIndexFor(selectedId: String?, view: BudgetMonthView, headerItemCount: Int = 1): Int? {
        if (selectedId == null) return null
        val activeIndex = view.active.indexOfFirst { it.budget.id == selectedId }
        if (activeIndex >= 0) return headerItemCount + view.summaries.size + activeIndex
        val disabledIndex = view.disabled.indexOfFirst { it.budget.id == selectedId }
        if (disabledIndex >= 0) return headerItemCount + view.summaries.size + view.active.size + 1 + disabledIndex
        return null
    }

    fun validateEditor(
        existing: Budget?,
        rawCategory: String,
        rawMonth: String,
        rawAmount: String,
        currency: String,
        owner: String,
        statuses: List<BudgetStatus>
    ): BudgetEditorValidation {
        val effectiveCategoryRaw = existing?.category ?: rawCategory
        val effectiveMonth = existing?.month ?: rawMonth.trim()
        val categoryTrimmed = effectiveCategoryRaw.trim()
        val categoryError = when {
            categoryTrimmed.isEmpty() -> CATEGORY_REQUIRED
            categoryTrimmed.length > 100 -> CATEGORY_TOO_LONG
            else -> null
        }
        val monthError = if (isValidMonth(effectiveMonth)) null else MONTH_INVALID
        val normalizedCategory = if (categoryError == null) TransactionTags.normalize(categoryTrimmed) else ""
        val candidateId = if (categoryError == null && monthError == null) {
            BudgetPolicy.id(effectiveMonth, normalizedCategory)
        } else null
        val duplicateError = if (existing == null && candidateId != null &&
            statuses.any { it.budget.active && it.budget.id == candidateId }
        ) DUPLICATE_BUDGET else null
        val prior = existing ?: candidateId?.let { id ->
            statuses.firstOrNull { !it.budget.active && it.budget.id == id }?.budget
        }
        val effectiveCurrency = prior?.currency ?: currency
        val parsedAmount = runCatching {
            MoneyUtils.parseMinor(rawAmount, effectiveCurrency).getOrThrow()
        }.getOrNull()
        val amountError = if (parsedAmount == null || parsedAmount <= 0L) AMOUNT_INVALID else null

        if (categoryError != null || monthError != null || amountError != null || duplicateError != null || candidateId == null) {
            return BudgetEditorValidation(
                budget = null,
                categoryError = categoryError,
                monthError = monthError,
                amountError = amountError,
                duplicateError = duplicateError
            )
        }
        val createdAt = prior?.createdAt ?: 0L
        val updatedAt = maxOf(createdAt, prior?.updatedAt ?: 0L)
        val built = Budget(
            id = existing?.id ?: candidateId,
            userId = owner,
            month = effectiveMonth,
            category = existing?.category ?: normalizedCategory,
            limitMinor = parsedAmount!!,
            currency = effectiveCurrency,
            active = true,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        return runCatching {
            BudgetPolicy.validate(built)
            BudgetEditorValidation(budget = built)
        }.getOrElse {
            BudgetEditorValidation(amountError = AMOUNT_INVALID)
        }
    }
}


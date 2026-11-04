package com.example.clarity.domain.export

import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.domain.model.Currencies
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object MonthlyBudgetReport {
    const val REPORT_TITLE = "Clarity - Monthly Budget Report"
    const val FOOTER_NOTE = "Limits are the current limits of each budget. Yearly budgets are not included."

    private val generatedFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    data class Row(
        val month: String,
        val category: String,
        val limit: String,
        val spent: String,
        val remaining: String,
        val usedPercent: String,
        val status: String,
        val currency: String,
        val uncounted: Int,
        val budgetStartMonth: String,
        val budgetId: String
    )

    data class CurrencySummary(
        val currency: String,
        val budgetCount: Int,
        val totalLimit: String,
        val totalSpent: String,
        val totalRemaining: String,
        val atOrOverLimitCount: Int,
        val totalUncounted: Int
    )

    data class ReportModel(
        val title: String,
        val monthLabel: String,
        val generatedLine: String,
        val rows: List<Row>,
        val summaries: List<CurrencySummary>,
        val footerNote: String = FOOTER_NOTE
    )

    fun build(
        statuses: List<BudgetStatus>,
        month: YearMonth,
        generatedAtMillis: Long,
        statusLabel: (BudgetThreshold) -> String,
        zone: ZoneId = ZoneId.systemDefault()
    ): ReportModel {
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        val monthLabel = month.format(monthFormatter)
        val generatedTimestamp = Instant.ofEpochMilli(generatedAtMillis)
            .atZone(zone)
            .format(generatedFormatter)
        val generatedLine = "Generated on $generatedTimestamp"
        val monthKey = month.toString()

        val rows = statuses.map { status ->
            val budget = status.budget
            val digits = Currencies.digits(budget.currency)
            val limitMinorBig = BigInteger.valueOf(budget.limitMinor)
            val limitStr = formatPlainDecimal(limitMinorBig, digits)
            val spentStr = formatPlainDecimal(status.spent, digits)
            val remainingStr = formatPlainDecimal(status.remaining, digits)
            val usedPercentStr = BigDecimal(status.spent)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(budget.limitMinor), 1, RoundingMode.DOWN)
                .toPlainString()

            Row(
                month = monthKey,
                category = budget.category,
                limit = limitStr,
                spent = spentStr,
                remaining = remainingStr,
                usedPercent = usedPercentStr,
                status = statusLabel(status.threshold),
                currency = budget.currency,
                uncounted = status.uncounted,
                budgetStartMonth = budget.month,
                budgetId = budget.id
            )
        }

        val summaries = statuses
            .groupBy { it.budget.currency }
            .toSortedMap()
            .map { (currency, group) ->
                val digits = Currencies.digits(currency)
                var totalLimitMinor = BigInteger.ZERO
                var totalSpentMinor = BigInteger.ZERO
                var totalRemainingMinor = BigInteger.ZERO
                var atOrOverCount = 0
                var totalUncounted = 0

                for (item in group) {
                    val limitBig = BigInteger.valueOf(item.budget.limitMinor)
                    totalLimitMinor += limitBig
                    totalSpentMinor += item.spent
                    totalRemainingMinor += item.remaining
                    if (item.threshold == BudgetThreshold.REACHED || item.threshold == BudgetThreshold.EXCEEDED) {
                        atOrOverCount++
                    }
                    totalUncounted += item.uncounted
                }

                CurrencySummary(
                    currency = currency,
                    budgetCount = group.size,
                    totalLimit = formatPlainDecimal(totalLimitMinor, digits),
                    totalSpent = formatPlainDecimal(totalSpentMinor, digits),
                    totalRemaining = formatPlainDecimal(totalRemainingMinor, digits),
                    atOrOverLimitCount = atOrOverCount,
                    totalUncounted = totalUncounted
                )
            }

        return ReportModel(
            title = REPORT_TITLE,
            monthLabel = monthLabel,
            generatedLine = generatedLine,
            rows = rows,
            summaries = summaries,
            footerNote = FOOTER_NOTE
        )
    }

    private fun formatPlainDecimal(minorUnits: BigInteger, digits: Int): String =
        BigDecimal(minorUnits, digits).toPlainString()
}


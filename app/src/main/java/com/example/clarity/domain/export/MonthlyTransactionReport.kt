package com.example.clarity.domain.export

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.TransactionCalendar
import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CurrencySummary(
    val currency: String,
    val income: String,
    val expense: String,
    val net: String
)

data class ReportRow(
    val date: String,
    val type: String,
    val category: String,
    val account: String,
    val description: String,
    val amountLine1: String,
    val amountLine2: String?
)

data class ReportModel(
    val title: String,
    val monthLabel: String,
    val generatedLine: String,
    val recordCount: Int,
    val summaries: List<CurrencySummary>,
    val pendingNote: String?,
    val tableHeader: List<String>,
    val rows: List<ReportRow>,
    val footerNote: String
)

object MonthlyTransactionReport {
    const val TITLE = "Clarity - Monthly Transaction Records"
    const val PENDING_CONVERSION_LINE = "Conversion pending"
    const val FOOTER_NOTE =
        "Long descriptions may be shortened in this PDF. The CSV export contains the full text."
    val TABLE_HEADER: List<String> = listOf(
        "Date",
        "Type",
        "Category",
        "Account",
        "Description",
        "Amount"
    )

    private val GENERATED_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun build(
        records: List<Transaction>,
        accountLabel: (String?) -> String,
        month: YearMonth,
        generatedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): ReportModel {
        val sortedRecords = records.sortedWith(
            compareBy<Transaction> { it.dateEpochMillis }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )

        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        val monthLabel = month.format(monthFormatter)
        val generatedTimestamp = Instant.ofEpochMilli(generatedAtMillis)
            .atZone(zone)
            .format(GENERATED_FORMATTER)
        val generatedLine = "Generated on $generatedTimestamp"

        val summaries = sortedRecords
            .filter { !it.pendingConversion }
            .groupBy { it.targetCurrency }
            .map { (currency, currencyRows) ->
                fun sum(type: TransactionType): BigInteger =
                    currencyRows
                        .filter { it.type == type }
                        .fold(BigInteger.ZERO) { total, t ->
                            total + BigInteger.valueOf(checkNotNull(t.conversion).amountMinor)
                        }

                val incomeMinor = sum(TransactionType.INCOME)
                val expenseMinor = sum(TransactionType.EXPENSE)
                val netMinor = incomeMinor - expenseMinor
                CurrencySummary(
                    currency = currency,
                    income = MoneyUtils.formatMinor(incomeMinor, currency),
                    expense = MoneyUtils.formatMinor(expenseMinor, currency),
                    net = MoneyUtils.formatMinor(netMinor, currency)
                )
            }

        val pendingCount = sortedRecords.count { it.pendingConversion }
        val pendingNote = if (pendingCount > 0) {
            "$pendingCount record(s) with pending conversion are not included in the totals."
        } else {
            null
        }

        val reportRows = sortedRecords.map { t ->
            val date = TransactionCalendar.dateAt(t.dateEpochMillis, zone).toString()
            val type = if (t.type == TransactionType.INCOME) "Income" else "Expense"
            val signedPrimary = formatSignedAmount(t.amountMinor, t.currency, t.type)
            val amountLine1 = "${t.currency} $signedPrimary"
            val conversion = t.conversion
            val amountLine2 = when {
                conversion == null -> PENDING_CONVERSION_LINE
                t.currency != t.targetCurrency -> {
                    val signedConverted = formatSignedAmount(
                        conversion.amountMinor,
                        t.targetCurrency,
                        t.type
                    )
                    "= ${t.targetCurrency} $signedConverted"
                }
                else -> null
            }

            ReportRow(
                date = date,
                type = type,
                category = t.category,
                account = accountLabel(t.accountId),
                description = t.note,
                amountLine1 = amountLine1,
                amountLine2 = amountLine2
            )
        }

        return ReportModel(
            title = TITLE,
            monthLabel = monthLabel,
            generatedLine = generatedLine,
            recordCount = sortedRecords.size,
            summaries = summaries,
            pendingNote = pendingNote,
            tableHeader = TABLE_HEADER,
            rows = reportRows,
            footerNote = FOOTER_NOTE
        )
    }

    private fun formatSignedAmount(
        amountMinor: Long,
        currency: String,
        type: TransactionType
    ): String {
        val plain = MoneyUtils.formatMinor(amountMinor, currency)
        return if (type == TransactionType.EXPENSE) "-$plain" else plain
    }
}


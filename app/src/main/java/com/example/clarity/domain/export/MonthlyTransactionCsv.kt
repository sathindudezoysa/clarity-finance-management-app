package com.example.clarity.domain.export

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.TransactionCalendar
import java.time.YearMonth
import java.time.ZoneId

object MonthlyTransactionCsv {
    const val HEADER =
        "Date,Type,Category,Account,Description,Amount,Currency,Converted Amount,Converted Currency,Exchange Rate,Conversion Status,Transaction ID"

    private const val CRLF = "\r\n"
    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun fileName(month: YearMonth): String = "clarity-transactions-$month.csv"

    fun recordsFor(
        transactions: List<Transaction>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Transaction> = transactions
        .filter { t -> YearMonth.from(TransactionCalendar.dateAt(t.dateEpochMillis, zone)) == month }
        .sortedWith(
            compareBy<Transaction> { it.dateEpochMillis }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )

    fun build(
        records: List<Transaction>,
        accountLabel: (String?) -> String,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        sb.append(HEADER).append(CRLF)
        for (t in records) {
            val date = TransactionCalendar.dateAt(t.dateEpochMillis, zone).toString()
            val type = if (t.type == TransactionType.INCOME) "Income" else "Expense"
            val category = escapeCsv(sanitizeFormula(t.category))
            val account = escapeCsv(sanitizeFormula(accountLabel(t.accountId)))
            val description = escapeCsv(sanitizeFormula(t.note))
            val amount = formatSignedAmount(t.amountMinor, t.currency, t.type)
            val currency = escapeCsv(t.currency)
            val conversion = t.conversion
            val convertedAmount = if (conversion != null) {
                formatSignedAmount(conversion.amountMinor, t.targetCurrency, t.type)
            } else {
                ""
            }
            val convertedCurrency = if (conversion != null) escapeCsv(t.targetCurrency) else ""
            val exchangeRate = if (conversion != null) escapeCsv(conversion.rate) else ""
            val conversionStatus = when {
                conversion == null -> "Pending"
                t.currency == t.targetCurrency -> "Same currency"
                else -> "Converted"
            }
            val transactionId = escapeCsv(t.id)

            sb.append(date).append(',')
                .append(type).append(',')
                .append(category).append(',')
                .append(account).append(',')
                .append(description).append(',')
                .append(amount).append(',')
                .append(currency).append(',')
                .append(convertedAmount).append(',')
                .append(convertedCurrency).append(',')
                .append(exchangeRate).append(',')
                .append(conversionStatus).append(',')
                .append(transactionId)
                .append(CRLF)
        }
        return sb.toString()
    }

    private fun formatSignedAmount(
        amountMinor: Long,
        currency: String,
        type: TransactionType
    ): String {
        val plain = MoneyUtils.formatMinor(amountMinor, currency)
        return if (type == TransactionType.EXPENSE) "-$plain" else plain
    }

    private fun sanitizeFormula(value: String): String {
        if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) {
            return "'$value"
        }
        return value
    }

    private fun escapeCsv(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        if (!needsQuoting) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}



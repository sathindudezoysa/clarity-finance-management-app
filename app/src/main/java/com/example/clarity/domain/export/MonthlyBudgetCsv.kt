package com.example.clarity.domain.export

object MonthlyBudgetCsv {
    const val HEADER =
        "Month,Category,Limit,Spent,Remaining,Used (%),Status,Currency,Uncounted Expenses,Budget Start Month,Budget ID"

    private const val CRLF = "\r\n"

    fun build(report: MonthlyBudgetReport.ReportModel): String {
        val sb = StringBuilder()
        sb.append(HEADER).append(CRLF)
        for (row in report.rows) {
            sb.append(escapeCsv(row.month)).append(',')
            sb.append(escapeCsv(sanitizeFormula(row.category))).append(',')
            sb.append(row.limit).append(',')
            sb.append(row.spent).append(',')
            sb.append(row.remaining).append(',')
            sb.append(row.usedPercent).append(',')
            sb.append(escapeCsv(row.status)).append(',')
            sb.append(escapeCsv(row.currency)).append(',')
            sb.append(row.uncounted).append(',')
            sb.append(escapeCsv(row.budgetStartMonth)).append(',')
            sb.append(escapeCsv(row.budgetId)).append(CRLF)
        }
        return sb.toString()
    }

    private fun sanitizeFormula(value: String): String {
        if (value.isEmpty()) return value
        return when (value[0]) {
            '=', '+', '-', '@', '\t', '\r' -> "'$value"
            else -> value
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        if (!needsQuotes) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}


package com.example.clarity.domain.export

import java.time.YearMonth

enum class BudgetExportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    fun fileName(month: YearMonth): String = "clarity-budgets-$month.$extension"
}


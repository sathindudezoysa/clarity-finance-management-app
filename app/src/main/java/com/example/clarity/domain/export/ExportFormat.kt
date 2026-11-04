package com.example.clarity.domain.export

import java.time.YearMonth

enum class ExportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf");

    fun fileName(month: YearMonth): String = "clarity-transactions-$month.$extension"
}


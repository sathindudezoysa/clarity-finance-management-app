package com.example.clarity.presentation.budgets.export

import com.example.clarity.domain.export.BudgetExportFormat

object BudgetExportMessages {
    const val EXPORT_BUTTON = "Export"
    const val SCREEN_TITLE = "Export budgets"
    const val BACK = "Back"
    const val PREVIOUS_MONTH = "Previous month"
    const val NEXT_MONTH = "Next month"
    const val SECTION_MONTH = "MONTH"
    const val SECTION_FORMAT = "FORMAT"
    const val SECTION_SUMMARY = "SUMMARY"
    const val FORMAT_CSV = "CSV"
    const val FORMAT_XLSX = "Excel (.xlsx)"
    const val CSV_DESCRIPTION = "Comma-separated values file. Opens in Excel, Google Sheets, or any spreadsheet app."
    const val XLSX_DESCRIPTION = "Excel workbook with Budgets and Summary sheets."
    const val NO_BUDGETS = "No monthly budgets for this month."
    const val YEARLY_NOTE = "Yearly budgets are not included."
    const val SAVE_TO_DEVICE = "Save to device"
    const val SIGN_IN_REQUIRED = "Sign in to export your budgets."
    const val SAVE_ERROR = "Unable to save budget export. Please try again."

    fun rowCountText(count: Int): String = when {
        count <= 0 -> NO_BUDGETS
        count == 1 -> "1 budget will be exported."
        else -> "$count budgets will be exported."
    }

    fun saveSuccess(count: Int, format: BudgetExportFormat): String {
        val label = when (format) {
            BudgetExportFormat.CSV -> FORMAT_CSV
            BudgetExportFormat.XLSX -> FORMAT_XLSX
        }
        return if (count == 1) {
            "Saved 1 budget as $label."
        } else {
            "Saved $count budgets as $label."
        }
    }
}

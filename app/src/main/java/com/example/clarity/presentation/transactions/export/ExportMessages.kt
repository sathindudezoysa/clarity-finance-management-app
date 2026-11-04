package com.example.clarity.presentation.transactions.export

import com.example.clarity.domain.export.ExportFormat

object ExportMessages {
    const val EXPORT_BUTTON = "Export"
    const val CANCEL_BUTTON = "Cancel"
    const val DIALOG_TITLE = "Export monthly records"
    const val SCREEN_TITLE = "Export records"
    const val BACK = "Back"
    const val PREVIOUS_MONTH = "Previous month"
    const val NEXT_MONTH = "Next month"
    const val SECTION_MONTH = "MONTH"
    const val SECTION_FORMAT = "FORMAT"
    const val SECTION_SUMMARY = "SUMMARY"
    const val FORMAT_LABEL = "Format"
    const val FORMAT_CSV = "CSV"
    const val FORMAT_PDF = "PDF"
    const val CSV_DESCRIPTION = "Spreadsheet file with every field. Opens in Excel or Sheets."
    const val PDF_DESCRIPTION = "Printable monthly report with a summary."
    const val NO_RECORDS = "No records for this month."
    const val EXPORT_ERROR = "Unable to export transactions. Please try again."

    fun recordCountText(count: Int): String = when {
        count <= 0 -> NO_RECORDS
        count == 1 -> "1 record ready to export."
        else -> "$count records ready to export."
    }

    fun exportSuccess(count: Int, format: ExportFormat = ExportFormat.CSV): String =
        if (count == 1) {
            "Exported 1 record as ${format.name}."
        } else {
            "Exported $count records as ${format.name}."
        }
}

package com.example.clarity.presentation.transactions.export

object ExportMessages {
    const val EXPORT_BUTTON = "Export"
    const val CANCEL_BUTTON = "Cancel"
    const val DIALOG_TITLE = "Export monthly records"
    const val PREVIOUS_MONTH = "Previous month"
    const val NEXT_MONTH = "Next month"
    const val NO_RECORDS = "No records for this month."
    const val EXPORT_ERROR = "Unable to export transactions. Please try again."

    fun recordCountText(count: Int): String = when {
        count <= 0 -> NO_RECORDS
        count == 1 -> "1 record ready to export."
        else -> "$count records ready to export."
    }

    fun exportSuccess(count: Int): String =
        if (count == 1) "Exported 1 record." else "Exported $count records."
}

